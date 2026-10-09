package com.lacocha.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacocha.backend.model.Device;
import com.lacocha.backend.model.SyncLog;

import jakarta.validation.constraints.Size;

/** Phones and nodes that sync, and the log of their pushes. */
public final class Devices {

    private Devices() {
    }

    public record DeviceUpdate(
            @JsonProperty("descripcion") @Size(min = 1, max = 120) String description,
            @JsonProperty("activo") Boolean active) {
    }

    public record DeviceResponse(
            String id,
            @JsonProperty("descripcion") String description,
            @JsonProperty("activo") boolean active,
            @JsonProperty("primer_visto_en") Instant firstSeenAt,
            @JsonProperty("ultimo_visto_en") Instant lastSeenAt) {

        public static DeviceResponse from(Device d) {
            return new DeviceResponse(d.getId(), d.getDescription(), d.isActive(), d.getFirstSeenAt(), d.getLastSeenAt());
        }
    }

    public record SyncLogResponse(
            UUID id,
            @JsonProperty("dispositivo_id") String deviceId,
            @JsonProperty("aceptados") int accepted,
            @JsonProperty("duplicados") int duplicates,
            @JsonProperty("obsoletos") int stale,
            @JsonProperty("rechazados") int rejected,
            @JsonProperty("alertas_generadas") int alertsCreated,
            @JsonProperty("servidor_en") Instant serverTime) {

        public static SyncLogResponse from(SyncLog s) {
            return new SyncLogResponse(s.getId(), s.getDeviceId(), s.getAccepted(), s.getDuplicates(), s.getStale(),
                    s.getRejected(), s.getAlertsCreated(), s.getServerTime());
        }
    }
}
