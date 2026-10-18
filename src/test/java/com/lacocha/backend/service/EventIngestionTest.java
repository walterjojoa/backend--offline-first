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

    private final EventIngestors ingestors = new EventIngestors(TROUT);

    private static final Instant SERVER_TIME = Instant.parse("2026-10-18T21:00:00Z");

    private static final String RECORDED_AT = "2026-10-18T15:30:00-05:00";

    private final UUID batchId = UUID.randomUUID();
}
