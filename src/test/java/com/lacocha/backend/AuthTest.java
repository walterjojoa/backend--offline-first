package com.lacocha.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
 * Registering, signing in and what a session is good for, against H2 in memory.
 *
 * The point of these checks is the rule the whole thing rests on: either credential opens the API,
 * so adding people did not lock out the ESP32 and the phones that were already syncing with the
 * shared key.
 */
// A producer that none of these tests registers: otherwise, when this class runs first on the
// shared H2 database, its first person would become the producer and stop being a caretaker
@SpringBootTest(properties = "lacocha.producer-documents=4000000001")
@AutoConfigureMockMvc
class AuthTest {

    private static final String API_KEY = "clave-prueba";

    /** Documents are unique in the database, so every test gets its own. */
    private static final AtomicLong NEXT_DOCUMENT = new AtomicLong(1_000_000_000L);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    // ---------- helpers ----------

    static String newDocument() {
        return String.valueOf(NEXT_DOCUMENT.incrementAndGet());
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private JsonNode send(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        MvcResult res = mvc.perform(req).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    private JsonNode withKey(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        return send(req.header("X-API-Key", API_KEY), expectedStatus);
    }

    private JsonNode postJson(String path, Map<String, Object> body, int expectedStatus) throws Exception {
        return withKey(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)), expectedStatus);
    }

    /** Closes a session the way the app does it: device key plus the token. */
    private void logout(String token, int expectedStatus) throws Exception {
        withKey(delete("/api/auth/sesion").header("Authorization", "Bearer " + token), expectedStatus);
    }

    /** Registers someone and returns their token. */
    private String register(String document, String password) throws Exception {
        JsonNode session = postJson("/api/auth/registro",
                obj("documento", document, "nombre", "Don Luis", "clave", password), 200);
        return session.get("token").asText();
    }

    // ---------- registro ----------

    @Test
    void registrarDejaLaSesionYaIniciada() throws Exception {
        String document = newDocument();

        JsonNode session = postJson("/api/auth/registro",
                obj("documento", document, "nombre", "  Don Luis  ", "clave", "laguna2026"), 200);

        assertThat(session.get("token").asText()).isNotBlank();
        assertThat(session.get("expira_en").asText()).isNotBlank();
        assertThat(session.get("usuario").get("documento").asText()).isEqualTo(document);
        // El nombre se guarda sin los espacios de sobra que deja el teclado del celular
        assertThat(session.get("usuario").get("nombre").asText()).isEqualTo("Don Luis");
        assertThat(session.get("usuario").get("rol").asText()).isEqualTo("cuidador");
    }

    @Test
    void laClaveNuncaVuelveEnLaRespuesta() throws Exception {
        JsonNode session = postJson("/api/auth/registro",
                obj("documento", newDocument(), "nombre", "Don Luis", "clave", "laguna2026"), 200);

        assertThat(session.toString()).doesNotContain("laguna2026");
        assertThat(session.get("usuario").has("clave")).isFalse();
        assertThat(session.get("usuario").has("clave_hash")).isFalse();
    }

    @Test
    void noSePuedeRegistrarDosVecesElMismoDocumento() throws Exception {
        String document = newDocument();
        register(document, "laguna2026");

        JsonNode error = postJson("/api/auth/registro",
                obj("documento", document, "nombre", "Otra persona", "clave", "otraclave1"), 409);

        assertThat(error.get("detalle").asText()).contains("Ya hay alguien registrado");
    }

    @Test
    void laClaveCortaSeRechazaCon422YElMotivo() throws Exception {
        JsonNode error = postJson("/api/auth/registro",
                obj("documento", newDocument(), "nombre", "Don Luis", "clave", "corta"), 422);

        assertThat(error.get("detalle").asText()).contains("clave");
    }

    @Test
    void elDocumentoTieneQueSerUnNumeroDeDigitos() throws Exception {
        JsonNode error = postJson("/api/auth/registro",
                obj("documento", "no-es-numero", "nombre", "Don Luis", "clave", "laguna2026"), 422);

        assertThat(error.get("detalle").asText()).contains("documento");
    }

    // ---------- inicio de sesion ----------

    @Test
    void seEntraConElDocumentoYLaClave() throws Exception {
        String document = newDocument();
        register(document, "laguna2026");

        JsonNode session = postJson("/api/auth/sesion", obj("documento", document, "clave", "laguna2026"), 200);

        assertThat(session.get("token").asText()).isNotBlank();
        assertThat(session.get("usuario").get("documento").asText()).isEqualTo(document);
    }

    @Test
    void laClaveEquivocadaNoDiceSiElDocumentoExiste() throws Exception {
        String document = newDocument();
        register(document, "laguna2026");

        JsonNode conClaveMala = postJson("/api/auth/sesion", obj("documento", document, "clave", "otraclave1"), 401);
        JsonNode sinCuenta = postJson("/api/auth/sesion", obj("documento", newDocument(), "clave", "laguna2026"), 401);

        // El mismo mensaje en los dos casos: distinguirlos dejaria averiguar que documentos existen
        assertThat(conClaveMala.get("detalle").asText()).isEqualTo(sinCuenta.get("detalle").asText());
    }

