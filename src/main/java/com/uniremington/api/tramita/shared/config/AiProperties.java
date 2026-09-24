package com.uniremington.api.tramita.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración del proveedor generativo; la clave solo existe en el backend. */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        int maxTokens,
        int timeoutSeconds,
        int maxRequests,
        int windowSeconds,
        /** Modelo alterno si el principal falla tras los reintentos; vacío = sin fallback. */
        String fallbackModel,
        /** TTL de la caché de respuestas idénticas; 0 = caché desactivada. */
        int cacheTtlSeconds) {
}
