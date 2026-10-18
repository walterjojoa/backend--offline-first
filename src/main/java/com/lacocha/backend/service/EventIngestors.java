package com.lacocha.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.lacocha.backend.dto.Events.BiometryInput;
import com.lacocha.backend.dto.Events.FeedingInput;
import com.lacocha.backend.dto.Events.FryCountInput;
import com.lacocha.backend.dto.Events.Input;
import com.lacocha.backend.dto.Events.MortalityInput;
import com.lacocha.backend.dto.Events.WaterReadingInput;
import com.lacocha.backend.model.Alert;

/**
 * The kinds of event the server accepts, and what each one triggers once it is stored.
 *
 * This is the only list of the wire tags: the adapter asks here which record to parse a raw item
 * into, and the pipeline asks here what to do with it afterwards.
 */
@Component
public class EventIngestors {
    /** What a kind of event triggers once it is stored. Returns how many alerts it created. */
    public interface Ingestor {
        int afterStore(Input event, EventStore store);
    }

    /**
     * Factory Method: each subclass answers for one "tipo" of the protocol and decides which
     * ingestor handles it. Accepting a new kind of event is adding a subclass and registering it
     * below; the adapter, the pipeline and the controllers do not change.
     */
    public abstract static class Factory {

        private Ingestor cached;

        /** The tag the device puts in the JSON "tipo" field. */
        public abstract String tipo();

        /** The record the adapter has to parse the raw item into. */
        public abstract Class<? extends Input> inputType();

        /** The factory method. */
        protected abstract Ingestor createIngestor();

        /** Created once: an ingestor holds no state and a single push can carry 500 events. */
        public final Ingestor ingestor() {
            if (cached == null) {
                cached = createIngestor();
            }
            return cached;
        }
    }

    /** Events that are only stored: a count, a mortality, a feeding, a biometry. */
    public static final class Plain extends Factory {

        private final String tipo;
        private final Class<? extends Input> inputType;

        public Plain(String tipo, Class<? extends Input> inputType) {
            this.tipo = tipo;
            this.inputType = inputType;
        }

        @Override
        public String tipo() {
            return tipo;
        }

        @Override
        public Class<? extends Input> inputType() {
            return inputType;
        }

        @Override
        protected Ingestor createIngestor() {
            return (event, store) -> 0;
        }
    }

    /**
     * A water reading also runs the expert system. The alert has to be raised in the same
     * transaction that stored the reading: otherwise a push that rolls back would leave an alert
     * pointing at a reading nobody has.
     */
    public static final class WaterReadings extends Factory {

        private final RuleParameters rules;

        public WaterReadings(RuleParameters rules) {
            this.rules = rules;
        }

        @Override
        public String tipo() {
            return "lectura_agua";
        }

        @Override
        public Class<? extends Input> inputType() {
            return WaterReadingInput.class;
        }

        @Override
        protected Ingestor createIngestor() {
            return this::raiseAlerts;
        }

        private int raiseAlerts(Input event, EventStore store) {
            WaterReadingInput reading = (WaterReadingInput) event;
            List<Alert> candidates = new ArrayList<>();
            for (Rules.Result rule : rules.current().evaluateReading(reading.tempC(), reading.ph(),
                    reading.oxygenMgL())) {
                Alert alert = new Alert();
                alert.setPondId(reading.pondId());
                alert.setReadingId(reading.id());
                alert.setVariable(rule.variable());
                alert.setValue(rule.value());
                alert.setLevel(rule.level());
                alert.setMessage(rule.message());
                alert.setMeasuredAt(reading.recordedAt().toInstant());
                candidates.add(alert);
            }
            // The store is the one that knows which of these are already pending
            return store.saveNewAlerts(candidates);
        }
    }
}
