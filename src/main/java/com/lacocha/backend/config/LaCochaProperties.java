package com.lacocha.backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under "lacocha.*" in application.properties (filled from environment variables on Render). */
@ConfigurationProperties(prefix = "lacocha")
public record LaCochaProperties(String databaseUrl, String apiKey, String allowedOrigins) {

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
