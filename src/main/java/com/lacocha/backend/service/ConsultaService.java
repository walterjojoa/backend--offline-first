package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lacocha.backend.dto.Queries.FryCountResponse;
import com.lacocha.backend.dto.Queries.BiometryResponse;
import com.lacocha.backend.dto.Queries.FeedingResponse;
import com.lacocha.backend.dto.Queries.MortalityResponse;
import com.lacocha.backend.dto.Queries.WaterReadingResponse;
import com.lacocha.backend.dto.Queries.DailyReadings;
import com.lacocha.backend.dto.Queries.BatchSummary;
import com.lacocha.backend.dto.Sync.AlertResponse;
import com.lacocha.backend.model.Alert;
import com.lacocha.backend.model.Biometry;
import com.lacocha.backend.model.FryCount;
import com.lacocha.backend.model.Feeding;
import com.lacocha.backend.model.Mortality;
import com.lacocha.backend.model.Event;
import com.lacocha.backend.model.WaterReading;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.repository.AlertRepository;
import com.lacocha.backend.repository.FeedingRepository;
import com.lacocha.backend.repository.BiometryRepository;
import com.lacocha.backend.repository.FryCountRepository;
import com.lacocha.backend.repository.PondRepository;
import com.lacocha.backend.repository.WaterReadingRepository;
import com.lacocha.backend.repository.BatchRepository;
import com.lacocha.backend.repository.MortalityRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
@Transactional(readOnly = true)
public class ConsultaService {

    private final EntityManager em;
    private final PondRepository ponds;
    private final BatchRepository batches;
    private final FryCountRepository conteos;
    private final MortalityRepository mortalidades;
    private final BiometryRepository biometrias;
    private final WaterReadingRepository readings;
    private final FeedingRepository alimentaciones;
    private final AlertRepository alertas;

    public ConsultaService(EntityManager em, PondRepository ponds, BatchRepository batches,
            FryCountRepository conteos, MortalityRepository mortalidades, BiometryRepository biometrias,
            WaterReadingRepository readings, FeedingRepository alimentaciones, AlertRepository alertas) {
        this.em = em;
        this.ponds = ponds;
        this.batches = batches;
        this.conteos = conteos;
        this.mortalidades = mortalidades;
        this.biometrias = biometrias;
        this.readings = readings;
        this.alimentaciones = alimentaciones;
        this.alertas = alertas;
    }

    public List<WaterReadingResponse> lecturasEstanque(UUID pondId, Instant desde, Instant hasta, int limite) {
        if (!ponds.existsById(pondId)) {
            throw CatalogoService.noExiste("El estanque");
        }
        StringBuilder jpql = new StringBuilder("select l from WaterReading l where l.pondId = :estanque");
        if (desde != null) jpql.append(" and l.recordedAt >= :desde");
        if (hasta != null) jpql.append(" and l.recordedAt <= :hasta");
        jpql.append(" order by l.recordedAt desc");

        TypedQuery<WaterReading> consulta = em.createQuery(jpql.toString(), WaterReading.class)
                .setParameter("estanque", pondId)
                .setMaxResults(Math.max(1, Math.min(limite, 5000)));
        if (desde != null) consulta.setParameter("desde", desde);
        if (hasta != null) consulta.setParameter("hasta", hasta);
        return consulta.getResultList().stream().map(WaterReadingResponse::from).toList();
    }

