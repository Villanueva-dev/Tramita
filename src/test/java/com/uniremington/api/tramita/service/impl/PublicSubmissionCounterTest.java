package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniremington.api.tramita.shared.config.PublicCaptureProperties;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.unit.DataSize;

/**
 * Contador del canal público (A-3 del review independiente del 2026-09-16).
 *
 * POR QUÉ EXISTE ESTA CLASE Y ESTE TEST. La mecánica de ventana deslizante ya estaba
 * escrita y testeada en SlidingWindowCounter, incluido su barrido de claves
 * abandonadas. Pero la instancia que protegía el canal público se construía con
 * {@code new} dentro de SecurityConfig: no era bean, no tenía scheduler y **nadie la
 * barría**. El único {@code @Scheduled} vivía en LoginAttemptService y barría su
 * propio contador.
 *
 * El efecto era memoria sin techo alcanzable sin credenciales: cada origen distinto
 * dejaba una entrada que solo se liberaba si ese mismo origen volvía. Con un /64 de
 * IPv6 eso es inagotable.
 *
 * El test de la anotación no es ceremonia: la garantía que falló NO fue que el
 * barrido estuviera mal escrito —estaba bien y tenía test— sino que **nadie lo
 * llamaba**. Eso es justo lo que un test funcional del barrido no puede ver.
 */
class PublicSubmissionCounterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-16T10:00:00Z"));
    private final PublicCaptureProperties properties = new PublicCaptureProperties(
            20, Duration.ofMinutes(15), DataSize.ofKilobytes(256));
    private final PublicSubmissionCounter counter =
            new PublicSubmissionCounter(clock, properties);

    @Test
    @DisplayName("el barrido de claves abandonadas está programado: sin eso, nadie lo llama")
    void evictionIsScheduled() throws Exception {
        Method sweep = PublicSubmissionCounter.class.getMethod("evictExpiredKeys");

        assertThat(sweep.isAnnotationPresent(Scheduled.class))
                .as("un barrido que existe pero que nadie invoca deja el canal público "
                        + "acumulando una clave por origen, para siempre")
                .isTrue();
    }

    @Test
    @DisplayName("el barrido libera las claves de orígenes que no volvieron")
    void sweepFreesAbandonedOrigins() {
        counter.record("203.0.113.1");
        counter.record("203.0.113.2");
        counter.record("203.0.113.3");

        clock.advance(properties.window().plusSeconds(1));

        assertThat(counter.evictExpiredKeys())
                .as("tres orígenes que enviaron una vez y no volvieron no pueden quedarse")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("bloquea al alcanzar el límite configurado y reporta la espera")
    void blocksAtConfiguredLimit() {
        String origin = "203.0.113.10";
        for (int i = 0; i < properties.maxSubmissions(); i++) {
            counter.record(origin);
        }

        assertThat(counter.isAtLimit(origin)).isTrue();
        assertThat(counter.retryAfterSeconds(origin)).isPositive();
    }

    @Test
    @DisplayName("el bloqueo expira solo (FR-019)")
    void blockExpiresOnItsOwn() {
        String origin = "203.0.113.11";
        for (int i = 0; i < properties.maxSubmissions(); i++) {
            counter.record(origin);
        }

        clock.advance(properties.window().plusSeconds(1));

        assertThat(counter.isAtLimit(origin)).isFalse();
    }

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
