package com.lacocha.backend.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.lacocha.backend.model.Finding;

/**
 * What makes the analysis happen, and the one place that decides when.
 *
 * A push stores readings, counts and mortalities and answers the phone. It does not analyse
 * anything. Instead it says what changed, and this listener picks that up <em>after the transaction
 * commits</em> and on another thread.
 *
 * <h2>Why after commit, and not at the end of the push</h2>
 *
 * Two reasons, both of them hard:
 *
 * <ul>
 *   <li>The AI service lives on Render's free plan, goes to sleep when unused, and takes close to a
 *       minute to wake up. The caretaker who finally found signal at the lake has a queue of a
 *       whole day to upload; making them wait a minute for a projection would be absurd, and if
 *       their signal dropped during that minute they would lose the upload too.</li>
 *   <li>Rolling back has to roll back everything. If the analysis ran inside the transaction and
 *       the push failed afterwards, findings would have been raised about readings that were never
 *       stored, and nobody could work out where they came from.</li>
 * </ul>
 *
 * So: commit first, answer the phone, then analyse. If the analysis never happens because the
 * service is down, nothing is lost — the readings are stored and the expert system already did its
 * part. The next push analyses the same window again, including them.
 *
 * <h2>It can be switched off</h2>
 *
 * {@code lacocha.ia-analisis-automatico=false} leaves the AI service in place and stops it being
 * asked on every sync. Two uses: a farm that would rather look at a pond when it decides to and
 * not have the service woken by every upload, and the tests, where an analysis running on its own
 * on another thread races with the one the test is asking for and makes the result depend on which
 * got there first.
 */
@Component
@ConditionalOnProperty(name = "lacocha.ia-analisis-automatico", matchIfMissing = true)
public class AiTrigger {

    private static final Logger log = LoggerFactory.getLogger(AiTrigger.class);

    private final AiAnalysis analysis;
    private final FindingPublisher publisher;

    public AiTrigger(AiAnalysis analysis, FindingPublisher publisher) {
        this.analysis = analysis;
        this.publisher = publisher;
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(Changed changed) {
        if (changed.isEmpty() || !analysis.available()) {
            return;
        }
        Set<Finding> all = new LinkedHashSet<>();
        for (UUID pondId : changed.ponds()) {
            all.addAll(guard("estanque " + pondId, () -> analysis.analyzePond(pondId)));
        }
        for (UUID batchId : changed.batches()) {
            all.addAll(guard("lote " + batchId, () -> analysis.analyzeBatch(batchId)));
        }
        // Published so the screen gets them on its own, without anybody pressing anything
        all.forEach(publisher::publish);
    }

    /**
     * One pond failing must not take the rest of the round down with it.
     *
     * This runs on its own thread after the response already went out, so an exception escaping
     * here would end up as a stack trace in the log with nobody to tell. Catching it per pond means
     * a batch with inconsistent data does not cost the other seven ponds their analysis.
     */
    private java.util.List<Finding> guard(String what, java.util.function.Supplier<java.util.List<Finding>> work) {
        try {
            return work.get();
        } catch (RuntimeException ex) {
            log.warn("no se pudo analizar el {}: {}", what, ex.getMessage());
            return java.util.List.of();
        }
    }
}
