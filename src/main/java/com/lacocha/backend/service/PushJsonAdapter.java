package com.lacocha.backend.service;

import java.util.Comparator;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.lacocha.backend.dto.Events.Input;
import com.lacocha.backend.dto.JsonNames;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Adapter between what a device puts on the wire and the domain objects of the system.
 *
 * The phone and the ESP32 node upload untyped JSON: a frame of the fry counter arrives as an object
 * whose "tipo" field is the only clue of what it is. This is the single place that turns one raw
 * item into a typed record with its rules already checked, and that names the field that failed as
 * it is named in the JSON and not as it is named in Java, because the caretaker reads that message.
 *
 * It is the server-side twin of the app's ArduinoSerialAdapter: the same job, one hop later.
 */
@Component
public class PushJsonAdapter {
    /** A pond, batch or event that cannot be stored. It is rejected alone; the rest of the push goes on. */
    public static class InvalidItem extends Exception {
        public InvalidItem(String message) {
            super(message);
        }
    }

    private final ObjectMapper mapper;

    private final Validator validator;

    private final EventIngestors ingestors;

    public PushJsonAdapter(ObjectMapper mapper, Validator validator, EventIngestors ingestors) {
        this.mapper = mapper;
        this.validator = validator;
        this.ingestors = ingestors;
    }

    /** The id of an item that could not even be parsed, so the phone can still match the rejection. */
    public static String rawId(JsonNode raw) {
        return raw != null && raw.hasNonNull("id") ? raw.get("id").asText() : null;
    }

    /** Jackson already reports the path with the JSON names. */
    private static String fieldWithError(JsonProcessingException ex) {
        if (ex instanceof MismatchedInputException m && !m.getPath().isEmpty()) {
            String field = m.getPath().get(m.getPath().size() - 1).getFieldName();
            if (field != null) {
                return field;
            }
        }
        return "dato";
    }

    /** JSON -> record, plus its annotations (@NotNull, @Size...). Ponds and batches come through here too. */
    public <T> T read(JsonNode raw, Class<T> type) throws InvalidItem {
        if (raw == null || !raw.isObject()) {
            throw new InvalidItem("debe ser un objeto JSON");
        }
        T value;
        try {
            value = mapper.treeToValue(raw, type);
        } catch (JsonProcessingException ex) {
            throw new InvalidItem(fieldWithError(ex) + ": formato no válido");
        }

        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> v = violations.stream()
                    .min(Comparator.comparing(x -> x.getPropertyPath().toString()))
                    .get();
            throw new InvalidItem(JsonNames.of(type, v.getPropertyPath().toString()) + ": " + v.getMessage());
        }
        return value;
    }
}
