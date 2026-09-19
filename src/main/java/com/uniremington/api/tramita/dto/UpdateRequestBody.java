package com.uniremington.api.tramita.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Datos corregibles de una solicitud devuelta o rechazada. */
public record UpdateRequestBody(
        @NotBlank @Size(max = 120) String studentName,
        @NotBlank @Size(max = 20) String studentDocument,
        @Size(max = 30) String studentCode,
        @Size(max = 120) String program,
        @Size(max = 50) String semester,
        @Size(max = 2000) String reason,
        @Valid List<SubjectRequestBody> subjects) {

    public List<SubjectRequestBody> subjects() {
        return subjects == null ? List.of() : subjects;
    }
}