package com.lacocha.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.lacocha.backend.config.LaCochaProperties;
import com.lacocha.backend.dto.Auth.LoginRequest;
import com.lacocha.backend.dto.Auth.RegisterRequest;
import com.lacocha.backend.dto.Auth.SessionResponse;
import com.lacocha.backend.dto.Auth.UserResponse;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.model.Session;
import com.lacocha.backend.model.User;
import com.lacocha.backend.repository.DeviceRepository;
import com.lacocha.backend.repository.SessionRepository;
import com.lacocha.backend.repository.UserRepository;

/**
 * Who is using the app. Until now every phone sent the same shared key, which could not say who
 * did what and could not be taken away from one person without changing it for the whole farm.
 *
 * The session lasts a long time on purpose: the app works without signal, and forcing a sign-in
 * every week would lock the caretaker out exactly at the lagoon. What protects a lost phone is the
 * PIN the app keeps locally plus closing the session here.
 */
@Service
public class AuthService {

    /** Six months. Long enough for a season of stocking; short enough that an old phone drops off. */
    private static final Duration SESSION_LIFE = Duration.ofDays(180);

    private static final String BAD_CREDENTIALS = "El documento o la clave no son correctos";

    private final UserRepository users;
    private final SessionRepository sessions;
    private final DeviceRepository devices;
    private final LaCochaProperties props;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, SessionRepository sessions, DeviceRepository devices,
            LaCochaProperties props) {
        this.users = users;
        this.sessions = sessions;
        this.devices = devices;
        this.props = props;
    }

    @Transactional
    public SessionResponse register(RegisterRequest data) {
        String document = data.document().trim();
        if (users.existsByDocument(document)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay alguien registrado con ese documento");
        }
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDocument(document);
        user.setName(data.name().trim());
        user.setPasswordHash(encoder.encode(data.password()));
        user.setRole(isProducer(document) ? "productor" : "cuidador");
        user.setActive(true);
        user.setCreatedAt(ServerClock.now());
        users.save(user);
        // Signing in right after registering saves the caretaker from typing it all twice
        return openSession(user, data.deviceId());
    }

    @Transactional
    public SessionResponse login(LoginRequest data) {
        User user = users.findByDocument(data.document().trim()).orElse(null);
        // The same message whether the document does not exist or the password is wrong: telling
        // them apart would let anyone find out which documents are registered
        if (user == null || !encoder.matches(data.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta cuenta está dada de baja");
        }
        user.setLastLoginAt(ServerClock.now());
        return openSession(user, data.deviceId());
    }

    /**
     * Whoever was named in the configuration is a producer, and so is the first person to register
     * when nobody was named: a farm that just installed the server has to be able to administer it.
     */
    private boolean isProducer(String document) {
        List<String> named = props.producers();
        return named.isEmpty() ? users.count() == 0 : named.contains(document);
    }

    /**
     * The user behind the token, or 401. Every operation that is not signing in goes through here
     * when it needs to know who is asking.
     */
    @Transactional(readOnly = true)
    public User require(String token) {
        return userOf(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión no es válida"));
    }

    /**
     * The same, but only for the producer. The shared key is not enough for these: it sits in plain
     * sight in every caretaker's Settings screen, so it cannot be what guards the farm's admin.
     */
    @Transactional(readOnly = true)
    public User requireProducer(String token) {
        User user = require(token);
        if (!user.isProducer()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esto solo lo puede hacer el productor");
        }
        return user;
    }

    /** Closes the session of the token that came with the request. Repeating it is not an error. */
    @Transactional
    public void logout(String token) {
        currentSession(token).ifPresent(session -> session.setClosedAt(ServerClock.now()));
    }

    /** The user behind a token, or empty when there is none, it expired or it was closed. */
    @Transactional(readOnly = true)
    public Optional<User> userOf(String token) {
        return currentSession(token).map(Session::getUserId).flatMap(users::findById).filter(User::isActive);
    }

    @Transactional(readOnly = true)
    public UserResponse me(String token) {
        return userOf(token).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión no es válida"));
    }

    private Optional<Session> currentSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessions.findByTokenHash(hash(token))
                .filter(s -> s.getClosedAt() == null && s.getExpiresAt().isAfter(ServerClock.now()));
    }

    private SessionResponse openSession(User user, String deviceId) {
        Instant now = ServerClock.now();
        // 32 random bytes: what the phone keeps. The server only ever stores its hash.
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setTokenHash(hash(token));
        // dispositivo_id is a foreign key and the row is only created by the first push, so a
        // phone that has not synced yet signs in with no device attached instead of failing
        session.setDeviceId(known(deviceId));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(SESSION_LIFE));
        sessions.save(session);

        return new SessionResponse(token, session.getExpiresAt(), UserResponse.from(user));
    }

    private String known(String deviceId) {
        return deviceId != null && !deviceId.isBlank() && devices.existsById(deviceId) ? deviceId : null;
    }

    private static String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 siempre existe en la JVM", ex);
        }
    }
}
