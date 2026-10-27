package com.lacocha.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.lacocha.backend.dto.Ai;

/**
 * Tests of the circuit breaker, without a database and without the AI service.
 *
 * That is the point of the Bridge: the rule being tested here is "stop asking a service that is not
 * answering", and it has nothing to do with HTTP, so none of it is needed to check it.
 */
class AnalyzerDecoratorsTest {

    /** An Analyzer that answers whatever it is told to and counts how many times it was asked. */
    private static final class Counting implements Analyzer {

        boolean works;
        int calls;

        Counting(boolean works) {
            this.works = works;
        }

        @Override
        public boolean available() {
            calls++;
            return works;
        }

        @Override
        public Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request) {
            calls++;
            return works ? Optional.of(List.of()) : Optional.empty();
        }

        @Override
        public Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request) {
            calls++;
            return works ? Optional.of(List.of()) : Optional.empty();
        }

        @Override
        public Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request) {
            calls++;
            return works ? Optional.of(Optional.empty()) : Optional.empty();
        }

        @Override
        public Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request) {
            calls++;
            return Optional.empty();
        }

    }

    private static Ai.ReadingsRequest anyReadings() {
        return new Ai.ReadingsRequest(List.of());
    }

    // ---------- the analyzer that is not there ----------

    @Test
    void elAnalizadorAusenteNoRompeNada() {
        Analyzer absent = Analyzer.absent();
        assertThat(absent.available()).isFalse();
        assertThat(absent.anomalies(anyReadings())).isEmpty();
        assertThat(absent.trends(new Ai.TrendRequest(List.of(), List.of()))).isEmpty();
        assertThat(absent.combination(anyReadings())).isEmpty();
        assertThat(absent.projection(new Ai.BatchRequest(null, null, null, List.of(), List.of(),
                List.of(), 30, null))).isEmpty();
    }

    // ---------- "could not ask" is not "nothing found" ----------

    @Test
    void unaRespuestaVaciaNoEsLoMismoQueNoHaberPodidoPreguntar() {
        Counting works = new Counting(true);
        Counting down = new Counting(false);

        // El servicio contesto y no encontro nada
        assertThat(works.anomalies(anyReadings())).isPresent();
        assertThat(works.anomalies(anyReadings()).get()).isEmpty();
        // El servicio no contesto
        assertThat(down.anomalies(anyReadings())).isEmpty();
    }

    @Test
    void unaCombinacionNulaEsUnaRespuestaYNoUnFallo() {
        // La ruta responde null cuando la lectura sigue el patron y cuando no hay historia para
        // saberlo. Ninguna de las dos es un fallo, asi que el Optional de afuera viene presente.
        Optional<Optional<Ai.CombinationJson>> answer = new Counting(true).combination(anyReadings());
        assertThat(answer).isPresent();
        assertThat(answer.get()).isEmpty();
    }

    // ---------- the door closing ----------

    @Test
    void despuesDeUnFalloNoSeVuelveAPreguntar() {
        Counting inner = new Counting(false);
        AnalyzerDecorators.Resilient resilient =
                new AnalyzerDecorators.Resilient(inner, Duration.ofMinutes(5));

        assertThat(resilient.anomalies(anyReadings())).isEmpty();
        assertThat(inner.calls).isEqualTo(1);
        assertThat(resilient.closed()).isTrue();

        // Analizar un estanque son tres llamadas, y una ronda son varios estanques: ninguna llega
        resilient.anomalies(anyReadings());
        resilient.trends(new Ai.TrendRequest(List.of(), List.of()));
        resilient.combination(anyReadings());
        resilient.available();
        assertThat(inner.calls).as("el servicio no se volvió a llamar").isEqualTo(1);
    }

    @Test
    void conLaPuertaCerradaDisponibleRespondeNoSinPreguntar() {
        Counting inner = new Counting(false);
        AnalyzerDecorators.Resilient resilient =
                new AnalyzerDecorators.Resilient(inner, Duration.ofMinutes(5));
        resilient.available();

        int afterFirst = inner.calls;
        assertThat(resilient.available()).isFalse();
        assertThat(inner.calls).isEqualTo(afterFirst);
    }

    // ---------- the door opening again, which is the whole point ----------

    @Test
    void cuandoLaPausaPasaSeVuelveAPreguntar() throws InterruptedException {
        Counting inner = new Counting(false);
        // Una pausa de verdad, pero corta: un cortacircuitos que cierra y no abre es un servicio
        // apagado por su primer minuto malo
        AnalyzerDecorators.Resilient resilient =
                new AnalyzerDecorators.Resilient(inner, Duration.ofMillis(120));

        resilient.anomalies(anyReadings());
        assertThat(inner.calls).isEqualTo(1);
        assertThat(resilient.closed()).isTrue();

        Thread.sleep(200);
        assertThat(resilient.closed()).isFalse();

        inner.works = true;
        assertThat(resilient.anomalies(anyReadings())).isPresent();
        assertThat(inner.calls).isEqualTo(2);
    }

    @Test
    void unaRespuestaBuenaDejaLaPuertaAbierta() throws InterruptedException {
        Counting inner = new Counting(false);
        AnalyzerDecorators.Resilient resilient =
                new AnalyzerDecorators.Resilient(inner, Duration.ofMillis(120));

        resilient.anomalies(anyReadings());
        Thread.sleep(200);
        inner.works = true;
        resilient.anomalies(anyReadings());

        assertThat(resilient.closed()).as("tras responder bien no queda ninguna pausa pendiente")
                .isFalse();
        resilient.anomalies(anyReadings());
        resilient.trends(new Ai.TrendRequest(List.of(), List.of()));
        assertThat(inner.calls).isEqualTo(4);
    }

    @Test
    void mientrasFuncionaNoEstorba() {
        Counting inner = new Counting(true);
        Analyzer resilient = new AnalyzerDecorators.Resilient(inner);

        for (int i = 0; i < 5; i++) {
            assertThat(resilient.anomalies(anyReadings())).isPresent();
        }
        assertThat(inner.calls).isEqualTo(5);
    }

    /**
     * Una ruta que falla no cierra la puerta para las otras dos de esa misma ronda... pero sí para
     * la siguiente. Es lo que se quiere: si el servicio se durmió, las tres van a fallar igual, y
     * esperar el tiempo completo tres veces por estanque es justo lo que el cortacircuitos evita.
     */
    @Test
    void unaRutaQueFallaCierraLaPuertaParaLasDemas() {
        Counting inner = new Counting(false);
        AnalyzerDecorators.Resilient resilient = new AnalyzerDecorators.Resilient(inner);

        resilient.anomalies(anyReadings());
        assertThat(inner.calls).isEqualTo(1);
        resilient.trends(new Ai.TrendRequest(List.of(), List.of()));
        resilient.combination(anyReadings());
        assertThat(inner.calls).as("las otras dos ya no se intentan").isEqualTo(1);
    }

    // ---------- stacking them ----------

    @Test
    void elTrazoNoCambiaLaRespuesta() {
        Counting inner = new Counting(true);
        Analyzer stacked = new AnalyzerDecorators.Trace(new AnalyzerDecorators.Resilient(inner));

        assertThat(stacked.anomalies(anyReadings())).isPresent();
        assertThat(stacked.available()).isTrue();
        assertThat(inner.calls).isEqualTo(2);
    }

    @Test
    void laPausaProduccionEsDeMinutos() {
        // Si alguien la baja a segundos, un servicio dormido vuelve a costar la espera completa
        assertThat(AnalyzerDecorators.Resilient.PAUSE).isGreaterThanOrEqualTo(Duration.ofMinutes(1));
    }

    // ---------- the level, which this backend decides ----------

    @Test
    void unaAnomaliaNoLlegaACriticaPorGrandeQueSeaElPuntaje() {
        // El error que la primera version del servicio de Python cometio y tuvo que deshacer: en un
        // estanque de manantial una lectura un grado afuera saca siete desviaciones, y eso no es el
        // mismo grito que el sistema experto reserva para agua donde la trucha no vive
        assertThat(AiAnalysis.levelOf("anomalia", false, false)).isEqualTo("advertencia");
        assertThat(AiAnalysis.levelOf("anomalia", true, true)).isEqualTo("advertencia");
    }

    @Test
    void unaTendenciaSubeDeNivelSegunQueTanCercaEsteElLimite() {
        assertThat(AiAnalysis.levelOf("tendencia", false, false)).isEqualTo("informe");
        assertThat(AiAnalysis.levelOf("tendencia", false, true)).isEqualTo("advertencia");
        assertThat(AiAnalysis.levelOf("tendencia", true, true)).isEqualTo("critica");
    }

    @Test
    void losHallazgosDelLoteSiguenLaBanderaDelServicio() {
        assertThat(AiAnalysis.levelOf("crecimiento", false, false)).isEqualTo("advertencia");
        assertThat(AiAnalysis.levelOf("crecimiento", true, false)).isEqualTo("critica");
        assertThat(AiAnalysis.levelOf("mortalidad", true, false)).isEqualTo("critica");
    }

    @Test
    void unaCombinacionSeQuedaEnAdvertencia() {
        // Es una sospecha que vale confirmar, y su distancia no se traduce en peligro para el pez
        assertThat(AiAnalysis.levelOf("combinacion", false, false)).isEqualTo("advertencia");
    }

    @Test
    void unaClaseDesconocidaNoSeVuelveCritica() {
        // Si el servicio agrega una ruta y este backend todavia no la conoce, lo peor que puede
        // pasar es que avise flojo: nunca que grite por algo que no sabe interpretar
        assertThat(AiAnalysis.levelOf("algo_nuevo", true, true)).isEqualTo("advertencia");
    }
}
