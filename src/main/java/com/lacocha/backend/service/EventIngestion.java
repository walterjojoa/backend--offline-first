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

    private static final Logger log = LoggerFactory.getLogger(EventIngestion.class);
}
