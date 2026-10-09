package com.lacocha.backend.servicio;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

final class Fechas {

    private Fechas() {
    }

    /** Hora de Colombia: define qué es "hoy" para la granja, sin importar dónde corra el servidor. */
    static final ZoneId ZONA_GRANJA = ZoneId.of("America/Bogota");

    /** Antes de esta fecha no existía el proyecto: un celular o ESP32 sin hora configurada suele reportar 1970. */
    static final Instant FECHA_MINIMA = Instant.parse("2024-01-01T00:00:00Z");

    /** Un celular con el reloj adelantado ensuciaría el historial y ganaría siempre en los cambios del catálogo. */
    static String errorFechaDispositivo(OffsetDateTime fecha) {
        if (fecha == null) {
            return null;
        }
        Instant instante = fecha.toInstant();
        if (instante.isAfter(Instant.now().plus(Duration.ofDays(1)))) {
            return "la fecha está en el futuro: revisa el reloj del celular";
        }
        if (instante.isBefore(FECHA_MINIMA)) {
            return "la fecha es demasiado antigua: revisa el reloj del celular";
        }
        return null;
    }
}
