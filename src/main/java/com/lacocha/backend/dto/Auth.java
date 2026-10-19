package com.lacocha.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacocha.backend.model.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What goes in and out of /api/auth. The JSON stays in Spanish, like the rest of the API. */
public final class Auth {
    private Auth() {
    }

    /** Digits only: it is an id number, and letting spaces or dots in makes it unmatchable later. */

    private static final String DOCUMENT = "\\d{5,20}";

    public record RegisterRequest(
            @JsonProperty("documento") @NotBlank @Pattern(regexp = DOCUMENT,
                    message = "debe ser un número de 5 a 20 dígitos") String document,
            @JsonProperty("nombre") @NotBlank @Size(max = 120) String name,
            // Eight is the shortest that is worth anything, and the caretaker types it with wet hands
            @JsonProperty("clave") @NotBlank @Size(min = 8, max = 72,
                    message = "debe tener entre 8 y 72 caracteres") String password,
            @JsonProperty("dispositivo_id") @Size(max = 64) String deviceId) {
    }
}

