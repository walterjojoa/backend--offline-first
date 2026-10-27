package com.lacocha.backend.service;

import java.util.List;
import java.util.Optional;

import com.lacocha.backend.dto.Ai;

/**
 * Implementor side of the Bridge: everything this backend needs from the AI service, and nothing
 * else.
 *
 * {@link AiAnalysis} sits above it and decides what is worth analysing, when, and what to do with
 * what comes back. It never sees an HTTP client. That is what lets those decisions be tested without
 * the service running, and what lets the service be reached a different way tomorrow without
 * touching them.
 *
 * The service lives in its own repository (MODELO-IA-offline-first) and is deployed separately, so
 * "not there" is a normal state and not a failure: a farm can install the backend and the database
 * without it and lose nothing it had before.
 *
 * <h2>Why every method returns Optional</h2>
 *
 * An empty Optional means "could not ask": the service is not configured, is asleep, or failed. It
 * is <em>not</em> the same as a present answer that happens to be empty, which means "I asked, and
 * the service found nothing to report".
 *
 * Collapsing those two would quietly turn both into "nothing found", which reads as "everything is
 * fine". The service was built to never say that when it does not know — its own routes answer an
 * empty list or null rather than an error for exactly that reason — and the type that carries its
 * answer here has to preserve the distinction.
 *
 * <h2>The forecast is not here, on purpose</h2>
 *
 * The service's fifth route, {@code /pronostico-temperatura}, has its own client in
 * {@link AiClient}, and it belongs there rather than here. A screen asks for a forecast and waits
 * for it, so the right answer to a failure is a 503 it can show — which is what AiClient throws.
 * Everything behind this port runs on a background thread after a sync committed, where throwing
 * reaches nobody, so a failure has to come back as an absent value instead.
 *
 * The two also need different windows. The forecast fits its model against the air history, and
 * Open-Meteo gives 92 days of that, so it sends 92 days of readings; the detectors here work off a
 * month, because beyond that a pond's "normal" starts dragging in the change of season.
 */
public interface Analyzer {

    /**
     * Whether it is worth asking at all. Cheap and never throws: it is meant to be called before
     * assembling a request, which involves reading a month of readings out of the database.
     */
    boolean available();

    /** A value that is unusual for this pond, even inside the rule ranges. */
    Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request);

    /** Where each variable is heading, and when it would reach the rules' critical limit. */
    Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request);

    /**
     * A pairing of values this pond has never produced.
     *
     * The nested Optional is awkward and honest: the outer one is "could I ask", the inner one is
     * "was there anything to report". The service answers null both when the reading follows the
     * pattern and when there is not enough history to tell, and neither of those is a failure.
     */
    Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request);

    /** Projected weight, harvest date and survival of a batch. */
    Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request);

    /**
     * The analyzer for a deployment without an AI service: it answers "could not ask" to everything,
     * instantly.
     *
     * It exists so nothing above has to check for null or for a configuration flag. A farm that
     * installs the backend and the database without the Python service gets a system that works
     * exactly as before, because the AI adds and does not hold anything up.
     */
    static Analyzer absent() {
        return new Analyzer() {

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request) {
                return Optional.empty();
            }

            @Override
            public Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request) {
                return Optional.empty();
            }

            @Override
            public Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request) {
                return Optional.empty();
            }

            @Override
            public Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request) {
                return Optional.empty();
            }

        };
    }
}
