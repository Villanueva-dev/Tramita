package com.uniremington.api.tramita.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una solicitud (contracts/openapi.yaml). availableTransitions queda
 * vacía cuando el estado actual es final: el trámite está cerrado.
 *
 * Los datos del formulario que la 003 agrega se omiten del JSON cuando son nulos
 * (NON_NULL): una solicitud registrada con el cuerpo mínimo de la 002 no devuelve
 * campos vacíos ni defaults inventados.
 *
 * NO expone ningún dato de contacto del estudiante (FR-020, constitución §III).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestResponse(
        UUID id,
        WorkflowDefinitionResponse definition,
        String studentName,
        String studentDocument,
        String studentCode,
        String program,
        String semester,
        String reason,
        List<SubjectResponse> subjects,
        StateResponse currentState,
        List<AvailableTransitionResponse> availableTransitions,
        LocalDateTime createdAt) {
}
