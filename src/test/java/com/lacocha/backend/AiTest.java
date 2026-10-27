package com.lacocha.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacocha.backend.dto.Ai;
import com.lacocha.backend.service.AiAnalysis;
import com.lacocha.backend.service.Analyzer;

/**
 * The AI side, end to end, with a fake service in place of the real one.
 *
 * Faking it is not a shortcut: it is what the Bridge exists for. What is being checked here belongs
 * entirely to this backend — that a repeat is not stored twice, that a finding that got worse is
 * stored again, that an unreachable service does not break a sync, that one route failing does not
 * cost the others their answer — and none of it depends on the service's arithmetic. That is tested
 * in the service's own repository, where it lives.
 *
 * The automatic analysis is switched off here, and the reason is worth writing down because the
 * first version of this class did not and three tests failed for it. With it on, every push made by
 * a test to set up its pond fires an analysis of its own, on another thread, against whatever the
 * fake happened to be returning at that instant. One of those stores the finding first, the
 * deduplication quite correctly drops the one the test then asks for, and the test fails over a
 * feature working exactly as intended. Here the analyses are asked for one at a time; the trigger is
 * checked separately, by calling it.
 */
@SpringBootTest(properties = "lacocha.ia-analisis-automatico=false")
@AutoConfigureMockMvc
@Import(AiTest.FakeService.class)
class AiTest {

    private static final String API_KEY = "clave-prueba";

    /**
     * The AI service, under this test's control.
     *
     * Marked primary so it beats the one AiConfig builds. Every answer is set by the test, which is
     * the only way to check what the backend does with a service that cannot be reached, that
     * answers nothing, or that repeats itself.
     */
    @TestConfiguration
    static class FakeService {

        @Bean
        @Primary
        Fake fakeAnalyzer() {
            return new Fake();
        }
    }

    static final class Fake implements Analyzer {

        boolean up = true;
        /** Which routes are broken while the rest work: the real failure mode of three calls. */
        boolean anomaliesUp = true;
        boolean trendsUp = true;
        boolean combinationUp = true;

        List<Ai.AnomalyJson> anomalies = List.of();
        List<Ai.TrendJson> trends = List.of();
        Ai.CombinationJson combination;
        List<Ai.BatchFindingJson> batchFindings = List.of();
        List<String> notes = List.of();

        Ai.ReadingsRequest lastReadings;
        Ai.TrendRequest lastTrendRequest;
        Ai.BatchRequest lastBatchRequest;
        int calls;

        void reset() {
            up = anomaliesUp = trendsUp = combinationUp = true;
            anomalies = List.of();
            trends = List.of();
            combination = null;
            batchFindings = List.of();
            notes = List.of();
            lastReadings = null;
            lastTrendRequest = null;
            lastBatchRequest = null;
            calls = 0;
        }

        @Override
        public boolean available() {
            return up;
        }

        @Override
        public Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request) {
            calls++;
            lastReadings = request;
            return up && anomaliesUp ? Optional.of(anomalies) : Optional.empty();
        }

