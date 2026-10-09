package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lacocha.backend.dto.Queries.BatchSummary;
import com.lacocha.backend.dto.Queries.BiometryResponse;
import com.lacocha.backend.dto.Queries.DailyReadings;
import com.lacocha.backend.dto.Queries.FeedingResponse;
import com.lacocha.backend.dto.Queries.FryCountResponse;
import com.lacocha.backend.dto.Queries.MortalityResponse;
import com.lacocha.backend.dto.Queries.WaterReadingResponse;
import com.lacocha.backend.dto.Sync.AlertResponse;
import com.lacocha.backend.model.Alert;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.Biometry;
import com.lacocha.backend.model.Event;
import com.lacocha.backend.model.Feeding;
import com.lacocha.backend.model.FryCount;
import com.lacocha.backend.model.Mortality;
import com.lacocha.backend.model.Pond;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.model.WaterReading;
import com.lacocha.backend.repository.AlertRepository;
import com.lacocha.backend.repository.BatchRepository;
import com.lacocha.backend.repository.BiometryRepository;
import com.lacocha.backend.repository.FeedingRepository;
import com.lacocha.backend.repository.FryCountRepository;
import com.lacocha.backend.repository.MortalityRepository;
import com.lacocha.backend.repository.PondRepository;
import com.lacocha.backend.repository.WaterReadingRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/** History, summaries and alerts for the panel and the app. */
@Service
@Transactional(readOnly = true)
public class QueryService {

    private final EntityManager em;
    private final PondRepository ponds;
    private final BatchRepository batches;
    private final FryCountRepository counts;
    private final MortalityRepository mortalities;
    private final BiometryRepository biometries;
    private final WaterReadingRepository readings;
    private final FeedingRepository feedings;
    private final AlertRepository alerts;
    private final RuleParameters rules;

    public QueryService(EntityManager em, PondRepository ponds, BatchRepository batches,
            FryCountRepository counts, MortalityRepository mortalities, BiometryRepository biometries,
            WaterReadingRepository readings, FeedingRepository feedings, AlertRepository alerts,
            RuleParameters rules) {
        this.em = em;
        this.ponds = ponds;
        this.batches = batches;
        this.counts = counts;
        this.mortalities = mortalities;
        this.biometries = biometries;
        this.readings = readings;
        this.feedings = feedings;
        this.alerts = alerts;
        this.rules = rules;
    }

    public List<WaterReadingResponse> pondReadings(UUID pondId, Instant since, Instant until, int limit) {
        requirePond(pondId);
        StringBuilder jpql = new StringBuilder("select r from WaterReading r where r.pondId = :pond");
        if (since != null) jpql.append(" and r.recordedAt >= :since");
        if (until != null) jpql.append(" and r.recordedAt <= :until");
        jpql.append(" order by r.recordedAt desc");

        TypedQuery<WaterReading> query = em.createQuery(jpql.toString(), WaterReading.class)
                .setParameter("pond", pondId)
                .setMaxResults(Math.max(1, Math.min(limit, 5000)));
        if (since != null) query.setParameter("since", since);
        if (until != null) query.setParameter("until", until);
        return query.getResultList().stream().map(WaterReadingResponse::from).toList();
    }

