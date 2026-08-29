package com.uniremington.api.tramita.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos estructurados de una asignatura capturada en el formulario. */
public record SubjectRequestBody(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        Integer credits,
        @Size(max = 30) String group,
        @Size(max = 20) String currentGrade,
        @Size(max = 20) String proposedGrade) {
}