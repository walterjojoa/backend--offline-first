package com.lacocha.backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What a device sends to /api/webhook/{fuente}: a push without the catalogue. */
public final class Webhook {

    private Webhook() {
    }

    public record Incoming(
            // Same rules as in a push: the id ends up in the records and in the log, so it stays clean
            @JsonProperty("dispositivo_id") @NotBlank @Size(max = 64)
            @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "solo letras, números, punto, guion y guion bajo") String deviceId,
            // Fewer than a push takes: a counter sends one batch of counts, not a day of queue.
            // Validated one by one in the service, so a bad one does not block the rest.
            @JsonProperty("eventos") @NotEmpty @Size(max = 100)
            @ArraySchema(schema = @Schema(implementation = Events.FryCountInput.class))
            List<JsonNode> events) {
    }
}
