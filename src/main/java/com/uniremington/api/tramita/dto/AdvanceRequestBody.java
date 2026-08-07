package com.uniremington.api.tramita.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body de POST /api/requests/{id}/transitions (contracts/openapi.yaml). La nota
 * es opcional aquí: su obligatoriedad la decide la transición en la definición
 * (requires_note, FR-014) — validarla es trabajo del motor, no del DTO.
 *
 * El tope de la nota NO es cosmético: sin él, un body arbitrariamente grande
 * termina persistido en request_transition_log, que el trigger de V2.0.0 hace
 * inmutable — no habría forma de borrarlo. Es el mismo razonamiento del tope de
 * LoginThrottlingFilter, con la diferencia de que aquí los bytes se guardan en
 * lugar de descartarse. 2.000 caracteres sobran para el motivo de una
 * devolución y es el orden de magnitud de un campo de observaciones del formato.
 */
public record AdvanceRequestBody(
        @NotBlank @Size(max = 50) String targetStateCode,
        @Size(max = 2000) String note) {
}
