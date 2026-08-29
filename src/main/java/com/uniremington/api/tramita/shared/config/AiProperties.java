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
        int timeoutSeconds) {
}
