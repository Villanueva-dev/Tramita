package com.uniremington.api.tramita.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test del throttling anti fuerza bruta (T016, RED antes de T021 — research.md D7):
 * ventana deslizante de 5 fallos / 15 minutos por clave (email normalizado + IP),
 * auto-reparación al vencer la ventana (Clock inyectable, jamás sleep), limpieza en
 * éxito y cálculo de segundos restantes para el header Retry-After.
 */
class LoginAttemptServiceTest {

    private static final String KEY = "coordinacion.cali@uniremington.edu.co|127.0.0.1";
    private static final String OTHER_KEY = "otra@uniremington.edu.co|127.0.0.1";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-12T10:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    @DisplayName("por debajo del umbral (4 fallos) no bloquea")
    void allowsAttemptsBelowThreshold() {
        recordFailures(KEY, 4);

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("al alcanzar 5 fallos en la ventana, la clave queda bloqueada")
    void blocksAfterThresholdFailures() {
        recordFailures(KEY, 5);

        assertThat(service.isBlocked(KEY)).isTrue();
    }

    @Test
    @DisplayName("el bloqueo se auto-repara al vencer la ventana de 15 minutos")
    void unblocksAutomaticallyWhenWindowExpires() {
        recordFailures(KEY, 5);
        assertThat(service.isBlocked(KEY)).isTrue();

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("ventana deslizante: los fallos viejos expiran individualmente")
    void slidingWindowExpiresOldFailuresIndividually() {
        recordFailures(KEY, 3);
        clock.advance(Duration.ofMinutes(16));
        recordFailures(KEY, 2);

        // Solo los 2 fallos recientes viven en la ventana: no alcanza el umbral
        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("un login exitoso limpia el contador de la clave")
    void successClearsCounter() {
        recordFailures(KEY, 4);

        service.recordSuccess(KEY);

        recordFailures(KEY, 4);
        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("las claves son independientes entre sí")
    void keysAreIndependent() {
        recordFailures(KEY, 5);

        assertThat(service.isBlocked(KEY)).isTrue();
        assertThat(service.isBlocked(OTHER_KEY)).isFalse();
    }

    @Test
    @DisplayName("retryAfterSeconds informa lo que resta de la ventana")
    void retryAfterSecondsCountsRemainingWindow() {
        recordFailures(KEY, 5);

        clock.advance(Duration.ofMinutes(5));

        // Quedan 10 de los 15 minutos de la ventana del fallo más viejo
        assertThat(service.retryAfterSeconds(KEY)).isEqualTo(Duration.ofMinutes(10).toSeconds());
    }

    @Test
    @DisplayName("sin bloqueo, retryAfterSeconds es cero")
    void retryAfterSecondsIsZeroWhenNotBlocked() {
        recordFailures(KEY, 2);

        assertThat(service.retryAfterSeconds(KEY)).isZero();
    }

    private void recordFailures(String key, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(key);
        }
    }

    /** Reloj mutable para simular el paso del tiempo sin sleep. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
