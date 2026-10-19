package com.lacocha.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Devices.DeviceResponse;
import com.lacocha.backend.dto.Devices.DeviceUpdate;
import com.lacocha.backend.dto.Devices.SyncLogResponse;
import com.lacocha.backend.service.PisciculturaFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Dispositivos")
public class DeviceController {

    private final PisciculturaFacade facade;

    public DeviceController(PisciculturaFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/dispositivos")
    @Operation(summary = "Celulares y nodos que sincronizan, con la última vez que lo hicieron")
    public List<DeviceResponse> list(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return facade.listDevices(Tokens.of(authorization));
    }

    @PatchMapping("/dispositivos/{id}")
    @Operation(summary = "Ponerle nombre a un dispositivo o darlo de baja")
    public DeviceResponse update(@RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String id, @Valid @RequestBody DeviceUpdate data) {
        return facade.updateDevice(Tokens.of(authorization), id, data);
    }

    @GetMapping("/sincronizaciones")
    @Operation(summary = "Historial de envíos: aceptados, duplicados y rechazados de cada push")
    public List<SyncLogResponse> syncLog(@RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "dispositivo_id", required = false) String deviceId) {
        return facade.syncLog(Tokens.of(authorization), deviceId);
    }
}
