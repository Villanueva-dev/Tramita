package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test de la ventana deslizante extraída de LoginAttemptService (T034, RED antes
 * de T036). Es la misma mecánica que ya protegía el login, con nombres neutros: acá
 * cuenta ENVÍOS de un canal abierto, no fallos de autenticación, y un método llamado
 * recordFailure para contar envíos exitosos sería un nombre que miente.
 *
 * Lo que este test protege por encima de todo es FR-019: el bloqueo expira solo.
 * Un bloqueo permanente en un canal público sería un DoS contra el estudiante
 * legítimo — exactamente el fallo que el límite existe para prevenir.
 */
class SlidingWindowCounterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int LIMIT = 20;

    private static final String KEY = "203.0.113.7";
    private static final String OTHER_KEY = "203.0.113.8";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-16T10:00:00Z"));
    private final SlidingWindowCounter counter = new SlidingWindowCounter(clock, WINDOW, LIMIT);

    @Test
    @DisplayName("por debajo del límite no bloquea")
    void allowsBelowLimit() {
        record(KEY, LIMIT - 1);

        assertThat(counter.isAtLimit(KEY)).isFalse();
    }

    @Test
    @DisplayName("al alcanzar el límite dentro de la ventana, la clave queda bloqueada")
    void blocksAtLimit() {
        record(KEY, LIMIT);

        assertThat(counter.isAtLimit(KEY)).isTrue();
    }

    @Test
    @DisplayName("el bloqueo se auto-repara al vencer la ventana, sin intervención (FR-019)")
    void unblocksAutomaticallyWhenWindowExpires() {
        record(KEY, LIMIT);
        assertThat(counter.isAtLimit(KEY)).isTrue();

        clock.advance(WINDOW.plusSeconds(1));

        assertThat(counter.isAtLimit(KEY))
                .as("pasada la ventana, nadie tuvo que desbloquear nada")
                .isFalse();
    }

    @Test
    @DisplayName("la ventana desliza: los registros expiran de a uno, no en bloque")
    void windowSlidesPerEntry() {
        record(KEY, LIMIT);

        // Vence el más viejo y solo el más viejo: queda uno por debajo del límite
        clock.advance(WINDOW.plusSeconds(1));
        record(KEY, LIMIT - 1);
        assertThat(counter.isAtLimit(KEY)).isFalse();

        record(KEY, 1);
        assertThat(counter.isAtLimit(KEY)).isTrue();
    }

    @Test
    @DisplayName("una clave bloqueada no afecta a otra: el bloqueo es por origen")
    void blockingIsPerKey() {
        record(KEY, LIMIT);

        assertThat(counter.isAtLimit(OTHER_KEY))
                .as("un origen bloqueado no puede arrastrar a otro")
                .isFalse();
    }

    @Test
    @DisplayName("los segundos restantes cuentan hasta que expire el registro más viejo")
    void reportsSecondsUntilBelowLimit() {
        record(KEY, LIMIT);
        clock.advance(Duration.ofMinutes(5));

        assertThat(counter.secondsUntilBelowLimit(KEY))
                .as("quedan 10 de los 15 minutos de la ventana")
                .isEqualTo(Duration.ofMinutes(10).getSeconds());
    }

    @Test
    @DisplayName("por debajo del límite no hay espera que reportar")
    void reportsNoWaitBelowLimit() {
        record(KEY, LIMIT - 1);

        assertThat(counter.secondsUntilBelowLimit(KEY)).isZero();
    }

    @Test
    @DisplayName("limpiar una clave borra su cuenta")
    void clearResetsTheKey() {
        record(KEY, LIMIT);

        counter.clear(KEY);

        assertThat(counter.isAtLimit(KEY)).isFalse();
    }

    @Test
    @DisplayName("el barrido libera las claves cuyos registros ya expiraron")
    void evictsAbandonedKeys() {
        record(KEY, 1);
        record(OTHER_KEY, 1);
        clock.advance(WINDOW.plusSeconds(1));

        assertThat(counter.evictExpiredKeys())
                .as("una clave que nadie vuelve a consultar no puede quedarse en memoria")
                .isEqualTo(2);
    }

    // --- helpers -------------------------------------------------------------------------

    private void record(String key, int times) {
        for (int i = 0; i < times; i++) {
            counter.record(key);
        }
    }

    /** Reloj bajo control del test: la expiración se prueba avanzando, jamás con sleep. */
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
