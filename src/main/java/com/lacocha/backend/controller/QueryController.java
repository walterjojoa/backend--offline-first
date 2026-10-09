package com.lacocha.backend.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

import com.lacocha.backend.dto.Queries.BatchSummary;
import com.lacocha.backend.dto.Queries.BiometryResponse;
import com.lacocha.backend.dto.Queries.DailyReadings;
import com.lacocha.backend.dto.Queries.FeedingResponse;
import com.lacocha.backend.dto.Queries.FryCountResponse;
import com.lacocha.backend.dto.Queries.MortalityResponse;
import com.lacocha.backend.dto.Queries.WaterReadingResponse;
import com.lacocha.backend.dto.Sync.AlertResponse;
import com.lacocha.backend.service.QueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Consultas")
public class QueryController {

    private final QueryService service;

    public QueryController(QueryService service) {
        this.service = service;
    }

    @GetMapping("/estanques/{id}/lecturas")
    @Operation(summary = "Historial de pH y temperatura de un estanque")
    public List<WaterReadingResponse> readings(
            @PathVariable UUID id,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @RequestParam(name = "limite", defaultValue = "500") int limit) {
        return service.pondReadings(id, toInstant(since), toInstant(until), limit);
    }

    @GetMapping(value = "/estanques/{id}/lecturas.csv", produces = "text/csv")
    @Operation(summary = "Descargar el historial de lecturas en CSV (para Excel o análisis de la tesis)")
    public ResponseEntity<String> readingsCsv(
            @PathVariable UUID id,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        List<WaterReadingResponse> list = service.pondReadings(id, toInstant(since), toInstant(until), 5000);
        // CSV headers match the JSON names
        StringBuilder csv = new StringBuilder("registrado_en,temp_c,ph,oxigeno_mg_l,origen\n");
        // Oldest to newest, as expected in a spreadsheet
        for (int i = list.size() - 1; i >= 0; i--) {
            WaterReadingResponse r = list.get(i);
            csv.append(r.recordedAt()).append(',')
                    .append(cell(r.tempC())).append(',')
                    .append(cell(r.ph())).append(',')
                    .append(cell(r.oxygenMgL())).append(',')
                    .append(r.source()).append('\n');
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lecturas-" + id + ".csv\"")
                .body(csv.toString());
    }

    @GetMapping("/estanques/{id}/lecturas/diario")
    @Operation(summary = "Mínimo, máximo y promedio diario de temperatura y pH (para gráficas)")
    public List<DailyReadings> dailyReadings(@PathVariable UUID id,
            @RequestParam(name = "dias", defaultValue = "7") int days) {
        return service.dailyReadings(id, days);
    }

    @GetMapping("/lotes/{id}/conteos")
    @Operation(summary = "Historial de conteos de alevinos del lote")
    public List<FryCountResponse> fryCounts(@PathVariable UUID id,
            @RequestParam(name = "limite", defaultValue = "100") int limit) {
        return service.fryCounts(id, limit);
    }

    @GetMapping("/lotes/{id}/mortalidades")
    @Operation(summary = "Historial de mortalidad del lote")
    public List<MortalityResponse> mortalities(@PathVariable UUID id,
            @RequestParam(name = "limite", defaultValue = "100") int limit) {
        return service.mortalities(id, limit);
    }

    @GetMapping("/lotes/{id}/alimentaciones")
    @Operation(summary = "Historial de alimentación del lote")
    public List<FeedingResponse> feedings(@PathVariable UUID id,
            @RequestParam(name = "limite", defaultValue = "100") int limit) {
        return service.feedings(id, limit);
    }

    @GetMapping("/lotes/{id}/biometrias")
    @Operation(summary = "Historial de biometrías (peso promedio) del lote")
    public List<BiometryResponse> biometries(@PathVariable UUID id,
            @RequestParam(name = "limite", defaultValue = "100") int limit) {
        return service.biometries(id, limit);
    }

    @GetMapping("/lotes/{id}/resumen")
    @Operation(summary = "Población, supervivencia, biomasa y ración sugerida del lote")
    public BatchSummary batchSummary(@PathVariable UUID id) {
        return service.batchSummary(id);
    }

    @GetMapping("/alertas")
    @Operation(summary = "Listar alertas (por defecto solo las pendientes)")
    public List<AlertResponse> alerts(
            @RequestParam(name = "pendientes", defaultValue = "true") boolean pendingOnly,
            @RequestParam(name = "estanque_id", required = false) UUID pondId,
            @RequestParam(name = "limite", defaultValue = "100") int limit) {
        return service.listAlerts(pendingOnly, pondId, limit);
    }

    @PostMapping("/alertas/{id}/atender")
    @Operation(summary = "Marcar una alerta como atendida")
    public AlertResponse attendAlert(@PathVariable UUID id) {
        return service.attendAlert(id);
    }

    private static Instant toInstant(OffsetDateTime date) {
        return date != null ? date.toInstant() : null;
    }

    private static String cell(Double x) {
        return x != null ? x.toString() : "";
    }
}
