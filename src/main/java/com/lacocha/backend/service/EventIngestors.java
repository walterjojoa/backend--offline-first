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
}
