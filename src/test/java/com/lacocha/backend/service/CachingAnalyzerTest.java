package com.lacocha.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.lacocha.backend.dto.Ai;

/**
 * Tests of the Proxy, without a database and without the AI service.
 *
 * What is checked here is what the pattern promises: that the same question asked twice reaches
 * the service once, that the answer stops being believed when it should, and — the part that is
 * easy to get wrong — that caching never makes the system say something that is not true.
 */
class CachingAnalyzerTest {

    /** The real subject, counting how many times it was actually reached. */
    private static final class Counting implements Analyzer {

        boolean up = true;
        int healthCalls;
        int projectionCalls;
        int waterCalls;
        double projectedWeight = 50.6;

        @Override
        public boolean available() {
            healthCalls++;
            return up;
        }

        @Override
        public Optional<Ai.ProjectionJson> projection(Ai.BatchRequest request) {
            projectionCalls++;
            if (!up) {
                return Optional.empty();
            }
            return Optional.of(new Ai.ProjectionJson(30, 1.78, true, 12.4, 28.0, projectedWeight,
                    148, LocalDate.of(2027, 3, 24), 9300, 9270, 93.0, 0.5, List.of(), List.of()));
        }

        @Override
        public Optional<List<Ai.AnomalyJson>> anomalies(Ai.ReadingsRequest request) {
            waterCalls++;
            return up ? Optional.of(List.of()) : Optional.empty();
        }

        @Override
        public Optional<List<Ai.TrendJson>> trends(Ai.TrendRequest request) {
            waterCalls++;
            return up ? Optional.of(List.of()) : Optional.empty();
        }

        @Override
        public Optional<Optional<Ai.CombinationJson>> combination(Ai.ReadingsRequest request) {
            waterCalls++;
            return up ? Optional.of(Optional.empty()) : Optional.empty();
        }
    }

    private static final Instant CUANDO = Instant.parse("2026-11-10T12:00:00Z");

    private static Ai.BatchRequest lote(int muertes) {
        return new Ai.BatchRequest(LocalDate.of(2026, 6, 1), 10000, 5.0,
                List.of(new Ai.WeighingJson(CUANDO, 28.0, 30)),
                List.of(new Ai.DeathJson(CUANDO, muertes)),
                List.of(new Ai.ReadingJson(CUANDO, 12.4, 7.1, 8.6)),
                30, 250.0);
    }

    private static Ai.BatchRequest loteConLecturas(int cuantas) {
        return new Ai.BatchRequest(LocalDate.of(2026, 6, 1), 10000, 5.0,
                List.of(new Ai.WeighingJson(CUANDO, 28.0, 30)),
                List.of(new Ai.DeathJson(CUANDO, 300)),
                java.util.stream.IntStream.range(0, cuantas)
                        .mapToObj(i -> new Ai.ReadingJson(CUANDO.plusSeconds(i), 12.4, 7.1, 8.6))
                        .toList(),
                30, 250.0);
    }

    // ---------- la salud, que se pregunta en cada sincronizacion ----------

    @Test
    void preguntarLaSaludDosVecesSeguidasLlegaUnaVezAlServicio() {
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real);

        assertThat(proxy.available()).isTrue();
        assertThat(proxy.available()).isTrue();
        assertThat(proxy.available()).isTrue();

