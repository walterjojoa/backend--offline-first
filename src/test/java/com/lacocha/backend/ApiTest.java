package com.lacocha.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end API tests against an in-memory H2 database.
 * The JSON stays in Spanish on purpose: it is the contract with the app and the ESP32.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

    private static final String API_KEY = "clave-prueba";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    // ---------- helpers ----------

    static String newId() {
        return UUID.randomUUID().toString();
    }

    static String minutesAgo(long minutes) {
        return Instant.now().minus(Duration.ofMinutes(minutes)).toString();
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    JsonNode call(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        MvcResult res = mvc.perform(req.header("X-API-Key", API_KEY)).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    JsonNode push(Map<String, Object> body) throws Exception {
        Map<String, Object> full = new HashMap<>(body);
        full.put("dispositivo_id", "cel-1");
        return call(post("/api/sync/push").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(full)), 200);
    }

    JsonNode pushEvents(Object... events) throws Exception {
        return push(obj("eventos", List.of(events)));
    }

    /** Creates a pond and a batch of 5000 fry of 2 g. Returns {pond, batch}. */
    String[] createCatalog() throws Exception {
        String pond = newId();
        String batch = newId();
        push(obj(
                "estanques", List.of(obj("id", pond, "nombre", "Tanque 1", "tipo", "tanque", "actualizado_en", minutesAgo(120))),
                "lotes", List.of(obj("id", batch, "estanque_id", pond, "codigo", "L12", "cantidad_inicial", 5000,
                        "peso_inicial_g", 2.0, "actualizado_en", minutesAgo(120)))));
        return new String[] {pond, batch};
    }

    static List<String> texts(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(n.asText()));
        return list;
    }

    // ---------- tests ----------

    @Test
    void healthNeedsNoKey() throws Exception {
        MvcResult res = mvc.perform(get("/salud")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getContentAsString()).contains("\"estado\":\"ok\"");
        assertThat(mapper.readTree(res.getResponse().getContentAsString()).get("version").asText()).isNotBlank();
    }

    @Test
    void wrongKeyGives401() throws Exception {
        MvcResult res = mvc.perform(get("/api/estanques").header("X-API-Key", "otra")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void pushIsIdempotentAndCreatesAlerts() throws Exception {
        String pond = createCatalog()[0];
        Map<String, Object> reading = obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond,
                "temp_c", 19.0, "ph", 7.1, "origen", "sensor", "registrado_en", minutesAgo(5));

        JsonNode first = pushEvents(reading);
        assertThat(texts(first.get("aceptados"))).containsExactly((String) reading.get("id"));
        assertThat(first.get("alertas_generadas").asInt()).isEqualTo(1);

        // The phone retries because the signal dropped before it got the response
        JsonNode second = pushEvents(reading);
        assertThat(texts(second.get("duplicados"))).containsExactly((String) reading.get("id"));
        assertThat(second.get("alertas_generadas").asInt()).isZero();

        JsonNode alerts = call(get("/api/alertas").param("estanque_id", pond), 200);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).get("nivel").asText()).isEqualTo("critica");
        assertThat(alerts.get(0).get("variable").asText()).isEqualTo("temp_c");
        assertThat(alerts.get(0).get("mensaje").asText()).isEqualTo("Temperatura alta: 19 °C (óptimo 10–16 °C)");
    }

    @Test
    void oneBadEventDoesNotBlockTheOthers() throws Exception {
        String[] cat = createCatalog();
        Map<String, Object> good = obj("tipo", "mortalidad", "id", newId(), "lote_id", cat[1], "cantidad", 3, "registrado_en", minutesAgo(1));
        Map<String, Object> impossiblePh = obj("tipo", "lectura_agua", "id", newId(), "estanque_id", cat[0], "ph", 20, "registrado_en", minutesAgo(1));
        Map<String, Object> unknownBatch = obj("tipo", "conteo", "id", newId(), "lote_id", newId(), "total", 10, "registrado_en", minutesAgo(1));
        Map<String, Object> oddType = obj("tipo", "otra_cosa", "id", newId());
        Map<String, Object> noValues = obj("tipo", "lectura_agua", "id", newId(), "estanque_id", cat[0], "registrado_en", minutesAgo(1));

        JsonNode r = pushEvents(good, impossiblePh, unknownBatch, oddType, noValues);
        assertThat(texts(r.get("aceptados"))).containsExactly((String) good.get("id"));

        Map<String, String> rejected = new HashMap<>();
        r.get("rechazados").forEach(x -> rejected.put(x.get("id").asText(), x.get("error").asText()));
        assertThat(rejected).containsOnlyKeys((String) impossiblePh.get("id"), (String) unknownBatch.get("id"),
                (String) oddType.get("id"), (String) noValues.get("id"));
        assertThat(rejected.get(impossiblePh.get("id"))).startsWith("ph:");
        assertThat(rejected.get(unknownBatch.get("id"))).isEqualTo("lote_id: no existe");
        assertThat(rejected.get(noValues.get("id"))).contains("no trae temperatura");
    }

    @Test
    void oneBadPondOrBatchDoesNotBlockThePush() throws Exception {
        String goodPond = newId();
        String noName = newId();
        String textVolume = newId();
        String longCode = newId();
        String reading = newId();
        JsonNode r = push(obj(
                "estanques", List.of(
                        obj("id", goodPond, "nombre", "Tanque B", "actualizado_en", minutesAgo(5)),
                        obj("id", noName, "nombre", "", "actualizado_en", minutesAgo(5)),
                        obj("id", textVolume, "nombre", "Tanque C", "volumen_m3", "mucho", "actualizado_en", minutesAgo(5))),
                "lotes", List.of(obj("id", longCode, "estanque_id", goodPond, "codigo", "L".repeat(41),
                        "actualizado_en", minutesAgo(5))),
                "eventos", List.of(obj("tipo", "lectura_agua", "id", reading, "estanque_id", goodPond, "ph", 7.2,
                        "registrado_en", minutesAgo(1)))));

        assertThat(texts(r.get("aceptados"))).containsExactlyInAnyOrder(goodPond, reading);
        Map<String, String> rejected = new HashMap<>();
        r.get("rechazados").forEach(x -> rejected.put(x.get("id").asText(), x.get("error").asText()));
        assertThat(rejected).containsOnlyKeys(noName, textVolume, longCode);
        assertThat(rejected.get(noName)).startsWith("nombre:");
        assertThat(rejected.get(textVolume)).isEqualTo("volumen_m3: formato no válido");
        assertThat(rejected.get(longCode)).startsWith("codigo:");
    }

    @Test
    void futureDateIsRejected() throws Exception {
        String batch = createCatalog()[1];
        String future = Instant.now().plus(Duration.ofDays(3)).toString();
        JsonNode r = pushEvents(obj("tipo", "mortalidad", "id", newId(), "lote_id", batch, "cantidad", 1, "registrado_en", future));
        assertThat(r.get("rechazados").get(0).get("error").asText()).contains("reloj");
    }

    @Test
    void batchSummaryFigures() throws Exception {
        String[] cat = createCatalog();
        String pond = cat[0];
        String batch = cat[1];
        pushEvents(
                obj("tipo", "mortalidad", "id", newId(), "lote_id", batch, "cantidad", 10, "registrado_en", minutesAgo(90)),
                obj("tipo", "conteo", "id", newId(), "lote_id", batch, "total", 5000, "origen", "contador", "registrado_en", minutesAgo(60)),
                obj("tipo", "mortalidad", "id", newId(), "lote_id", batch, "cantidad", 30, "registrado_en", minutesAgo(30)),
                obj("tipo", "biometria", "id", newId(), "lote_id", batch, "peso_promedio_g", 2.5, "muestra", 50, "registrado_en", minutesAgo(20)),
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 12.0, "ph", 7.2, "registrado_en", minutesAgo(10)),
                obj("tipo", "alimentacion", "id", newId(), "lote_id", batch, "kg", 0.5, "registrado_en", minutesAgo(5)));

        JsonNode r = call(get("/api/lotes/" + batch + "/resumen"), 200);
        assertThat(r.get("poblacion_estimada").asInt()).isEqualTo(4970); // 5000 counted - 30 dead after the count
        assertThat(r.get("mortalidad_total").asInt()).isEqualTo(40);
        assertThat(r.get("supervivencia_pct").asDouble()).isEqualTo(99.4);
        assertThat(r.get("biomasa_kg").asDouble()).isEqualTo(12.425); // 4970 x 2.5 g
        assertThat(r.get("tasa_alimentacion_pct").asDouble()).isEqualTo(4.5);
        assertThat(r.get("racion_diaria_kg").asDouble()).isEqualTo(0.559);
        assertThat(r.get("alimento_ultima_semana_kg").asDouble()).isEqualTo(0.5);
        assertThat(r.get("alimento_total_kg").asDouble()).isEqualTo(0.5);
        assertThat(r.get("conversion_alimenticia").asDouble()).isEqualTo(0.21); // 0.5 kg / (12.425 - 10) kg
        assertThat(r.get("alertas_pendientes").asInt()).isZero();
        assertThat(r.get("notas")).isEmpty();
    }

    @Test
    void latestChangeWins() throws Exception {
        String pond = createCatalog()[0];
        push(obj("estanques", List.of(obj("id", pond, "nombre", "Tanque A", "actualizado_en", minutesAgo(10)))));
        JsonNode old = push(obj("estanques", List.of(obj("id", pond, "nombre", "Nombre viejo", "actualizado_en", minutesAgo(60)))));
        assertThat(texts(old.get("obsoletos"))).containsExactly(pond);

        JsonNode list = call(get("/api/estanques"), 200);
        list.forEach(e -> {
            if (e.get("id").asText().equals(pond)) {
                assertThat(e.get("nombre").asText()).isEqualTo("Tanque A");
            }
        });
    }

    @Test
    void pullOnlyReturnsNewChanges() throws Exception {
        createCatalog();
        JsonNode full = call(get("/api/sync/pull"), 200);
        assertThat(full.get("estanques").size()).isGreaterThanOrEqualTo(1);
        String cursor = full.get("servidor_en").asText();

        JsonNode empty = call(get("/api/sync/pull").param("desde", cursor), 200);
        assertThat(empty.get("estanques")).isEmpty();
        assertThat(empty.get("lotes")).isEmpty();

        call(post("/api/estanques").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Tanque 2\"}"), 201);
        JsonNode latest = call(get("/api/sync/pull").param("desde", cursor), 200);
        assertThat(latest.get("estanques")).hasSize(1);
        assertThat(latest.get("estanques").get(0).get("nombre").asText()).isEqualTo("Tanque 2");
    }

    @Test
    void attendAlert() throws Exception {
        String pond = createCatalog()[0];
        pushEvents(obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "ph", 6.2, "registrado_en", minutesAgo(1)));

        JsonNode alerts = call(get("/api/alertas").param("estanque_id", pond), 200);
        assertThat(alerts.get(0).get("nivel").asText()).isEqualTo("advertencia");
        String id = alerts.get(0).get("id").asText();
        JsonNode attended = call(post("/api/alertas/" + id + "/atender"), 200);
        assertThat(attended.get("atendida").asBoolean()).isTrue();
        String time = attended.get("atendida_en").asText();
        assertThat(time).isNotEmpty();
        assertThat(call(post("/api/alertas/" + id + "/atender"), 200).get("atendida_en").asText()).isEqualTo(time);
        assertThat(call(get("/api/alertas").param("estanque_id", pond), 200)).isEmpty();
    }

    @Test
    void catalogValidationGives422() throws Exception {
        JsonNode r = call(post("/api/estanques").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"tipo\":\"piscina\"}"), 422);
        assertThat(r.get("detalle").asText()).contains("nombre").contains("tipo");
    }

    @Test
    void corsAllowsThePanelAndRejectsOtherOrigins() throws Exception {
        MvcResult allowed = mvc.perform(options("/api/estanques")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "X-API-Key")).andReturn();
        assertThat(allowed.getResponse().getStatus()).isEqualTo(200);
        assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(allowed.getResponse().getHeader("Access-Control-Max-Age")).isEqualTo("3600");

        MvcResult foreign = mvc.perform(options("/api/estanques")
                .header("Origin", "https://otro-sitio.com")
                .header("Access-Control-Request-Method", "GET")).andReturn();
        assertThat(foreign.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void listBatchesFiltersByPondAndStatus() throws Exception {
        String[] cat = createCatalog();
        String closed = newId();
        push(obj("lotes", List.of(obj("id", closed, "estanque_id", cat[0], "codigo", "L01", "estado", "cerrado",
                "actualizado_en", minutesAgo(100)))));

        JsonNode all = call(get("/api/lotes").param("estanque_id", cat[0]), 200);
        assertThat(all).extracting(n -> n.get("codigo").asText()).containsExactly("L01", "L12");

        JsonNode activeOnly = call(get("/api/lotes").param("estanque_id", cat[0]).param("estado", "activo"), 200);
        assertThat(activeOnly).extracting(n -> n.get("id").asText()).containsExactly(cat[1]);

        JsonNode error = call(get("/api/lotes").param("estado", "vendido"), 400);
        assertThat(error.get("detalle").asText()).isEqualTo("estado: debe ser activo o cerrado");
    }

    @Test
    void createWithExistingIdGives409() throws Exception {
        String[] cat = createCatalog();
        JsonNode e = call(post("/api/estanques").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + cat[0] + "\",\"nombre\":\"Otro\"}"), 409);
        assertThat(e.get("detalle").asText()).isEqualTo("El estanque ya existe con ese id");

        call(post("/api/lotes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + cat[1] + "\",\"estanque_id\":\"" + cat[0] + "\",\"codigo\":\"X\"}"), 409);
    }

    @Test
    void unknownRouteAndMethodAnswerInJson() throws Exception {
        JsonNode r = call(get("/api/no-existe"), 404);
        assertThat(r.get("detalle").asText()).contains("no existe");

        JsonNode m = call(delete("/api/estanques"), 405);
        assertThat(m.get("detalle").asText()).isEqualTo("Método DELETE no permitido en esta ruta");
    }

    @Test
    void getPondAndBatchById() throws Exception {
        String[] cat = createCatalog();
        assertThat(call(get("/api/estanques/" + cat[0]), 200).get("nombre").asText()).isEqualTo("Tanque 1");
        assertThat(call(get("/api/lotes/" + cat[1]), 200).get("codigo").asText()).isEqualTo("L12");
        assertThat(call(get("/api/lotes/" + newId()), 404).get("detalle").asText()).isEqualTo("El lote no existe");
    }

    @Test
    void fryCountHistory() throws Exception {
        String batch = createCatalog()[1];
        pushEvents(
                obj("tipo", "conteo", "id", newId(), "lote_id", batch, "total", 5000, "origen", "contador", "registrado_en", minutesAgo(60)),
                obj("tipo", "conteo", "id", newId(), "lote_id", batch, "total", 4950, "origen", "contador", "registrado_en", minutesAgo(10)));

        JsonNode r = call(get("/api/lotes/" + batch + "/conteos"), 200);
        assertThat(r).extracting(n -> n.get("total").asInt()).containsExactly(4950, 5000);
        assertThat(r.get(0).get("dispositivo_id").asText()).isEqualTo("cel-1");
        call(get("/api/lotes/" + newId() + "/conteos"), 404);
    }

    @Test
    void mortalityHistory() throws Exception {
        String batch = createCatalog()[1];
        pushEvents(obj("tipo", "mortalidad", "id", newId(), "lote_id", batch, "cantidad", 4, "causa", "hongos",
                "origen", "voz", "registrado_en", minutesAgo(15)));
        JsonNode r = call(get("/api/lotes/" + batch + "/mortalidades"), 200);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).get("causa").asText()).isEqualTo("hongos");
        assertThat(r.get(0).get("origen").asText()).isEqualTo("voz");
    }

    @Test
    void feedingHistory() throws Exception {
        String batch = createCatalog()[1];
        pushEvents(
                obj("tipo", "alimentacion", "id", newId(), "lote_id", batch, "kg", 0.4, "registrado_en", minutesAgo(300)),
                obj("tipo", "alimentacion", "id", newId(), "lote_id", batch, "kg", 0.6, "registrado_en", minutesAgo(30)));
        JsonNode r = call(get("/api/lotes/" + batch + "/alimentaciones").param("limite", "1"), 200);
        assertThat(r).extracting(n -> n.get("kg").asDouble()).containsExactly(0.6);
    }

    @Test
    void biometryHistory() throws Exception {
        String batch = createCatalog()[1];
        pushEvents(obj("tipo", "biometria", "id", newId(), "lote_id", batch, "peso_promedio_g", 3.1, "muestra", 40,
                "registrado_en", minutesAgo(20)));
        JsonNode r = call(get("/api/lotes/" + batch + "/biometrias"), 200);
        assertThat(r.get(0).get("peso_promedio_g").asDouble()).isEqualTo(3.1);
        assertThat(r.get(0).get("muestra").asInt()).isEqualTo(40);
    }

    @Test
    void summaryComputesCultureDays() throws Exception {
        String pond = createCatalog()[0];
        String batch = newId();
        String stocking = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).minusDays(21).toString();
        push(obj("lotes", List.of(obj("id", batch, "estanque_id", pond, "codigo", "L20", "fecha_siembra", stocking,
                "cantidad_inicial", 1000, "actualizado_en", minutesAgo(5)))));
        assertThat(call(get("/api/lotes/" + batch + "/resumen"), 200).get("dias_cultivo").asInt()).isEqualTo(21);
    }

    @Test
    void summaryComputesDensity() throws Exception {
        String pond = newId();
        String batch = newId();
        push(obj(
                "estanques", List.of(obj("id", pond, "nombre", "Tanque D", "volumen_m3", 2.0, "actualizado_en", minutesAgo(60))),
                "lotes", List.of(obj("id", batch, "estanque_id", pond, "codigo", "LD", "cantidad_inicial", 1000,
                        "peso_inicial_g", 5.0, "actualizado_en", minutesAgo(60)))));
        // 1000 fry x 5 g = 5 kg in 2 m3
        assertThat(call(get("/api/lotes/" + batch + "/resumen"), 200).get("densidad_kg_m3").asDouble()).isEqualTo(2.5);
    }

    @Test
    void repeatedReadingsDoNotDuplicateThePendingAlert() throws Exception {
        String pond = createCatalog()[0];
        JsonNode r = pushEvents(
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 19.0, "registrado_en", minutesAgo(3)),
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 19.5, "registrado_en", minutesAgo(2)),
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 17.0, "registrado_en", minutesAgo(1)));
        assertThat(r.get("aceptados")).hasSize(3);
        // One critical (19 and 19.5 °C) and one warning (17 °C)
        assertThat(r.get("alertas_generadas").asInt()).isEqualTo(2);

        JsonNode alerts = call(get("/api/alertas").param("estanque_id", pond), 200);
        call(post("/api/alertas/" + alerts.get(0).get("id").asText() + "/atender"), 200);
        call(post("/api/alertas/" + alerts.get(1).get("id").asText() + "/atender"), 200);

        // Once attended, a new hot reading alerts again
        JsonNode again = pushEvents(obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 19.0,
                "registrado_en", minutesAgo(0)));
        assertThat(again.get("alertas_generadas").asInt()).isEqualTo(1);
    }

    @Test
    void readingsSummarizedByDay() throws Exception {
        String pond = createCatalog()[0];
        java.time.ZoneId bogota = java.time.ZoneId.of("America/Bogota");
        java.time.LocalDate today = java.time.LocalDate.now(bogota);
        String yesterdayNoon = today.minusDays(1).atTime(12, 0).atZone(bogota).toOffsetDateTime().toString();
        String yesterdayAfternoon = today.minusDays(1).atTime(15, 0).atZone(bogota).toOffsetDateTime().toString();
        pushEvents(
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 12.0, "ph", 7.0, "registrado_en", yesterdayNoon),
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 14.0, "registrado_en", yesterdayAfternoon),
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 13.0, "ph", 7.4, "registrado_en", minutesAgo(0)));

        JsonNode r = call(get("/api/estanques/" + pond + "/lecturas/diario").param("dias", "2"), 200);
        assertThat(r).hasSize(2);
        JsonNode yesterday = r.get(0);
        assertThat(yesterday.get("fecha").asText()).isEqualTo(today.minusDays(1).toString());
        assertThat(yesterday.get("lecturas").asInt()).isEqualTo(2);
        assertThat(yesterday.get("temp_min").asDouble()).isEqualTo(12.0);
        assertThat(yesterday.get("temp_max").asDouble()).isEqualTo(14.0);
        assertThat(yesterday.get("temp_promedio").asDouble()).isEqualTo(13.0);
        assertThat(yesterday.get("ph_promedio").asDouble()).isEqualTo(7.0); // the reading without pH does not count
        assertThat(r.get(1).get("fecha").asText()).isEqualTo(today.toString());
    }

    @Test
    void exportReadingsAsCsv() throws Exception {
        String pond = createCatalog()[0];
        String t1 = Instant.now().minus(Duration.ofMinutes(20)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        String t2 = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        pushEvents(
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 12.5, "ph", 7.1, "origen", "sensor", "registrado_en", t1),
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond, "temp_c", 13.0, "registrado_en", t2));

        MvcResult res = mvc.perform(get("/api/estanques/" + pond + "/lecturas.csv").header("X-API-Key", API_KEY)).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getContentType()).startsWith("text/csv");
        assertThat(res.getResponse().getContentAsString()).isEqualTo(
                "registrado_en,temp_c,ph,oxigeno_mg_l,origen\n"
                        + t1 + ",12.5,7.1,,sensor\n"
                        + t2 + ",13.0,,,manual\n");
    }

    @Test
    void futureStockingDateIsRejected() throws Exception {
        String pond = createCatalog()[0];
        String inFiveDays = java.time.LocalDate.now().plusDays(5).toString();
        JsonNode r = call(post("/api/lotes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estanque_id\":\"" + pond + "\",\"codigo\":\"LF\",\"fecha_siembra\":\"" + inFiveDays + "\"}"), 422);
        assertThat(r.get("detalle").asText()).startsWith("fecha_siembra:");
    }

    @Test
    void deviceIdWithOddCharactersIsRejected() throws Exception {
        JsonNode r = call(post("/api/sync/push").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dispositivo_id\":\"cel 1 <script>\",\"eventos\":[]}"), 422);
        assertThat(r.get("detalle").asText()).startsWith("dispositivo_id:");
    }

    @Test
    void missingKeyGives401AndDocsStayOpen() throws Exception {
        MvcResult noKey = mvc.perform(get("/api/alertas")).andReturn();
        assertThat(noKey.getResponse().getStatus()).isEqualTo(401);
        assertThat(noKey.getResponse().getContentAsString()).contains("X-API-Key");

        assertThat(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
