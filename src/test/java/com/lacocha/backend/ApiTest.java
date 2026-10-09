package com.lacocha.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

    private static final String CLAVE = "clave-prueba";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    // ---------- utilidades ----------

    static String nuevoId() {
        return UUID.randomUUID().toString();
    }

    static String hace(long minutos) {
        return Instant.now().minus(Duration.ofMinutes(minutos)).toString();
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    JsonNode llamar(MockHttpServletRequestBuilder req, int estadoEsperado) throws Exception {
        MvcResult res = mvc.perform(req.header("X-API-Key", CLAVE)).andReturn();
        String cuerpo = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(cuerpo).isEqualTo(estadoEsperado);
        return cuerpo.isEmpty() ? null : mapper.readTree(cuerpo);
    }

    JsonNode push(Map<String, Object> cuerpo) throws Exception {
        Map<String, Object> completo = new HashMap<>(cuerpo);
        completo.put("dispositivo_id", "cel-1");
        return llamar(post("/api/sync/push").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(completo)), 200);
    }

    JsonNode pushEventos(Object... eventos) throws Exception {
        return push(mapa("eventos", List.of(eventos)));
    }

    /** Crea un estanque y un lote de 5000 alevinos de 2 g. Devuelve {estanque, lote}. */
    String[] crearCatalogo() throws Exception {
        String estanque = nuevoId();
        String lote = nuevoId();
        push(mapa(
                "estanques", List.of(mapa("id", estanque, "nombre", "Tanque 1", "tipo", "tanque", "actualizado_en", hace(120))),
                "lotes", List.of(mapa("id", lote, "estanque_id", estanque, "codigo", "L12", "cantidad_inicial", 5000,
                        "peso_inicial_g", 2.0, "actualizado_en", hace(120)))));
        return new String[] {estanque, lote};
    }

    static List<String> textos(JsonNode arreglo) {
        List<String> lista = new ArrayList<>();
        arreglo.forEach(n -> lista.add(n.asText()));
        return lista;
    }

    // ---------- pruebas ----------

    @Test
    void saludNoPideClave() throws Exception {
        MvcResult res = mvc.perform(get("/salud")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getContentAsString()).contains("\"estado\":\"ok\"");
    }

    @Test
    void claveIncorrectaDa401() throws Exception {
        MvcResult res = mvc.perform(get("/api/estanques").header("X-API-Key", "otra")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void pushEsIdempotenteYGeneraAlertas() throws Exception {
        String estanque = crearCatalogo()[0];
        Map<String, Object> lectura = mapa("tipo", "lectura_agua", "id", nuevoId(), "estanque_id", estanque,
                "temp_c", 19.0, "ph", 7.1, "origen", "sensor", "registrado_en", hace(5));

        JsonNode primero = pushEventos(lectura);
        assertThat(textos(primero.get("aceptados"))).containsExactly((String) lectura.get("id"));
        assertThat(primero.get("alertas_generadas").asInt()).isEqualTo(1);

        // El celular reintenta porque se cayó la señal antes de recibir la respuesta
        JsonNode segundo = pushEventos(lectura);
        assertThat(textos(segundo.get("duplicados"))).containsExactly((String) lectura.get("id"));
        assertThat(segundo.get("alertas_generadas").asInt()).isZero();

        JsonNode alertas = llamar(get("/api/alertas").param("estanque_id", estanque), 200);
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).get("nivel").asText()).isEqualTo("critica");
        assertThat(alertas.get(0).get("variable").asText()).isEqualTo("temp_c");
        assertThat(alertas.get(0).get("mensaje").asText()).isEqualTo("Temperatura alta: 19 °C (óptimo 10–16 °C)");
    }

    @Test
    void unEventoMaloNoBloqueaLosDemas() throws Exception {
        String[] cat = crearCatalogo();
        Map<String, Object> bueno = mapa("tipo", "mortalidad", "id", nuevoId(), "lote_id", cat[1], "cantidad", 3, "registrado_en", hace(1));
        Map<String, Object> phImposible = mapa("tipo", "lectura_agua", "id", nuevoId(), "estanque_id", cat[0], "ph", 20, "registrado_en", hace(1));
        Map<String, Object> loteInexistente = mapa("tipo", "conteo", "id", nuevoId(), "lote_id", nuevoId(), "total", 10, "registrado_en", hace(1));
        Map<String, Object> tipoRaro = mapa("tipo", "otra_cosa", "id", nuevoId());
        Map<String, Object> sinVariables = mapa("tipo", "lectura_agua", "id", nuevoId(), "estanque_id", cat[0], "registrado_en", hace(1));

        JsonNode r = pushEventos(bueno, phImposible, loteInexistente, tipoRaro, sinVariables);
        assertThat(textos(r.get("aceptados"))).containsExactly((String) bueno.get("id"));

        Map<String, String> rechazados = new HashMap<>();
        r.get("rechazados").forEach(x -> rechazados.put(x.get("id").asText(), x.get("error").asText()));
        assertThat(rechazados).containsOnlyKeys((String) phImposible.get("id"), (String) loteInexistente.get("id"),
                (String) tipoRaro.get("id"), (String) sinVariables.get("id"));
        assertThat(rechazados.get(phImposible.get("id"))).startsWith("ph:");
        assertThat(rechazados.get(loteInexistente.get("id"))).isEqualTo("lote_id: no existe");
        assertThat(rechazados.get(sinVariables.get("id"))).contains("no trae temperatura");
    }

    @Test
    void fechaEnElFuturoSeRechaza() throws Exception {
        String lote = crearCatalogo()[1];
        String futuro = Instant.now().plus(Duration.ofDays(3)).toString();
        JsonNode r = pushEventos(mapa("tipo", "mortalidad", "id", nuevoId(), "lote_id", lote, "cantidad", 1, "registrado_en", futuro));
        assertThat(r.get("rechazados").get(0).get("error").asText()).contains("reloj");
    }

    @Test
    void resumenDelLote() throws Exception {
        String[] cat = crearCatalogo();
        String estanque = cat[0];
        String lote = cat[1];
        pushEventos(
                mapa("tipo", "mortalidad", "id", nuevoId(), "lote_id", lote, "cantidad", 10, "registrado_en", hace(90)),
                mapa("tipo", "conteo", "id", nuevoId(), "lote_id", lote, "total", 5000, "origen", "contador", "registrado_en", hace(60)),
                mapa("tipo", "mortalidad", "id", nuevoId(), "lote_id", lote, "cantidad", 30, "registrado_en", hace(30)),
                mapa("tipo", "biometria", "id", nuevoId(), "lote_id", lote, "peso_promedio_g", 2.5, "muestra", 50, "registrado_en", hace(20)),
                mapa("tipo", "lectura_agua", "id", nuevoId(), "estanque_id", estanque, "temp_c", 12.0, "ph", 7.2, "registrado_en", hace(10)),
                mapa("tipo", "alimentacion", "id", nuevoId(), "lote_id", lote, "kg", 0.5, "registrado_en", hace(5)));

        JsonNode r = llamar(get("/api/lotes/" + lote + "/resumen"), 200);
        assertThat(r.get("poblacion_estimada").asInt()).isEqualTo(4970); // 5000 contados - 30 muertos después del conteo
        assertThat(r.get("mortalidad_total").asInt()).isEqualTo(40);
        assertThat(r.get("supervivencia_pct").asDouble()).isEqualTo(99.4);
        assertThat(r.get("biomasa_kg").asDouble()).isEqualTo(12.425); // 4970 x 2.5 g
        assertThat(r.get("tasa_alimentacion_pct").asDouble()).isEqualTo(4.5);
        assertThat(r.get("racion_diaria_kg").asDouble()).isEqualTo(0.559);
        assertThat(r.get("alimento_ultima_semana_kg").asDouble()).isEqualTo(0.5);
        assertThat(r.get("alertas_pendientes").asInt()).isZero();
        assertThat(r.get("notas")).isEmpty();
    }

    @Test
    void ganaElCambioMasReciente() throws Exception {
        String estanque = crearCatalogo()[0];
        push(mapa("estanques", List.of(mapa("id", estanque, "nombre", "Tanque A", "actualizado_en", hace(10)))));
        JsonNode viejo = push(mapa("estanques", List.of(mapa("id", estanque, "nombre", "Nombre viejo", "actualizado_en", hace(60)))));
        assertThat(textos(viejo.get("obsoletos"))).containsExactly(estanque);

        JsonNode lista = llamar(get("/api/estanques"), 200);
        lista.forEach(e -> {
            if (e.get("id").asText().equals(estanque)) {
                assertThat(e.get("nombre").asText()).isEqualTo("Tanque A");
            }
        });
    }

    @Test
    void pullSoloTraeCambiosNuevos() throws Exception {
        crearCatalogo();
        JsonNode completo = llamar(get("/api/sync/pull"), 200);
        assertThat(completo.get("estanques").size()).isGreaterThanOrEqualTo(1);
        String cursor = completo.get("servidor_en").asText();

        JsonNode vacio = llamar(get("/api/sync/pull").param("desde", cursor), 200);
        assertThat(vacio.get("estanques")).isEmpty();
        assertThat(vacio.get("lotes")).isEmpty();

        llamar(post("/api/estanques").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Tanque 2\"}"), 201);
        JsonNode nuevo = llamar(get("/api/sync/pull").param("desde", cursor), 200);
        assertThat(nuevo.get("estanques")).hasSize(1);
        assertThat(nuevo.get("estanques").get(0).get("nombre").asText()).isEqualTo("Tanque 2");
    }

    @Test
    void atenderAlerta() throws Exception {
        String estanque = crearCatalogo()[0];
        pushEventos(mapa("tipo", "lectura_agua", "id", nuevoId(), "estanque_id", estanque, "ph", 6.2, "registrado_en", hace(1)));

        JsonNode alertas = llamar(get("/api/alertas").param("estanque_id", estanque), 200);
        assertThat(alertas.get(0).get("nivel").asText()).isEqualTo("advertencia");
        llamar(post("/api/alertas/" + alertas.get(0).get("id").asText() + "/atender"), 200);
        assertThat(llamar(get("/api/alertas").param("estanque_id", estanque), 200)).isEmpty();
    }

    @Test
    void validacionDelCatalogoDa422() throws Exception {
        JsonNode r = llamar(post("/api/estanques").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"tipo\":\"piscina\"}"), 422);
        assertThat(r.get("detalle").asText()).contains("nombre").contains("tipo");
    }

    @Test
    void corsPermiteElPanelYRechazaOtrosOrigenes() throws Exception {
        MvcResult permitido = mvc.perform(options("/api/estanques")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "X-API-Key")).andReturn();
        assertThat(permitido.getResponse().getStatus()).isEqualTo(200);
        assertThat(permitido.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(permitido.getResponse().getHeader("Access-Control-Max-Age")).isEqualTo("3600");

        MvcResult extrano = mvc.perform(options("/api/estanques")
                .header("Origin", "https://otro-sitio.com")
                .header("Access-Control-Request-Method", "GET")).andReturn();
        assertThat(extrano.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void listarLotesFiltraPorEstanqueYEstado() throws Exception {
        String[] cat = crearCatalogo();
        String cerrado = nuevoId();
        push(mapa("lotes", List.of(mapa("id", cerrado, "estanque_id", cat[0], "codigo", "L01", "estado", "cerrado",
                "actualizado_en", hace(100)))));

        JsonNode todos = llamar(get("/api/lotes").param("estanque_id", cat[0]), 200);
        assertThat(todos).extracting(n -> n.get("codigo").asText()).containsExactly("L01", "L12");

        JsonNode activos = llamar(get("/api/lotes").param("estanque_id", cat[0]).param("estado", "activo"), 200);
        assertThat(activos).extracting(n -> n.get("id").asText()).containsExactly(cat[1]);

        JsonNode error = llamar(get("/api/lotes").param("estado", "vendido"), 400);
        assertThat(error.get("detalle").asText()).isEqualTo("estado: debe ser activo o cerrado");
    }

    @Test
    void crearConIdRepetidoDa409() throws Exception {
        String[] cat = crearCatalogo();
        JsonNode e = llamar(post("/api/estanques").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + cat[0] + "\",\"nombre\":\"Otro\"}"), 409);
        assertThat(e.get("detalle").asText()).isEqualTo("El estanque ya existe con ese id");

        llamar(post("/api/lotes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + cat[1] + "\",\"estanque_id\":\"" + cat[0] + "\",\"codigo\":\"X\"}"), 409);
    }
}
