package com.lacocha.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the AI service lives (repo MODELO-IA-offline-first) and the key it expects in X-API-Key.
 * Both empty means "no AI": the endpoints that need it answer 503 and everything else keeps working.
 */
@ConfigurationProperties(prefix = "lacocha.ia")
public record AiProperties(String url, String apiKey) {

    public boolean configured() {
        return url != null && !url.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
