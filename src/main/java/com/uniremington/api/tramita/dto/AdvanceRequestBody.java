package com.uniremington.api.tramita.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body de POST /api/requests/{id}/transitions (contracts/openapi.yaml). La nota
 * es opcional aquí: su obligatoriedad la decide la transición en la definición
 * (requires_note, FR-014) — validarla es trabajo del motor, no del DTO.
 */
public record AdvanceRequestBody(
        @NotBlank @Size(max = 50) String targetStateCode,
        String note) {
}