    @Test
    void cadaIngresoAbreUnaSesionDistinta() throws Exception {
        String document = newDocument();
        String primero = register(document, "laguna2026");
        String segundo = postJson("/api/auth/sesion", obj("documento", document, "clave", "laguna2026"), 200)
                .get("token").asText();

        // El cuidador puede tener la app en dos celulares; cerrar uno no bota el otro
        assertThat(segundo).isNotEqualTo(primero);
        send(get("/api/auth/yo").header("Authorization", "Bearer " + primero), 200);
        send(get("/api/auth/yo").header("Authorization", "Bearer " + segundo), 200);
    }

    // ---------- la sesion sirve para entrar a la API ----------

    @Test
    void elTokenSoloAbreLaApiSinLaClaveCompartida() throws Exception {
        String token = register(newDocument(), "laguna2026");

        send(get("/api/estanques").header("Authorization", "Bearer " + token), 200);
    }

    @Test
    void laClaveCompartidaSigueAbriendoLaApi() throws Exception {
        // El ESP32 y un celular que todavia no inicia sesion siguen entrando con la clave
        withKey(get("/api/estanques"), 200);
    }

    @Test
    void sinNingunaDeLasDosSeNiegaLaEntrada() throws Exception {
        send(get("/api/estanques"), 401);
        send(get("/api/estanques").header("Authorization", "Bearer no-es-un-token"), 401);
    }

    @Test
    void yoDiceQuienEsElDuenoDelToken() throws Exception {
        String document = newDocument();
        String token = register(document, "laguna2026");

        JsonNode yo = send(get("/api/auth/yo").header("Authorization", "Bearer " + token), 200);

        assertThat(yo.get("documento").asText()).isEqualTo(document);
        assertThat(yo.get("nombre").asText()).isEqualTo("Don Luis");
    }

    // ---------- cerrar sesion ----------

    @Test
    void cerrarSesionDejaElTokenInservible() throws Exception {
        String token = register(newDocument(), "laguna2026");
        send(get("/api/auth/yo").header("Authorization", "Bearer " + token), 200);

        logout(token, 200);

        send(get("/api/auth/yo").header("Authorization", "Bearer " + token), 401);
        // Y deja de abrir el resto de la API: es lo que protege un celular perdido
        send(get("/api/estanques").header("Authorization", "Bearer " + token), 401);
    }

    // ---------- intentos fallidos ----------

    private void tryLogin(String document, String password, int expectedStatus) throws Exception {
        postJson("/api/auth/sesion", obj("documento", document, "clave", password), expectedStatus);
    }

    @Test
    void cincoClavesMalasBloqueanElDocumentoAunqueLaSextaSeaLaBuena() throws Exception {
        String document = newDocument();
        register(document, "laguna2026");
        for (int i = 0; i < 5; i++) {
            tryLogin(document, "adivinando" + i, 401);
        }

        // Si la buena entrara, el bloqueo no detendria a quien prueba claves hasta acertar
        JsonNode error = postJson("/api/auth/sesion", obj("documento", document, "clave", "laguna2026"), 429);
        assertThat(error.get("detalle").asText()).contains("15 minutos");
    }

    @Test
    void unDocumentoQueNoExisteSeBloqueaIgual() throws Exception {
        String document = newDocument();
        for (int i = 0; i < 5; i++) {
            tryLogin(document, "adivinando" + i, 401);
        }

        // Si solo se bloquearan los que existen, el 429 diria cuales estan registrados
        tryLogin(document, "adivinando5", 429);
    }

    @Test
    void entrarBienBorraLosIntentosFallidos() throws Exception {
        String document = newDocument();
        register(document, "laguna2026");
        for (int i = 0; i < 4; i++) {
            tryLogin(document, "mal" + i + "tecleada", 401);
        }
        tryLogin(document, "laguna2026", 200);

        // Otros cuatro errores no lo bloquean: la cuenta empezo de nuevo
        for (int i = 0; i < 4; i++) {
            tryLogin(document, "mal" + i + "tecleada", 401);
        }
        tryLogin(document, "laguna2026", 200);
    }

    @Test
    void elBloqueoDeUnDocumentoNoAfectaAOtro() throws Exception {
        String bloqueado = newDocument();
        String vecino = newDocument();
        register(vecino, "laguna2026");
        for (int i = 0; i < 5; i++) {
            tryLogin(bloqueado, "adivinando" + i, 401);
        }

        tryLogin(vecino, "laguna2026", 200);
    }

    @Test
    void cerrarDosVecesNoEsUnError() throws Exception {
        String token = register(newDocument(), "laguna2026");

        logout(token, 200);
        // Sin señal la app reintenta: repetir el cierre tiene que ser inofensivo
        logout(token, 200);
    }
}
