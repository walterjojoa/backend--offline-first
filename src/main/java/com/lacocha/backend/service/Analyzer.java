package com.lacocha.backend.service;

import java.util.Optional;

import com.lacocha.backend.dto.Ai;

/**
 * Implementor side of the Bridge: everything this backend needs from the AI service, and nothing
 * else.
 *
 * {@link AiAnalysis} sits above it and decides what is worth analysing, when, and what to do with
 * what comes back. It never sees an HTTP client. That is what lets those decisions be tested
 * without the Python service running, and what lets the service be reached a different way
 * tomorrow without touching them.
 *
 * <h2>Why every method returns Optional</h2>
 *
 * An empty Optional means "could not ask": the service is not configured, is asleep, or failed.
 * It is <em>not</em> the same as a present {@link Ai.WaterAnalysis} whose {@code enoughData} is
 * false, which means "I asked, and the service answered that it does not have enough data to tell".
 *
 * Collapsing those two into one would quietly turn both into "nothing found", which reads as
 * "everything is fine". The whole service was built to never say that when it does not know, and
 * the type that carries its answer has to preserve the distinction.
 */
public interface Analyzer {

    /**
     * Whether it is worth asking at all. Cheap and never throws: it is meant to be called before
     * assembling a request, which involves reading a month of readings out of the database.
     */
    boolean available();

    Optional<Ai.WaterAnalysis> analyzeWater(Ai.WaterRequest request);

    Optional<Ai.BatchProjection> projectBatch(Ai.BatchRequest request);

    /**
     * The analyzer for a deployment without an AI service: it answers "could not ask" to
     * everything, instantly.
     *
     * It exists so that nothing above has to check for null or for a configuration flag. A farm
     * that installs the backend and the database without the Python service gets a system that
     * works exactly as before, because the AI adds and does not hold anything up.
     */
    static Analyzer absent() {
        return new Analyzer() {

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public Optional<Ai.WaterAnalysis> analyzeWater(Ai.WaterRequest request) {
                return Optional.empty();
            }

            @Override
            public Optional<Ai.BatchProjection> projectBatch(Ai.BatchRequest request) {
                return Optional.empty();
            }
        };
    }
}
