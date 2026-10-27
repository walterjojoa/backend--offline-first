package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lacocha.backend.dto.Ai;
import com.lacocha.backend.model.ServerClock;

/**
 * Capabilities of the AI service added from the outside and stackable, the same way
 * {@link EventStoreDecorators} does it for the event store. Neither {@link AiAnalysis} above nor
 * {@link RestAnalyzer} below knows they are there, and turning one off is not wrapping it.
 */
public final class AnalyzerDecorators {

    private AnalyzerDecorators() {
    }

    /** Base: an Analyzer that wraps another one and forwards everything untouched. */
    public abstract static class Decorator implements Analyzer {

        protected final Analyzer inner;

        protected Decorator(Analyzer inner) {
            this.inner = inner;
        }

        @Override
        public boolean available() {
            return inner.available();
        }

        @Override
        public Optional<Ai.WaterAnalysis> analyzeWater(Ai.WaterRequest request) {
            return inner.analyzeWater(request);
        }

        @Override
        public Optional<Ai.BatchProjection> projectBatch(Ai.BatchRequest request) {
            return inner.projectBatch(request);
        }
    }

    /**
     * Stops asking for a while after a failure.
     *
     * The reason is specific to how this is deployed. Render's free plan puts the AI service to
     * sleep after a spell unused, and waking it takes close to a minute. During that minute every
     * call waits out the full read timeout before giving up. A farm that syncs eight ponds in a row
     * would spend eight minutes of threads waiting on a service that was never going to answer in
     * time, and the readings that did arrive would be the ones paying for it.
     *
     * So the first failure closes the door for {@link #PAUSE}. While it is closed nothing is
     * called: {@code available()} answers false straight away and the analyses answer "could not
     * ask", which is exactly what they would have answered a minute later anyway. When the pause is
     * over the next call goes through, and if it works the door stays open.
     *
     * It is not a retry. Retrying a service that is asleep makes it worse: it is the same wait
     * again, several times over.
     */
    public static final class Resilient extends Decorator {

        private static final Logger log = LoggerFactory.getLogger(Resilient.class);

        /**
         * Long enough that a round of syncs does not keep running into the same wall, short enough
         * that a service that came back is picked up within a few minutes without anyone's help.
         */
        static final Duration PAUSE = Duration.ofMinutes(2);

        /** Until when the door stays closed. EPOCH means open. Shared between threads. */
        private final AtomicReference<Instant> closedUntil = new AtomicReference<>(Instant.EPOCH);

        public Resilient(Analyzer inner) {
            super(inner);
        }

        /** Visible for the tests, which cannot wait two real minutes. */
        boolean closed() {
            return ServerClock.now().isBefore(closedUntil.get());
        }

        @Override
        public boolean available() {
            if (closed()) {
                return false;
            }
            return note(inner.available());
        }

        @Override
        public Optional<Ai.WaterAnalysis> analyzeWater(Ai.WaterRequest request) {
            return closed() ? Optional.empty() : note(inner.analyzeWater(request));
        }

        @Override
        public Optional<Ai.BatchProjection> projectBatch(Ai.BatchRequest request) {
            return closed() ? Optional.empty() : note(inner.projectBatch(request));
        }

        private boolean note(boolean worked) {
            record(worked);
            return worked;
        }

        private <T> Optional<T> note(Optional<T> answer) {
            record(answer.isPresent());
            return answer;
        }

        private void record(boolean worked) {
            if (worked) {
                closedUntil.set(Instant.EPOCH);
                return;
            }
            Instant until = ServerClock.now().plus(PAUSE);
            // Only the thread that actually closes the door writes the log line, so a round of
            // syncs failing at once leaves one line and not eight
            if (closedUntil.getAndSet(until).equals(Instant.EPOCH)) {
                log.info("el servicio de IA no respondió: no se le vuelve a preguntar por {} minutos",
                        PAUSE.toMinutes());
            }
        }
    }

    /**
     * One line per call, with how long it took.
     *
     * It answers the question that comes up as soon as the service is deployed: whether an analysis
     * that produced nothing produced nothing because the pond is fine, or because the call never
     * got through. Only wrapped when its level is really on.
     */
    public static final class Trace extends Decorator {

        private static final Logger log = LoggerFactory.getLogger(Trace.class);

        public Trace(Analyzer inner) {
            super(inner);
        }

        /** Lets the wiring skip the wrapping altogether when nobody is reading the trace. */
        public static boolean enabled() {
            return log.isDebugEnabled();
        }

        @Override
        public Optional<Ai.WaterAnalysis> analyzeWater(Ai.WaterRequest request) {
            long start = System.nanoTime();
            Optional<Ai.WaterAnalysis> answer = inner.analyzeWater(request);
            log.debug("agua del estanque {}: {} lectura(s) enviadas, {} en {} ms",
                    request.pondId(), request.history().size(),
                    answer.map(a -> a.findings().size() + " hallazgo(s), detectores " + a.detectors())
                            .orElse("sin respuesta"),
                    (System.nanoTime() - start) / 1_000_000);
            return answer;
        }

        @Override
        public Optional<Ai.BatchProjection> projectBatch(Ai.BatchRequest request) {
            long start = System.nanoTime();
            Optional<Ai.BatchProjection> answer = inner.projectBatch(request);
            log.debug("lote {}: {} en {} ms", request.batchId(),
                    answer.map(p -> p.findings().size() + " hallazgo(s), tgc " + p.tgc())
                            .orElse("sin respuesta"),
                    (System.nanoTime() - start) / 1_000_000);
            return answer;
        }
    }
}
