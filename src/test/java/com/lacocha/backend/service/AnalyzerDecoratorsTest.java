package com.lacocha.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lacocha.backend.dto.Ai;

/**
 * Tests of the circuit breaker, without a database and without the Python service.
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
        public Optional<Ai.WaterAnalysis> analyzeWater(Ai.WaterRequest request) {
            calls++;
            return works
                    ? Optional.of(new Ai.WaterAnalysis(request.pondId(), 30, 10.0,
                            List.of("desviacion"), true, List.of()))
                    : Optional.empty();
        }

        @Override
        public Optional<Ai.BatchProjection> projectBatch(Ai.BatchRequest request) {
            calls++;
            return Optional.empty();
        }
    }

    private static Ai.WaterRequest anyRequest() {
        return new Ai.WaterRequest(UUID.randomUUID(), List.of(), List.of());
    }

    // ---------- the analyzer that is not there ----------

    @Test
    void elAnalizadorAusenteNoRompeNada() {
        Analyzer absent = Analyzer.absent();
        assertThat(absent.available()).isFalse();
        assertThat(absent.analyzeWater(anyRequest())).isEmpty();
        assertThat(absent.projectBatch(new Ai.BatchRequest(UUID.randomUUID(), null, null, null,
                List.of(), List.of(), List.of(), 30, null))).isEmpty();
    }

    // ---------- the door closing ----------

    @Test
    void despuesDeUnFalloNoSeVuelveAPreguntar() {
        Counting inner = new Counting(false);
        AnalyzerDecorators.Resilient resilient =
                new AnalyzerDecorators.Resilient(inner, Duration.ofMinutes(5));

        assertThat(resilient.analyzeWater(anyRequest())).isEmpty();
        assertThat(inner.calls).isEqualTo(1);
        assertThat(resilient.closed()).isTrue();

        // Three more rounds of ponds: ninguna llega al servicio
        resilient.analyzeWater(anyRequest());
        resilient.analyzeWater(anyRequest());
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

        resilient.analyzeWater(anyRequest());
        assertThat(inner.calls).isEqualTo(1);
        assertThat(resilient.closed()).isTrue();

        Thread.sleep(200);
        assertThat(resilient.closed()).isFalse();

        inner.works = true;
        assertThat(resilient.analyzeWater(anyRequest())).isPresent();
        assertThat(inner.calls).isEqualTo(2);
    }

    @Test
    void unaRespuestaBuenaDejaLaPuertaAbierta() throws InterruptedException {
        Counting inner = new Counting(false);
        AnalyzerDecorators.Resilient resilient =
                new AnalyzerDecorators.Resilient(inner, Duration.ofMillis(120));

        resilient.analyzeWater(anyRequest());
        Thread.sleep(200);
        inner.works = true;
        resilient.analyzeWater(anyRequest());

        assertThat(resilient.closed()).as("tras responder bien no queda ninguna pausa pendiente")
                .isFalse();
        resilient.analyzeWater(anyRequest());
        resilient.analyzeWater(anyRequest());
        assertThat(inner.calls).isEqualTo(4);
    }

    @Test
    void mientrasFuncionaNoEstorba() {
        Counting inner = new Counting(true);
        AnalyzerDecorators.Resilient resilient = new AnalyzerDecorators.Resilient(inner);

        for (int i = 0; i < 5; i++) {
            assertThat(resilient.analyzeWater(anyRequest())).isPresent();
        }
        assertThat(inner.calls).isEqualTo(5);
        assertThat(resilient.closed()).isFalse();
    }

    // ---------- stacking them ----------

    @Test
    void elTrazoNoCambiaLaRespuesta() {
        Counting inner = new Counting(true);
        Analyzer stacked = new AnalyzerDecorators.Trace(new AnalyzerDecorators.Resilient(inner));

        assertThat(stacked.analyzeWater(anyRequest())).isPresent();
        assertThat(stacked.available()).isTrue();
        assertThat(inner.calls).isEqualTo(2);
    }

    @Test
    void laPausaProduccionEsDeMinutos() {
        // Si alguien la baja a segundos, un servicio dormido vuelve a costar la espera completa
        assertThat(AnalyzerDecorators.Resilient.PAUSE).isGreaterThanOrEqualTo(Duration.ofMinutes(1));
    }
}
