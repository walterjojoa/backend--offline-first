package com.lacocha.backend.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Queries.FryCountResponse;
import com.lacocha.backend.dto.Queries.BiometryResponse;
import com.lacocha.backend.dto.Queries.FeedingResponse;
import com.lacocha.backend.dto.Queries.MortalityResponse;
import com.lacocha.backend.dto.Queries.WaterReadingResponse;
import com.lacocha.backend.dto.Queries.DailyReadings;
import com.lacocha.backend.dto.Queries.BatchSummary;
import com.lacocha.backend.dto.Sync.AlertResponse;
import com.lacocha.backend.service.ConsultaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Consultas")
public class ConsultaController {

    private final ConsultaService service;

    public ConsultaController(ConsultaService service) {
        this.service = service;
    }

    @GetMapping("/estanques/{id}/lecturas")
    @Operation(summary = "Historial de pH y temperatura de un estanque")
    public List<WaterReadingResponse> readings(
            @PathVariable UUID id,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(name = "limite", defaultValue = "500") int limite) {
        return service.lecturasEstanque(id,
                desde != null ? desde.toInstant() : null,
                hasta != null ? hasta.toInstant() : null,
                limite);
    }

    @GetMapping(value = "/estanques/{id}/lecturas.csv", produces = "text/csv")
    @Operation(summary = "Descargar el historial de lecturas en CSV (para Excel o análisis de la tesis)")
    public ResponseEntity<String> lecturasCsv(
            @PathVariable UUID id,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        List<WaterReadingResponse> lista = service.lecturasEstanque(id,
                desde != null ? desde.toInstant() : null,
                hasta != null ? hasta.toInstant() : null,
                5000);
        StringBuilder csv = new StringBuilder("registrado_en,temp_c,ph,oxigeno_mg_l,origen\n");
        // Del más antiguo al más reciente, como se espera en una hoja de cálculo
        for (int i = lista.size() - 1; i >= 0; i--) {
            WaterReadingResponse l = lista.get(i);
            csv.append(l.recordedAt()).append(',')
                    .append(value(l.tempC())).append(',')
                    .append(value(l.ph())).append(',')
                    .append(value(l.oxygenMgL())).append(',')
                    .append(l.source()).append('\n');
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lecturas-" + id + ".csv\"")
                .body(csv.toString());
    }

    private static String value(Double x) {
        return x != null ? x.toString() : "";
    }

    @GetMapping("/estanques/{id}/lecturas/diario")
    @Operation(summary = "Mínimo, máximo y promedio diario de temperatura y pH (para gráficas)")
    public List<DailyReadings> lecturasDiarias(@PathVariable UUID id, @RequestParam(name = "dias", defaultValue = "7") int dias) {
        return service.lecturasPorDia(id, dias);
    }

    @GetMapping("/lotes/{id}/conteos")
    @Operation(summary = "Historial de conteos de alevinos del lote")
    public List<FryCountResponse> conteos(@PathVariable UUID id, @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return service.conteosLote(id, limite);
    }

    @GetMapping("/lotes/{id}/mortalidades")
    @Operation(summary = "Historial de mortalidad del lote")
    public List<MortalityResponse> mortalidades(@PathVariable UUID id, @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return service.mortalidadesLote(id, limite);
    }

    @GetMapping("/lotes/{id}/alimentaciones")
    @Operation(summary = "Historial de alimentación del lote")
    public List<FeedingResponse> alimentaciones(@PathVariable UUID id, @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return service.alimentacionesLote(id, limite);
    }

    @GetMapping("/lotes/{id}/biometrias")
    @Operation(summary = "Historial de biometrías (peso promedio) del lote")
    public List<BiometryResponse> biometrias(@PathVariable UUID id, @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return service.biometriasLote(id, limite);
    }

    @GetMapping("/lotes/{id}/resumen")
    @Operation(summary = "Población, supervivencia, biomasa y ración sugerida del lote")
    public BatchSummary resumen(@PathVariable UUID id) {
        return service.resumenLote(id);
    }

    @GetMapping("/alertas")
    @Operation(summary = "Listar alertas (por defecto solo las pendientes)")
    public List<AlertResponse> alertas(
            @RequestParam(name = "pendientes", defaultValue = "true") boolean pendientes,
            @RequestParam(name = "estanque_id", required = false) UUID pondId,
            @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return service.listarAlertas(pendientes, pondId, limite);
    }

    @PostMapping("/alertas/{id}/atender")
    @Operation(summary = "Marcar una alerta como atendida")
    public AlertResponse atender(@PathVariable UUID id) {
        return service.atenderAlerta(id);
    }
}