        assertThat(real.healthCalls).as("tres preguntas, una llamada").isEqualTo(1);
    }

    @Test
    void laSaludSeVuelveAPreguntarCuandoVence() throws InterruptedException {
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real, Duration.ofMillis(100),
                Duration.ofMinutes(2));

        proxy.available();
        Thread.sleep(160);
        proxy.available();

        // Una caché que nunca vence es un servicio caído que nadie nota, o uno que volvió y nadie usa
        assertThat(real.healthCalls).isEqualTo(2);
    }

    @Test
    void unServicioCaidoTambienSeRecuerda() {
        Counting real = new Counting();
        real.up = false;
        CachingAnalyzer proxy = new CachingAnalyzer(real);

        assertThat(proxy.available()).isFalse();
        assertThat(proxy.available()).isFalse();
        // Recordar el "no" es justamente lo que evita que cada pantalla despierte al servicio
        assertThat(real.healthCalls).isEqualTo(1);
    }

    // ---------- la proyeccion, que se pide al abrir el lote ----------

    @Test
    void abrirDosVecesElMismoLoteLlegaUnaVezAlServicio() {
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real);

        Optional<Ai.ProjectionJson> primera = proxy.projection(lote(300));
        Optional<Ai.ProjectionJson> segunda = proxy.projection(lote(300));

        assertThat(primera).isPresent();
        assertThat(segunda).isEqualTo(primera);
        assertThat(real.projectionCalls).isEqualTo(1);
    }

    @Test
    void registrarUnaMortalidadNuevaNoSirveLaRespuestaVieja() {
        // Lo que no puede pasar nunca: que el cuidador registre una muerte y la pantalla siga
        // mostrando la supervivencia de antes
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real);

        proxy.projection(lote(300));
        proxy.projection(lote(360));

        assertThat(real.projectionCalls).as("los datos cambiaron: hay que volver a preguntar")
                .isEqualTo(2);
    }

    @Test
    void unaLecturaDeAguaMasNoTiraLaRespuestaGuardada() {
        /*
         * La decisión de diseño de esta clase, puesta a prueba.
         *
         * El sensor mide cada pocos segundos. Si las lecturas entraran en la llave, habría un fallo
         * de caché cada vez que llega una, y la caché no serviría de nada. Quedan fuera porque el
         * modelo usa su mediana entre cientos: una más no mueve la proyección.
         */
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real);

        proxy.projection(loteConLecturas(400));
        proxy.projection(loteConLecturas(401));

        assertThat(real.projectionCalls).isEqualTo(1);
    }

    @Test
    void laProyeccionSeVuelveAPedirCuandoVence() throws InterruptedException {
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real, Duration.ofMinutes(1),
                Duration.ofMillis(100));

        proxy.projection(lote(300));
        Thread.sleep(160);
        proxy.projection(lote(300));

        assertThat(real.projectionCalls).isEqualTo(2);
    }

    @Test
    void noSeGuardaLoQueNoSePudoPreguntar() {
        // Guardar un "no pude" convertiría un minuto malo en dos. De eso se encarga el
        // cortacircuitos, que sabe cuándo volver a intentar.
        Counting real = new Counting();
        real.up = false;
        CachingAnalyzer proxy = new CachingAnalyzer(real);

        assertThat(proxy.projection(lote(300))).isEmpty();
        assertThat(proxy.projection(lote(300))).isEmpty();
        assertThat(real.projectionCalls).isEqualTo(2);
    }

    @Test
    void loQueSeGuardoSigueSirviendoCuandoElServicioSeCae() {
        /*
         * Por qué el Proxy va por fuera del cortacircuitos. Si fuera al revés, lo guardado sería
         * inalcanzable justo en la única situación para la que se guardó.
         */
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real);

        Optional<Ai.ProjectionJson> buena = proxy.projection(lote(300));
        real.up = false;

        assertThat(proxy.projection(lote(300))).isEqualTo(buena);
        assertThat(real.projectionCalls).as("ni siquiera se intentó").isEqualTo(1);
    }

    // ---------- lo que no se guarda, y es a proposito ----------

    @Test
    void elAnalisisDelAguaNuncaSeGuarda() {
        /*
         * Las tres rutas del agua se llaman justo después de que una sincronización guardó lecturas
         * nuevas, o sea justo después de que la respuesta cambió. Guardarlas devolvería el veredicto
         * sobre datos que ya no existen: un cuidador leyendo "todo normal" sobre lecturas que nadie
         * miró. Es el fallo que este proyecto no se puede permitir.
         */
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real);
        Ai.ReadingsRequest lecturas = new Ai.ReadingsRequest(List.of());

        proxy.anomalies(lecturas);
        proxy.anomalies(lecturas);
        proxy.trends(new Ai.TrendRequest(List.of(), List.of()));
        proxy.combination(lecturas);

        assertThat(real.waterCalls).as("las cuatro llegaron al servicio").isEqualTo(4);
    }

    // ---------- que no se vuelva una fuga de memoria ----------

    @Test
    void noSeGuardanMasLotesDeLaCuenta() {
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real);

        for (int i = 0; i < CachingAnalyzer.MAX_BATCHES + 20; i++) {
            proxy.projection(lote(i));
        }
        assertThat(proxy.remembered()).isEqualTo(CachingAnalyzer.MAX_BATCHES);
    }

    @Test
    void olvidarDejaTodoLimpio() {
        Counting real = new Counting();
        CachingAnalyzer proxy = new CachingAnalyzer(real);
        proxy.available();
        proxy.projection(lote(300));

        proxy.forget();

        assertThat(proxy.remembered()).isZero();
        proxy.available();
        assertThat(real.healthCalls).isEqualTo(2);
    }

    // ---------- la interfaz comun, que es lo que lo vuelve un Proxy ----------

    @Test
    void quienLlamaNoDistingueElProxyDelServicio() {
        /*
         * La cuarta ventaja del patrón: interfaz común. AiAnalysis recibe un Analyzer y no tiene
         * forma de saber si habló con el servicio, con la caché, con el cortacircuitos o con el
         * analizador ausente. Por eso se pueden apilar en cualquier combinación sin tocarlo.
         */
        Analyzer apilado = new AnalyzerDecorators.Trace(
                new CachingAnalyzer(new AnalyzerDecorators.Resilient(new Counting())));

        assertThat(apilado.available()).isTrue();
        assertThat(apilado.projection(lote(300))).isPresent();
        assertThat(apilado.anomalies(new Ai.ReadingsRequest(List.of()))).isPresent();
    }
}
