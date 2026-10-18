package com.lacocha.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lacocha.backend.model.Alert;
import com.lacocha.backend.model.Event;
import com.lacocha.backend.model.FryCount;

import jakarta.validation.Validation;

/**
 * The rules of the push, tested with no database and no Spring context behind them.
 *
 * That is what the Bridge buys: EventIngestion holds an EventStore and never learns which one, so
 * the store here is a map. These checks run in milliseconds, while the same ground covered through
 * the HTTP API in ApiTest costs a full application context and an H2 schema.
 */
class EventIngestionTest {

    /** Implementor of the Bridge for the tests. It keeps the one rule the real store also applies. */
    static final class InMemoryEventStore implements EventStore {

        final Map<String, Event> stored = new LinkedHashMap<>();
        final List<Alert> alerts = new ArrayList<>();
        final Set<UUID> parents = new HashSet<>();

        /** Each kind of event has its own table, so the key is the table and the id, not the id alone. */
        private static String key(Event event) {
            return event.getClass().getSimpleName() + ":" + event.getId();
        }

        @Override
        public boolean exists(Event event) {
            return stored.containsKey(key(event));
        }

        @Override
        public void save(Event event) {
            stored.put(key(event), event);
        }

        @Override
        public boolean parentExists(boolean parentIsPond, UUID parentId) {
            return parents.contains(parentId);
        }

        @Override
        public int saveNewAlerts(List<Alert> candidates) {
            int created = 0;
            for (Alert candidate : candidates) {
                boolean alreadyPending = alerts.stream()
                        .anyMatch(a -> a.getPondId().equals(candidate.getPondId())
                                && a.getVariable().equals(candidate.getVariable())
                                && a.getLevel().equals(candidate.getLevel()));
                if (!alreadyPending) {
                    alerts.add(candidate);
                    created++;
                }
            }
            return created;
        }
    }

    /** Fixed thresholds: this test is about the pipeline, not about what is good water for a trout. */
    private static final RuleParameters TROUT = new RuleParameters(null, null, null) {
        @Override
        public Rules current() {
            return RulesTest.TROUT;
        }
    };

    /**
     * Configured like the one Spring Boot hands the application: the same naming strategy
     * application.properties sets (without it "temp_c" would not map) and unknown properties
     * ignored, because every item carries a "tipo" field that no record of Events declares.
     */
    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final InMemoryEventStore store = new InMemoryEventStore();
    private final EventIngestors ingestors = new EventIngestors(TROUT);
    private final EventIngestion ingestion = new EventIngestion(store,
            new PushJsonAdapter(mapper, Validation.buildDefaultValidatorFactory().getValidator(), ingestors),
            ingestors);

    private static final Instant SERVER_TIME = Instant.parse("2026-10-18T21:00:00Z");
    private static final String RECORDED_AT = "2026-10-18T15:30:00-05:00";

    private final UUID batchId = UUID.randomUUID();
    private final UUID pondId = UUID.randomUUID();

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private JsonNode count(UUID id, int total) {
        return json("""
                {"tipo":"conteo","id":"%s","registrado_en":"%s","origen":"contador",
                 "lote_id":"%s","total":%d,"cortes_multiples":2}
                """.formatted(id, RECORDED_AT, batchId, total));
    }

    private JsonNode reading(UUID id, double tempC) {
        return json("""
                {"tipo":"lectura_agua","id":"%s","registrado_en":"%s","origen":"sensor",
                 "estanque_id":"%s","temp_c":%s}
                """.formatted(id, RECORDED_AT, pondId, tempC));
    }

    private EventIngestion.Outcome ingest(JsonNode... items) {
        return ingestion.ingest(List.of(items), "cel-prueba", SERVER_TIME);
    }

    @Test
    void acceptsACountAndStampsWhoSentItAndWhenItArrived() {
        store.parents.add(batchId);
        UUID id = UUID.randomUUID();

        EventIngestion.Outcome outcome = ingest(count(id, 1200));

        assertThat(outcome.accepted()).containsExactly(id);
        assertThat(outcome.rejected()).isEmpty();
        FryCount saved = (FryCount) store.stored.values().iterator().next();
        assertThat(saved.getTotal()).isEqualTo(1200);
        assertThat(saved.getMultipleCuts()).isEqualTo(2);
        assertThat(saved.getSource()).isEqualTo("contador");
        assertThat(saved.getDeviceId()).isEqualTo("cel-prueba");
        assertThat(saved.getReceivedAt()).isEqualTo(SERVER_TIME);
        assertThat(saved.getRecordedAt()).isEqualTo(OffsetDateTime.parse(RECORDED_AT).toInstant());
    }

