package com.lacocha.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.lacocha.backend.model.Finding;

/**
 * The contract with the AI service, which lives in its own repository
 * (<a href="https://github.com/walterjojoa/MODELO-IA-offline-first">MODELO-IA-offline-first</a>).
 *
 * These records mirror that service's five routes exactly. The JSON names are the Spanish ones the
 * rest of the project already uses, because they are the names of the database columns too
 * ({@code registrado_en}, {@code oxigeno_mg_l}): a request is built by copying what is already in an
 * entity, with no translation layer in the middle where a field can go missing in silence.
 *
 * <h2>Three calls for the water, not one</h2>
 *
 * The service does not have a single "analyse this water" route, and that is not an omission: its
 * three water routes answer three different questions, and each one keeps quiet about the others'
 * business. {@code /anomalias} catches a jump, {@code /tendencias} a drift, {@code /combinaciones} a
 * pairing of values the pond never produced. {@link com.lacocha.backend.service.AiAnalysis} calls
 * the three and merges what comes back.
 *
 * <h2>No urgency comes down the wire</h2>
 *
 * Nothing here carries a level. An anomaly brings none at all, a trend brings how many days are left
 * before the critical limit, and a batch finding brings a boolean. Deciding how loudly to say each
 * of those depends on the thresholds in parametros_rango, which are this database's data and not the
 * service's, so the service does not try. The mapping lives in one place: AiAnalysis.levelOf().
 *
 * Every response record ignores unknown properties on purpose: the service and this backend are
 * deployed separately, so a version of the service that starts returning one extra field must not
 * break a backend that has not been redeployed yet.
 */
public final class Ai {

    private Ai() {
    }

    // ---------- what goes up, and is shared by every route ----------

    public record ReadingJson(
            @JsonProperty("registrado_en") Instant recordedAt,
            @JsonProperty("temp_c") Double tempC,
            @JsonProperty("ph") Double ph,
            @JsonProperty("oxigeno_mg_l") Double oxygenMgL) {
    }

    /** The readings of one pond. What /anomalias and /combinaciones take. */
    public record ReadingsRequest(@JsonProperty("lecturas") List<ReadingJson> readings) {
    }

    // ---------- /anomalias ----------

