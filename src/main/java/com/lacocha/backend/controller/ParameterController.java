package com.lacocha.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Parameters.ParametersResponse;
import com.lacocha.backend.service.PisciculturaFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Parámetros")
public class ParameterController {

    private final PisciculturaFacade facade;

    public ParameterController(PisciculturaFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/parametros")
    @Operation(summary = "Umbrales de calidad de agua y curva de alimentación, con su fuente")
    public ParametersResponse parameters() {
        return facade.parameters();
    }
}