    /** Mínimo, máximo y promedio de temperatura y pH por día (hora de Colombia) de los últimos días. */
    public List<DailyReadings> lecturasPorDia(UUID pondId, int dias) {
        if (!ponds.existsById(pondId)) {
            throw CatalogoService.noExiste("El estanque");
        }
        dias = Math.max(1, Math.min(dias, 90));
        Instant desde = LocalDate.now(Fechas.ZONA_GRANJA).minusDays(dias - 1L)
                .atStartOfDay(Fechas.ZONA_GRANJA).toInstant();
        List<WaterReading> lista = em.createQuery(
                        "select l from WaterReading l where l.pondId = :estanque and l.recordedAt >= :desde",
                        WaterReading.class)
                .setParameter("estanque", pondId)
                .setParameter("desde", desde)
                .getResultList();

        Map<LocalDate, List<WaterReading>> porDia = lista.stream().collect(Collectors.groupingBy(
                l -> LocalDate.ofInstant(l.getRecordedAt(), Fechas.ZONA_GRANJA), TreeMap::new, Collectors.toList()));

        return porDia.entrySet().stream().map(dia -> {
            DoubleSummaryStatistics temp = estadisticas(dia.getValue(), WaterReading::getTempC);
            DoubleSummaryStatistics ph = estadisticas(dia.getValue(), WaterReading::getPh);
            return new DailyReadings(dia.getKey(), dia.getValue().size(),
                    minimo(temp), maximo(temp), promedio(temp, 2),
                    minimo(ph), maximo(ph), promedio(ph, 2));
        }).toList();
    }

    private static DoubleSummaryStatistics estadisticas(List<WaterReading> lista, Function<WaterReading, Double> campo) {
        return lista.stream().map(campo).filter(Objects::nonNull).mapToDouble(Double::doubleValue).summaryStatistics();
    }

    private static Double minimo(DoubleSummaryStatistics e) {
        return e.getCount() > 0 ? e.getMin() : null;
    }

    private static Double maximo(DoubleSummaryStatistics e) {
        return e.getCount() > 0 ? e.getMax() : null;
    }

    private static Double promedio(DoubleSummaryStatistics e, int decimales) {
        return e.getCount() > 0 ? Reglas.redondear(e.getAverage(), decimales) : null;
    }

    public List<FryCountResponse> conteosLote(UUID batchId, int limite) {
        return eventosLote(FryCount.class, batchId, limite).stream().map(FryCountResponse::from).toList();
    }

    public List<MortalityResponse> mortalidadesLote(UUID batchId, int limite) {
        return eventosLote(Mortality.class, batchId, limite).stream().map(MortalityResponse::from).toList();
    }

    public List<FeedingResponse> alimentacionesLote(UUID batchId, int limite) {
        return eventosLote(Feeding.class, batchId, limite).stream().map(FeedingResponse::from).toList();
    }

    public List<BiometryResponse> biometriasLote(UUID batchId, int limite) {
        return eventosLote(Biometry.class, batchId, limite).stream().map(BiometryResponse::from).toList();
    }

    /** Historial de un tipo de evento del lote, del más reciente al más antiguo. */
    private <T extends Event> List<T> eventosLote(Class<T> clase, UUID batchId, int limite) {
        if (!batches.existsById(batchId)) {
            throw CatalogoService.noExiste("El lote");
        }
        return em.createQuery("select e from " + clase.getSimpleName()
                        + " e where e.batchId = :lote order by e.recordedAt desc", clase)
                .setParameter("lote", batchId)
                .setMaxResults(Math.max(1, Math.min(limite, 1000)))
                .getResultList();
    }

