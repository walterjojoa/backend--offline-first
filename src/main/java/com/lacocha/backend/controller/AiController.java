package com.lacocha.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.lacocha.backend.dto.Ai.BatchProjection;
import com.lacocha.backend.dto.Ai.FindingResponse;
import com.lacocha.backend.service.PisciculturaFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** What the AI service found, and where a batch is heading. */
@RestController
@RequestMapping("/api")
@Tag(name = "IA", description = "Hallazgos del servicio de análisis y proyección de lotes")
public class AiController {

    private final PisciculturaFacade facade;

    public AiController(PisciculturaFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/ia/disponible")
    @Operation(summary = "Si hay servicio de IA configurado y respondiendo")
    public Disponible available() {
        return new Disponible(facade.aiAvailable());
    }

    /** Its own tiny record so the answer is an object and not a bare boolean in the body. */
    public record Disponible(boolean disponible) {
    }

    @GetMapping("/hallazgos")
    @Operation(summary = "Hallazgos pendientes de atender, lo más nuevo primero")
    public List<FindingResponse> pending() {
        return facade.pendingFindings();
    }

    @GetMapping("/estanques/{id}/hallazgos")
    @Operation(summary = "Historial de hallazgos de un estanque")
    public List<FindingResponse> ofPond(@PathVariable UUID id) {
        return facade.pondFindings(id);
    }

    @GetMapping("/lotes/{id}/hallazgos")
    @Operation(summary = "Historial de hallazgos de un lote")
    public List<FindingResponse> ofBatch(@PathVariable UUID id) {
        return facade.batchFindings(id);
    }

    /**
     * Marks a finding as looked at.
     *
     * Same convention the alerts already use: the device id when it comes from a phone, "panel"
     * when it comes from the browser. Attending one is also what lets it be raised again later —
     * while it is pending it silences its own repeats.
     */
    @PostMapping("/hallazgos/{id}/atender")
    @Operation(summary = "Marcar un hallazgo como atendido, dejando quién y cuándo")
    public FindingResponse attend(
            @PathVariable UUID id,
            @RequestParam(name = "dispositivo_id", required = false) String deviceId) {
        return facade.attendFinding(id, deviceId != null ? deviceId : "panel");
    }

    /**
     * Projected weight, harvest date and survival of a batch.
     *
     * Worked out on the spot and never stored. A projection kept from a month ago, made with the
     * data of a month ago, is worse than no projection: it looks current and is not.
     *
     * 503 and not an empty body when there is no AI service: the screen has to be able to say "not
     * available right now" rather than show a projection full of blanks that reads like a batch
     * about which nothing is known.
     */
    @GetMapping("/lotes/{id}/proyeccion")
    @Operation(summary = "Peso proyectado, fecha de cosecha y supervivencia del lote")
    public BatchProjection projection(@PathVariable UUID id) {
        return facade.batchProjection(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de IA no está disponible en este momento. "
                        + "El registro y las alertas de umbral siguen funcionando."));
    }
}