    /** Minimum, maximum and average temperature and pH per day (Colombian time) over the last days. */
    public List<DailyReadings> dailyReadings(UUID pondId, int days) {
        requirePond(pondId);
        days = Math.max(1, Math.min(days, 90));
        Instant since = LocalDate.now(Dates.FARM_ZONE).minusDays(days - 1L)
                .atStartOfDay(Dates.FARM_ZONE).toInstant();
        List<WaterReading> list = em.createQuery(
                        "select r from WaterReading r where r.pondId = :pond and r.recordedAt >= :since",
                        WaterReading.class)
                .setParameter("pond", pondId)
                .setParameter("since", since)
                .getResultList();

        Map<LocalDate, List<WaterReading>> byDay = list.stream().collect(Collectors.groupingBy(
                r -> LocalDate.ofInstant(r.getRecordedAt(), Dates.FARM_ZONE), TreeMap::new, Collectors.toList()));

        return byDay.entrySet().stream().map(day -> {
            DoubleSummaryStatistics temp = statistics(day.getValue(), WaterReading::getTempC);
            DoubleSummaryStatistics ph = statistics(day.getValue(), WaterReading::getPh);
            return new DailyReadings(day.getKey(), day.getValue().size(),
                    min(temp), max(temp), average(temp, 2),
                    min(ph), max(ph), average(ph, 2));
        }).toList();
    }

    private static DoubleSummaryStatistics statistics(List<WaterReading> list, Function<WaterReading, Double> field) {
        return list.stream().map(field).filter(Objects::nonNull).mapToDouble(Double::doubleValue).summaryStatistics();
    }

    private static Double min(DoubleSummaryStatistics s) {
        return s.getCount() > 0 ? s.getMin() : null;
    }

    private static Double max(DoubleSummaryStatistics s) {
        return s.getCount() > 0 ? s.getMax() : null;
    }

    private static Double average(DoubleSummaryStatistics s, int decimals) {
        return s.getCount() > 0 ? Rules.round(s.getAverage(), decimals) : null;
    }

    public List<FryCountResponse> fryCounts(UUID batchId, int limit) {
        return batchEvents(FryCount.class, batchId, limit).stream().map(FryCountResponse::from).toList();
    }

    public List<MortalityResponse> mortalities(UUID batchId, int limit) {
        return batchEvents(Mortality.class, batchId, limit).stream().map(MortalityResponse::from).toList();
    }

    public List<FeedingResponse> feedings(UUID batchId, int limit) {
        return batchEvents(Feeding.class, batchId, limit).stream().map(FeedingResponse::from).toList();
    }

    public List<BiometryResponse> biometries(UUID batchId, int limit) {
        return batchEvents(Biometry.class, batchId, limit).stream().map(BiometryResponse::from).toList();
    }

    /** History of one event type of the batch, newest first. */
    private <T extends Event> List<T> batchEvents(Class<T> type, UUID batchId, int limit) {
        if (!batches.existsById(batchId)) {
            throw CatalogService.notFound("El lote");
        }
        return em.createQuery("select e from " + type.getSimpleName()
                        + " e where e.batchId = :batch order by e.recordedAt desc", type)
                .setParameter("batch", batchId)
                .setMaxResults(Math.max(1, Math.min(limit, 1000)))
                .getResultList();
    }

