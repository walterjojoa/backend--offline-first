package com.lacocha.backend.controlador;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

/** Chequeo de Render: no pide clave y confirma que la base de datos responde. */
@RestController
@Tag(name = "Sistema")
public class SaludController {

    private final JdbcTemplate jdbc;
    private final ObjectProvider<BuildProperties> build;

    public SaludController(JdbcTemplate jdbc, ObjectProvider<BuildProperties> build) {
        this.jdbc = jdbc;
        this.build = build;
    }

    @GetMapping("/salud")
    public Map<String, String> salud() {
        jdbc.queryForObject("select 1", Integer.class);
        Map<String, String> respuesta = new LinkedHashMap<>();
        respuesta.put("estado", "ok");
        // Sirve para confirmar qué versión quedó desplegada en Render
        BuildProperties info = build.getIfAvailable();
        if (info != null) {
            respuesta.put("version", info.getVersion());
        }
        return respuesta;
    }
}
