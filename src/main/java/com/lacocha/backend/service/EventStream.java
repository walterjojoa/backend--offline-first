package com.lacocha.backend.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.lacocha.backend.dto.Ai;
import com.lacocha.backend.dto.Stream;
import com.lacocha.backend.model.Finding;

/**
 * The event handler: what lets a screen find out that something happened without asking.
 *
 * Until now the app only learned things by asking: it synced, and whatever had arrived, arrived.
 * That is fine for a caretaker who records all morning and uploads at midday, and it is useless for
 * the two cases this exists for — watching the counter climb while fry go through it, and getting a
 * finding from the AI service, which arrives a minute after the sync that caused it.
 *
 * <h2>Why SSE and not WebSocket</h2>
 *
 * Everything here goes one way: the server tells, the browser listens. The browser already has a
 * way to say things, which is the API it has been using all along. A WebSocket would add a second,
 * bidirectional channel with its own protocol, its own reconnection and its own authentication, to
 * carry traffic in a direction nothing travels in. Server-sent events are plain HTTP, go through
 * any proxy, and reconnect by themselves.
 *
 * <h2>What it does not carry</h2>
 *
 * Notifications, not data — with one exception. A count event says "batch X got 143 more", and the
 * screen then asks the API for the batch total. It would be shorter to push the total down the
 * stream and shorter still to push the whole summary, and both would be wrong: there would then be
 * two places computing the same number, and the one the caretaker reads would be the one that is
 * never checked against the database.
 *
 * The exception is a finding, which travels whole. It is small, it is self-contained, and its text
 * is stored exactly as it is sent (see {@link Finding}), so there is no second source to disagree
 * with.
 */
@Component
public class EventStream implements FindingPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventStream.class);

    /**
     * How long a connection is held before the browser is asked to come back.
     *
     * Half an hour, which is well inside what proxies tolerate, and it bounds the damage of a
     * browser that went away without saying so: half an hour later its slot is free either way.
     */
    private static final Duration LIFETIME = Duration.ofMinutes(30);

    /**
     * Something has to travel regularly or the connection looks idle and gets cut, by Render's
     * proxy or by whatever is between the lake and the server. Twenty seconds is comfortably under
     * the usual sixty-second idle limits.
     */
    private static final Duration HEARTBEAT = Duration.ofSeconds(20);

    /**
     * Cap on open connections.
     *
     * Each one holds a servlet thread's worth of resources, and the free plan has 512 MB and one
     * core. The farm has a handful of phones and a web panel, so fifty is far more than it needs
     * and still small enough that it cannot be used to exhaust the server. Past the cap the oldest
     * connection is closed rather than the new one refused: a caretaker whose phone reconnected
     * after a tunnel should not be the one locked out by their own stale connection.
     */
    private static final int MAX_LISTENERS = 50;

    /** One connected browser, and what it cares about. */
    private record Listener(SseEmitter emitter, UUID pondId, UUID batchId, long id) {

        /** Whether this event is any of this listener's business. */
        boolean wants(UUID eventPond, UUID eventBatch) {
            if (pondId == null && batchId == null) {
                return true;
            }
            return (pondId != null && pondId.equals(eventPond))
                    || (batchId != null && batchId.equals(eventBatch));
        }
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong nextId = new AtomicLong();

    /**
     * Opens a connection, optionally only for one pond or one batch.
     *
     * The filter is what makes the counting screen bearable: a phone standing at one channel does
     * not need to be woken by every reading of every other pond on the farm, and on a connection
     * paid by the megabyte that is not a detail.
     */
    public SseEmitter open(UUID pondId, UUID batchId) {
        while (listeners.size() >= MAX_LISTENERS) {
            Listener oldest = listeners.get(0);
            log.info("se alcanzó el tope de {} conexiones: se cierra la más vieja", MAX_LISTENERS);
            close(oldest);
        }

        SseEmitter emitter = new SseEmitter(LIFETIME.toMillis());
        Listener listener = new Listener(emitter, pondId, batchId, nextId.incrementAndGet());
        listeners.add(listener);

        // All three fire: completion when the browser closes, timeout when LIFETIME runs out, error
        // when the connection broke. Without removing the listener on every one of them, a phone
        // that walked into a tunnel would stay in the list forever and keep taking up a slot.
        emitter.onCompletion(() -> listeners.remove(listener));
        emitter.onTimeout(() -> listeners.remove(listener));
        emitter.onError(ex -> listeners.remove(listener));

        // Sent at once so the browser knows it is connected, instead of waiting for the first real
        // event to find out whether the connection worked
        send(listener, Stream.ready(pondId, batchId));
        return emitter;
    }

    /** Open connections. Used by the tests and by /salud. */
    public int listening() {
        return listeners.size();
    }

    // ---------- what gets published ----------

    @Override
    public void publish(Finding finding) {
        broadcast(Stream.finding(Ai.FindingResponse.from(finding)),
                finding.getPondId(), finding.getBatchId());
    }

    /**
     * Tells the screens that a sync left something new, so they go and ask for it.
     *
     * Listens to the same event as {@link AiTrigger} and knows nothing about it. This one is
     * immediate; the analysis takes as long as it takes and publishes its own findings when it is
     * done. That is why the counter climbs right away while a finding shows up a minute later.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(Changed changed) {
        if (changed.isEmpty() || listeners.isEmpty()) {
            return;
        }
        for (UUID pondId : changed.ponds()) {
            broadcast(Stream.synced(changed.deviceId(), pondId, null, changed.accepted()), pondId, null);
        }
        for (UUID batchId : changed.batches()) {
            broadcast(Stream.synced(changed.deviceId(), null, batchId, changed.accepted()), null, batchId);
        }
    }

    private void broadcast(Stream.Message message, UUID pondId, UUID batchId) {
        for (Listener listener : listeners) {
            if (listener.wants(pondId, batchId)) {
                send(listener, message);
            }
        }
    }

    /**
     * Keeps the connections from being cut for looking idle.
     *
     * The heartbeat goes out as an SSE comment, which the browser ignores: it is two bytes on the
     * wire and does not reach the application. Any listener that cannot even take a comment is
     * gone, and this is where that gets noticed — closing it here is what stops a phone that
     * disappeared from holding a slot until its half hour is up.
     */
    @Scheduled(fixedRateString = "#{T(java.time.Duration).ofSeconds(20).toMillis()}")
    public void heartbeat() {
        for (Listener listener : listeners) {
            try {
                listener.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                close(listener);
            }
        }
    }

    private void send(Listener listener, Stream.Message message) {
        try {
            listener.emitter().send(SseEmitter.event().name(message.tipo()).data(message));
        } catch (IOException | IllegalStateException ex) {
            // The browser left. Not worth a warning: it is what happens every time somebody closes
            // a tab, and the only thing to do about it is let go of the connection.
            close(listener);
        }
    }

    private void close(Listener listener) {
        listeners.remove(listener);
        try {
            listener.emitter().complete();
        } catch (RuntimeException ignored) {
            // Already broken, which is why we got here
        }
    }

    /** Lets go of every connection on shutdown, so no browser is left waiting on a dead server. */
    @jakarta.annotation.PreDestroy
    public void closeAll() {
        List.copyOf(listeners).forEach(this::close);
    }

    /** How long the heartbeat is, for the frontend to know when to give a silent connection up. */
    public static Duration heartbeatInterval() {
        return HEARTBEAT;
    }
}
