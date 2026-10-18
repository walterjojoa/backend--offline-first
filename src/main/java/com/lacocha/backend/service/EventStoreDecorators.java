package com.lacocha.backend.service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lacocha.backend.model.Alert;
import com.lacocha.backend.model.Event;

/**
 * Cross-cutting capabilities of the event store, added from the outside and stackable. Neither the
 * ingestion pipeline nor the JPA store knows they are there, and turning one off is not wrapping it.
 */
public final class EventStoreDecorators {
    private EventStoreDecorators() {
    }

    /** Base: an EventStore that wraps another one and forwards everything untouched. */
    public abstract static class Decorator implements EventStore {

        protected final EventStore inner;

        protected Decorator(EventStore inner) {
            this.inner = inner;
        }

        @Override
        public boolean exists(Event event) {
            return inner.exists(event);
        }

        @Override
        public void save(Event event) {
            inner.save(event);
        }

        @Override
        public boolean parentExists(boolean parentIsPond, UUID parentId) {
            return inner.parentExists(parentIsPond, parentId);
        }

        @Override
        public int saveNewAlerts(List<Alert> alerts) {
            return inner.saveNewAlerts(alerts);
        }
    }
}
