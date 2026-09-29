package com.uniremington.api.tramita.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Fila paginada del tablero: no expone el documento del estudiante. */
public record RequestDashboardEntryResponse(
        UUID id,
        WorkflowDefinitionResponse definition,
        String studentName,
        StateResponse currentState,
        LocalDateTime createdAt,
        String priority) {
}