package com.lacocha.backend.service;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.lacocha.backend.config.AiProperties;
import com.lacocha.backend.dto.Assistant.Answer;
import com.lacocha.backend.dto.Assistant.AssistantRequest;
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

    /**
     * The assistant waits longer: on top of waking the service, the language model itself can take
     * half a minute to write a report.
     */
    static final Duration ASSISTANT_READ_TIMEOUT = Duration.ofSeconds(150);

    private final AiProperties properties;
    private final RestClient http;
    private final RestClient slowHttp;

    public AiClient(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.http = builder.clone().requestFactory(factory(READ_TIMEOUT)).build();
        this.slowHttp = builder.clone().requestFactory(factory(ASSISTANT_READ_TIMEOUT)).build();
    }

    private static SimpleClientHttpRequestFactory factory(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /**
     * A question for the farm assistant. When the AI service answers with an error it explains in
     * Spanish (no Anthropic key, no credit, too many questions), and that sentence is passed on as is.
     */
    public Answer ask(AssistantRequest request) {
        requireConfigured();
        try {
            return slowHttp.post()
                    .uri(properties.url().replaceAll("/+$", "") + "/asistente")
                    .header("X-API-Key", properties.apiKey().strip())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Answer.class);
        } catch (RestClientResponseException e) {
            log.warn("AI assistant failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            String detail = detailOf(e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    detail != null ? detail : "El asistente no respondió. Intenta de nuevo en un minuto", e);
        } catch (RestClientException e) {
            log.warn("AI assistant failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El asistente no respondió. Intenta de nuevo en un minuto", e);
        }
    }

    /** FastAPI's {"detail": "..."}, only when it is a plain sentence (validation errors are lists). */
    private static String detailOf(RestClientResponseException e) {
        if (e.getStatusCode().value() == 401) {
            return null;
        }
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            return body != null && body.get("detail") instanceof String text ? text : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de IA no está configurado (faltan IA_URL e IA_API_KEY)");
        }
    }

    public WaterForecast waterForecast(WaterForecastRequest request) {
        requireConfigured();
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
