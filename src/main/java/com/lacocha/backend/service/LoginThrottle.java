package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.lacocha.backend.model.ServerClock;

/**
 * Wrong passwords per document. Without this anyone with the shared key, which sits in every
 * caretaker's Settings screen, could try passwords against a document all night.
 *
 * It counts by document and not by IP: behind Render every request arrives from the proxy, and the
 * header with the real IP is whatever the client wants it to be. The cost is that someone can lock
 * a caretaker out for a quarter of an hour by typing their document wrong on purpose, which is far
 * better than letting their password be guessed.
 *
 * In memory, because the server is a single instance: a restart forgets the counts, and that only
 * buys an attacker five more tries.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    /** Documents are not validated on sign-in, so the map must not grow with whatever gets typed. */
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public LoginThrottle() {
        this(ServerClock::now);
    }

    LoginThrottle(Supplier<Instant> clock) {
        this.clock = clock;
    }

    /** true while the document has used up its tries. Checked before the password, right or not. */
    public boolean isLocked(String document) {
        Failures f = failures.get(document);
        return f != null && f.count >= MAX_FAILURES && !expired(f);
    }

    public void failed(String document) {
        if (failures.size() >= MAX_TRACKED) {
            failures.values().removeIf(this::expired);
        }
        failures.compute(document, (d, f) -> f == null || expired(f)
                ? new Failures(1, clock.get())
                : new Failures(f.count + 1, f.since));
    }

    /** Getting in clears the count: the caretaker who mistyped twice starts over. */
    public void succeeded(String document) {
        failures.remove(document);
    }

    private boolean expired(Failures f) {
        return !clock.get().isBefore(f.since.plus(WINDOW));
    }

    private record Failures(int count, Instant since) {
    }
}
