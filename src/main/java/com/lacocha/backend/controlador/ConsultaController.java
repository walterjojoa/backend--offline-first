package com.lacocha.backend.controlador;

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

import com.lacocha.backend.dto.Consultas.ConteoSalida;
import com.lacocha.backend.dto.Consultas.BiometriaSalida;
import com.lacocha.backend.dto.Consultas.AlimentacionSalida;
import com.lacocha.backend.dto.Consultas.MortalidadSalida;
import com.lacocha.backend.dto.Consultas.LecturaAguaSalida;
import com.lacocha.backend.dto.Consultas.LecturasDia;
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
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(name = "limite", defaultValue = "500") int limite) {
        return servicio.lecturasEstanque(id,
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
        List<LecturaAguaSalida> lista = servicio.lecturasEstanque(id,
                desde != null ? desde.toInstant() : null,
                hasta != null ? hasta.toInstant() : null,
                5000);
        StringBuilder csv = new StringBuilder("registrado_en,temp_c,ph,oxigeno_mg_l,origen\n");
        // Del más antiguo al más reciente, como se espera en una hoja de cálculo
        for (int i = lista.size() - 1; i >= 0; i--) {
            LecturaAguaSalida l = lista.get(i);
            csv.append(l.registradoEn()).append(',')
                    .append(valor(l.tempC())).append(',')
                    .append(valor(l.ph())).append(',')
                    .append(valor(l.oxigenoMgL())).append(',')
                    .append(l.origen()).append('\n');
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lecturas-" + id + ".csv\"")
                .body(csv.toString());
    }

    private static String valor(Double x) {
        return x != null ? x.toString() : "";
    }

    @GetMapping("/estanques/{id}/lecturas/diario")
    @Operation(summary = "Mínimo, máximo y promedio diario de temperatura y pH (para gráficas)")
    public List<LecturasDia> lecturasDiarias(@PathVariable UUID id, @RequestParam(name = "dias", defaultValue = "7") int dias) {
        return servicio.lecturasPorDia(id, dias);
    }

    @GetMapping("/lotes/{id}/conteos")
    @Operation(summary = "Historial de conteos de alevinos del lote")
    public List<ConteoSalida> conteos(@PathVariable UUID id, @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return servicio.conteosLote(id, limite);
    }

    @GetMapping("/lotes/{id}/mortalidades")
    @Operation(summary = "Historial de mortalidad del lote")
    public List<MortalidadSalida> mortalidades(@PathVariable UUID id, @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return servicio.mortalidadesLote(id, limite);
    }

    @GetMapping("/lotes/{id}/alimentaciones")
    @Operation(summary = "Historial de alimentación del lote")
    public List<AlimentacionSalida> alimentaciones(@PathVariable UUID id, @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return servicio.alimentacionesLote(id, limite);
    }

    @GetMapping("/lotes/{id}/biometrias")
    @Operation(summary = "Historial de biometrías (peso promedio) del lote")
    public List<BiometriaSalida> biometrias(@PathVariable UUID id, @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return servicio.biometriasLote(id, limite);
    }

    @GetMapping("/lotes/{id}/resumen")
    @Operation(summary = "Población, supervivencia, biomasa y ración sugerida del lote")
    public ResumenLote resumen(@PathVariable UUID id) {
        return servicio.resumenLote(id);
    }

    @GetMapping("/alertas")
    @Operation(summary = "Listar alertas (por defecto solo las pendientes)")
    public List<AlertaSalida> alertas(
            @RequestParam(name = "pendientes", defaultValue = "true") boolean pendientes,
            @RequestParam(name = "estanque_id", required = false) UUID estanqueId,
            @RequestParam(name = "limite", defaultValue = "100") int limite) {
        return servicio.listarAlertas(pendientes, estanqueId, limite);
    }

    @PostMapping("/alertas/{id}/atender")
    @Operation(summary = "Marcar una alerta como atendida")
    public AlertaSalida atender(@PathVariable UUID id) {
        return servicio.atenderAlerta(id);
    }
}
