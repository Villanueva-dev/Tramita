package com.uniremington.api.tramita.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Allowlist de orígenes del SPA (research.md D9), leída de APP_CORS_ALLOWED_ORIGINS
 * (coma-separada) vía application.yml. Con allowCredentials=true la spec CORS prohíbe
 * el comodín, así que se falla el arranque si aparece (fail-fast, patrón Convenia).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins no puede estar vacía (APP_CORS_ALLOWED_ORIGINS)");
        }
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins no admite comodines: allowlist explícita (research.md D9)");
        }
    }
}
