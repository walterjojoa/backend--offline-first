package com.lacocha.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.lacocha.backend.dto.Catalog.BatchResponse;
import com.lacocha.backend.dto.Catalog.BatchSync;
import com.lacocha.backend.dto.Catalog.PondResponse;
import com.lacocha.backend.dto.Catalog.PondSync;
import com.lacocha.backend.model.Alert;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class Sync {

    private Sync() {
    }

    public record PushRequest(
            // Letters, digits, dash, underscore and dot: keeps the id clean in the records and in the log
            @JsonProperty("dispositivo_id") @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "solo letras, números, punto, guion y guion bajo") String deviceId,
            // Ponds, batches and events are validated one by one in the service: a bad one must not
            // block the phone's queue. The schemas only tell /docs what each item looks like.
            @JsonProperty("estanques") @Size(max = 200)
            @ArraySchema(schema = @Schema(implementation = PondSync.class)) List<JsonNode> ponds,
            @JsonProperty("lotes") @Size(max = 200)
            @ArraySchema(schema = @Schema(implementation = BatchSync.class)) List<JsonNode> batches,
            @JsonProperty("eventos") @Size(max = 500) List<JsonNode> events) {
    }

    public record Rejection(String id, String error) {
    }

    public record PushResponse(
            @JsonProperty("aceptados") List<UUID> accepted,
            @JsonProperty("duplicados") List<UUID> duplicates,
            @JsonProperty("obsoletos") List<UUID> stale,
            @JsonProperty("rechazados") List<Rejection> rejected,
            @JsonProperty("alertas_generadas") int alertsCreated,
            @JsonProperty("servidor_en") Instant serverTime) {
    }

    public record AlertResponse(
            UUID id,
            @JsonProperty("estanque_id") UUID pondId,
            @JsonProperty("lectura_id") UUID readingId,
            String variable,
            @JsonProperty("valor") Double value,
            @JsonProperty("nivel") String level,
            @JsonProperty("mensaje") String message,
            @JsonProperty("medido_en") Instant measuredAt,
            @JsonProperty("atendida") boolean attended,
            @JsonProperty("atendida_en") Instant attendedAt) {

        public static AlertResponse from(Alert a) {
            return new AlertResponse(a.getId(), a.getPondId(), a.getReadingId(), a.getVariable(), a.getValue(),
                    a.getLevel(), a.getMessage(), a.getMeasuredAt(), a.isAttended(), a.getAttendedAt());
        }
    }

    public record PullResponse(
            @JsonProperty("estanques") List<PondResponse> ponds,
            @JsonProperty("lotes") List<BatchResponse> batches,
            @JsonProperty("alertas_pendientes") List<AlertResponse> pendingAlerts,
            @JsonProperty("servidor_en") Instant serverTime) {
    }
}
