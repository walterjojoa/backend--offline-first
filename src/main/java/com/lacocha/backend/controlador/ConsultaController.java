package com.lacocha.backend.controlador;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Consultas.ConteoSalida;
import com.lacocha.backend.dto.Consultas.LecturaAguaSalida;
import com.lacocha.backend.dto.Consultas.ResumenLote;
import com.lacocha.backend.dto.Sync.AlertaSalida;
import com.lacocha.backend.servicio.ConsultaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Consultas")
public class ConsultaController {

    private final ConsultaService servicio;

    public ConsultaController(ConsultaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/estanques/{id}/lecturas")
    @Operation(summary = "Historial de pH y temperatura de un estanque")
    public List<LecturaAguaSalida> lecturas(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(defaultValue = "500") int limite) {
        return servicio.lecturasEstanque(id,
                desde != null ? desde.toInstant() : null,
                hasta != null ? hasta.toInstant() : null,
                limite);
    }

    @GetMapping("/lotes/{id}/conteos")
    @Operation(summary = "Historial de conteos de alevinos del lote")
    public List<ConteoSalida> conteos(@PathVariable UUID id, @RequestParam(defaultValue = "100") int limite) {
        return servicio.conteosLote(id, limite);
    }

    @GetMapping("/lotes/{id}/resumen")
    @Operation(summary = "Población, supervivencia, biomasa y ración sugerida del lote")
    public ResumenLote resumen(@PathVariable UUID id) {
        return servicio.resumenLote(id);
    }

    @GetMapping("/alertas")
    public List<AlertaSalida> alertas(
            @RequestParam(defaultValue = "true") boolean pendientes,
            @RequestParam(name = "estanque_id", required = false) UUID estanqueId,
            @RequestParam(defaultValue = "100") int limite) {
        return servicio.listarAlertas(pendientes, estanqueId, limite);
    }

    @PostMapping("/alertas/{id}/atender")
    public AlertaSalida atender(@PathVariable UUID id) {
        return servicio.atenderAlerta(id);
    }
}
