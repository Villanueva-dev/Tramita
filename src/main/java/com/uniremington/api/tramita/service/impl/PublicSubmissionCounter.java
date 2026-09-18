package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.shared.config.PublicCaptureProperties;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cuenta los envíos del canal público por origen (004, FR-016 a FR-019).
 *
 * Es al canal público lo que {@link LoginAttemptService} es al login: el vocabulario
 * del dominio sobre la mecánica neutra de {@link SlidingWindowCounter}. Y existe por
 * la misma razón que aquel — **para tener dónde colgar el barrido programado**.
 *
 * ⚠️ HASTA EL 2026-09-16 ESTE CONTADOR SE CONSTRUÍA CON {@code new} DENTRO DE
 * SecurityConfig. No era bean, no tenía scheduler y nadie lo barría: cada origen
 * distinto dejaba una entrada que solo se liberaba si ese mismo origen volvía a
 * enviar. En el endpoint más expuesto del sistema —sin credenciales, alcanzable desde
 * internet— eso es memoria que crece y nunca baja; con un /64 de IPv6 el espacio de
 * orígenes es inagotable. Lo encontró un review independiente (A-3), y es el mismo
 * defecto que JD3-002 ya había corregido para el login sin extenderlo acá.
 *
 * El barrido corre con el período de la propia ventana: nada expira antes, así que
 * barrer más seguido solo gastaría ciclos.
 */
@Service
public class PublicSubmissionCounter {

    private final SlidingWindowCounter counter;

    public PublicSubmissionCounter(Clock clock, PublicCaptureProperties properties) {
        this.counter = new SlidingWindowCounter(
                clock, properties.window(), properties.maxSubmissions());
    }

    /** Registra un envío de este origen. */
    public void record(String origin) {
        counter.record(origin);
    }

    /** Si este origen agotó su cupo dentro de la ventana. */
    public boolean isAtLimit(String origin) {
        return counter.isAtLimit(origin);
    }

    /** Segundos hasta que el origen vuelva a poder enviar (cabecera Retry-After). */
    public long retryAfterSeconds(String origin) {
        return counter.secondsUntilBelowLimit(origin);
    }

    /**
     * Libera las claves cuyos envíos ya expiraron. Devuelve cuántas liberó, para que
     * sea observable desde los tests.
     *
     * El período se lee de la configuración: barrer con la cadencia de la ventana
     * basta porque ningún evento expira antes.
     */
    @Scheduled(fixedDelayString = "${app.public-capture.window}")
    public int evictExpiredKeys() {
        return counter.evictExpiredKeys();
    }
}
