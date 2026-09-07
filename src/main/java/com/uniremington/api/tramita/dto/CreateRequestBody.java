package com.uniremington.api.tramita.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body de POST /api/requests (contracts/openapi.yaml). Nombre y cédula son los dos
 * datos con los que la Coordinación localiza un trámite; la 003 suma el resto del
 * formulario oficial y las asignaturas involucradas (FR-001, FR-002).
 *
 * Todos los campos de la 003 son opcionales, y el constructor de tres argumentos
 * de abajo es lo que hace que ampliar este record NO rompa a los clientes ni a los
 * tests de la 002 (FR-006, SC-007).
 *
 * NO hay campo de correo del estudiante: su consumidor era SP7, fuera de alcance
 * (FR-020, constitución §III). Si un cliente lo envía, se ignora.
 *
 * NO hay campo de prioridad. La coordinación atiende por orden de llegada y no tiene
 * procedimiento formal de priorización; una bandera de prioridad quedó registrada como
 * deseable pero no bloqueante para la primera versión (Q20 de la tercera entrevista,
 * material-coord/2026-06-04-entrevista3-sintesis-analitica.md:165-168). Diferido a SP5.
 */
public record CreateRequestBody(
        @NotBlank @Size(max = 50) String definitionCode,
        @NotBlank @Size(max = 120) String studentName,
        @NotBlank @Size(max = 20) String studentDocument,
        @Size(max = 30) String studentCode,
        @Size(max = 120) String program,
        @Size(max = 50) String semester,
        @Size(max = 2000) String reason,
        @Valid List<SubjectRequestBody> subjects) {

    /** Compatibilidad con los clientes y tests de la primera versión del API (FR-006). */
    public CreateRequestBody(String definitionCode, String studentName, String studentDocument) {
        this(definitionCode, studentName, studentDocument, null, null, null, null, List.of());
    }

    /** Nunca null: simplifica a los consumidores y evita repetir la guarda. */
    public List<SubjectRequestBody> subjects() {
        return subjects == null ? List.of() : subjects;
    }
}
