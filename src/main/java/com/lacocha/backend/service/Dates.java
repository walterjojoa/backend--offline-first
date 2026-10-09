package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

final class Dates {

    private Dates() {
    }

    /** Colombian time: defines what "today" is for the farm, wherever the server runs. */
    static final ZoneId FARM_ZONE = ZoneId.of("America/Bogota");

    /** The project did not exist before this date: a phone or ESP32 without a configured clock usually reports 1970. */
    static final Instant MIN_DATE = Instant.parse("2024-01-01T00:00:00Z");

    /**
     * A phone with its clock ahead would pollute the history and always win catalog changes.
     * Returns the error message (in Spanish, it is shown to the caretaker) or null if the date is fine.
     */
    static String deviceDateError(OffsetDateTime date) {
        if (date == null) {
            return null;
        }
        Instant instant = date.toInstant();
        if (instant.isAfter(Instant.now().plus(Duration.ofDays(1)))) {
            return "la fecha está en el futuro: revisa el reloj del celular";
        }
        if (instant.isBefore(MIN_DATE)) {
            return "la fecha es demasiado antigua: revisa el reloj del celular";
        }
        return null;
    }
}
