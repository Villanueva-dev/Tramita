package com.uniremington.api.tramita.dto;

import java.math.BigDecimal;

/** Asignatura persistida de una solicitud (contracts/openapi.yaml). */
public record SubjectResponse(
        String code,
        String name,
        Integer credits,
        String group,
        BigDecimal currentGrade,
        BigDecimal proposedGrade) {
}