    public BatchSummary resumenLote(UUID batchId) {
        Batch lote = batches.findById(batchId).orElseThrow(() -> CatalogoService.noExiste("El lote"));
        List<String> notes = new ArrayList<>();

        List<FryCount> listaConteos = conteos.findByBatchIdOrderByRecordedAtAsc(batchId);
        Integer initialQuantity = lote.getInitialQuantity() != null ? lote.getInitialQuantity()
                : (listaConteos.isEmpty() ? null : listaConteos.get(0).getTotal());

        // Población = último conteo menos las muertes registradas después de ese conteo
        long totalMortality = mortalidades.totalForBatch(batchId);
        Integer poblacion;
        if (!listaConteos.isEmpty()) {
            FryCount ultimo = listaConteos.get(listaConteos.size() - 1);
            long despues = mortalidades.totalForBatchAfter(batchId, ultimo.getRecordedAt());
            poblacion = (int) Math.max(ultimo.getTotal() - despues, 0);
        } else if (initialQuantity != null) {
            poblacion = (int) Math.max(initialQuantity - totalMortality, 0);
        } else {
            poblacion = null;
            notes.add("Sin conteo ni cantidad inicial: no se puede estimar la población.");
        }

        Double supervivencia = poblacion != null && initialQuantity != null && initialQuantity > 0
                ? Reglas.redondear(poblacion * 100.0 / initialQuantity, 1)
                : null;

        Double peso = biometrias.findFirstByBatchIdOrderByRecordedAtDesc(batchId)
                .map(Biometry::getAvgWeightG)
                .orElse(lote.getInitialWeightG());
        if (peso == null) {
            notes.add("Falta el peso promedio: registra una biometría para calcular biomasa y ración.");
        }
        Double biomasa = poblacion != null && peso != null ? Reglas.redondear(poblacion * peso / 1000, 3) : null;

        // Densidad de siembra: sirve para saber si el estanque está sobrecargado
        Double volumen = ponds.findById(lote.getPondId()).map(e -> e.getVolumeM3()).orElse(null);
        Double densidad = biomasa != null && volumen != null && volumen > 0
                ? Reglas.redondear(biomasa / volumen, 2)
                : null;

        WaterReading lectura = readings.findFirstByPondIdAndTempCIsNotNullOrderByRecordedAtDesc(lote.getPondId())
                .orElse(null);
        Double temp = lectura != null ? lectura.getTempC() : null;
        if (lectura == null) {
            notes.add("No hay lecturas de temperatura del estanque.");
        }

        Double tasa = null;
        Double racion = null;
        if (peso != null && temp != null) {
            tasa = Reglas.feedingRatePct(peso, temp);
            if (biomasa != null) {
                racion = Reglas.dailyRationKg(biomasa, peso, temp);
            }
            if (tasa == 0) {
                notes.add("Agua sobre 18 °C: se recomienda suspender la alimentación.");
            }
        }

        Long cultureDays = lote.getStockingDate() != null
                ? ChronoUnit.DAYS.between(lote.getStockingDate(), LocalDate.now(Fechas.ZONA_GRANJA))
                : null;

        double alimentoSemana = alimentaciones.kgForBatchSince(batchId, Instant.now().minus(Duration.ofDays(7)));

        // Factor de conversión alimenticia (FCA): kg de alimento por cada kg de biomasa ganada
        double alimentoTotal = alimentaciones.totalKgForBatch(batchId);
        Double conversion = null;
        if (biomasa != null && initialQuantity != null && lote.getInitialWeightG() != null && alimentoTotal > 0) {
            double ganancia = biomasa - initialQuantity * lote.getInitialWeightG() / 1000;
            if (ganancia > 0) {
                conversion = Reglas.redondear(alimentoTotal / ganancia, 2);
            }
        }

        return new BatchSummary(
                lote.getId(), lote.getCode(), lote.getPondId(), cultureDays,
                initialQuantity, poblacion, totalMortality, supervivencia,
                peso, biomasa, densidad, temp, lectura != null ? lectura.getRecordedAt() : null,
                tasa, racion, Reglas.redondear(alimentoSemana, 3),
                Reglas.redondear(alimentoTotal, 3), conversion,
                alertas.countByPondIdAndAttendedFalse(lote.getPondId()),
                notes);
    }

    public List<AlertResponse> listarAlertas(boolean pendientes, UUID pondId, int limite) {
        StringBuilder jpql = new StringBuilder("select a from Alert a where 1 = 1");
        if (pendientes) jpql.append(" and a.attended = false");
        if (pondId != null) jpql.append(" and a.pondId = :estanque");
        jpql.append(" order by a.measuredAt desc");

        TypedQuery<Alert> consulta = em.createQuery(jpql.toString(), Alert.class)
                .setMaxResults(Math.max(1, Math.min(limite, 1000)));
        if (pondId != null) consulta.setParameter("estanque", pondId);
        return consulta.getResultList().stream().map(AlertResponse::from).toList();
    }

    @Transactional
    public AlertResponse atenderAlerta(UUID id) {
        Alert alerta = alertas.findById(id).orElseThrow(() -> CatalogoService.noExiste("La alerta"));
        // Atender dos veces no cambia la hora original
        if (!alerta.isAttended()) {
            alerta.setAttended(true);
            alerta.setAttendedAt(ServerClock.now());
        }
        return AlertResponse.from(alerta);
    }
}