        @Override
        public Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request) {
            calls++;
            lastTrendRequest = request;
            return up && trendsUp ? Optional.of(trends) : Optional.empty();
        }

        @Override
        public Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request) {
            calls++;
            return up && combinationUp ? Optional.of(Optional.ofNullable(combination))
                    : Optional.empty();
        }

        @Override
        public Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request) {
            calls++;
            lastBatchRequest = request;
            if (!up) {
                return Optional.empty();
            }
            return Optional.of(new Ai.ProjectionJson(30, 1.78, true, 12.4, 28.0, 50.6, 148,
                    java.time.LocalDate.of(2027, 3, 24), 9300, 9270, 93.0, 0.5,
                    batchFindings, notes));
        }

    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    Fake fake;

    @Autowired
    AiAnalysis analysis;

    @BeforeEach
    void limpio() {
        fake.reset();
    }

    // ---------- helpers ----------

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    static String minutesAgo(long minutes) {
        return Instant.now().minus(Duration.ofMinutes(minutes)).toString();
    }

    JsonNode call(MockHttpServletRequestBuilder req, int expected) throws Exception {
        MvcResult res = mvc.perform(req.header("X-API-Key", API_KEY)).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(body).isEqualTo(expected);
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    JsonNode push(Map<String, Object> body) throws Exception {
        Map<String, Object> full = new HashMap<>(body);
        full.putIfAbsent("dispositivo_id", "cel-ia");
        return call(post("/api/sync/push").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(full)), 200);
    }

    /** A pond with a batch, and readings for the window the analysis looks at. */
    String[] pondWithReadings(int readings) throws Exception {
        String pond = newId();
        String batch = newId();
        push(obj(
                "estanques", List.of(obj("id", pond, "nombre", "Tanque " + pond.substring(0, 8),
                        "tipo", "tanque", "actualizado_en", minutesAgo(600))),
                "lotes", List.of(obj("id", batch, "estanque_id", pond, "codigo", "L-IA",
                        "cantidad_inicial", 5000, "peso_inicial_g", 2.0,
                        "actualizado_en", minutesAgo(600)))));

        List<Object> events = new ArrayList<>();
        for (int i = 0; i < readings; i++) {
            events.add(obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond,
                    "registrado_en", minutesAgo(500 - i * 10L), "origen", "sensor",
                    "temp_c", 12.0 + (i % 3) * 0.1, "ph", 7.1, "oxigeno_mg_l", 8.6));
        }
        push(obj("eventos", events));
        return new String[] {pond, batch};
    }

    static Ai.AnomalyJson anomaly(String variable, double score) {
        return new Ai.AnomalyJson(variable, 15.0, 11.0, score,
                "Temperatura subió de forma inusual: 15 °C (lo normal en este estanque ahora: 11 °C)");
    }

    static Ai.TrendJson trend(String variable, boolean urgent, Double daysToLimit) {
        return new Ai.TrendJson(variable, -0.4, 0.94, 6.0, daysToLimit, 5.0, urgent,
                "Oxígeno viene bajando 0.4 mg/L por día (6 días de datos).");
    }

    // ---------- what gets sent ----------

    @Test
    void seLeManadanAlServicioLasLecturasYLosUmbrales() throws Exception {
        String pond = pondWithReadings(20)[0];
        analysis.analyzePond(UUID.fromString(pond));

        assertThat(fake.lastReadings).isNotNull();
        assertThat(fake.lastReadings.readings()).hasSize(20);
        // Oldest first: the whole service reasons about time, and the last one is "the current one"
        assertThat(fake.lastReadings.readings().get(0).recordedAt())
                .isBefore(fake.lastReadings.readings().get(19).recordedAt());

        // And the thresholds, which are what turn a slope into a date
        assertThat(fake.lastTrendRequest).isNotNull();
        assertThat(fake.lastTrendRequest.ranges())
                .anySatisfy(r -> assertThat(r.variable()).isEqualTo("oxigeno_mg_l"));
    }

    @Test
    void unEstanqueSonTresLlamadas() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.calls = 0;
        analysis.analyzePond(UUID.fromString(pond));
        // Las tres rutas del agua: anomalias, tendencias y combinaciones
        assertThat(fake.calls).isEqualTo(3);
    }

    @Test
    void conUnaSolaLecturaNiSePreguntaAlServicio() throws Exception {
        String pond = pondWithReadings(1)[0];
        fake.calls = 0;
        analysis.analyzePond(UUID.fromString(pond));
        assertThat(fake.calls).as("no valen tres llamadas para que digan que no saben").isZero();
    }

    // ---------- one route failing must not cost the others ----------

    @Test
    void siUnaRutaFallaLasOtrasSiguenContando() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.combinationUp = false;
        fake.anomalies = List.of(anomaly("temp_c", 7.4));
        fake.trends = List.of(trend("oxigeno_mg_l", false, 4.0));

        List<?> stored = analysis.analyzePond(UUID.fromString(pond));
        // Perder la tendencia porque la combinacion se cayo seria lo peor de los dos mundos
        assertThat(stored).hasSize(2);
        JsonNode found = call(get("/api/estanques/" + pond + "/hallazgos"), 200);
        assertThat(found).hasSize(2);
    }

    @Test
    void siElServicioEnteroNoRespondeNoSeGuardaNadaYNoFallaNada() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.up = false;
        assertThat(analysis.analyzePond(UUID.fromString(pond))).isEmpty();

        // Y lo que importa de verdad: la sincronización sigue andando
        JsonNode result = push(obj("eventos", List.of(
                obj("tipo", "lectura_agua", "id", newId(), "estanque_id", pond,
                        "registrado_en", minutesAgo(1), "origen", "sensor", "temp_c", 12.3))));
        assertThat(result.get("aceptados")).hasSize(1);
    }

    @Test
    void unaRespuestaVaciaNoGuardaNada() throws Exception {
        // El servicio contestó las tres rutas y no encontró nada: no es lo mismo que no responder,
        // y en los dos casos no hay nada que guardar, pero solo uno queda en el log como fallo
        String pond = pondWithReadings(20)[0];
        assertThat(analysis.analyzePond(UUID.fromString(pond))).isEmpty();
        assertThat(call(get("/api/estanques/" + pond + "/hallazgos"), 200)).isEmpty();
    }

    // ---------- the level this backend decides ----------

    @Test
    void unaAnomaliaSeGuardaComoAdvertenciaYNoComoCritica() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.anomalies = List.of(anomaly("temp_c", 32.0));  // un puntaje enorme

        analysis.analyzePond(UUID.fromString(pond));
        JsonNode found = call(get("/api/estanques/" + pond + "/hallazgos"), 200).get(0);
        assertThat(found.get("clase").asText()).isEqualTo("anomalia");
        assertThat(found.get("nivel").asText()).isEqualTo("advertencia");
    }

    @Test
    void unaTendenciaUrgenteSeGuardaComoCritica() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.trends = List.of(trend("oxigeno_mg_l", true, 1.2));

        analysis.analyzePond(UUID.fromString(pond));
        JsonNode found = call(get("/api/estanques/" + pond + "/hallazgos"), 200).get(0);
        assertThat(found.get("nivel").asText()).isEqualTo("critica");
    }

    @Test
    void unaTendenciaSinLimiteAlQueLlegarSeQuedaEnInforme() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.trends = List.of(trend("oxigeno_mg_l", false, null));

        analysis.analyzePond(UUID.fromString(pond));
        JsonNode found = call(get("/api/estanques/" + pond + "/hallazgos"), 200).get(0);
        assertThat(found.get("nivel").asText()).isEqualTo("informe");
    }

    // ---------- not repeating the same notice ----------

    @Test
    void elMismoHallazgoNoSeGuardaDosVeces() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.trends = List.of(trend("oxigeno_mg_l", false, 4.0));

        assertThat(analysis.analyzePond(UUID.fromString(pond))).hasSize(1);
        // El sensor mide cada pocos segundos: la misma tendencia sigue siendo la misma tendencia
        assertThat(analysis.analyzePond(UUID.fromString(pond))).isEmpty();
        assertThat(analysis.analyzePond(UUID.fromString(pond))).isEmpty();

        assertThat(call(get("/api/estanques/" + pond + "/hallazgos"), 200)).hasSize(1);
    }

    @Test
    void unHallazgoQueEmpeoroSiSeVuelveAGuardar() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.trends = List.of(trend("oxigeno_mg_l", false, 5.0));
        assertThat(analysis.analyzePond(UUID.fromString(pond))).hasSize(1);

        // "llega al límite en cinco días" pasó a "llega mañana": lo que hay que hacer cambió
        fake.trends = List.of(trend("oxigeno_mg_l", true, 1.1));
        assertThat(analysis.analyzePond(UUID.fromString(pond))).hasSize(1);
        assertThat(call(get("/api/estanques/" + pond + "/hallazgos"), 200)).hasSize(2);

        // Y no al revés: bajar de crítica a advertencia no vuelve a avisar
        fake.trends = List.of(trend("oxigeno_mg_l", false, 5.0));
        assertThat(analysis.analyzePond(UUID.fromString(pond))).isEmpty();
    }

    @Test
    void dosVariablesDistintasSonDosHallazgos() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.anomalies = List.of(anomaly("temp_c", 4.1), anomaly("oxigeno_mg_l", 5.2));
        assertThat(analysis.analyzePond(UUID.fromString(pond))).hasSize(2);
    }

    @Test
    void unaAnomaliaYUnaTendenciaDeLaMismaVariableSonDosHallazgos() throws Exception {
        // Responden preguntas distintas: un salto y una deriva. Que coincidan en la variable no
        // las vuelve la misma cosa.
        String pond = pondWithReadings(20)[0];
        fake.anomalies = List.of(anomaly("oxigeno_mg_l", 4.1));
        fake.trends = List.of(trend("oxigeno_mg_l", false, 4.0));
        assertThat(analysis.analyzePond(UUID.fromString(pond))).hasSize(2);
    }

    @Test
    void atenderUnHallazgoPermiteQueVuelvaAAvisar() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.trends = List.of(trend("oxigeno_mg_l", false, 4.0));
        analysis.analyzePond(UUID.fromString(pond));
        assertThat(analysis.analyzePond(UUID.fromString(pond))).isEmpty();

        String id = call(get("/api/estanques/" + pond + "/hallazgos"), 200).get(0).get("id").asText();
        JsonNode attended = call(post("/api/hallazgos/" + id + "/atender"), 200);
        assertThat(attended.get("atendido").asBoolean()).isTrue();
        assertThat(attended.get("atendido_por").asText()).isEqualTo("panel");
        assertThat(attended.get("atendido_en").isNull()).isFalse();

        // Ya atendido, el problema que vuelve no se queda escondido detrás del aviso viejo
        assertThat(analysis.analyzePond(UUID.fromString(pond))).hasSize(1);
    }

    @Test
    void atenderDosVecesElMismoHallazgoEsConflicto() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.anomalies = List.of(anomaly("temp_c", 4.0));
        analysis.analyzePond(UUID.fromString(pond));

        String id = call(get("/api/estanques/" + pond + "/hallazgos"), 200).get(0).get("id").asText();
        call(post("/api/hallazgos/" + id + "/atender"), 200);
        call(post("/api/hallazgos/" + id + "/atender"), 409);
    }

    @Test
    void elMensajeSeGuardaTalCualLlego() throws Exception {
        String pond = pondWithReadings(20)[0];
        String mensaje = "Oxígeno bajó de forma inusual: 6.5 mg/L "
                + "(lo normal en este estanque ahora: 9.01 mg/L)";
        fake.anomalies = List.of(new Ai.AnomalyJson("oxigeno_mg_l", 6.5, 9.01, -12.9, mensaje));
        analysis.analyzePond(UUID.fromString(pond));

        JsonNode stored = call(get("/api/estanques/" + pond + "/hallazgos"), 200).get(0);
        // Palabra por palabra: si mañana se ajusta un umbral, el aviso de hoy tiene que seguir
        // diciendo lo que dijo, que es lo que permite auditar por qué se decidió algo
        assertThat(stored.get("mensaje").asText()).isEqualTo(mensaje);
        // Y el puntaje en valor absoluto: ordena por rareza, no por direccion
        assertThat(stored.get("puntaje").asDouble()).isEqualTo(12.9);
    }

    // ---------- batches ----------

    @Test
    void laProyeccionLlevaBiometriasMortalidadesYLaTemperaturaDelEstanque() throws Exception {
        String[] ids = pondWithReadings(20);
        String batch = ids[1];
        push(obj("eventos", List.of(
                obj("tipo", "biometria", "id", newId(), "lote_id", batch,
                        "registrado_en", minutesAgo(400), "origen", "manual",
                        "peso_promedio_g", 5.0, "muestra", 30),
                obj("tipo", "mortalidad", "id", newId(), "lote_id", batch,
                        "registrado_en", minutesAgo(300), "origen", "manual", "cantidad", 120))));

        analysis.analyzeBatch(UUID.fromString(batch));

        assertThat(fake.lastBatchRequest).isNotNull();
        assertThat(fake.lastBatchRequest.weighings()).hasSize(1);
        assertThat(fake.lastBatchRequest.deaths()).hasSize(1);
        assertThat(fake.lastBatchRequest.initialQuantity()).isEqualTo(5000);
        // Las lecturas son del estanque donde está el lote: ahí está el agua
        assertThat(fake.lastBatchRequest.readings()).isNotEmpty();
        // Y el peso objetivo, que es de donde sale la fecha de cosecha
        assertThat(fake.lastBatchRequest.targetWeightG()).isEqualTo(250.0);
    }

    @Test
    void laProyeccionSeCalculaAlPreguntarYNoSeGuarda() throws Exception {
        String batch = pondWithReadings(20)[1];
        JsonNode projection = call(get("/api/lotes/" + batch + "/proyeccion"), 200);

        assertThat(projection.get("tgc_ajustado").asBoolean()).isTrue();
        assertThat(projection.get("peso_proyectado_g").asDouble()).isEqualTo(50.6);
        assertThat(projection.get("dias_para_objetivo").asInt()).isEqualTo(148);
        // No creó ningún hallazgo: la fecha de cosecha no es un problema, es la respuesta a cuándo vendo
        assertThat(call(get("/api/lotes/" + batch + "/hallazgos"), 200)).isEmpty();
    }

    @Test
    void sinServicioLaProyeccionDiceQueNoEstaDisponibleYNoDevuelveCeros() throws Exception {
        String batch = pondWithReadings(20)[1];
        fake.up = false;
        JsonNode error = call(get("/api/lotes/" + batch + "/proyeccion"), 503);
        assertThat(error.get("detalle").asText()).contains("no está disponible");
        assertThat(error.get("detalle").asText()).contains("siguen funcionando");
    }

    @Test
    void laProyeccionDeUnLoteQueNoExisteEs404() throws Exception {
        call(get("/api/lotes/" + newId() + "/proyeccion"), 404);
    }

    @Test
    void losHallazgosDelLoteSeGuardanContraElLoteYNoContraElEstanque() throws Exception {
        String[] ids = pondWithReadings(20);
        fake.batchFindings = List.of(new Ai.BatchFindingJson("mortalidad", true, 76.5,
                "La mortalidad se aceleró: 25.7 muertes por día en los últimos 7 días."));
        analysis.analyzeBatch(UUID.fromString(ids[1]));

        JsonNode ofBatch = call(get("/api/lotes/" + ids[1] + "/hallazgos"), 200);
        assertThat(ofBatch).hasSize(1);
        assertThat(ofBatch.get(0).get("lote_id").asText()).isEqualTo(ids[1]);
        assertThat(ofBatch.get(0).get("estanque_id").isNull()).isTrue();
        assertThat(ofBatch.get(0).get("nivel").asText()).isEqualTo("critica");
        assertThat(call(get("/api/estanques/" + ids[0] + "/hallazgos"), 200)).isEmpty();
    }

    // ---------- the routes ----------

    @Test
    void sePuedeSaberSiHayServicioDeIa() throws Exception {
        assertThat(call(get("/api/ia/disponible"), 200).get("disponible").asBoolean()).isTrue();
        fake.up = false;
        assertThat(call(get("/api/ia/disponible"), 200).get("disponible").asBoolean()).isFalse();
    }

    @Test
    void losHallazgosPendientesSalenEnUnaSolaLista() throws Exception {
        String pond = pondWithReadings(20)[0];
        fake.combination = new Ai.CombinationJson(List.of("temp_c", "ph"), List.of(12.2, 7.52),
                6.4, 3.035, "Esta combinación no se había visto en el estanque.");
        analysis.analyzePond(UUID.fromString(pond));

        JsonNode pending = call(get("/api/hallazgos"), 200);
        assertThat(pending).anySatisfy(h ->
                assertThat(h.get("clase").asText()).isEqualTo("combinacion"));
    }

    @Test
    void losHallazgosNecesitanCredenciales() throws Exception {
        MvcResult res = mvc.perform(get("/api/hallazgos")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    // ---------- the webhook ----------

    @Test
    void elWebhookGuardaUnConteoSinMandarElCatalogo() throws Exception {
        String batch = pondWithReadings(2)[1];
        String countId = newId();

        JsonNode result = call(post("/api/webhook/contador").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj(
                        "dispositivo_id", "esp32-contador-1",
                        "eventos", List.of(obj("tipo", "conteo", "id", countId, "lote_id", batch,
                                "registrado_en", minutesAgo(2), "origen", "contador",
                                "total", 143, "cortes_multiples", 4))))), 200);

        assertThat(result.get("aceptados")).hasSize(1);
        assertThat(result.get("aceptados").get(0).asText()).isEqualTo(countId);
    }

    @Test
    void reenviarAlWebhookNoDuplica() throws Exception {
        String batch = pondWithReadings(2)[1];
        String countId = newId();
        Map<String, Object> body = obj("dispositivo_id", "esp32-contador-1",
                "eventos", List.of(obj("tipo", "conteo", "id", countId, "lote_id", batch,
                        "registrado_en", minutesAgo(2), "origen", "contador", "total", 143)));

        call(post("/api/webhook/contador").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)), 200);
        // El aparato reintenta cuando no está seguro de que llegó: la idempotencia es la misma del
        // push, porque es la misma ingesta
        JsonNode again = call(post("/api/webhook/contador").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)), 200);

        assertThat(again.get("duplicados")).hasSize(1);
        assertThat(again.get("aceptados")).isEmpty();
    }

    @Test
    void elWebhookExigeAlMenosUnEvento() throws Exception {
        call(post("/api/webhook/contador").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("dispositivo_id", "esp32-contador-1",
                        "eventos", List.of()))), 422);
    }

    @Test
    void elWebhookNecesitaCredenciales() throws Exception {
        MvcResult res = mvc.perform(post("/api/webhook/contador")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }
}