    /** A value that is unusual for this pond, even inside the rule ranges. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnomalyJson(
            @JsonProperty("variable") String variable,
            @JsonProperty("valor") double value,
            @JsonProperty("esperado") double expected,
            /** Robust z: how many of this pond's usual movements away. Its sign is the direction. */
            @JsonProperty("puntaje") double score,
            @JsonProperty("mensaje") String message) {
    }

    // ---------- /tendencias ----------

    /** One row of parametros_rango. Without it a trend cannot be turned into a date. */
    public record RangeJson(
            @JsonProperty("variable") String variable,
            @JsonProperty("critico_min") Double criticalMin,
            @JsonProperty("critico_max") Double criticalMax) {
    }

    public record TrendRequest(
            @JsonProperty("lecturas") List<ReadingJson> readings,
            @JsonProperty("rangos") List<RangeJson> ranges) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrendJson(
            @JsonProperty("variable") String variable,
            @JsonProperty("por_dia") double perDay,
            /** Share of the compared pairs agreeing on the direction: a drift against the daily cycle. */
            @JsonProperty("acuerdo") double agreement,
            @JsonProperty("dias_cubiertos") double daysCovered,
            /** null when there is no limit to head towards, or it is further than the horizon. */
            @JsonProperty("dias_al_limite") Double daysToLimit,
            @JsonProperty("limite") Double limit,
            @JsonProperty("urgente") boolean urgent,
            @JsonProperty("mensaje") String message) {
    }

    // ---------- /combinaciones ----------

    /**
     * A pairing of values this pond has never produced.
     *
     * The service answers null both when the reading follows the pattern and when there is not
     * enough history to tell, so an empty answer here is "nothing to report" and never "all fine".
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CombinationJson(
            @JsonProperty("variables") List<String> variables,
            @JsonProperty("valores") List<Double> values,
            @JsonProperty("distancia") double distance,
            @JsonProperty("corte") double cutoff,
            @JsonProperty("mensaje") String message) {
    }

    // ---------- /proyeccion-lote ----------

    public record WeighingJson(
            @JsonProperty("registrado_en") Instant recordedAt,
            @JsonProperty("peso_promedio_g") Double averageWeightG,
            @JsonProperty("muestra") Integer sample) {
    }

    public record DeathJson(
            @JsonProperty("registrado_en") Instant recordedAt,
            @JsonProperty("cantidad") Integer quantity) {
    }

    public record BatchRequest(
            @JsonProperty("fecha_siembra") LocalDate stockingDate,
            @JsonProperty("cantidad_inicial") Integer initialQuantity,
            @JsonProperty("peso_inicial_g") Double initialWeightG,
            @JsonProperty("biometrias") List<WeighingJson> weighings,
            @JsonProperty("mortalidades") List<DeathJson> deaths,
            // The pond's readings, not the batch's: that is where the water is
            @JsonProperty("lecturas") List<ReadingJson> readings,
            @JsonProperty("dias_adelante") int daysAhead,
            @JsonProperty("peso_objetivo_g") Double targetWeightG) {
    }

    /** What the projection found in the batch's numbers. clase is "crecimiento" or "mortalidad". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchFindingJson(
            @JsonProperty("clase") String kind,
            @JsonProperty("urgente") boolean urgent,
            @JsonProperty("puntaje") double score,
            @JsonProperty("mensaje") String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectionJson(
            @JsonProperty("dias_adelante") int daysAhead,
            @JsonProperty("tgc") Double tgc,
            /** false means it is the reference value from the literature, not this farm's. */
            @JsonProperty("tgc_ajustado") boolean tgcFitted,
            @JsonProperty("temp_media_c") Double meanTempC,
            @JsonProperty("peso_actual_g") Double currentWeightG,
            @JsonProperty("peso_proyectado_g") Double projectedWeightG,
            @JsonProperty("dias_para_objetivo") Integer daysToTarget,
            @JsonProperty("fecha_objetivo") LocalDate targetDate,
            @JsonProperty("cantidad_actual") Integer currentQuantity,
            @JsonProperty("cantidad_proyectada") Integer projectedQuantity,
            /** A ceiling, not a measurement: what was never recorded is not counted. */
            @JsonProperty("supervivencia_pct") Double survivalPct,
            @JsonProperty("muertes_por_dia") Double deathsPerDay,
            @JsonProperty("hallazgos") List<BatchFindingJson> findings,
            /** What could not be worked out and why. It is what stops a null being read as a zero. */
            @JsonProperty("notas") List<String> notes) {
    }

    // ---------- /pronostico-temperatura ----------

    public record ForecastRequest(
            @JsonProperty("lecturas") List<ReadingJson> readings,
            @JsonProperty("dias") int days) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastDayJson(
            @JsonProperty("fecha") String date,
            @JsonProperty("minima") double low,
            @JsonProperty("maxima") double high,
            @JsonProperty("promedio") double mean) {
    }

    /**
     * Water temperature for the coming days, learned from the pond's own readings and the air at
     * El Encano. Empty days with a reason in the message when there are not enough readings.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForecastJson(
            @JsonProperty("dias") List<ForecastDayJson> days,
            /** °C the model typically missed by; the min-max range already includes it. */
            @JsonProperty("error_tipico") Double typicalError,
            /** How slowly the pond follows the air: spring-fed reacts in days, shallow in hours. */
            @JsonProperty("reaccion_horas") Double reactionHours,
            @JsonProperty("lecturas_usadas") int readingsUsed,
            @JsonProperty("mensaje") String message) {
    }

    // ---------- health ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Health(
            @JsonProperty("estado") String state,
            @JsonProperty("version") String version) {
    }

    // ---------- what the app reads back ----------

    public record FindingResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("estanque_id") UUID pondId,
            @JsonProperty("lote_id") UUID batchId,
            @JsonProperty("clase") String kind,
            @JsonProperty("nivel") String level,
            @JsonProperty("variable") String variable,
            @JsonProperty("puntaje") double score,
            @JsonProperty("mensaje") String message,
            @JsonProperty("medido_en") Instant measuredAt,
            @JsonProperty("creado_en") Instant createdAt,
            @JsonProperty("atendido") boolean attended,
            @JsonProperty("atendido_en") Instant attendedAt,
            @JsonProperty("atendido_por") String attendedBy) {

        public static FindingResponse from(Finding f) {
            return new FindingResponse(f.getId(), f.getPondId(), f.getBatchId(), f.getKind(),
                    f.getLevel(), f.getVariable(), f.getScore(), f.getMessage(), f.getMeasuredAt(),
                    f.getCreatedAt(), f.isAttended(), f.getAttendedAt(), f.getAttendedBy());
        }
    }
}
