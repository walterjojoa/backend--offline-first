package com.lacocha.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Qué puede hacer cada rol, y cómo vuelve a entrar quien olvidó la clave.
 *
 * Lo que estas pruebas cuidan es una regla concreta: la clave compartida no abre la administración.
 * Esa clave está a la vista en los Ajustes de cada celular, así que si abriera la administración
 * cualquier cuidador podría dar de baja el celular de otro.
 */
@SpringBootTest(properties = "lacocha.producer-documents=2000000001")
@AutoConfigureMockMvc
class RolesTest {

    private static final String API_KEY = "clave-prueba";
    /** El primero de esta cuenta es el documento que la configuración declara productor. */
    private static final String PRODUCER_DOCUMENT = "2000000001";
    private static final AtomicLong NEXT_DOCUMENT = new AtomicLong(2_000_000_001L);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Solo con el token, sin la clave compartida: así entra un cliente que ya inició sesión. */
    private JsonNode tokenOnly(MockHttpServletRequestBuilder req, String token, int expectedStatus) throws Exception {
        MvcResult res = mvc.perform(req.header("Authorization", "Bearer " + token)).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String token, int expectedStatus) throws Exception {
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        MvcResult res = mvc.perform(req.header("X-API-Key", API_KEY)).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    /** Registra a alguien con un documento nuevo y devuelve su sesión. Siempre sale cuidador. */
    private JsonNode register(String password) throws Exception {
        return registerAs(String.valueOf(NEXT_DOCUMENT.incrementAndGet()), password);
    }

    private JsonNode registerAs(String document, String password) throws Exception {
        return call(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        obj("documento", document, "nombre", "Persona " + document, "clave", password))),
                null, 200);
    }

    /**
     * El productor de la finca. No depende de quién se registre primero: su documento está
     * declarado en la configuración, que es como se instala el servidor de verdad.
     */
    private JsonNode producer() throws Exception {
        JsonNode session = call(post("/api/auth/sesion").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("documento", PRODUCER_DOCUMENT, "clave", "laguna2026"))),
                null, 200);
        assertThat(session.get("usuario").get("rol").asText()).isEqualTo("productor");
        return session;
    }

    @org.junit.jupiter.api.BeforeEach
    void elProductorDeLaFincaExiste() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/registro").header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("documento", PRODUCER_DOCUMENT, "nombre", "Productora",
                        "clave", "laguna2026")))).andReturn();
        // 200 la primera vez, 409 en las siguientes: en cualquier caso ya está
        assertThat(res.getResponse().getStatus()).isIn(200, 409);
    }

    // ---------- quién puede qué ----------

    @Test
    void laClaveCompartidaNoAbreLaAdministracion() throws Exception {
        // Sin token, solo con la clave que todos los celulares llevan
        call(get("/api/usuarios"), null, 401);
        call(get("/api/dispositivos"), null, 401);
        call(get("/api/sincronizaciones"), null, 401);
    }

    @Test
    void unCuidadorNoPuedeVerNiCambiarLaAdministracion() throws Exception {
        // La primera cuenta del esquema ya existe, así que esta entra como cuidadora
        JsonNode caretaker = register("laguna2026");
        assertThat(caretaker.get("usuario").get("rol").asText()).isEqualTo("cuidador");
        String token = caretaker.get("token").asText();

        JsonNode error = call(get("/api/usuarios"), token, 403);
        assertThat(error.get("detalle").asText()).contains("solo lo puede hacer el productor");
        call(get("/api/dispositivos"), token, 403);
        call(get("/api/sincronizaciones"), token, 403);
    }

    @Test
    void unCuidadorSiPuedeTrabajarEnElCampo() throws Exception {
        String token = register("laguna2026").get("token").asText();

        // Lo suyo es registrar, y eso no se le toca
        call(get("/api/estanques"), token, 200);
        call(get("/api/alertas"), token, 200);
    }

    // ---------- la clave que se olvidó ----------

    @Test
    void elProductorReiniciaLaClaveYLaTemporalViajaUnaSolaVez() throws Exception {
        String producer = producer().get("token").asText();
        JsonNode forgetful = register("claveolvidada");
        String id = forgetful.get("usuario").get("id").asText();

        JsonNode reset = call(post("/api/usuarios/" + id + "/clave-temporal"), producer, 200);

        String temporary = reset.get("clave_temporal").asText();
        assertThat(temporary).hasSize(10);
        // Se dicta en voz alta junto a un estanque: sin vocales ni caracteres que se confundan
        assertThat(temporary).doesNotContainAnyWhitespaces().matches("[bcdfghjkmnpqrstvwxyz23456789]+");
        assertThat(reset.get("usuario").get("debe_cambiar_clave").asBoolean()).isTrue();
    }

    @Test
    void laClaveViejaDejaDeServirYLaTemporalEntra() throws Exception {
        String producer = producer().get("token").asText();
        JsonNode forgetful = register("claveolvidada");
        String document = forgetful.get("usuario").get("documento").asText();
        String id = forgetful.get("usuario").get("id").asText();

        String temporary = call(post("/api/usuarios/" + id + "/clave-temporal"), producer, 200)
                .get("clave_temporal").asText();

        call(post("/api/auth/sesion").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("documento", document, "clave", "claveolvidada"))), null, 401);
        JsonNode entered = call(post("/api/auth/sesion").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("documento", document, "clave", temporary))), null, 200);
        assertThat(entered.get("usuario").get("debe_cambiar_clave").asBoolean()).isTrue();
    }

    @Test
    void cambiarLaClaveQuitaLaMarcaDeTemporal() throws Exception {
        String producer = producer().get("token").asText();
        JsonNode forgetful = register("claveolvidada");
        String id = forgetful.get("usuario").get("id").asText();
        String temporary = call(post("/api/usuarios/" + id + "/clave-temporal"), producer, 200)
                .get("clave_temporal").asText();
        String document = forgetful.get("usuario").get("documento").asText();
        String token = call(post("/api/auth/sesion").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("documento", document, "clave", temporary))), null, 200)
                .get("token").asText();

        call(post("/api/auth/clave").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("clave_actual", temporary, "clave_nueva", "laguna2027"))),
                token, 200);

        JsonNode me = call(get("/api/auth/yo"), token, 200);
        assertThat(me.get("debe_cambiar_clave").asBoolean()).isFalse();
    }

    /** El productor le reinicia la clave a alguien; devuelve la temporal. */
    private String resetPassword(JsonNode person) throws Exception {
        return call(post("/api/usuarios/" + person.get("usuario").get("id").asText() + "/clave-temporal"),
                producer().get("token").asText(), 200).get("clave_temporal").asText();
    }

    private String signIn(JsonNode person, String password) throws Exception {
        return call(post("/api/auth/sesion").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("documento", person.get("usuario").get("documento").asText(),
                        "clave", password))), null, 200).get("token").asText();
    }

    @Test
    void conLaClaveTemporalElTokenSoloSirveParaCambiarla() throws Exception {
        JsonNode person = register("claveolvidada");
        String token = signIn(person, resetPassword(person));

        // El productor la oyó cuando la dijo en voz alta: hasta cambiarla no abre nada más
        JsonNode error = tokenOnly(get("/api/estanques"), token, 403);
        assertThat(error.get("detalle").asText()).contains("clave temporal");
        tokenOnly(get("/api/auth/yo"), token, 200);
    }

    @Test
    void unProductorConClaveTemporalNoAdministraHastaCambiarla() throws Exception {
        JsonNode other = register("laguna2026");
        call(patch("/api/usuarios/" + other.get("usuario").get("id").asText()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"productor\"}"), producer().get("token").asText(), 200);
        String temporary = resetPassword(other);
        String token = signIn(other, temporary);

        // Ni con la clave compartida: la administración pide la sesión, y esta todavía no vale
        JsonNode error = call(get("/api/usuarios"), token, 403);
        assertThat(error.get("detalle").asText()).contains("clave temporal");

        call(post("/api/auth/clave").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("clave_actual", temporary, "clave_nueva", "laguna2027"))),
                token, 200);
        call(get("/api/usuarios"), token, 200);
    }

    @Test
    void despuesDeCambiarLaTemporalElTokenAbreTodo() throws Exception {
        JsonNode person = register("claveolvidada");
        String temporary = resetPassword(person);
        String token = signIn(person, temporary);

        call(post("/api/auth/clave").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("clave_actual", temporary, "clave_nueva", "laguna2027"))),
                token, 200);

        tokenOnly(get("/api/estanques"), token, 200);
    }

    @Test
    void reiniciarLaClaveCierraLasSesionesQueYaHabia() throws Exception {
        JsonNode person = register("claveolvidada");
        String oldToken = person.get("token").asText();
        call(get("/api/auth/yo"), oldToken, 200);

        resetPassword(person);

        // Un celular perdido con la sesión abierta deja de entrar en cuanto el productor la reinicia
        call(get("/api/auth/yo"), oldToken, 401);
        tokenOnly(get("/api/estanques"), oldToken, 401);
    }

    @Test
    void reactivarAAlguienNoReviveSusSesionesViejas() throws Exception {
        JsonNode person = register("laguna2026");
        String oldToken = person.get("token").asText();
        String id = person.get("usuario").get("id").asText();
        String producer = producer().get("token").asText();

        call(patch("/api/usuarios/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"),
                producer, 200);
        call(patch("/api/usuarios/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"),
                producer, 200);

        call(get("/api/auth/yo"), oldToken, 401);
    }

    @Test
    void noSePuedeCambiarLaClaveSinSaberLaActual() throws Exception {
        String token = register("laguna2026").get("token").asText();

        JsonNode error = call(post("/api/auth/clave").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("clave_actual", "noeslaquees", "clave_nueva", "laguna2027"))),
                token, 401);
        assertThat(error.get("detalle").asText()).contains("clave actual");
    }

    @Test
    void laClaveNuevaTieneQueSerDistinta() throws Exception {
        String token = register("laguna2026").get("token").asText();

        JsonNode error = call(post("/api/auth/clave").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(obj("clave_actual", "laguna2026", "clave_nueva", "laguna2026"))),
                token, 409);
        assertThat(error.get("detalle").asText()).contains("distinta");
    }

    @Test
    void unCuidadorNoPuedeReiniciarleLaClaveANadie() throws Exception {
        String caretaker = register("laguna2026").get("token").asText();
        String otherId = register("laguna2026").get("usuario").get("id").asText();

        call(post("/api/usuarios/" + otherId + "/clave-temporal"), caretaker, 403);
    }

    @Test
    void elProductorNoPuedeDarseDeBajaASiMismo() throws Exception {
        JsonNode session = producer();
        String token = session.get("token").asText();
        String ownId = session.get("usuario").get("id").asText();

        // Si pudiera, la finca se quedaría sin quien administre
        JsonNode error = call(patch("/api/usuarios/" + ownId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}"), token, 409);
        assertThat(error.get("detalle").asText()).contains("tu propia cuenta");
    }
}
