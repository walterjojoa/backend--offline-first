package com.lacocha.backend.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.lacocha.backend.dto.Ai;

/**
 * The AI service over HTTP.
 *
 * It is the only class in the backend that knows where that service is or how it is reached.
 * Everything that can go wrong ends up as an empty Optional and a line in the log, never as an
 * exception travelling upwards: a sync must not fail because an analysis could not be done.
 */
public class RestAnalyzer implements Analyzer {

    private static final Logger log = LoggerFactory.getLogger(RestAnalyzer.class);

    /**
     * Render's free plan puts a service to sleep after a while unused, and waking it takes close to
     * a minute. That is why the read timeout is this generous, and also why nothing calls this from
     * inside a sync: the call is made outside, and the answer is published as an event when it
     * arrives (see {@link AiTrigger}).
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    /** Two routes answer a bare JSON array, which needs the element type spelled out. */
    private static final ParameterizedTypeReference<List<Ai.AnomalyJson>> ANOMALIES =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Ai.TrendJson>> TRENDS =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient client;

    public RestAnalyzer(String baseUrl, String apiKey) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeouts())
                // The service calls its own variable IA_API_KEY and reads it from the same header
                // the rest of the project already uses
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
                .filter(health -> "ok".equals(health.state()))
                .isPresent();
    }

    @Override
    public Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request) {
        return ask("anomalias", () -> client.post().uri("/anomalias")
                .body(request).retrieve().body(ANOMALIES));
    }

    @Override
    public Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request) {
        return ask("tendencias", () -> client.post().uri("/tendencias")
                .body(request).retrieve().body(TRENDS));
    }

    @Override
    public Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request) {
        // This route answers a bare null when there is nothing to report, so an absent body is a
        // perfectly good answer and must not be confused with a call that never got through. That
        // is why it does not go through ask(), which cannot tell those two apart.
        try {
            Ai.CombinationJson body = client.post().uri("/combinaciones")
                    .body(request).retrieve().body(Ai.CombinationJson.class);
            return Optional.of(Optional.ofNullable(body));
        } catch (RuntimeException ex) {
            log.warn("el servicio de IA no respondió en combinaciones: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request) {
        return ask("proyeccion-lote", () -> client.post().uri("/proyeccion-lote")
                .body(request).retrieve().body(Ai.ProjectionJson.class));
    }


    /**
     * Runs the call and turns anything that goes wrong into an empty Optional.
     *
     * RuntimeException is caught on purpose and not a narrower list. What can come out of here is a
     * connection refused, a timeout, a 401 for a mismatched key, the 503 the service answers when
     * Open-Meteo is unreachable, a 500, an unparseable body or a DNS failure, and the answer to
     * every one of them is the same: there is no analysis this time, and the farm carries on with
     * its expert system. Letting one through to find out which it was would mean a lost sync, which
     * is the one thing that cannot happen.
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
