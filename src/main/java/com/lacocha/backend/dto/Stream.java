package com.lacocha.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.lacocha.backend.model.ServerClock;

/**
 * What travels down the event stream to the browser.
 *
 * One envelope for every kind, with "tipo" saying which, so the frontend has a single place to
 * decide what to do and does not need a parser per route. The names are the Spanish ones the rest
 * of the API uses.
 */
public final class Stream {

    private Stream() {
    }

    /**
     * An event on the stream.
     *
     * Empty fields are left out of the JSON: most messages fill two or three of these, and sending
     * nulls for the rest would double the size of a message that often travels over a phone
     * connection paid by the megabyte.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            /** listo | sincronizado | hallazgo */
            @JsonProperty("tipo") String tipo,
            @JsonProperty("en") Instant at,
            @JsonProperty("estanque_id") UUID pondId,
            @JsonProperty("lote_id") UUID batchId,
            @JsonProperty("dispositivo_id") String deviceId,
            /** How many events the sync accepted. The screen then asks the API for the totals. */
            @JsonProperty("aceptados") Integer accepted,
            @JsonProperty("hallazgo") Ai.FindingResponse finding) {
    }

    /**
     * Sent the moment the connection opens, so the browser knows it worked instead of having to
     * wait for the first real event to find out.
     */
    public static Message ready(UUID pondId, UUID batchId) {
        return new Message("listo", ServerClock.now(), pondId, batchId, null, null, null);
    }

    /** Something arrived for this pond or batch. Carries no totals on purpose: see EventStream. */
    public static Message synced(String deviceId, UUID pondId, UUID batchId, int accepted) {
        return new Message("sincronizado", ServerClock.now(), pondId, batchId, deviceId, accepted, null);
    }

    /** A finding travels whole: it is small and its text is stored exactly as it is sent. */
    public static Message finding(Ai.FindingResponse finding) {
        return new Message("hallazgo", ServerClock.now(), finding.pondId(), finding.batchId(),
                null, null, finding);
    }
}
