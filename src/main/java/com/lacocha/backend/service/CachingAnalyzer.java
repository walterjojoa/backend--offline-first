package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lacocha.backend.dto.Ai;
import com.lacocha.backend.model.ServerClock;

/**
 * Proxy: controls access to the AI service, so the same question is not asked twice.
 *
 * It stands between whoever wants an answer and {@link RestAnalyzer}, which is the only thing that
 * really reaches the service, and it answers from what it already knows whenever that is honest.
 *
 * <h2>Why a Proxy here and not another Decorator</h2>
 *
 * The two look alike from outside — both implement {@link Analyzer} and both wrap another one — and
 * the project now has an example of each, so the difference is worth stating.
 * {@link AnalyzerDecorators} <em>adds behaviour around</em> a call that still happens: Trace logs
 * it, Resilient lets it through or refuses based on how the last one went. This class <em>stands in
 * for</em> the service: on a hit nothing is called at all, and the caller cannot tell whether it
 * spoke to the real thing. Same interface, different job — one decorates the call, the other
 * replaces it.
 *
 * <h2>What makes it worth the extra layer</h2>
 *
 * The cost of a call here is not microseconds. The service is deployed on Render's free plan: it
 * sleeps when unused and takes close to a minute to wake. Every avoided call is a minute the
 * caretaker does not wait, and these two are asked far more often than their answers change:
 *
 * <ul>
 *   <li>{@code available()} runs before <em>every</em> sync ({@link AiTrigger}) and on every
 *       {@code /api/ia/disponible}, which the app asks on every screen that shows findings. It is
 *       a health check; asking it twice in the same half minute tells nobody anything new.</li>
 *   <li>{@code projection()} is worked out every time somebody opens a batch. Walk into the screen,
 *       walk out, walk back in: two identical calls. With several phones on the farm, more.</li>
 * </ul>
 *
 * There is a second saving that does not show up as a cache hit. When {@code available()} answers
 * false from here, {@link AiAnalysis} never assembles the request — and assembling it means reading
 * a month of readings out of the database. That is the lazy loading side of this pattern: the
 * expensive work is not merely cached, it never happens.
 *
 * <h2>What is deliberately not cached</h2>
 *
 * The three water routes. They are called from the background analysis right after a sync stored
 * new readings, which is to say right after the answer changed. A cache there would hand back the
 * verdict on data that no longer exists, which is the exact failure this project cannot afford: a
 * caretaker reading "all normal" about readings nobody looked at.
 */
public final class CachingAnalyzer implements Analyzer {

    private static final Logger log = LoggerFactory.getLogger(CachingAnalyzer.class);

    /**
     * How long a health check is believed.
     *
     * Short enough that a service that just came back is noticed within half a minute, long enough
     * that a round of syncs over several ponds asks once instead of once per pond.
     */
    static final Duration HEALTH_LIFE = Duration.ofSeconds(30);

    /**
     * How long a projection is believed.
     *
     * Two minutes is the compromise. A projection answers "what will it weigh and when do I sell",
     * which moves on the scale of weeks, so two minutes of staleness changes nothing anybody would
     * act on. Longer and a caretaker who just recorded a mortality would not see it reflected, and
     * not understanding why is worse than waiting for the call.
     */
    static final Duration PROJECTION_LIFE = Duration.ofMinutes(2);

    /**
     * Cap on remembered projections.
     *
     * A farm has a handful of active batches, so this is never reached in practice. It is here
     * because a cache without a bound is a memory leak that waits for an unusual day, and the free
     * plan has 512 MB. The oldest goes first.
     */
    static final int MAX_BATCHES = 64;

    /**
     * What identifies a projection, which is the batch's own data and not the whole request.
     *
     * The water readings are left out on purpose, and it is the one judgement call in this class.
     * They are included in the request because the growth model needs the temperature, but there
     * are hundreds of them and the model uses their median: one more reading cannot move the
     * answer. Keying on them would mean a miss every few seconds, from the sensor alone, and the
     * cache would never hit.
     *
     * What does move the answer — a weighing, a death, the stocking figures, the horizon asked for
     * — is all in the key, so recording any of those misses the cache and gets a fresh answer.
     * Anything the readings could drift in two minutes is covered by {@link #PROJECTION_LIFE}.
     */
    private record Key(java.time.LocalDate stockingDate, Integer initialQuantity,
            Double initialWeightG, List<Ai.WeighingJson> weighings, List<Ai.DeathJson> deaths,
            int daysAhead, Double targetWeightG) {

        static Key of(Ai.BatchRequest r) {
            return new Key(r.stockingDate(), r.initialQuantity(), r.initialWeightG(),
                    r.weighings(), r.deaths(), r.daysAhead(), r.targetWeightG());
        }
    }

    /** An answer and the moment it stops being believed. */
    private record Remembered<T>(T value, Instant until) {

        boolean fresh() {
            return ServerClock.now().isBefore(until);
        }
    }

    private final Analyzer real;
    private final Duration healthLife;
    private final Duration projectionLife;

    private final AtomicReference<Remembered<Boolean>> health = new AtomicReference<>();

    /**
     * Access is synchronized rather than concurrent because this map is also ordered: it evicts
     * the oldest, which a ConcurrentHashMap cannot do. The critical section is a map lookup, and
     * the calls it protects take a minute, so there is nothing to win by being cleverer.
     */
    private final Map<Key, Remembered<Ai.ProjectionJson>> projections =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, Remembered<Ai.ProjectionJson>> eldest) {
                    return size() > MAX_BATCHES;
                }
            };

    public CachingAnalyzer(Analyzer real) {
        this(real, HEALTH_LIFE, PROJECTION_LIFE);
    }

    /** With the lifetimes spelled out, which is how the tests check that things do expire. */
    CachingAnalyzer(Analyzer real, Duration healthLife, Duration projectionLife) {
        this.real = real;
        this.healthLife = healthLife;
        this.projectionLife = projectionLife;
    }

    @Override
    public boolean available() {
        Remembered<Boolean> known = health.get();
        if (known != null && known.fresh()) {
            return known.value();
        }
        boolean answer = real.available();
        health.set(new Remembered<>(answer, ServerClock.now().plus(healthLife)));
        return answer;
    }

    @Override
    public Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request) {
        Key key = Key.of(request);
        synchronized (projections) {
            Remembered<Ai.ProjectionJson> known = projections.get(key);
            if (known != null && known.fresh()) {
                log.debug("proyección servida de lo ya sabido, sin llamar al servicio");
                return Optional.of(known.value());
            }
        }

        Optional<Ai.ProjectionJson> answer = real.projection(request);
        // Only a real answer is remembered. Caching "could not ask" would turn one bad minute into
        // two, and the circuit breaker already covers that case properly.
        answer.ifPresent(value -> {
            synchronized (projections) {
                projections.put(key, new Remembered<>(value, ServerClock.now().plus(projectionLife)));
            }
        });
        return answer;
    }

    // ---------- what goes straight through ----------

    @Override
    public Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request) {
        return real.anomalies(request);
    }

    @Override
    public Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request) {
        return real.trends(request);
    }

    @Override
    public Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request) {
        return real.combination(request);
    }

    // ---------- for the tests ----------

    /** How many projections are being remembered right now. */
    int remembered() {
        synchronized (projections) {
            return projections.size();
        }
    }

    /** Throws away everything known. Used when the tests need a clean slate. */
    void forget() {
        health.set(null);
        synchronized (projections) {
            projections.clear();
        }
    }
}
