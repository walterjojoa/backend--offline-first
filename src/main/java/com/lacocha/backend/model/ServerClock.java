package com.lacocha.backend.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Server time truncated to microseconds, which is what PostgreSQL stores.
 * This way a freshly created value is identical to the one read back from the database,
 * and the /api/sync/pull cursor never ends up "before" a row because of rounding.
 */
public final class ServerClock {

    private ServerClock() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
