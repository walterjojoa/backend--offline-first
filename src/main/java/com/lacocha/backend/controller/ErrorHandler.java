package com.lacocha.backend.controller;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.lacocha.backend.dto.JsonNames;

/**
 * Every error is returned as {"detalle": "..."} so the app can show them all the same way.
 * The messages are in Spanish because they reach the caretaker.
 */
@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return body(ex.getStatusCode().value(), String.valueOf(ex.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                // Same name as in the JSON (fecha_siembra, not stockingDate)
                .map(e -> JsonNames.of(ex.getParameter().getParameterType(), e.getField()) + ": " + e.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return body(HttpStatus.UNPROCESSABLE_ENTITY.value(), detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidJson(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST.value(), "El cuerpo no es un JSON válido o tiene campos con formato incorrecto");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parameter(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST.value(), ex.getName() + ": formato no válido");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> unknownRoute(NoResourceFoundException ex) {
        return body(HttpStatus.NOT_FOUND.value(), "La ruta /" + ex.getResourcePath() + " no existe");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> method(HttpRequestMethodNotSupportedException ex) {
        return body(HttpStatus.METHOD_NOT_ALLOWED.value(), "Método " + ex.getMethod() + " no permitido en esta ruta");
    }

    /** Any unexpected error: logged in full, the client only gets a generic message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error interno del servidor. Intenta de nuevo más tarde");
    }

    private static ResponseEntity<Map<String, String>> body(int status, String detail) {
        return ResponseEntity.status(status).body(Map.of("detalle", detail));
    }
}
