package com.lacocha.backend.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Render health check: needs no key and confirms the database answers. */
@RestController
@Tag(name = "Sistema")
public class HealthController {

    private final JdbcTemplate jdbc;
    private final ObjectProvider<BuildProperties> build;

    public HealthController(JdbcTemplate jdbc, ObjectProvider<BuildProperties> build) {
        this.jdbc = jdbc;
        this.build = build;
    }

    @GetMapping("/salud")
    @Operation(summary = "Chequeo de vida para Render: no pide clave")
    public Map<String, String> health() {
        jdbc.queryForObject("select 1", Integer.class);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("estado", "ok");
        // Confirms which version ended up deployed on Render
        BuildProperties info = build.getIfAvailable();
        if (info != null) {
            response.put("version", info.getVersion());
        }
        return response;
    }
}
