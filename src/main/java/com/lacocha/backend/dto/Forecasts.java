package com.lacocha.backend.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What goes to the AI service and what comes back. Same Spanish contract as that service. */
public final class Forecasts {

    private Forecasts() {
    }

    public record TemperatureReading(
            @JsonProperty("registrado_en") Instant recordedAt,
            @JsonProperty("temp_c") Double tempC) {
    }

    public record WaterForecastRequest(
            @JsonProperty("lecturas") List<TemperatureReading> readings,
            @JsonProperty("dias") int days) {
    }

    public record ForecastDay(
            @JsonProperty("fecha") String date,
            @JsonProperty("minima") double low,
            @JsonProperty("maxima") double high,
            @JsonProperty("promedio") double mean) {
    }

    /** days is empty when the pond still has too few readings; message says how many are missing. */
    public record WaterForecast(
            @JsonProperty("dias") List<ForecastDay> days,
            @JsonProperty("error_tipico") Double typicalError,
            @JsonProperty("reaccion_horas") Double reactionHours,
            @JsonProperty("lecturas_usadas") int readingsUsed,
            @JsonProperty("mensaje") String message) {
    }
}
