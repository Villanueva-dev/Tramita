package com.uniremington.api.tramita.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Config mínima para notificaciones; SMTP es opcional y tiene fallback manual. */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(String fromEmail) {
}