package com.lacocha.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacocha.backend.model.Biometry;
import com.lacocha.backend.model.Feeding;
import com.lacocha.backend.model.FryCount;
import com.lacocha.backend.model.Mortality;
import com.lacocha.backend.model.WaterReading;

/** Responses of the read-only endpoints (history, summaries). JSON names stay in Spanish. */
public final class Queries {

    private Queries() {
    }

    public record WaterReadingResponse(
            UUID id,
            @JsonProperty("estanque_id") UUID pondId,
            Double tempC,
            Double ph,
            @JsonProperty("oxigeno_mg_l") Double oxygenMgL,
            @JsonProperty("origen") String source,
            @JsonProperty("registrado_en") Instant recordedAt) {

        public static WaterReadingResponse from(WaterReading r) {
            return new WaterReadingResponse(r.getId(), r.getPondId(), r.getTempC(), r.getPh(), r.getOxygenMgL(),
                    r.getSource(), r.getRecordedAt());
        }
    }

    /** Summary of one day of water readings, for the panel charts. */
    public record DailyReadings(
            @JsonProperty("fecha") LocalDate date,
            @JsonProperty("lecturas") int readings,
            Double tempMin,
            Double tempMax,
            @JsonProperty("temp_promedio") Double tempAvg,
            Double phMin,
            Double phMax,
            @JsonProperty("ph_promedio") Double phAvg) {
    }

    public record FryCountResponse(
            UUID id,
            @JsonProperty("lote_id") UUID batchId,
            Integer total,
            @JsonProperty("cortes_multiples") Integer multipleCuts,
            @JsonProperty("origen") String source,
            @JsonProperty("dispositivo_id") String deviceId,
            @JsonProperty("registrado_en") Instant recordedAt) {

        public static FryCountResponse from(FryCount c) {
            return new FryCountResponse(c.getId(), c.getBatchId(), c.getTotal(), c.getMultipleCuts(), c.getSource(),
                    c.getDeviceId(), c.getRecordedAt());
        }
    }

    public record MortalityResponse(
            UUID id,
            @JsonProperty("lote_id") UUID batchId,
            @JsonProperty("cantidad") Integer quantity,
            @JsonProperty("causa") String cause,
            @JsonProperty("origen") String source,
            @JsonProperty("dispositivo_id") String deviceId,
            @JsonProperty("registrado_en") Instant recordedAt) {

        public static MortalityResponse from(Mortality m) {
            return new MortalityResponse(m.getId(), m.getBatchId(), m.getQuantity(), m.getCause(), m.getSource(),
                    m.getDeviceId(), m.getRecordedAt());
        }
    }

    public record FeedingResponse(
            UUID id,
            @JsonProperty("lote_id") UUID batchId,
            Double kg,
            @JsonProperty("origen") String source,
            @JsonProperty("dispositivo_id") String deviceId,
            @JsonProperty("registrado_en") Instant recordedAt) {

        public static FeedingResponse from(Feeding f) {
            return new FeedingResponse(f.getId(), f.getBatchId(), f.getKg(), f.getSource(),
                    f.getDeviceId(), f.getRecordedAt());
        }
    }

    public record BiometryResponse(
            UUID id,
            @JsonProperty("lote_id") UUID batchId,
            @JsonProperty("peso_promedio_g") Double avgWeightG,
            @JsonProperty("muestra") Integer sampleSize,
            @JsonProperty("origen") String source,
            @JsonProperty("dispositivo_id") String deviceId,
            @JsonProperty("registrado_en") Instant recordedAt) {

        public static BiometryResponse from(Biometry b) {
            return new BiometryResponse(b.getId(), b.getBatchId(), b.getAvgWeightG(), b.getSampleSize(), b.getSource(),
                    b.getDeviceId(), b.getRecordedAt());
        }
    }

    public record BatchSummary(
            @JsonProperty("lote_id") UUID batchId,
            @JsonProperty("codigo") String code,
            @JsonProperty("estanque_id") UUID pondId,
            @JsonProperty("dias_cultivo") Long cultureDays,
            @JsonProperty("cantidad_inicial") Integer initialQuantity,
            @JsonProperty("poblacion_estimada") Integer estimatedPopulation,
            @JsonProperty("mortalidad_total") long totalMortality,
            @JsonProperty("supervivencia_pct") Double survivalPct,
            @JsonProperty("peso_promedio_g") Double avgWeightG,
            @JsonProperty("biomasa_kg") Double biomassKg,
            @JsonProperty("densidad_kg_m3") Double densityKgM3,
            @JsonProperty("ultima_temperatura_c") Double lastTemperatureC,
            @JsonProperty("ultima_lectura_en") Instant lastReadingAt,
            @JsonProperty("tasa_alimentacion_pct") Double feedingRatePct,
            @JsonProperty("racion_diaria_kg") Double dailyRationKg,
            @JsonProperty("alimento_ultima_semana_kg") double feedLastWeekKg,
            @JsonProperty("alimento_total_kg") double totalFeedKg,
            @JsonProperty("conversion_alimenticia") Double feedConversionRatio,
            @JsonProperty("alertas_pendientes") long pendingAlerts,
            @JsonProperty("notas") List<String> notes) {
    }
}
