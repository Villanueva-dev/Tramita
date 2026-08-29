package com.uniremington.api.tramita.dto;

/** Asignatura persistida de una solicitud. */
public record SubjectResponse(
        String code,
        String name,
        Integer credits,
        String group,
        String currentGrade,
        String proposedGrade) {
}