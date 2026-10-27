package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import jakarta.persistence.TypedQuery;

/**
 * Abstraction side of the Bridge: what is worth analysing, when, and what to do with the answer.
 *
 * It never sees an HTTP client. Below it sits {@link Analyzer}, which may be the real Python
 * service, the same service behind a circuit breaker, or {@link Analyzer#absent()} on a farm that
 * did not install it. None of that changes anything here.
 *
 * <h2>Nothing here runs inside a sync</h2>
 *
 * The analysis is triggered <em>after</em> the push transaction commits, on another thread (see
 * {@link AiTrigger}). Two reasons, and both are hard requirements rather than preferences:
 *
 * <ul>
 *   <li>Render's free plan puts the AI service to sleep, and waking it takes close to a minute. A
 *       caretaker who finally found signal at the lake cannot wait a minute for their queue to
 *       upload because a projection was being worked out.</li>
 *   <li>The analysis needs a month of readings <em>including the ones that just arrived</em>. Doing
 *       it inside the transaction would mean analysing data that may yet roll back.</li>
 * </ul>
 *
 * So the push answers immediately, and when the analysis comes back its findings are stored and
 * published as events, which is how they reach the screen without anyone pressing anything.
 */
@Service
public class AiAnalysis {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysis.class);

    /**
     * How far back the pond's own habits are read from.
     *
     * A month is the compromise. Less does not pin down what is normal for the pond; more starts
     * dragging in the change of season, and then a perfectly ordinary November reading looks odd
     * next to August.
     */
    static final Duration WINDOW = Duration.ofDays(30);

    /** Matches TOPE_LECTURAS in the Python service: beyond that it trims the list anyway. */
    static final int MAX_READINGS = 500;

    /** Market weight of trout around here. Only used to work out the harvest date. */
    static final double TARGET_WEIGHT_G = 250.0;

    static final int DAYS_AHEAD = 30;

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

    // ---------- water ----------

    /**
     * Analyses the pond's most recent reading against its own history.
     *
     * Returns the findings that were really stored, which is not the same as the ones the service
     * returned: the ones already pending are dropped (see {@link #worthKeeping}).
     */
    @Transactional
    public List<Finding> analyzePond(UUID pondId) {
        List<WaterReading> history = readingsOf(pondId);
        if (history.size() < 2) {
            // Not an error and not worth a round trip: the service would answer "not enough data"
            return List.of();
        }

        Ai.WaterRequest request = new Ai.WaterRequest(pondId,
                history.stream().map(AiAnalysis::readingJson).toList(),
                ranges.findAll().stream().map(AiAnalysis::rangeJson).toList());

        Optional<Ai.WaterAnalysis> answer = analyzer.analyzeWater(request);
        if (answer.isEmpty()) {
            return List.of();
        }
        Ai.WaterAnalysis analysis = answer.get();
        if (!analysis.enoughData()) {
            // "I could not tell" is not "everything is fine". It is left in the log and nothing is
            // stored, because storing a finding that says nothing would only train people to ignore
            // the ones that do say something.
            log.debug("estanque {}: el servicio no tuvo datos suficientes ({} lecturas)",
                    pondId, analysis.readingsUsed());
            return List.of();
        }
        return store(analysis.findings(), pondId, null);
    }

    /** The window of readings, oldest first, which is the order the service expects. */
    private List<WaterReading> readingsOf(UUID pondId) {
        Instant since = ServerClock.now().minus(WINDOW);
        TypedQuery<WaterReading> query = em.createQuery(
                "select r from WaterReading r where r.pondId = :pond and r.recordedAt >= :since "
                        + "order by r.recordedAt asc", WaterReading.class)
                .setParameter("pond", pondId)
                .setParameter("since", since)
                .setMaxResults(MAX_READINGS);
        return query.getResultList();
    }

    private static Ai.ReadingJson readingJson(WaterReading r) {
        return new Ai.ReadingJson(r.getRecordedAt(), r.getTempC(), r.getPh(), r.getOxygenMgL());
    }

    private static Ai.RangeJson rangeJson(RangeParameter p) {
        return new Ai.RangeJson(p.getVariable(), p.getCriticalMin(), p.getCriticalMax(),
                p.getOptimalMin(), p.getOptimalMax());
    }

    // ---------- batches ----------

    /** Projects where the batch is going, and stores whatever that turns up. */
    @Transactional
    public List<Finding> analyzeBatch(UUID batchId) {
        Batch batch = batches.findById(batchId).orElse(null);
        if (batch == null) {
            return List.of();
        }

        Ai.BatchRequest request = new Ai.BatchRequest(batchId, batch.getStockingDate(),
                batch.getInitialQuantity(), batch.getInitialWeightG(),
                batchEvents(Biometry.class, batchId).stream()
                        .map(b -> new Ai.BiometryJson(b.getRecordedAt(), b.getAvgWeightG(), b.getSampleSize()))
                        .toList(),
                batchEvents(Mortality.class, batchId).stream()
                        .map(m -> new Ai.MortalityJson(m.getRecordedAt(), m.getQuantity()))
                        .toList(),
                // The temperatures are the pond's, not the batch's: that is where the water is
                readingsOf(batch.getPondId()).stream()
                        .filter(r -> r.getTempC() != null)
                        .map(r -> new Ai.TemperatureJson(r.getRecordedAt(), r.getTempC()))
                        .toList(),
                DAYS_AHEAD, TARGET_WEIGHT_G);

        Optional<Ai.BatchProjection> answer = analyzer.projectBatch(request);
        if (answer.isEmpty()) {
            return List.of();
        }
        Ai.BatchProjection projection = answer.get();
        if (!projection.notes().isEmpty()) {
            // What could not be worked out, and what is missing to be able to. Worth a log line:
            // it is usually something somebody can go and register.
            log.debug("lote {}: {}", batchId, String.join(" ", projection.notes()));
        }
        return store(projection.findings(), null, batchId);
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
    public Optional<Ai.BatchProjection> projectionOf(UUID batchId) {
        Batch batch = batches.findById(batchId).orElse(null);
        if (batch == null) {
            throw CatalogService.notFound("El lote");
        }
        return analyzer.projectBatch(new Ai.BatchRequest(batchId, batch.getStockingDate(),
                batch.getInitialQuantity(), batch.getInitialWeightG(),
                batchEvents(Biometry.class, batchId).stream()
                        .map(b -> new Ai.BiometryJson(b.getRecordedAt(), b.getAvgWeightG(), b.getSampleSize()))
                        .toList(),
                batchEvents(Mortality.class, batchId).stream()
                        .map(m -> new Ai.MortalityJson(m.getRecordedAt(), m.getQuantity()))
                        .toList(),
                readingsOf(batch.getPondId()).stream()
                        .filter(r -> r.getTempC() != null)
                        .map(r -> new Ai.TemperatureJson(r.getRecordedAt(), r.getTempC()))
                        .toList(),
                DAYS_AHEAD, TARGET_WEIGHT_G));
    }

    // ---------- storing, without repeating ----------

    private List<Finding> store(List<Ai.FindingJson> found, UUID pondId, UUID batchId) {
        List<Finding> pending = pondId != null
                ? findings.findByPondIdAndAttendedFalse(pondId)
                : findings.findByBatchIdAndAttendedFalse(batchId);

        List<Finding> stored = new ArrayList<>();
        for (Ai.FindingJson json : found) {
            if (!worthKeeping(json, pending)) {
                continue;
            }
            Finding finding = new Finding();
            finding.setPondId(pondId);
            finding.setBatchId(batchId);
            finding.setKind(json.kind());
            finding.setLevel(json.level());
            finding.setVariable(json.variable());
            finding.setScore(json.score());
            finding.setMessage(json.message());
            finding.setMeasuredAt(json.measuredAt());
            em.persist(finding);
            stored.add(finding);
            // Counts for the ones that come after it in this same batch of findings
            pending.add(finding);
        }
        if (!stored.isEmpty()) {
            log.info("{} hallazgo(s) nuevo(s) de la IA en {}", stored.size(),
                    pondId != null ? "el estanque " + pondId : "el lote " + batchId);
        }
        return stored;
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
    private static boolean worthKeeping(Ai.FindingJson json, List<Finding> pending) {
        return pending.stream()
                .filter(p -> p.getKind().equals(json.kind()))
                .filter(p -> java.util.Objects.equals(p.getVariable(), json.variable()))
                .noneMatch(p -> urgency(p.getLevel()) >= urgency(json.level()));
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
     * Attending one is also what lets the same finding be raised again later: while it is pending
     * it silences its repeats, and once attended the next one gets through. That is how a problem
     * that comes back does not stay hidden behind an old notice.
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
