package com.lacocha.backend.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacocha.backend.model.FeedingRate;
import com.lacocha.backend.model.RangeParameter;
import com.lacocha.backend.model.TemperatureFactor;

/** What the expert system decides with, and where each number comes from. */
public final class Parameters {

    private Parameters() {
    }

    public record RangeResponse(
            String variable,
            @JsonProperty("nombre") String label,
            @JsonProperty("unidad") String unit,
            @JsonProperty("optimo_min") Double optimalMin,
            @JsonProperty("optimo_max") Double optimalMax,
            @JsonProperty("critico_min") Double criticalMin,
            @JsonProperty("critico_max") Double criticalMax,
            @JsonProperty("fuente") String source,
            @JsonProperty("actualizado_en") Instant updatedAt) {

        public static RangeResponse from(RangeParameter p) {
            return new RangeResponse(p.getVariable(), p.getLabel(), p.getUnit().trim(), p.getOptimalMin(),
                    p.getOptimalMax(), p.getCriticalMin(), p.getCriticalMax(), p.getSource(), p.getUpdatedAt());
        }
    }

    public record RateResponse(
            @JsonProperty("orden") Integer position,
            @JsonProperty("peso_hasta_g") Double upToWeightG,
            @JsonProperty("tasa_pct") Double ratePct,
            @JsonProperty("fuente") String source) {

        public static RateResponse from(FeedingRate r) {
            return new RateResponse(r.getPosition(), r.getUpToWeightG(), r.getRatePct(), r.getSource());
        }
    }

    public record FactorResponse(
            @JsonProperty("orden") Integer position,
            @JsonProperty("temp_hasta_c") Double upToTempC,
            Double factor,
            @JsonProperty("fuente") String source) {

        public static FactorResponse from(TemperatureFactor f) {
            return new FactorResponse(f.getPosition(), f.getUpToTempC(), f.getFactor(), f.getSource());
        }
    }

    public record ParametersResponse(
            @JsonProperty("rangos") List<RangeResponse> ranges,
            @JsonProperty("tasas_alimentacion") List<RateResponse> feedingRates,
            @JsonProperty("factores_temperatura") List<FactorResponse> temperatureFactors) {
    }
}
