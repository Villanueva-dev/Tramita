package com.uniremington.api.tramita.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una solicitud (contracts/openapi.yaml). availableTransitions queda
 * vacía cuando el estado actual es final: el trámite está cerrado.
 */
public record RequestResponse(
        UUID id,
        WorkflowDefinitionResponse definition,
        String studentName,
        String studentDocument,
        String studentCode,
        String studentEmail,
        String program,
        String semester,
        String reason,
        String priority,
        List<SubjectResponse> subjects,
        StateResponse currentState,
        List<AvailableTransitionResponse> availableTransitions,
        LocalDateTime createdAt) {
}
