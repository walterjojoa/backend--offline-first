package com.lacocha.backend;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * The assistant goes through the real HTTP client against a fake AI service, so what is checked is what
 * would travel to Render: the path, the key, the question and the state of the farm the backend gathers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantTest {

    private static final String API_KEY = "clave-prueba";
    private static final String AI_KEY = "clave-ia";

    static final HttpServer fakeAi = start();
    static volatile int status;
    static volatile String answer;
    static volatile String receivedBody;
    static volatile String receivedKey;
    static volatile String receivedPath;

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                // Each push also starts the background analysis, which calls this same fake service a
                // moment later; only the calls to the route under test count
                if (path.equals("/asistente")) {
                    receivedPath = path;
                    receivedKey = exchange.getRequestHeaders().getFirst("X-API-Key");
                    receivedBody = body;
                }
                byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
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
        registry.add("lacocha.ia.url", () -> "http://127.0.0.1:" + fakeAi.getAddress().getPort());
        registry.add("lacocha.ia.api-key", () -> AI_KEY);
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
        answer = "{\"respuesta\": \"Dale 1,2 kg hoy, en 3 tomas.\", \"modelo\": \"claude-opus-5-5\"}";
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

    JsonNode ask(Object body, int expectedStatus) throws Exception {
        return call(post("/api/asistente").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)), expectedStatus);
    }

    /** A pond with a batch and a reading, so the farm state has something in every section. */
    String pondWithBatch() throws Exception {
        String pond = UUID.randomUUID().toString();
        String name = "Asistente " + pond.substring(0, 8);
        call(post("/api/sync/push").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(obj(
                "dispositivo_id", "cel-asistente",
                "estanques", List.of(obj("id", pond, "nombre", name, "tipo", "tanque",
                        "volumen_m3", 20.0, "actualizado_en", ago(Duration.ofDays(30)))),
                "lotes", List.of(obj("id", UUID.randomUUID().toString(), "estanque_id", pond, "codigo",
                        "L-" + pond.substring(0, 6), "cantidad_inicial", 3000, "peso_inicial_g", 20.0,
                        "actualizado_en", ago(Duration.ofDays(30)))),
                "eventos", List.of(obj("tipo", "lectura_agua", "id", UUID.randomUUID().toString(),
                        "estanque_id", pond, "temp_c", 12.5, "ph", 7.2, "oxigeno_mg_l", 8.1,
                        "registrado_en", ago(Duration.ofHours(3))))))), 200);
        return name;
    }

    @Test
    void sendsTheQuestionWithTheFarmStateAndReturnsTheAnswer() throws Exception {
        String pondName = pondWithBatch();

        JsonNode reply = ask(obj("pregunta", "¿Cuánto les doy de comer hoy?",
                "historial", List.of(obj("rol", "usuario", "texto", "hola"),
                        obj("rol", "asistente", "texto", "¡Hola!"))), 200);

        assertThat(reply.get("respuesta").asText()).isEqualTo("Dale 1,2 kg hoy, en 3 tomas.");
        assertThat(reply.get("modelo").asText()).isEqualTo("claude-opus-5-5");
        assertThat(receivedPath).isEqualTo("/asistente");
        assertThat(receivedKey).isEqualTo(AI_KEY);

        JsonNode sent = mapper.readTree(receivedBody);
        assertThat(sent.get("pregunta").asText()).isEqualTo("¿Cuánto les doy de comer hoy?");
        assertThat(sent.get("historial")).hasSize(2);
        assertThat(sent.get("historial").get(1).get("rol").asText()).isEqualTo("asistente");
        JsonNode farm = sent.get("datos_finca");
        assertThat(farm.has("ahora_colombia")).isTrue();
        assertThat(farm.get("parametros_sistema_experto").has("tasas_alimentacion")).isTrue();
        assertThat(farm.get("alertas_pendientes").isArray()).isTrue();
        JsonNode pond = null;
        for (JsonNode p : farm.get("estanques")) {
            if (p.get("nombre").asText().equals(pondName)) {
                pond = p;
            }
        }
        assertThat(pond).as("the new pond goes to the assistant").isNotNull();
        assertThat(pond.get("ultimas_lecturas")).hasSize(1);
        JsonNode batch = pond.get("lotes_activos").get(0);
        // The expert system's ration travels with the batch; the assistant uses it, it does not invent one
        assertThat(batch.has("racion_diaria_kg")).isTrue();
        assertThat(batch.get("racion_diaria_kg").isNumber()).as(batch.toString()).isTrue();
        assertThat(batch.get("tasa_alimentacion_pct").isNumber()).isTrue();
    }

    @Test
    void theAiExplanationIsPassedOnWhenItFails() throws Exception {
        status = 503;
        answer = "{\"detail\": \"El asistente no está configurado (falta ANTHROPIC_API_KEY en el servicio de IA)\"}";

        JsonNode error = ask(obj("pregunta", "hola"), 503);

        assertThat(error.get("detalle").asText()).contains("ANTHROPIC_API_KEY");
    }

    @Test
    void anAiThatDoesNotAnswerIs503InSpanish() throws Exception {
        status = 500;
        answer = "Internal Server Error";

        JsonNode error = ask(obj("pregunta", "hola"), 503);

        assertThat(error.get("detalle").asText()).contains("asistente no respondió");
    }

    @Test
    void anEmptyQuestionOrAStrangeRoleIsRejectedWithoutCallingTheAi() throws Exception {
        ask(obj("pregunta", "  "), 422);
        ask(obj("pregunta", "hola", "historial", List.of(obj("rol", "sistema", "texto", "x"))), 422);
        assertThat(receivedBody).isNull();
    }

    @Test
    void historyIsOptional() throws Exception {
        ask(obj("pregunta", "Hazme un informe"), 200);
        assertThat(mapper.readTree(receivedBody).get("historial")).isEmpty();
    }
}
