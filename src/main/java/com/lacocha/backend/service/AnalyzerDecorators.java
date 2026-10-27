package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
        public Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request) {
            return inner.anomalies(request);
        }

        @Override
        public Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request) {
            return inner.trends(request);
        }

        @Override
        public Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request) {
            return inner.combination(request);
        }

        @Override
        public Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request) {
            return inner.projection(request);
        }

    }

    /**
     * Stops asking for a while after a failure.
     *
     * The reason is specific to how this is deployed. Render's free plan puts the AI service to
     * sleep after a spell unused, and waking it takes close to a minute. During that minute every
     * call waits out the full read timeout before giving up — and analysing one pond now takes
     * <em>three</em> calls, so the waiting multiplies. A farm syncing eight ponds in a row would
     * spend half an hour of threads waiting on a service that was never going to answer in time,
     * and the readings that did arrive would be the ones paying for it.
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

        private final Duration pause;

        public Resilient(Analyzer inner) {
            this(inner, PAUSE);
        }

        /**
         * With the pause spelled out, which is how the tests check that the door opens again.
         *
         * Waiting two real minutes in a test is not an option, and neither is trusting that it
         * reopens because the code looks like it should: the whole value of this decorator is in
         * the reopening. A circuit breaker that closes and never opens is a service switched off by
         * its first bad minute.
         */
        Resilient(Analyzer inner, Duration pause) {
            super(inner);
            this.pause = pause;
        }

        /** Whether the door is closed right now. Visible for the tests. */
        boolean closed() {
            return ServerClock.now().isBefore(closedUntil.get());
        }

        @Override
        public boolean available() {
            return closed() ? false : note(inner.available());
        }

        @Override
        public Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request) {
            return closed() ? Optional.empty() : note(inner.anomalies(request));
        }

        @Override
        public Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request) {
            return closed() ? Optional.empty() : note(inner.trends(request));
        }

        @Override
        public Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request) {
            return closed() ? Optional.empty() : note(inner.combination(request));
        }

        @Override
        public Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request) {
            return closed() ? Optional.empty() : note(inner.projection(request));
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
            Instant until = ServerClock.now().plus(pause);
            // Only the thread that actually closes the door writes the log line, so a round of
            // syncs failing at once leaves one line and not twenty-four
            if (closedUntil.getAndSet(until).equals(Instant.EPOCH)) {
                log.info("el servicio de IA no respondió: no se le vuelve a preguntar por {} segundos",
                        pause.toSeconds());
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
        public Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request) {
            return timed("anomalias", request.readings().size(),
                    () -> inner.anomalies(request), found -> found.size() + " anomalía(s)");
        }

        @Override
        public Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request) {
            return timed("tendencias", request.readings().size(),
                    () -> inner.trends(request), found -> found.size() + " tendencia(s)");
        }

        @Override
        public Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request) {
            return timed("combinaciones", request.readings().size(),
                    () -> inner.combination(request),
                    found -> found.isPresent() ? "una combinación rara" : "nada que reportar");
        }

        @Override
        public Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request) {
            return timed("proyeccion-lote", request.readings().size(),
                    () -> inner.projection(request),
                    found -> found.findings().size() + " hallazgo(s), tgc " + found.tgc());
        }

        private <T> Optional<T> timed(String route, int sent, java.util.function.Supplier<Optional<T>> call,
                java.util.function.Function<T, String> describe) {
            long start = System.nanoTime();
            Optional<T> answer = call.get();
            log.debug("{}: {} lectura(s) enviadas, {} en {} ms", route, sent,
                    answer.map(describe).orElse("sin respuesta"),
                    (System.nanoTime() - start) / 1_000_000);
            return answer;
        }
    }
}
