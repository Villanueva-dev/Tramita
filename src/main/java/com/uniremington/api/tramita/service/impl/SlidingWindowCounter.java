package com.uniremington.api.tramita.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana deslizante en memoria, con nombres neutros: cuenta EVENTOS por clave y
 * responde si una clave alcanzó su límite dentro de la ventana.
 *
 * Es la mecánica que {@link LoginAttemptService} venía implementando para el login y
 * que ahora también usa el canal público. Se extrajo en vez de duplicarla —30 líneas
 * de concurrencia y purga— y en vez de reusar `LoginAttemptService` tal cual, que
 * habría obligado a llamar `recordFailure` para contar envíos EXITOSOS: un método
 * cuyo nombre miente es peor que el duplicado (research.md D3).
 *
 * El límite y la ventana llegan por constructor porque los dos usos difieren: el
 * login corta a los 5 fallos en 15 minutos, y el canal público a los 20 envíos en la
 * misma ventana (D3-bis).
 *
 * GARANTÍA CENTRAL: el bloqueo se auto-repara al vencer la ventana y NUNCA es
 * permanente. Un bloqueo que no expira es un DoS contra quien tiene derecho a usar el
 * sistema — en el login, contra la coordinadora; en el canal público, contra el
 * estudiante que necesita radicar su trámite (FR-019).
 */
public class SlidingWindowCounter {

    private final Clock clock;
    private final Duration window;
    private final int limit;
    private final Map<String, Deque<Instant>> eventsByKey = new ConcurrentHashMap<>();

    public SlidingWindowCounter(Clock clock, Duration window, int limit) {
        this.clock = clock;
        this.window = window;
        this.limit = limit;
    }

    public void record(String key) {
        Deque<Instant> events = eventsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (events) {
            purgeExpired(events);
            events.addLast(clock.instant());
        }
    }

    public void clear(String key) {
        eventsByKey.remove(key);
    }

    public boolean isAtLimit(String key) {
        Deque<Instant> events = eventsByKey.get(key);
        if (events == null) {
            return false;
        }
        synchronized (events) {
            purgeExpired(events);
            if (events.isEmpty()) {
                eventsByKey.remove(key, events);
                return false;
            }
            return events.size() >= limit;
        }
    }

    /** Segundos hasta que expire el evento más viejo de la ventana (header Retry-After). */
    public long secondsUntilBelowLimit(String key) {
        Deque<Instant> events = eventsByKey.get(key);
        if (events == null) {
            return 0;
        }
        synchronized (events) {
            purgeExpired(events);
            if (events.isEmpty()) {
                eventsByKey.remove(key, events);
                return 0;
            }
            if (events.size() < limit) {
                return 0;
            }
            Instant oldestExpiresAt = events.peekFirst().plus(window);
            return Math.max(0, Duration.between(clock.instant(), oldestExpiresAt).getSeconds());
        }
    }

    /**
     * Barrido de claves abandonadas: una clave que nadie vuelve a consultar jamás se
     * liberaría, y en un endpoint abierto eso es memoria sin techo. Devuelve cuántas
     * liberó, para que sea observable desde los tests.
     *
     * La carrera con {@link #record} es benigna: a lo sumo se pierde un evento
     * (undercount), jamás bloquea de más.
     */
    public int evictExpiredKeys() {
        int evicted = 0;
        for (Map.Entry<String, Deque<Instant>> entry : eventsByKey.entrySet()) {
            Deque<Instant> events = entry.getValue();
            synchronized (events) {
                purgeExpired(events);
                if (events.isEmpty() && eventsByKey.remove(entry.getKey(), events)) {
                    evicted++;
                }
            }
        }
        return evicted;
    }

    // Deslizante: cada evento expira individualmente al cumplir la ventana
    private void purgeExpired(Deque<Instant> events) {
        Instant cutoff = clock.instant().minus(window);
        while (!events.isEmpty() && events.peekFirst().isBefore(cutoff)) {
            events.removeFirst();
        }
    }
}
