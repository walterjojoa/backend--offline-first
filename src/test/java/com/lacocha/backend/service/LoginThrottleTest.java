package com.lacocha.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/** El bloqueo de ingresos con un reloj de mentira, para no esperar quince minutos de verdad. */
class LoginThrottleTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-20T08:00:00Z"));
    private final LoginThrottle throttle = new LoginThrottle(now::get);

    private void failTimes(String document, int times) {
        for (int i = 0; i < times; i++) {
            throttle.failed(document);
        }
    }

    @Test
    void cuatroErroresTodaviaNoBloquean() {
        failTimes("12345", LoginThrottle.MAX_FAILURES - 1);

        assertThat(throttle.isLocked("12345")).isFalse();
    }

    @Test
    void elQuintoErrorBloquea() {
        failTimes("12345", LoginThrottle.MAX_FAILURES);

        assertThat(throttle.isLocked("12345")).isTrue();
    }

    @Test
    void elBloqueoSeLevantaCuandoPasaLaVentana() {
        failTimes("12345", LoginThrottle.MAX_FAILURES);

        now.set(now.get().plus(LoginThrottle.WINDOW).minusSeconds(1));
        assertThat(throttle.isLocked("12345")).isTrue();

        now.set(now.get().plusSeconds(1));
        assertThat(throttle.isLocked("12345")).isFalse();
    }

    @Test
    void despuesDeLaVentanaLaCuentaEmpiezaDeCero() {
        failTimes("12345", LoginThrottle.MAX_FAILURES);
        now.set(now.get().plus(LoginThrottle.WINDOW));

        // Un solo error nuevo no lo vuelve a bloquear: los cinco viejos ya no cuentan
        throttle.failed("12345");
        assertThat(throttle.isLocked("12345")).isFalse();
    }

    @Test
    void entrarBienBorraLaCuenta() {
        failTimes("12345", LoginThrottle.MAX_FAILURES - 1);
        throttle.succeeded("12345");
        failTimes("12345", LoginThrottle.MAX_FAILURES - 1);

        assertThat(throttle.isLocked("12345")).isFalse();
    }
}
