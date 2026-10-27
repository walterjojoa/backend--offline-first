package com.lacocha.backend.service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.lacocha.backend.dto.Ai;

/**
 * The AI service over HTTP.
 *
 * It is the only class in the backend that knows the Python service exists. Everything it can go
 * wrong with ends up as an empty Optional and a line in the log, never as an exception travelling
 * upwards: a sync must not fail because an analysis could not be done.
 */
public class RestAnalyzer implements Analyzer {

    private static final Logger log = LoggerFactory.getLogger(RestAnalyzer.class);

    /**
     * Render's free plan puts a service to sleep after a while unused, and waking it takes close to
     * a minute. That is why the read timeout is this generous, and also why nothing calls this from
     * inside a sync: the call is made outside, and the answer is published as an event when it
     * arrives (see AiAnalysis).
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient client;

    public RestAnalyzer(String baseUrl, String apiKey) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeouts())
                .defaultHeader("X-API-Key", apiKey != null ? apiKey : "")
                .build();
    }

    private static ClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public boolean available() {
        return ask("salud", () -> client.get().uri("/salud").retrieve().body(Ai.Health.class))
                .filter(health -> "vivo".equals(health.state()))
                .isPresent();
    }

    @Override
    public Optional<Ai.WaterAnalysis> analyzeWater(Ai.WaterRequest request) {
        return ask("analizar/agua", () -> client.post()
                .uri("/analizar/agua")
                .body(request)
                .retrieve()
                .body(Ai.WaterAnalysis.class));
    }

    @Override
    public Optional<Ai.BatchProjection> projectBatch(Ai.BatchRequest request) {
        return ask("proyectar/lote", () -> client.post()
                .uri("/proyectar/lote")
                .body(request)
                .retrieve()
                .body(Ai.BatchProjection.class));
    }

    /**
     * Runs the call and turns anything that goes wrong into an empty Optional.
     *
     * RuntimeException is caught on purpose and not a narrower list. What can come out of here is
     * a connection refused, a timeout, a 401 for a mismatched key, a 500, an unparseable body or a
     * DNS failure, and the answer to every one of them is the same: there is no analysis this time,
     * and the farm carries on with its expert system. Letting one of them through to find out which
     * it was would mean a lost sync, which is the one thing that cannot happen.
     */
    private <T> Optional<T> ask(String route, Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (RuntimeException ex) {
            log.warn("el servicio de IA no respondió en {}: {}", route, ex.getMessage());
            return Optional.empty();
        }
    }
}
