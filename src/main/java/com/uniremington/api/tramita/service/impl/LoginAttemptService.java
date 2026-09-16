package com.uniremington.api.tramita.service.impl;

import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Throttling anti fuerza bruta en memoria (research.md D7, FR-010): ventana deslizante
 * de {@value #MAX_FAILURES} fallos / {@link #WINDOW} por clave (email normalizado + IP).
 * El bloqueo se auto-repara al vencer la ventana — NUNCA es permanente (un lockout de
 * cuenta sería un DoS contra la usuaria legítima). Un éxito limpia el contador.
 *
 * La clave es un String neutro: el mismo servicio protege el login (US1) y la
 * verificación de contraseña actual del cambio de clave (US2) — todo punto que
 * verifica una contraseña tiene throttling.
 *
 * La mecánica vive desde la 004 en {@link SlidingWindowCounter}, porque el canal
 * público de captura necesita la misma ventana con otro límite. Esta clase conserva
 * su API pública intacta —sus 14 puntos de uso en 5 archivos no se tocaron— y aporta
 * lo que es suyo: el vocabulario del dominio de autenticación (fallo, éxito, bloqueo)
 * sobre una mecánica que en sí misma no sabe de contraseñas.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final SlidingWindowCounter counter;

    public LoginAttemptService(Clock clock) {
        this.counter = new SlidingWindowCounter(clock, WINDOW, MAX_FAILURES);
    }

    /**
     * Clave canónica del contador (email normalizado + IP). Único punto de armado:
     * la usan el filtro de throttling y los handlers de éxito/fallo — si divergieran,
     * el bloqueo y la limpieza operarían sobre contadores distintos.
     */
    public static String key(String normalizedEmail, String clientIp) {
        return normalizedEmail + "|" + clientIp;
    }

    public void recordFailure(String key) {
        counter.record(key);
    }

    public void recordSuccess(String key) {
        counter.clear(key);
    }

    public boolean isBlocked(String key) {
        return counter.isAtLimit(key);
    }

    /** Segundos hasta que expire el fallo más viejo de la ventana (header Retry-After). */
    public long retryAfterSeconds(String key) {
        return counter.secondsUntilBelowLimit(key);
    }

    /**
     * Barrido de claves abandonadas (T044, JD3-002): una clave que nadie re-consulta
     * jamás se liberaba — spray de un intento por email distinto = memoria sin techo
     * en el path permitAll. Corre con el período de la propia ventana (nada expira
     * antes) y devuelve cuántas claves liberó (observable para los tests).
     */
    @Scheduled(fixedDelayString = "PT15M")
    public int evictExpiredKeys() {
        return counter.evictExpiredKeys();
    }
}
