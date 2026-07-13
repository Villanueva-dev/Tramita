package com.uniremington.api.tramita.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Throttling anti fuerza bruta en memoria (research.md D7, FR-010): ventana deslizante
 * de {@value #MAX_FAILURES} fallos / {@link #WINDOW} por clave (email normalizado + IP).
 * El bloqueo se auto-repara al vencer la ventana — NUNCA es permanente (un lockout de
 * cuenta sería un DoS contra la usuaria legítima). Un éxito limpia el contador.
 *
 * La clave es un String neutro: el mismo servicio protege el login (US1) y la
 * verificación de contraseña actual del cambio de clave (US2) — todo punto que
 * verifica una contraseña tiene throttling.
 */
@Component
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Deque<Instant>> failuresByKey = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
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
        Deque<Instant> failures = failuresByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (failures) {
            purgeExpired(failures);
            failures.addLast(clock.instant());
        }
    }

    public void recordSuccess(String key) {
        failuresByKey.remove(key);
    }

    public boolean isBlocked(String key) {
        Deque<Instant> failures = failuresByKey.get(key);
        if (failures == null) {
            return false;
        }
        synchronized (failures) {
            purgeExpired(failures);
            if (failures.isEmpty()) {
                failuresByKey.remove(key, failures);
                return false;
            }
            return failures.size() >= MAX_FAILURES;
        }
    }

    /** Segundos hasta que expire el fallo más viejo de la ventana (header Retry-After). */
    public long retryAfterSeconds(String key) {
        Deque<Instant> failures = failuresByKey.get(key);
        if (failures == null) {
            return 0;
        }
        synchronized (failures) {
            purgeExpired(failures);
            if (failures.size() < MAX_FAILURES) {
                return 0;
            }
            Instant oldestExpiresAt = failures.peekFirst().plus(WINDOW);
            return Math.max(0, Duration.between(clock.instant(), oldestExpiresAt).getSeconds());
        }
    }

    // Ventana deslizante: cada fallo expira individualmente a los 15 minutos
    private void purgeExpired(Deque<Instant> failures) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
    }
}
