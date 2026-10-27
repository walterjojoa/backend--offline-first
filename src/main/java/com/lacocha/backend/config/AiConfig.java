package com.lacocha.backend.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.lacocha.backend.service.Analyzer;
import com.lacocha.backend.service.AnalyzerDecorators;
import com.lacocha.backend.service.RestAnalyzer;

/**
 * Wires the AI service, and decides what happens when it is not there.
 *
 * Without IA_URL the whole thing falls back to {@link Analyzer#absent()} and the farm runs exactly
 * as it did before: the expert system keeps generating its threshold alerts and nothing waits on
 * anything. That is the point of the AI being a separate service — it adds, it does not hold up.
 */
@Configuration
@EnableAsync
// El latido del manejador de eventos, que es lo que impide que un proxy corte una conexion
// abierta por verla quieta (ver EventStream.heartbeat)
@org.springframework.scheduling.annotation.EnableScheduling
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * The analyzer, wrapped from the inside out: the HTTP call, then the circuit breaker, then the
     * trace if anyone is reading it.
     *
     * The order matters. Resilient has to sit outside RestAnalyzer so that a closed door skips the
     * call rather than making it and swallowing the result, which is the whole point of having it.
     */
    @Bean
    public Analyzer analyzer(LaCochaProperties props) {
        if (props.iaUrl() == null || props.iaUrl().isBlank()) {
            log.info("Sin IA_URL: el backend trabaja solo con el sistema experto de umbrales");
            return Analyzer.absent();
        }
        log.info("Servicio de IA en {}", props.iaUrl());
        Analyzer analyzer = new AnalyzerDecorators.Resilient(
                new RestAnalyzer(props.iaUrl().trim(), props.iaApiKey()));
        return AnalyzerDecorators.Trace.enabled() ? new AnalyzerDecorators.Trace(analyzer) : analyzer;
    }

    /**
     * The threads the analysis runs on, after the push already answered.
     *
     * Small on purpose. Render's free plan gives 512 MB and one core, and this work is optional:
     * what cannot be allowed is for it to take memory or CPU away from storing what the phones
     * upload, which is the part that cannot be repeated.
     *
     * When the queue fills up the task is discarded with a log line, and not run on the calling
     * thread. CallerRunsPolicy would be the usual choice and here it would be wrong: the caller is
     * the thread finishing a push, and making it wait out a minute of AI service is exactly what
     * all of this exists to avoid. A skipped analysis costs nothing — the next push analyses the
     * same window again, with the same readings in it.
     */
    @Bean("aiExecutor")
    public TaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ia-");
        executor.setRejectedExecutionHandler(new DiscardAndSay());
        executor.initialize();
        return executor;
    }

    /** Throws away the analysis and says so, instead of making the push wait for it. */
    static final class DiscardAndSay implements java.util.concurrent.RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            log.warn("la cola del análisis está llena: se omite este. "
                    + "La próxima sincronización vuelve a mirar la misma ventana.");
        }
    }
}
