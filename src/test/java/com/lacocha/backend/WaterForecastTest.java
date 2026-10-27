package com.lacocha.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacocha.backend.config.AiProperties;
import com.lacocha.backend.dto.Forecasts.WaterForecastRequest;
import com.lacocha.backend.service.AiClient;
import com.sun.net.httpserver.HttpServer;

/**
 * The forecast goes through the real HTTP client against a fake AI service on a local port, so what is
 * checked is what would travel to Render: the path, the key and the Spanish JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WaterForecastTest {

    private static final String API_KEY = "clave-prueba";
    private static final String AI_KEY = "clave-ia";
    private static final String AI_ANSWER = """
            {"dias": [{"fecha": "2026-10-27", "minima": 11.5, "maxima": 12.4, "promedio": 12.0}],
             "error_tipico": 0.2, "reaccion_horas": 36, "lecturas_usadas": 2, "mensaje": "ok"}""";

    static final HttpServer fakeAi = start();
    static volatile int status;
    static volatile String receivedBody;
    static volatile String receivedKey;
    static volatile String receivedPath;

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                receivedPath = exchange.getRequestURI().getPath();
                receivedKey = exchange.getRequestHeaders().getFirst("X-API-Key");
                receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] answer = (status == 200 ? AI_ANSWER : "{\"detail\":\"falla\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, answer.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(answer);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        // Trailing slash on purpose: whoever pastes the URL in Render may copy it with one
        registry.add("lacocha.ia.url", () -> "http://127.0.0.1:" + fakeAi.getAddress().getPort() + "/");
        registry.add("lacocha.ia.api-key", () -> " " + AI_KEY);
    }

    @AfterAll
    static void stop() {
        fakeAi.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @BeforeEach
    void reset() {
        status = 200;
        receivedBody = null;
        receivedKey = null;
        receivedPath = null;
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    static String ago(Duration d) {
        return Instant.now().minus(d).toString();
    }

    JsonNode call(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        MvcResult res = mvc.perform(req.header("X-API-Key", API_KEY)).andReturn();
        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(res.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    String pondWithReadings(Object... readings) throws Exception {
        String pond = UUID.randomUUID().toString();
        List<Object> events = new java.util.ArrayList<>();
        for (Object r : readings) {
            @SuppressWarnings("unchecked")
            Map<String, Object> reading = new HashMap<>((Map<String, Object>) r);
            reading.put("tipo", "lectura_agua");
            reading.put("id", UUID.randomUUID().toString());
            reading.put("estanque_id", pond);
            events.add(reading);
        }
        call(post("/api/sync/push").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(obj(
                "dispositivo_id", "cel-pronostico",
                "estanques", List.of(obj("id", pond, "nombre", "Pronóstico " + pond.substring(0, 8), "tipo", "tanque",
                        "actualizado_en", ago(Duration.ofDays(200)))),
                "eventos", events))), 200);
        return pond;
    }

    @Test
    void sendsThePondTemperaturesWithTheKeyAndReturnsTheForecast() throws Exception {
        String pond = pondWithReadings(
                obj("temp_c", 12.1, "ph", 7.0, "registrado_en", ago(Duration.ofHours(30))),
                obj("ph", 7.1, "registrado_en", ago(Duration.ofHours(20))),
                obj("temp_c", 12.4, "registrado_en", ago(Duration.ofHours(10))),
                // Older than the 92 days of air Open-Meteo gives back: useless to the model
                obj("temp_c", 9.0, "registrado_en", ago(Duration.ofDays(120))));

        JsonNode forecast = call(get("/api/estanques/" + pond + "/pronostico-temperatura").param("dias", "3"), 200);

        assertThat(forecast.get("dias").get(0).get("maxima").asDouble()).isEqualTo(12.4);
        assertThat(forecast.get("reaccion_horas").asDouble()).isEqualTo(36);
        assertThat(receivedPath).isEqualTo("/pronostico-temperatura");
        assertThat(receivedKey).isEqualTo(AI_KEY);
        JsonNode sent = mapper.readTree(receivedBody);
        assertThat(sent.get("dias").asInt()).isEqualTo(3);
        // Only readings with temperature, and the pH-only one left out
        assertThat(sent.get("lecturas")).hasSize(2);
        assertThat(sent.get("lecturas").get(0).has("registrado_en")).isTrue();
        assertThat(sent.get("lecturas").findValuesAsText("temp_c")).containsExactlyInAnyOrder("12.1", "12.4");
    }

    @Test
    void pondWithoutTemperaturesAnswersWithoutCallingTheAi() throws Exception {
        String pond = pondWithReadings(obj("ph", 7.0, "registrado_en", ago(Duration.ofHours(5))));

        JsonNode forecast = call(get("/api/estanques/" + pond + "/pronostico-temperatura"), 200);

        assertThat(forecast.get("dias")).isEmpty();
        assertThat(forecast.get("mensaje").asText()).startsWith("Faltan lecturas");
        assertThat(receivedBody).isNull();
    }

    @Test
    void aiFailureIs503WithSpanishMessage() throws Exception {
        status = 500;
        String pond = pondWithReadings(obj("temp_c", 12.0, "registrado_en", ago(Duration.ofHours(5))));

        JsonNode error = call(get("/api/estanques/" + pond + "/pronostico-temperatura"), 503);

        assertThat(error.get("detalle").asText()).contains("servicio de IA");
    }

    @Test
    void daysAreKeptBetweenOneAndSeven() throws Exception {
        String pond = pondWithReadings(obj("temp_c", 12.0, "registrado_en", ago(Duration.ofHours(5))));

        call(get("/api/estanques/" + pond + "/pronostico-temperatura").param("dias", "30"), 200);

        assertThat(mapper.readTree(receivedBody).get("dias").asInt()).isEqualTo(7);
    }

    @Test
    void unknownPondIs404() throws Exception {
        call(get("/api/estanques/" + UUID.randomUUID() + "/pronostico-temperatura"), 404);
        assertThat(receivedBody).isNull();
    }

    @Test
    void withoutConfigurationTheClientSays503() {
        AiClient unconfigured = new AiClient(new AiProperties("", ""), RestClient.builder());
        assertThatThrownBy(() -> unconfigured.waterForecast(new WaterForecastRequest(List.of(), 2)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no está configurado");
    }
}
