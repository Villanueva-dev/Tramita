package com.uniremington.api.tramita.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Metadatos de un documento adjunto; el contenido se descarga por separado. */
public record DocumentResponse(
        UUID id,
        String originalName,
        String contentType,
        long size,
        String sha256,
        LocalDateTime createdAt) {
}