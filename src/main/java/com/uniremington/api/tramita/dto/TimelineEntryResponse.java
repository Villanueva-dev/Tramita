package com.uniremington.api.tramita.dto;

import java.time.LocalDateTime;

/**
 * Entrada del timeline de auditoría (contracts/openapi.yaml). fromState null =
 * nacimiento de la solicitud (research.md D7). responsible es el "en nombre de"
 * derivado de la definición (FR-006); null en la entrada de nacimiento.
 */
public record TimelineEntryResponse(
        long id,
        StateResponse fromState,
        StateResponse toState,
        String actorEmail,
        String responsible,
        String note,
        LocalDateTime occurredAt) {
}
