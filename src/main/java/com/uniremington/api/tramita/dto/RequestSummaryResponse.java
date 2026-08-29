package com.uniremington.api.tramita.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Resultado de la localización por nombre o cédula (FR-011, contracts/openapi.yaml). */
public record RequestSummaryResponse(
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
        java.util.List<SubjectResponse> subjects,
        StateResponse currentState,
        LocalDateTime createdAt) {
}
