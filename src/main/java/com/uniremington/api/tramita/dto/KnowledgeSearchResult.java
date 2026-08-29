package com.uniremington.api.tramita.dto;

import java.util.UUID;

/** Fragmento recuperado con sus metadatos de cita; no expone la entidad JPA. */
public record KnowledgeSearchResult(
        UUID chunkId,
        String content,
        String sourceId,
        String title,
        String version,
        String locator,
        String section,
        Integer page) {
}
