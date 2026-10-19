package com.lacocha.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, SessionRepository sessions, DeviceRepository devices) {
        this.users = users;
        this.sessions = sessions;
        this.devices = devices;
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
        user.setRole("cuidador");
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

    /** Closes the session of the token that came with the request. Repeating it is not an error. */

    @Transactional
    public void logout(String token) {
        currentSession(token).ifPresent(session -> session.setClosedAt(ServerClock.now()));
    }

    /** The user behind a token, or empty when there is none, it expired or it was closed. */
}

