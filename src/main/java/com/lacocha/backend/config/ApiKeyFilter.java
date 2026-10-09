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

/** Todas las rutas /api/** exigen el encabezado X-API-Key. /salud y /docs quedan libres. */
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
        String esperada = props.apiKey();
        if (esperada == null || esperada.isBlank()) {
            responder(response, HttpStatus.INTERNAL_SERVER_ERROR, "API_KEY no está configurada en el servidor");
            return;
        }
        String recibida = request.getHeader("X-API-Key");
        if (recibida == null || !MessageDigest.isEqual(
                recibida.getBytes(StandardCharsets.UTF_8), esperada.getBytes(StandardCharsets.UTF_8))) {
            // Sin escribir la clave recibida: solo quién intentó y a qué ruta
            log.warn("Clave rechazada: {} {} desde {}", request.getMethod(), request.getRequestURI(), ipCliente(request));
            responder(response, HttpStatus.UNAUTHORIZED, "Falta el encabezado X-API-Key o la clave no es válida");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Render pone la IP real del cliente en X-Forwarded-For. */
    private static String ipCliente(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        return reenviada != null && !reenviada.isBlank() ? reenviada.split(",")[0].trim() : request.getRemoteAddr();
    }

    private static void responder(HttpServletResponse response, HttpStatus status, String detalle) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"detalle\":\"" + detalle + "\"}");
    }
}
