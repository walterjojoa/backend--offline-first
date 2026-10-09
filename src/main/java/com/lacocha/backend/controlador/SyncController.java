package com.lacocha.backend.controlador;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Sync.PullRespuesta;
import com.lacocha.backend.dto.Sync.PushPeticion;
import com.lacocha.backend.dto.Sync.PushRespuesta;
import com.lacocha.backend.servicio.SyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sync")
@Tag(name = "Sincronización")
public class SyncController {

    private final SyncService servicio;

    public SyncController(SyncService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/push")
    @Operation(summary = "El celular sube su cola de pendientes (idempotente)")
    public PushRespuesta push(@Valid @RequestBody PushPeticion peticion) {
        return servicio.push(peticion);
    }

    @GetMapping("/pull")
    @Operation(summary = "El celular baja cambios del catálogo y alertas pendientes")
    public PullRespuesta pull(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde) {
        return servicio.pull(desde != null ? desde.toInstant() : null);
    }
}
