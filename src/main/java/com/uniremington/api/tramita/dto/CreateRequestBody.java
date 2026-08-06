package com.uniremington.api.tramita.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body de POST /api/requests (contracts/openapi.yaml). Datos mínimos de
 * identificación (FR-002): nombre y cédula son los dos datos con los que la
 * Coordinación localiza un trámite; el resto del formato oficial es SP2 y va
 * en la feature 003. Aquí sí corre @Valid (path de MVC, no de filtro).
 */
public record CreateRequestBody(
        @NotBlank @Size(max = 50) String definitionCode,
        @NotBlank @Size(max = 120) String studentName,
        @NotBlank @Size(max = 20) String studentDocument) {
}
