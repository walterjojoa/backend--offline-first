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
}
