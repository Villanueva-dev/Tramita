package com.uniremington.api.tramita.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;

/**
 * Body de POST /api/requests (contracts/openapi.yaml). Datos mínimos de
 * identificación (FR-002): nombre y cédula son los dos datos con los que la
 * Coordinación localiza un trámite; el resto del formato oficial es SP2 y va
 * en la feature 003. Aquí sí corre @Valid (path de MVC, no de filtro).
 */
public record CreateRequestBody(
        @NotBlank @Size(max = 50) String definitionCode,
        @NotBlank @Size(max = 120) String studentName,
                @NotBlank @Size(max = 20) String studentDocument,
                @Size(max = 30) String studentCode,
                @Size(max = 255) String studentEmail,
                @Size(max = 120) String program,
                @Size(max = 50) String semester,
                String reason,
                @Size(max = 20) String priority,
                @Valid List<SubjectRequestBody> subjects) {

        /** Compatibilidad con los tests y clientes de la primera versión del API. */
        public CreateRequestBody(String definitionCode, String studentName, String studentDocument) {
                this(definitionCode, studentName, studentDocument, null, null, null, null, null, "normal", List.of());
        }
}
