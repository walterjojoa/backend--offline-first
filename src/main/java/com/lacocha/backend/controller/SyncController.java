package com.lacocha.backend.controller;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Sync.PullResponse;
import com.lacocha.backend.dto.Sync.PushRequest;
import com.lacocha.backend.dto.Sync.PushResponse;
import com.lacocha.backend.service.SyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sync")
@Tag(name = "Sincronización")
public class SyncController {

    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    @PostMapping("/push")
    @Operation(summary = "El celular sube su cola de pendientes (idempotente)")
    public PushResponse push(@Valid @RequestBody PushRequest peticion) {
        return service.push(peticion);
    }

    @GetMapping("/pull")
    @Operation(summary = "El celular baja cambios del catálogo y alertas pendientes")
    public PullResponse pull(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde) {
        return service.pull(desde != null ? desde.toInstant() : null);
    }
}
