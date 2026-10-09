package com.lacocha.backend.modelo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Hora del servidor recortada a microsegundos, que es lo que guarda PostgreSQL.
 * Así lo que se devuelve recién creado es igual a lo que se lee después de la base de datos,
 * y el cursor de /api/sync/pull nunca queda "antes" de un registro por culpa del redondeo.
 */
public final class Reloj {

    private Reloj() {
    }

    public static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
