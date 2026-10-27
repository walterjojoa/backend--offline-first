package com.lacocha.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.lacocha.backend.model.Finding;

/**
 * What travels between this backend and the Python service, and what the app reads back.
 *
 * The JSON names are the Spanish ones the rest of the project already uses, because they are the
 * names of the database columns too ({@code registrado_en}, {@code oxigeno_mg_l}). A request is
 * built by copying what is already in an entity, with no translation layer in the middle where a
 * field can go missing in silence.
 *
 * Every response record is annotated to ignore unknown properties. That is deliberate: the Python
 * service and this backend are deployed separately, so a version of the service that starts
 * returning one extra field must not break a backend that has not been redeployed yet.
 */
public final class Ai {

    private Ai() {
    }

    // ---------- what the service finds, which both analyses share ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FindingJson(
            @JsonProperty("clase") String kind,
            @JsonProperty("nivel") String level,
            @JsonProperty("mensaje") String message,
            @JsonProperty("puntaje") double score,
            @JsonProperty("variable") String variable,
            @JsonProperty("medido_en") Instant measuredAt) {
    }

    // ---------- water analysis ----------

    public record ReadingJson(
            @JsonProperty("registrado_en") Instant recordedAt,
            @JsonProperty("temp_c") Double tempC,
            @JsonProperty("ph") Double ph,
            @JsonProperty("oxigeno_mg_l") Double oxygenMgL) {
    }

    /** One row of parametros_rango. Without it the service cannot grade how urgent anything is. */
    public record RangeJson(
            @JsonProperty("variable") String variable,
            // NOT NULL en parametros_rango, pero la entidad lo declara envuelto
            @JsonProperty("critico_min") Double criticalMin,
            @JsonProperty("critico_max") Double criticalMax,
            @JsonProperty("optimo_min") Double optimalMin,
            @JsonProperty("optimo_max") Double optimalMax) {
    }

    public record WaterRequest(
            @JsonProperty("estanque_id") UUID pondId,
            // The pond's own history: it is what defines what counts as normal *there*
            @JsonProperty("historial") List<ReadingJson> history,
            @JsonProperty("rangos") List<RangeJson> ranges) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WaterAnalysis(
            @JsonProperty("estanque_id") UUID pondId,
            @JsonProperty("lecturas_usadas") int readingsUsed,
            @JsonProperty("dias_cubiertos") double daysCovered,
            /** Which detectors managed to run. Empty means there was no data for any of them. */
            @JsonProperty("detectores") List<String> detectors,
            /**
             * false is not "everything is fine": it is "I could not tell". Keeping the two apart is
             * the whole point of the service answering 200 instead of an error in that case.
             */
            @JsonProperty("suficientes_datos") boolean enoughData,
            @JsonProperty("hallazgos") List<FindingJson> findings) {
    }

    // ---------- batch projection ----------

    public record BiometryJson(
            @JsonProperty("registrado_en") Instant recordedAt,
            @JsonProperty("peso_promedio_g") Double averageWeightG,
            @JsonProperty("muestra") Integer sample) {
    }

    public record MortalityJson(
            @JsonProperty("registrado_en") Instant recordedAt,
            @JsonProperty("cantidad") Integer quantity) {
    }

    public record TemperatureJson(
            @JsonProperty("registrado_en") Instant recordedAt,
            @JsonProperty("temp_c") Double tempC) {
    }

    public record BatchRequest(
            @JsonProperty("lote_id") UUID batchId,
            @JsonProperty("fecha_siembra") LocalDate stockingDate,
            @JsonProperty("cantidad_inicial") Integer initialQuantity,
            @JsonProperty("peso_inicial_g") Double initialWeightG,
            @JsonProperty("biometrias") List<BiometryJson> biometries,
            @JsonProperty("mortalidades") List<MortalityJson> mortalities,
            // From the pond the batch lives in: trout grow with accumulated heat, not with days
            @JsonProperty("temperaturas") List<TemperatureJson> temperatures,
            @JsonProperty("dias_adelante") int daysAhead,
            @JsonProperty("peso_objetivo_g") Double targetWeightG) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchProjection(
            @JsonProperty("lote_id") UUID batchId,
            @JsonProperty("dias_adelante") int daysAhead,
            @JsonProperty("tgc") Double tgc,
            /** false means it is the reference value from the literature, not this farm's. */
            @JsonProperty("tgc_ajustado") boolean tgcFitted,
            @JsonProperty("fuente_tgc") String tgcSource,
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
            @JsonProperty("hallazgos") List<FindingJson> findings,
            /** What could not be worked out and why. It is what stops a null being read as a zero. */
            @JsonProperty("notas") List<String> notes) {
    }

    // ---------- health ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Health(
            @JsonProperty("estado") String state,
            @JsonProperty("version") String version,
            @JsonProperty("detectores") List<String> detectors) {
    }

    // ---------- what the app reads ----------

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
