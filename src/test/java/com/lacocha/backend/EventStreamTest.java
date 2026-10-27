package com.lacocha.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.lacocha.backend.model.Finding;
import com.lacocha.backend.service.Changed;
import com.lacocha.backend.service.EventStream;

/**
 * The event handler.
 *
 * The annotations are deliberately identical to {@link AiTest}'s so Spring hands back the same
 * cached context instead of starting a second one for six seconds.
 */
@SpringBootTest(properties = "lacocha.ia-analisis-automatico=false")
@AutoConfigureMockMvc
@Import(AiTest.FakeService.class)
class EventStreamTest {

    private static final String API_KEY = "clave-prueba";

    @Autowired
    MockMvc mvc;

    @Autowired
    EventStream stream;

    @AfterEach
    void cierroTodo() {
        // The context is shared between test classes: a connection left open here would be counted
        // by whatever runs next
        stream.closeAll();
    }

    /** Opens a stream and gives back what the server has already written to it. */
    private MvcResult open(String query) throws Exception {
        return mvc.perform(get("/api/eventos/stream" + query).header("X-API-Key", API_KEY))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static Finding finding(UUID pondId, UUID batchId, String level, String message) {
        Finding f = new Finding();
        f.setPondId(pondId);
        f.setBatchId(batchId);
        f.setKind("tendencia");
        f.setLevel(level);
        f.setVariable("oxigeno_mg_l");
        f.setScore(0.94);
        f.setMessage(message);
        f.setMeasuredAt(Instant.now());
        return f;
    }

    // ---------- opening ----------

    @Test
    void alAbrirSeAvisaDeInmediatoQueLaConexionSirve() throws Exception {
        MvcResult result = open("");

        // Sent before any real event so the browser does not have to wait for one to find out
        // whether the connection worked at all
        assertThat(result.getResponse().getContentAsString()).contains("\"tipo\":\"listo\"");
        assertThat(stream.listening()).isEqualTo(1);
    }

    @Test
    void elStreamNecesitaCredenciales() throws Exception {
        MvcResult result = mvc.perform(get("/api/eventos/stream")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(stream.listening()).isZero();
    }

    @Test
    void elStreamSeSirveComoEventosYNoComoJson() throws Exception {
        MvcResult result = open("");
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
    }

    @Test
    void elStreamDeclaraQueVaEnUtf8() throws Exception {
        MvcResult result = open("");
        // Sin declararlo, el servlet pone ISO-8859-1 por omisión mientras manda UTF-8 de verdad.
        // Un navegador lo perdona porque el estándar de SSE obliga a UTF-8 pase lo que pase; lo que
        // haya en el camino, no. Y cada hallazgo que pasa por aquí es una frase con acentos.
        assertThat(result.getResponse().getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        assertThat(result.getResponse().getContentType()).containsIgnoringCase("utf-8");
    }

    // ---------- what reaches whom ----------

    @Test
    void unHallazgoLlegaEnteroPorElStream() throws Exception {
        MvcResult result = open("");
        UUID pond = UUID.randomUUID();

        stream.publish(finding(pond, null, "critica",
                "El oxígeno viene bajando 0.4 mg/L por día. A este ritmo llega al límite mañana."));

        String written = result.getResponse().getContentAsString();
        assertThat(written).contains("\"tipo\":\"hallazgo\"");
        // Whole, and not a notification to go and ask: it is small and its text is stored exactly
        // as it is sent, so there is no second source to disagree with
        assertThat(written).contains("llega al límite mañana");
        assertThat(written).contains("\"nivel\":\"critica\"");
        assertThat(written).contains(pond.toString());
    }

    @Test
    void conFiltroDeEstanqueNoLlegaLoDeOtroEstanque() throws Exception {
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        MvcResult result = open("?estanque_id=" + mine);

        stream.publish(finding(theirs, null, "critica", "Esto es de otro estanque"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("otro estanque");

        // This is what keeps a phone standing at one channel from being woken by every reading on
        // the farm, which on a connection paid by the megabyte is not a detail
        stream.publish(finding(mine, null, "advertencia", "Esto si es mio"));
        assertThat(result.getResponse().getContentAsString()).contains("Esto si es mio");
    }

    @Test
    void conFiltroDeLoteLlegaLoDeEseLote() throws Exception {
        UUID batch = UUID.randomUUID();
        MvcResult result = open("?lote_id=" + batch);

        stream.publish(finding(null, batch, "critica", "La mortalidad se acelero"));
        assertThat(result.getResponse().getContentAsString()).contains("La mortalidad se acelero");
    }

    @Test
    void sinFiltroLlegaTodo() throws Exception {
        MvcResult result = open("");
        stream.publish(finding(UUID.randomUUID(), null, "informe", "De un estanque"));
        stream.publish(finding(null, UUID.randomUUID(), "informe", "De un lote"));

        String written = result.getResponse().getContentAsString();
        assertThat(written).contains("De un estanque").contains("De un lote");
    }

    // ---------- the sync notification ----------

    @Test
    void unaSincronizacionAvisaSinMandarLosTotales() throws Exception {
        UUID batch = UUID.randomUUID();
        MvcResult result = open("?lote_id=" + batch);

        stream.onChanged(new Changed("esp32-contador-1", Set.of(), Set.of(batch), 143));

        String written = result.getResponse().getContentAsString();
        assertThat(written).contains("\"tipo\":\"sincronizado\"");
        assertThat(written).contains("esp32-contador-1");
        assertThat(written).contains("\"aceptados\":143");
        // How many arrived, not what the total is: the screen asks the API for the total, so there
        // is only ever one place computing it
        assertThat(written).doesNotContain("total");
    }

    @Test
    void unaSincronizacionDeOtroLoteNoDespiertaEstaPantalla() throws Exception {
        MvcResult result = open("?lote_id=" + UUID.randomUUID());
        stream.onChanged(new Changed("cel-1", Set.of(), Set.of(UUID.randomUUID()), 7));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("sincronizado");
    }

    @Test
    void unaSincronizacionSinNadaCambiadoNoAvisa() throws Exception {
        MvcResult result = open("");
        String before = result.getResponse().getContentAsString();
        stream.onChanged(new Changed("cel-1", Set.of(), Set.of(), 0));
        assertThat(result.getResponse().getContentAsString()).isEqualTo(before);
    }

    @Test
    void publicarSinNadieEscuchandoNoFalla() {
        stream.closeAll();
        assertThat(stream.listening()).isZero();
        stream.publish(finding(UUID.randomUUID(), null, "critica", "Nadie lo va a ver"));
        stream.onChanged(new Changed("cel-1", Set.of(UUID.randomUUID()), Set.of(), 3));
    }

    // ---------- letting go ----------

    @Test
    void cerrarTodoLibera() throws Exception {
        open("");
        open("");
        assertThat(stream.listening()).isEqualTo(2);

        stream.closeAll();
        assertThat(stream.listening()).isZero();
    }

    @Test
    void elLatidoNoCierraUnaConexionViva() throws Exception {
        open("");
        stream.heartbeat();
        // Two bytes on the wire that the browser ignores, and the connection stays
        assertThat(stream.listening()).isEqualTo(1);
    }
}