    public BatchSummary batchSummary(UUID batchId) {
        Batch batch = batches.findById(batchId).orElseThrow(() -> CatalogService.notFound("El lote"));
        List<String> notes = new ArrayList<>();

        List<FryCount> countList = counts.findByBatchIdOrderByRecordedAtAsc(batchId);
        Integer initialQuantity = batch.getInitialQuantity() != null ? batch.getInitialQuantity()
                : (countList.isEmpty() ? null : countList.get(0).getTotal());

        // Population = last count minus the deaths recorded after that count
        long totalMortality = mortalities.totalForBatch(batchId);
        Integer population;
        if (!countList.isEmpty()) {
            FryCount last = countList.get(countList.size() - 1);
            long after = mortalities.totalForBatchAfter(batchId, last.getRecordedAt());
            population = (int) Math.max(last.getTotal() - after, 0);
        } else if (initialQuantity != null) {
            population = (int) Math.max(initialQuantity - totalMortality, 0);
        } else {
            population = null;
            notes.add("Sin conteo ni cantidad inicial: no se puede estimar la población.");
        }

        Double survival = population != null && initialQuantity != null && initialQuantity > 0
                ? Rules.round(population * 100.0 / initialQuantity, 1)
                : null;

        Double weight = biometries.findFirstByBatchIdOrderByRecordedAtDesc(batchId)
                .map(Biometry::getAvgWeightG)
                .orElse(batch.getInitialWeightG());
        if (weight == null) {
            notes.add("Falta el peso promedio: registra una biometría para calcular biomasa y ración.");
        }
        Double biomass = population != null && weight != null ? Rules.round(population * weight / 1000, 3) : null;

        // Stocking density: tells whether the pond is overloaded
        Double volume = ponds.findById(batch.getPondId()).map(Pond::getVolumeM3).orElse(null);
        Double density = biomass != null && volume != null && volume > 0
                ? Rules.round(biomass / volume, 2)
                : null;

        WaterReading reading = readings.findFirstByPondIdAndTempCIsNotNullOrderByRecordedAtDesc(batch.getPondId())
                .orElse(null);
        Double temp = reading != null ? reading.getTempC() : null;
        if (reading == null) {
            notes.add("No hay lecturas de temperatura del estanque.");
        }

        Double rate = null;
        Double ration = null;
        if (weight != null && temp != null) {
            rate = rules.current().feedingRatePct(weight, temp);
            if (biomass != null) {
                ration = rules.current().dailyRationKg(biomass, weight, temp);
            }
            if (rate == 0) {
                notes.add("Agua sobre 18 °C: se recomienda suspender la alimentación.");
            }
        }

        Long cultureDays = batch.getStockingDate() != null
                ? ChronoUnit.DAYS.between(batch.getStockingDate(), LocalDate.now(Dates.FARM_ZONE))
                : null;

        double feedLastWeek = feedings.kgForBatchSince(batchId, Instant.now().minus(Duration.ofDays(7)));

        // Feed conversion ratio (FCR): kg of feed per kg of biomass gained
        double totalFeed = feedings.totalKgForBatch(batchId);
        Double conversion = null;
        if (biomass != null && initialQuantity != null && batch.getInitialWeightG() != null && totalFeed > 0) {
            double gain = biomass - initialQuantity * batch.getInitialWeightG() / 1000;
            if (gain > 0) {
                conversion = Rules.round(totalFeed / gain, 2);
            }
        }

        return new BatchSummary(
                batch.getId(), batch.getCode(), batch.getPondId(), cultureDays,
                initialQuantity, population, totalMortality, survival,
                weight, biomass, density, temp, reading != null ? reading.getRecordedAt() : null,
                rate, ration, Rules.round(feedLastWeek, 3),
                Rules.round(totalFeed, 3), conversion,
                alerts.countByPondIdAndAttendedFalse(batch.getPondId()),
                notes);
    }

    public List<AlertResponse> listAlerts(boolean pendingOnly, UUID pondId, int limit) {
        StringBuilder jpql = new StringBuilder("select a from Alert a where 1 = 1");
        if (pendingOnly) jpql.append(" and a.attended = false");
        if (pondId != null) jpql.append(" and a.pondId = :pond");
        jpql.append(" order by a.measuredAt desc");

        TypedQuery<Alert> query = em.createQuery(jpql.toString(), Alert.class)
                .setMaxResults(Math.max(1, Math.min(limit, 1000)));
        if (pondId != null) query.setParameter("pond", pondId);
        return query.getResultList().stream().map(AlertResponse::from).toList();
    }

    @Transactional
    public AlertResponse attendAlert(UUID id) {
        Alert alert = alerts.findById(id).orElseThrow(() -> CatalogService.notFound("La alerta"));
        // Attending twice does not change the original time
        if (!alert.isAttended()) {
            alert.setAttended(true);
            alert.setAttendedAt(ServerClock.now());
        }
        return AlertResponse.from(alert);
    }

    private void requirePond(UUID pondId) {
        if (!ponds.existsById(pondId)) {
            throw CatalogService.notFound("El estanque");
        }
    }
}
