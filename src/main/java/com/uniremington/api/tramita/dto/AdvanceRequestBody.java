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
 *
 * fromStateCode es el código del estado que quien envía VIO al decidir, y existe
 * por H-10: una devolución pulsada desde una pestaña vieja se registraba desde el
 * estado ACTUAL, no desde el que la usuaria tenía en pantalla. En la novedad de
 * notas las tres devoluciones van al mismo destino (EN_PREPARACION), así que el
 * destino seguía siendo válido desde el estado nuevo y el motor la aceptaba; el
 * timeline, inmutable por trigger, quedaba con «desde EN_REVISION_FINANCIERA,
 * nota: Facultad devuelve…» sin forma de corregirlo. El @Version de Request no
 * cubre este caso: la segunda petición carga la entidad YA actualizada y su
 * versión coincide. Por eso la premisa viaja en el body y el motor la compara con
 * el estado vigente antes de evaluar cualquier otra cosa.
 */
public record AdvanceRequestBody(
        @NotBlank @Size(max = 50) String fromStateCode,
        @NotBlank @Size(max = 50) String targetStateCode,
        @Size(max = 2000) String note) {
}
