package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.lacocha.backend.dto.Ai;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.Biometry;
import com.lacocha.backend.model.Finding;
import com.lacocha.backend.model.Mortality;
import com.lacocha.backend.model.RangeParameter;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.model.WaterReading;
import com.lacocha.backend.repository.BatchRepository;
import com.lacocha.backend.repository.FindingRepository;
import com.lacocha.backend.repository.PondRepository;
import com.lacocha.backend.repository.RangeParameterRepository;

import jakarta.persistence.EntityManager;

/**
 * Abstraction side of the Bridge: what is worth analysing, when, and what to do with the answer.
 *
 * It never sees an HTTP client. Below it sits {@link Analyzer}, which may be the real service, the
 * same service behind a circuit breaker, or {@link Analyzer#absent()} on a farm that did not install
 * it. None of that changes anything here.
 *
 * <h2>Three calls for one pond</h2>
 *
 * The service has no single "analyse this water" route, and that is not an omission: its three water
 * routes answer three different questions and each keeps quiet about the others' business — a jump,
 * a drift, a pairing of values the pond never produced. So one pond is three calls, and this class
 * merges what comes back into one list of findings.
 *
 * A call that fails is skipped and the others still count. Losing the trend because the combination
 * timed out would be the worst of both: no answer and no explanation of why.
 *
 * <h2>Nothing here runs inside a sync</h2>
 *
 * The analysis is triggered <em>after</em> the push transaction commits, on another thread (see
 * {@link AiTrigger}). Two reasons, and both are hard requirements rather than preferences:
 *
 * <ul>
 *   <li>The service is deployed on Render's free plan, goes to sleep when unused and takes close to
 *       a minute to wake. With three calls per pond, a caretaker who finally found signal at the
 *       lake would wait minutes for their queue to upload.</li>
 *   <li>The analysis needs a month of readings <em>including the ones that just arrived</em>. Doing
 *       it inside the transaction would mean analysing data that may yet roll back.</li>
 * </ul>
 */
