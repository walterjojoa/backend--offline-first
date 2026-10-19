package com.lacocha.backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under "lacocha.*" in application.properties (filled from environment variables on Render). */
@ConfigurationProperties(prefix = "lacocha")
public record LaCochaProperties(String databaseUrl, String apiKey, String allowedOrigins,
        String producerDocuments) {

    /**
     * Documentos que siempre se registran como productor (PRODUCER_DOCUMENTS, separados por coma).
     *
     * Sin esto el único productor sería quien se registre primero, y si ese orden sale mal la finca
     * se queda sin nadie que pueda administrar y sin forma de arreglarlo: un cuidador no puede
     * promover a otro. Con esto, quien instala el servidor deja escrito quién manda.
     */
    public List<String> producers() {
        if (producerDocuments == null) {
            return List.of();
        }
        return Arrays.stream(producerDocuments.split(","))
                .map(String::trim)
                .filter(d -> !d.isEmpty())
                .toList();
    }

    /** ALLOWED_ORIGINS split by comma. */
    public List<String> origins() {
        if (allowedOrigins == null) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
    }
}