    @Test
    void theSameEventTwiceIsADuplicateAndIsStoredOnce() {
        store.parents.add(batchId);
        UUID id = UUID.randomUUID();

        assertThat(ingest(count(id, 500)).accepted()).containsExactly(id);
        EventIngestion.Outcome retry = ingest(count(id, 500));

        assertThat(retry.duplicates()).containsExactly(id);
        assertThat(retry.accepted()).isEmpty();
        assertThat(store.stored).hasSize(1);
    }

    @Test
    void aBadItemIsRejectedAloneAndTheRestOfTheQueueGoesThrough() {
        store.parents.add(batchId);
        UUID good = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        JsonNode otherBatch = json("""
                {"tipo":"conteo","id":"%s","registrado_en":"%s","lote_id":"%s","total":10}
                """.formatted(orphan, RECORDED_AT, UUID.randomUUID()));

        EventIngestion.Outcome outcome = ingest(otherBatch, count(good, 10));

        assertThat(outcome.accepted()).containsExactly(good);
        assertThat(outcome.rejected()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(orphan.toString());
            assertThat(r.error()).isEqualTo("lote_id: no existe");
        });
    }

    @Test
    void theRejectionNamesTheFieldAsTheJsonNamesIt() {
        store.parents.add(batchId);
        UUID id = UUID.randomUUID();
        JsonNode negative = json("""
                {"tipo":"conteo","id":"%s","registrado_en":"%s","lote_id":"%s","total":-5}
                """.formatted(id, RECORDED_AT, batchId));

        assertThat(ingest(negative).rejected()).singleElement()
                .satisfies(r -> assertThat(r.error()).startsWith("total:"));
    }

    @Test
    void anUnknownTipoListsTheOnesTheServerAccepts() {
        JsonNode unknown = json("""
                {"tipo":"caudal","id":"%s","registrado_en":"%s"}
                """.formatted(UUID.randomUUID(), RECORDED_AT));

        assertThat(ingest(unknown).rejected()).singleElement()
                .satisfies(r -> assertThat(r.error())
                        .isEqualTo("tipo: debe ser uno de lectura_agua, conteo, mortalidad, alimentacion, biometria"));
    }

    @Test
    void withoutOrigenTheEventIsStoredAsManual() {
        store.parents.add(batchId);
        JsonNode noSource = json("""
                {"tipo":"conteo","id":"%s","registrado_en":"%s","lote_id":"%s","total":7}
                """.formatted(UUID.randomUUID(), RECORDED_AT, batchId));

        ingest(noSource);

        assertThat(store.stored.values()).singleElement()
                .satisfies(e -> assertThat(e.getSource()).isEqualTo("manual"));
    }

    @Test
    void aReadingOutOfRangeRaisesAnAlertAndAnIdenticalOneDoesNotRepeatIt() {
        store.parents.add(pondId);

        EventIngestion.Outcome first = ingest(reading(UUID.randomUUID(), 22.0));
        assertThat(first.alertsCreated()).isEqualTo(1);
        assertThat(store.alerts).singleElement()
                .satisfies(a -> assertThat(a.getVariable()).isEqualTo("temp_c"));

        // The sensor measures every few seconds: repeating the alert would bury the pending one
        EventIngestion.Outcome second = ingest(reading(UUID.randomUUID(), 22.5));
        assertThat(second.accepted()).hasSize(1);
        assertThat(second.alertsCreated()).isZero();
        assertThat(store.alerts).hasSize(1);
    }

    @Test
    void aCountNeverRaisesAlerts() {
        store.parents.add(batchId);

        EventIngestion.Outcome outcome = ingest(count(UUID.randomUUID(), 999_999));

        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.alertsCreated()).isZero();
        assertThat(store.alerts).isEmpty();
    }
}
