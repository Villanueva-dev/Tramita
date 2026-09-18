package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.shared.config.AiProperties;
import com.uniremington.api.tramita.shared.exception.AssistantRateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Ventana deslizante en memoria por usuario autenticado para acotar costo del proveedor. */
@Service
public class AssistantRateLimitService {

    private final Clock clock;
    private final AiProperties properties;
    private final Map<String, Deque<Instant>> requestsByUser = new ConcurrentHashMap<>();

    public AssistantRateLimitService(Clock clock, AiProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public void checkAndRecord(String userEmail) {
        Deque<Instant> requests = requestsByUser.computeIfAbsent(userEmail, ignored -> new ArrayDeque<>());
        synchronized (requests) {
            purgeExpired(requests);
            if (requests.size() >= properties.maxRequests()) {
                throw new AssistantRateLimitExceededException(retryAfterSeconds(requests));
            }
            requests.addLast(clock.instant());
        }
    }

    private void purgeExpired(Deque<Instant> requests) {
        Instant cutoff = clock.instant().minusSeconds(properties.windowSeconds());
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(cutoff)) {
            requests.removeFirst();
        }
    }

    private long retryAfterSeconds(Deque<Instant> requests) {
        Instant expiresAt = requests.peekFirst().plusSeconds(properties.windowSeconds());
        return Math.max(1, Duration.between(clock.instant(), expiresAt).getSeconds());
    }
}