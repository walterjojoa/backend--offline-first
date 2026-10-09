package com.lacocha.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Every /api/** route requires the X-API-Key header. /salud and /docs stay open. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final LaCochaProperties props;

    public ApiKeyFilter(LaCochaProperties props) {
        this.props = props;
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
        String received = request.getHeader("X-API-Key");
        if (received == null || !MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            // Without logging the received key: only who tried and which route
            log.warn("Rejected API key: {} {} from {}", request.getMethod(), request.getRequestURI(), clientIp(request));
            respond(response, HttpStatus.UNAUTHORIZED, "Falta el encabezado X-API-Key o la clave no es válida");
            return;
        }
        chain.doFilter(request, response);
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
