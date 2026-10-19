package com.lacocha.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.lacocha.backend.controller.Tokens;
import com.lacocha.backend.service.AuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Every /api/** route needs one of two things: the shared X-API-Key, which is what the ESP32 node
 * and a phone that has not signed in yet carry, or the session token of a user. Either one is
 * enough, so adding people did not break the devices that were already syncing.
 *
 * Signing in and registering are the exception: they are how a phone gets a token, so they only
 * need the key. /salud and /docs stay open.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final LaCochaProperties props;
    private final ObjectProvider<AuthService> auth;

    /**
     * The service is asked for lazily: the filter is built before the persistence layer is ready,
     * and asking for it directly would make the two depend on each other at startup.
     */
    public ApiKeyFilter(LaCochaProperties props, ObjectProvider<AuthService> auth) {
        this.props = props;
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = props.apiKey();
        if (expected == null || expected.isBlank()) {
            respond(response, HttpStatus.INTERNAL_SERVER_ERROR, "API_KEY no está configurada en el servidor");
            return;
        }
        if (hasValidKey(request, expected) || hasLiveSession(request)) {
            chain.doFilter(request, response);
            return;
        }
        // Without logging the key or the token: only who tried and which route
        log.warn("Rejected credentials: {} {} from {}", request.getMethod(), request.getRequestURI(), clientIp(request));
        // Two readers at once: the caretaker, who sees this in the app and needs to know what to
        // do, and whoever is wiring up a device, who needs to know which header is missing
        respond(response, HttpStatus.UNAUTHORIZED,
                "Tu sesión se venció o falta la clave del servidor (X-API-Key). Vuelve a entrar.");
    }

    private static boolean hasValidKey(HttpServletRequest request, String expected) {
        String received = request.getHeader("X-API-Key");
        // Compared byte by byte in constant time so the answer does not leak the key
        return received != null && MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasLiveSession(HttpServletRequest request) {
        String token = Tokens.of(request.getHeader("Authorization"));
        AuthService service = auth.getIfAvailable();
        return token != null && service != null && service.userOf(token).isPresent();
    }

    /** Render puts the real client IP in X-Forwarded-For. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private static void respond(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"detalle\":\"" + detail + "\"}");
    }
}
