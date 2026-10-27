package com.lacocha.backend.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.lacocha.backend.config.AiProperties;
import com.lacocha.backend.dto.Forecasts.WaterForecast;
import com.lacocha.backend.dto.Forecasts.WaterForecastRequest;

/**
 * Calls the AI service. It is the only client that service has (professor's diagram): the app never
 * talks to it, so its key never reaches a phone.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    /**
     * On Render's free plan the service sleeps when idle and takes about a minute to wake up. A shorter
     * timeout would fail exactly the first request of the day, which is the one the caretaker makes.
     */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(90);

    private final AiProperties properties;
    private final RestClient http;

    public AiClient(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(READ_TIMEOUT);
        this.http = builder.requestFactory(factory).build();
    }

    public WaterForecast waterForecast(WaterForecastRequest request) {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de IA no está configurado (faltan IA_URL e IA_API_KEY)");
        }
        try {
            return http.post()
                    .uri(properties.url().replaceAll("/+$", "") + "/pronostico-temperatura")
                    .header("X-API-Key", properties.apiKey().strip())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(WaterForecast.class);
        } catch (RestClientException e) {
            // Includes the AI answering 401 (keys differ) or 503 (Open-Meteo down): the log says which
            log.warn("AI service failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de IA no respondió. Intenta de nuevo en un minuto", e);
        }
    }
}
