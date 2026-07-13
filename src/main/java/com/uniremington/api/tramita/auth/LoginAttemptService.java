package com.uniremington.api.tramita.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
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
            if (failures.isEmpty()) {
                failuresByKey.remove(key, failures);
                return 0;
            }
            if (failures.size() < MAX_FAILURES) {
                return 0;
            }
            Instant oldestExpiresAt = failures.peekFirst().plus(WINDOW);
            return Math.max(0, Duration.between(clock.instant(), oldestExpiresAt).getSeconds());
        }
    }

    /**
     * Barrido de claves abandonadas (T044, JD3-002): una clave que nadie re-consulta
     * jamás se liberaba — spray de un intento por email distinto = memoria sin techo
     * en el path permitAll. Corre con el período de la propia ventana (nada expira
     * antes) y devuelve cuántas claves liberó (observable para los tests). La race
     * con recordFailure es la misma benigna documentada de isBlocked (JD3-003):
     * a lo sumo se pierde un fallo (undercount), jamás bloquea de más.
     */
    @Scheduled(fixedDelayString = "PT15M")
    public int evictExpiredKeys() {
        int evicted = 0;
        for (Map.Entry<String, Deque<Instant>> entry : failuresByKey.entrySet()) {
            Deque<Instant> failures = entry.getValue();
            synchronized (failures) {
                purgeExpired(failures);
                if (failures.isEmpty() && failuresByKey.remove(entry.getKey(), failures)) {
                    evicted++;
                }
            }
        }
        return evicted;
    }

    // Ventana deslizante: cada fallo expira individualmente a los 15 minutos
    private void purgeExpired(Deque<Instant> failures) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
    }
}