@Service
public class AiAnalysis {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysis.class);

    /**
     * How far back the pond's own habits are read from.
     *
     * A month is the compromise. Less does not pin down what is normal for the pond; more starts
     * dragging in the change of season, and then a perfectly ordinary November reading looks odd
     * next to August. It also comfortably covers the service's own windows, the largest of which is
     * 300 readings for a trend.
     */
    static final Duration WINDOW = Duration.ofDays(30);

    /** Beyond this the service trims the list anyway, so there is no point sending more. */
    static final int MAX_READINGS = 500;

    /** Market weight of trout around here. Only used to work out the harvest date. */
    static final double TARGET_WEIGHT_G = 250.0;

    static final int DAYS_AHEAD = 30;

    /** Days of water temperature the forecast is asked for. The service caps it at seven. */
    static final int FORECAST_DAYS = 3;

    /** Urgency, in order. Used to decide whether a finding that repeats is worth raising again. */
    private static final List<String> LEVELS = List.of("informe", "advertencia", "critica");

    private final EntityManager em;
    private final Analyzer analyzer;
    private final FindingRepository findings;
    private final PondRepository ponds;
    private final BatchRepository batches;
    private final RangeParameterRepository ranges;

    public AiAnalysis(EntityManager em, Analyzer analyzer, FindingRepository findings,
            PondRepository ponds, BatchRepository batches, RangeParameterRepository ranges) {
        this.em = em;
        this.analyzer = analyzer;
        this.findings = findings;
        this.ponds = ponds;
        this.batches = batches;
        this.ranges = ranges;
    }

    /** Whether there is an AI service to ask at all. The screens use it to decide what to show. */
    public boolean available() {
        return analyzer.available();
    }

    // ---------- how loudly each thing gets said ----------

    /**
     * The level of a finding, which this backend decides and the service does not.
     *
     * Worth spelling out, because it is the one piece of judgement that does not live in the
     * service. What comes down the wire is <em>what</em> was found: an anomaly brings no urgency at
     * all, a trend brings how many days are left before the critical limit, and a batch finding
     * brings a boolean. How loudly to say each of those depends on the thresholds in
     * parametros_rango, which are this database's data, and on who is going to read it. A service
     * that does not know this farm's thresholds cannot grade the urgency of anything, so it does
     * not try.
     *
     * <ul>
     *   <li><b>anomalia</b> stays at advertencia however large its score. A jump is a jump; whether
     *       the water is actually dangerous is the expert system's call, and it already made it.
     *       This is the mistake the first version of the Python service made and had to undo: in a
     *       spring-fed pond that varies by tenths of a degree, a reading one degree off scores
     *       seven deviations, and calling that "critica" is the same shout the rules reserve for
     *       water trout cannot live in.</li>
     *   <li><b>tendencia</b> reaches critica when the service says it is urgent, which means the
     *       critical limit is within two days. With a limit further off it is an advertencia, and
     *       with no limit to head towards it is only an informe.</li>
     *   <li><b>combinacion</b> stays at advertencia: it is a suspicion worth confirming, and its
     *       distance does not translate into danger for the fish.</li>
     *   <li><b>crecimiento</b> and <b>mortalidad</b> follow the service's own urgent flag, which it
     *       sets for a batch losing weight or a die-off that started this week.</li>
     * </ul>
     */
    static String levelOf(String kind, boolean urgent, boolean hasLimit) {
        return switch (kind) {
            case "tendencia" -> urgent ? "critica" : (hasLimit ? "advertencia" : "informe");
            case "crecimiento", "mortalidad" -> urgent ? "critica" : "advertencia";
            default -> "advertencia";
        };
    }

    // ---------- water ----------

    /**
     * Analyses the pond's recent readings, asking the service's three water routes.
     *
     * Returns the findings that were really stored, which is not the same as the ones the service
     * returned: the ones already pending are dropped (see {@link #worthKeeping}).
     */
    @Transactional
    public List<Finding> analyzePond(UUID pondId) {
        List<WaterReading> history = readingsOf(pondId);
        if (history.size() < 2) {
            // Not worth three round trips: every route would answer that it cannot tell
            return List.of();
        }
        List<Ai.ReadingJson> readings = history.stream().map(AiAnalysis::readingJson).toList();
        Ai.ReadingsRequest plain = new Ai.ReadingsRequest(readings);

        List<Draft> drafts = new ArrayList<>();
        // Each route on its own: one that fails must not cost the others their answer
        analyzer.anomalies(plain).ifPresent(found -> found.forEach(a -> drafts.add(
                new Draft("anomalia", a.variable(), Math.abs(a.score()), a.message(), false, false))));
        analyzer.trends(new Ai.TrendRequest(readings, rangesJson())).ifPresent(found ->
                found.forEach(t -> drafts.add(new Draft("tendencia", t.variable(), t.agreement(),
                        t.message(), t.urgent(), t.daysToLimit() != null))));
        analyzer.combination(plain).ifPresent(maybe -> maybe.ifPresent(c -> drafts.add(
                new Draft("combinacion", null, c.distance(), c.message(), false, false))));

        Instant measuredAt = history.get(history.size() - 1).getRecordedAt();
        return store(drafts, pondId, null, measuredAt);
    }

    /** The window of readings, oldest first, which is the order the service expects. */
    private List<WaterReading> readingsOf(UUID pondId) {
        return em.createQuery(
                "select r from WaterReading r where r.pondId = :pond and r.recordedAt >= :since "
                        + "order by r.recordedAt asc", WaterReading.class)
                .setParameter("pond", pondId)
                .setParameter("since", ServerClock.now().minus(WINDOW))
                .setMaxResults(MAX_READINGS)
                .getResultList();
    }

    private static Ai.ReadingJson readingJson(WaterReading r) {
        return new Ai.ReadingJson(r.getRecordedAt(), r.getTempC(), r.getPh(), r.getOxygenMgL());
    }

    /** The rules' critical range for each variable, which is what turns a slope into a date. */
    private List<Ai.RangeJson> rangesJson() {
        return ranges.findAll().stream()
                .map(p -> new Ai.RangeJson(p.getVariable(), p.getCriticalMin(), p.getCriticalMax()))
                .toList();
    }

    // ---------- batches ----------

    /** Projects where the batch is going, and stores whatever that turns up. */
    @Transactional
    public List<Finding> analyzeBatch(UUID batchId) {
        Batch batch = batches.findById(batchId).orElse(null);
        if (batch == null) {
            return List.of();
        }
        Optional<Ai.ProjectionJson> answer = analyzer.projection(batchRequest(batch));
        if (answer.isEmpty()) {
            return List.of();
        }
        Ai.ProjectionJson projection = answer.get();
        if (!projection.notes().isEmpty()) {
            // What could not be worked out, and what is missing to be able to. Worth a log line:
            // it is usually something somebody can go and register.
            log.debug("lote {}: {}", batchId, String.join(" ", projection.notes()));
        }
        List<Draft> drafts = projection.findings().stream()
                .map(f -> new Draft(f.kind(), null, f.score(), f.message(), f.urgent(), false))
                .toList();
        return store(drafts, null, batchId, null);
    }

    private Ai.BatchRequest batchRequest(Batch batch) {
        return new Ai.BatchRequest(batch.getStockingDate(), batch.getInitialQuantity(),
                batch.getInitialWeightG(),
                batchEvents(Biometry.class, batch.getId()).stream()
                        .map(b -> new Ai.WeighingJson(b.getRecordedAt(), b.getAvgWeightG(), b.getSampleSize()))
                        .toList(),
                batchEvents(Mortality.class, batch.getId()).stream()
                        .map(m -> new Ai.DeathJson(m.getRecordedAt(), m.getQuantity()))
                        .toList(),
                // The temperatures are the pond's, not the batch's: that is where the water is
                readingsOf(batch.getPondId()).stream()
                        .filter(r -> r.getTempC() != null)
                        .map(AiAnalysis::readingJson)
                        .toList(),
                DAYS_AHEAD, TARGET_WEIGHT_G);
    }

    /** Full history of one event type of the batch, oldest first. */
    private <T> List<T> batchEvents(Class<T> type, UUID batchId) {
        return em.createQuery("select e from " + type.getSimpleName()
                        + " e where e.batchId = :batch order by e.recordedAt asc", type)
                .setParameter("batch", batchId)
                .getResultList();
    }

    /**
     * The projection as the app reads it, without storing anything.
     *
     * The projected weight and the harvest date are not findings: they are not a problem, they are
     * the answer to "when do I sell". They are worked out when the screen asks and never kept,
     * because a projection made a month ago with the data of a month ago is worse than no
     * projection: it looks current and is not.
     */
    @Transactional(readOnly = true)
    public Optional<Ai.ProjectionJson> projectionOf(UUID batchId) {
        Batch batch = batches.findById(batchId).orElseThrow(() -> CatalogService.notFound("El lote"));
        return analyzer.projection(batchRequest(batch));
    }

    /**
     * Water temperature for the coming days of one pond.
     *
     * Pairs with the projection: the forecast says how warm the water will be, and the growth model
     * says what that warmth is worth in grams. Not stored either, and for a stronger reason — a
     * forecast kept from yesterday is simply wrong today.
     */
    @Transactional(readOnly = true)
    public Optional<Ai.ForecastJson> forecastOf(UUID pondId) {
        if (!ponds.existsById(pondId)) {
            throw CatalogService.notFound("El estanque");
        }
        List<Ai.ReadingJson> readings = readingsOf(pondId).stream()
                .filter(r -> r.getTempC() != null)
                .map(AiAnalysis::readingJson)
                .toList();
        if (readings.isEmpty()) {
            return Optional.empty();
        }
        return analyzer.forecast(new Ai.ForecastRequest(readings, FORECAST_DAYS));
    }

    // ---------- storing, without repeating ----------

    /** One finding on its way to the database, before it has a level or an id. */
    private record Draft(String kind, String variable, double score, String message,
            boolean urgent, boolean hasLimit) {
    }

    private List<Finding> store(List<Draft> drafts, UUID pondId, UUID batchId, Instant measuredAt) {
        if (drafts.isEmpty()) {
            return List.of();
        }
        List<Finding> pending = pondId != null
                ? findings.findByPondIdAndAttendedFalse(pondId)
                : findings.findByBatchIdAndAttendedFalse(batchId);

        List<Finding> stored = new ArrayList<>();
        for (Draft draft : drafts) {
            String level = levelOf(draft.kind(), draft.urgent(), draft.hasLimit());
            if (!worthKeeping(draft, level, pending)) {
                continue;
            }
            Finding finding = new Finding();
            finding.setPondId(pondId);
            finding.setBatchId(batchId);
            finding.setKind(draft.kind());
            finding.setLevel(level);
            finding.setVariable(draft.variable());
            finding.setScore(round(draft.score()));
            finding.setMessage(draft.message());
            finding.setMeasuredAt(measuredAt);
            em.persist(finding);
            stored.add(finding);
            // Counts for the ones that come after it in this same round
            pending.add(finding);
        }
        if (!stored.isEmpty()) {
            log.info("{} hallazgo(s) nuevo(s) de la IA en {}", stored.size(),
                    pondId != null ? "el estanque " + pondId : "el lote " + batchId);
        }
        return stored;
    }

    /** Two decimals: the score is for ordering and for the thesis to cite, not for arithmetic. */
    private static double round(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    /**
     * Whether this finding adds anything to what is already waiting.
     *
     * A finding repeats on every reading: the oxygen trend is still the same trend a minute later.
     * So one that matches something already pending is dropped, <em>unless</em> it is more urgent
     * than the one already there. That exception is the point of doing this by level instead of by
     * a plain "does one exist": a trend that went from "reaches the critical limit in five days" to
     * "reaches it tomorrow" has to be raised again, because what the caretaker has to do about it
     * changed.
     */
    private static boolean worthKeeping(Draft draft, String level, List<Finding> pending) {
        return pending.stream()
                .filter(p -> p.getKind().equals(draft.kind()))
                .filter(p -> Objects.equals(p.getVariable(), draft.variable()))
                .noneMatch(p -> urgency(p.getLevel()) >= urgency(level));
    }

    private static int urgency(String level) {
        int rank = LEVELS.indexOf(level);
        // An unknown level is treated as the lowest, so a service that invents one cannot silence
        // findings that do exist
        return rank < 0 ? -1 : rank;
    }

    // ---------- what the app reads ----------

    @Transactional(readOnly = true)
    public List<Finding> pending() {
        return findings.findTop100ByAttendedFalseOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Finding> ofPond(UUID pondId) {
        if (!ponds.existsById(pondId)) {
            throw CatalogService.notFound("El estanque");
        }
        return findings.findTop100ByPondIdOrderByCreatedAtDesc(pondId);
    }

    @Transactional(readOnly = true)
    public List<Finding> ofBatch(UUID batchId) {
        if (!batches.existsById(batchId)) {
            throw CatalogService.notFound("El lote");
        }
        return findings.findTop100ByBatchIdOrderByCreatedAtDesc(batchId);
    }

    /**
     * Marks a finding as looked at, leaving who and when.
     *
     * Attending one is also what lets the same finding be raised again later: while it is pending it
     * silences its repeats, and once attended the next one gets through. That is how a problem that
     * comes back does not stay hidden behind an old notice.
     */
    @Transactional
    public Finding attend(UUID id, String who) {
        Finding finding = findings.findById(id).orElseThrow(() -> CatalogService.notFound("El hallazgo"));
        if (finding.isAttended()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese hallazgo ya estaba atendido");
        }
        finding.setAttended(true);
        finding.setAttendedAt(ServerClock.now());
        finding.setAttendedBy(who);
        return finding;
    }
}
