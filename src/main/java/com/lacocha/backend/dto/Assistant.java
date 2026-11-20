package com.lacocha.backend.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The farm assistant (AI level 3): what the app asks, what goes to the AI service, what comes back. */
public final class Assistant {

    private Assistant() {
    }

    public record ChatTurn(
            @JsonProperty("rol") @NotNull @Pattern(regexp = "usuario|asistente", message = "debe ser usuario o asistente") String role,
            @JsonProperty("texto") @NotNull @Size(max = 20000) String text) {
    }

    /** From the app: the question and the conversation so far (the phone keeps it, the server does not). */
    public record Question(
            @JsonProperty("pregunta") @NotBlank @Size(max = 4000) String question,
            @JsonProperty("historial") @Size(max = 40) List<@Valid ChatTurn> history) {
    }

    /** To the AI service: the same question plus the state of the farm, which only the backend has. */
    public record AssistantRequest(
            @JsonProperty("pregunta") String question,
            @JsonProperty("datos_finca") Map<String, Object> farm,
            @JsonProperty("historial") List<ChatTurn> history) {
    }

    public record Answer(
            @JsonProperty("respuesta") String answer,
            @JsonProperty("modelo") String model) {
    }
}
