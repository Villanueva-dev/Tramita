package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.shared.config.AiProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Caché en memoria, acotada por TTL, de respuestas ya generadas por el proveedor. Preguntas
 * repetidas (frecuentes en un asistente de soporte) no vuelven a consumir el presupuesto de
 * OpenRouter ni pagan su latencia; {@code cacheTtlSeconds = 0} la desactiva. No persiste entre
 * reinicios ni se comparte entre instancias — coherente con KISS/YAGNI para el volumen del MVP.
 */
@Component
@RequiredArgsConstructor
public class AssistantResponseCache {

    private static final int MAX_ENTRIES = 500;

    private final AiProperties properties;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public java.util.Optional<String> get(String key) {
        if (properties.cacheTtlSeconds() <= 0) {
            return java.util.Optional.empty();
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            return java.util.Optional.empty();
        }
        if (entry.expiresAt().isBefore(clock.instant())) {
            entries.remove(key);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(entry.answer());
    }

    public void put(String key, String answer) {
        if (properties.cacheTtlSeconds() <= 0) {
            return;
        }
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear(); // evicción simple: el volumen esperado no justifica una LRU real.
        }
        entries.put(key, new Entry(answer, clock.instant().plusSeconds(properties.cacheTtlSeconds())));
    }

    private record Entry(String answer, Instant expiresAt) {
    }
}
