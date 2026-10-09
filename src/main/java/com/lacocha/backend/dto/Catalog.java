package com.lacocha.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.Pond;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Ponds and batches: what the panel creates, what the phone uploads and what is returned.
 * The JSON names stay in Spanish (@JsonProperty) because they are the contract with the app and the ESP32.
 */
public final class Catalog {

    private Catalog() {
    }

    static final String POND_TYPES = "estanque|tanque|jaula";
    static final String BATCH_STATUSES = "activo|cerrado";

    public record PondCreate(
            UUID id,
            @JsonProperty("nombre") @NotBlank @Size(max = 80) String name,
            @JsonProperty("tipo") @Pattern(regexp = POND_TYPES) String type,
            @JsonProperty("volumen_m3") @Positive Double volumeM3) {
    }

    public record PondUpdate(
            @JsonProperty("nombre") @Size(min = 1, max = 80) String name,
            @JsonProperty("tipo") @Pattern(regexp = POND_TYPES) String type,
            @JsonProperty("volumen_m3") @Positive Double volumeM3,
            @JsonProperty("activo") Boolean active) {
    }

    public record PondSync(
            @NotNull UUID id,
            @JsonProperty("nombre") @NotBlank @Size(max = 80) String name,
            @JsonProperty("tipo") @Pattern(regexp = POND_TYPES) String type,
            @JsonProperty("volumen_m3") @Positive Double volumeM3,
            @JsonProperty("activo") Boolean active,
            @JsonProperty("actualizado_en") @NotNull OffsetDateTime updatedAt) {
    }

    public record PondResponse(
            UUID id,
            @JsonProperty("nombre") String name,
            @JsonProperty("tipo") String type,
            @JsonProperty("volumen_m3") Double volumeM3,
            @JsonProperty("activo") boolean active,
            @JsonProperty("actualizado_en") Instant updatedAt) {

        public static PondResponse from(Pond p) {
            return new PondResponse(p.getId(), p.getName(), p.getType(), p.getVolumeM3(), p.isActive(),
                    p.getUpdatedAt());
        }
    }

    public record BatchCreate(
            UUID id,
            @JsonProperty("estanque_id") @NotNull UUID pondId,
            @JsonProperty("codigo") @NotBlank @Size(max = 40) String code,
            @JsonProperty("fecha_siembra") @PastOrPresent LocalDate stockingDate,
            @JsonProperty("cantidad_inicial") @PositiveOrZero Integer initialQuantity,
            @JsonProperty("peso_inicial_g") @Positive Double initialWeightG,
            @JsonProperty("estado") @Pattern(regexp = BATCH_STATUSES) String status,
            @JsonProperty("fecha_cierre") @PastOrPresent LocalDate closingDate) {
    }

    public record BatchUpdate(
            @JsonProperty("codigo") @Size(min = 1, max = 40) String code,
            @JsonProperty("fecha_siembra") @PastOrPresent LocalDate stockingDate,
            @JsonProperty("cantidad_inicial") @PositiveOrZero Integer initialQuantity,
            @JsonProperty("peso_inicial_g") @Positive Double initialWeightG,
            @JsonProperty("estado") @Pattern(regexp = BATCH_STATUSES) String status,
            @JsonProperty("fecha_cierre") @PastOrPresent LocalDate closingDate) {
    }

    public record BatchSync(
            @NotNull UUID id,
            @JsonProperty("estanque_id") @NotNull UUID pondId,
            @JsonProperty("codigo") @NotBlank @Size(max = 40) String code,
            @JsonProperty("fecha_siembra") @PastOrPresent LocalDate stockingDate,
            @JsonProperty("cantidad_inicial") @PositiveOrZero Integer initialQuantity,
            @JsonProperty("peso_inicial_g") @Positive Double initialWeightG,
            @JsonProperty("estado") @Pattern(regexp = BATCH_STATUSES) String status,
            @JsonProperty("fecha_cierre") @PastOrPresent LocalDate closingDate,
            @JsonProperty("actualizado_en") @NotNull OffsetDateTime updatedAt) {
    }

    public record BatchResponse(
            UUID id,
            @JsonProperty("estanque_id") UUID pondId,
            @JsonProperty("codigo") String code,
            @JsonProperty("fecha_siembra") LocalDate stockingDate,
            @JsonProperty("cantidad_inicial") Integer initialQuantity,
            @JsonProperty("peso_inicial_g") Double initialWeightG,
            @JsonProperty("estado") String status,
            @JsonProperty("fecha_cierre") LocalDate closingDate,
            @JsonProperty("actualizado_en") Instant updatedAt) {

        public static BatchResponse from(Batch b) {
            return new BatchResponse(b.getId(), b.getPondId(), b.getCode(), b.getStockingDate(),
                    b.getInitialQuantity(), b.getInitialWeightG(), b.getStatus(), b.getClosingDate(),
                    b.getUpdatedAt());
        }
    }
}
