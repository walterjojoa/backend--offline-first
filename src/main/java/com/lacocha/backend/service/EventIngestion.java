package com.lacocha.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.lacocha.backend.dto.Events.Input;
import com.lacocha.backend.dto.Sync.Rejection;
import com.lacocha.backend.model.Event;
import com.lacocha.backend.service.EventStoreDecorators.SourceTally;
import com.lacocha.backend.service.EventStoreDecorators.Trace;
import com.lacocha.backend.service.PushJsonAdapter.InvalidItem;

/**
 * Abstraction side of the Bridge: the rules that decide what the server does with the events of a
 * push, written once and holding an EventStore whose kind it never learns.
 *
 * An event is accepted when its parent exists and its id was never seen. A repeat comes back as a
 * duplicate and not as an error, because the phone resends whatever it is not sure arrived. A bad
 * one is rejected alone, so a single unreadable item cannot block the rest of the queue.
 */
@Component
public class EventIngestion {

    private static final Logger log = LoggerFactory.getLogger(EventIngestion.class);

    /** What the events of one push produced. */
    public record Outcome(List<UUID> accepted, List<UUID> duplicates, List<Rejection> rejected, int alertsCreated) {
    }

    private final EventStore store;
    private final PushJsonAdapter adapter;
    private final EventIngestors ingestors;

    public EventIngestion(EventStore store, PushJsonAdapter adapter, EventIngestors ingestors) {
        this.store = store;
        this.adapter = adapter;
        this.ingestors = ingestors;
    }

    public Outcome ingest(List<JsonNode> items, String deviceId, Instant serverTime) {
        List<UUID> accepted = new ArrayList<>();
        List<UUID> duplicates = new ArrayList<>();
        List<Rejection> rejected = new ArrayList<>();
        int alertsCreated = 0;

        // The decorators live for this push only: a tally that counted two pushes would say nothing
        SourceTally tally = new SourceTally(Trace.enabled() ? new Trace(store) : store);
        // A push usually carries many events of the same batch; asking once is enough
        Set<UUID> knownParents = new HashSet<>();

        for (JsonNode item : items) {
            Input event;
            try {
                event = adapter.readEvent(item);
            } catch (InvalidItem ex) {
                rejected.add(new Rejection(PushJsonAdapter.rawId(item), ex.getMessage()));
                continue;
            }

            if (!knownParents.contains(event.parentId())
                    && !tally.parentExists(event.parentIsPond(), event.parentId())) {
                rejected.add(new Rejection(event.id().toString(),
                        (event.parentIsPond() ? "estanque_id" : "lote_id") + ": no existe"));
                continue;
            }
            knownParents.add(event.parentId());

            Event entity = stamp(event, deviceId, serverTime);
            if (tally.exists(entity)) {
                duplicates.add(event.id());
                continue;
            }
            tally.save(entity);
            accepted.add(event.id());
            alertsCreated += ingestors.forEvent(event).afterStore(event, tally);
        }

        if (!accepted.isEmpty()) {
            log.info("events from {}: {}", deviceId, tally.summary());
        }
        return new Outcome(accepted, duplicates, rejected, alertsCreated);
    }

    /** The id comes from the phone (that is what makes the push idempotent); the rest is the server's. */
    private static Event stamp(Input event, String deviceId, Instant serverTime) {
        Event entity = event.toEntity();
        entity.setId(event.id());
        entity.setDeviceId(deviceId);
        entity.setSource(event.source() != null ? event.source() : "manual");
        entity.setRecordedAt(event.recordedAt().toInstant());
        entity.setReceivedAt(serverTime);
        return entity;
    }
}
