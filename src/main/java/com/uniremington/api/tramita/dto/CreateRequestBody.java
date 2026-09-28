package com.uniremington.api.tramita.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body de POST /api/requests (contracts/openapi.yaml). Nombre y cédula son los dos
 * datos con los que la Coordinación localiza un trámite; la 003 suma el resto del
 * formulario oficial y las asignaturas involucradas (FR-001, FR-002); la 004 suma
 * los seis campos que el formato pide y el modelo no guardaba.
 *
 * Todos los campos posteriores a la cédula son opcionales, y los constructores de
 * compatibilidad de abajo son lo que hace que ampliar este record NO rompa a los
 * clientes ni a los tests de las versiones anteriores (FR-006, SC-007).
 *
 * Los seis campos de la 004 son opcionales ACÁ y obligatorios en el canal público
 * (PublicRequestBody). No es una inconsistencia: el formulario interno sigue
 * aceptando el cuerpo mínimo de la 002, y quien diligencia el formato completo es el
 * estudiante desde el enlace público.
 *
 * EL TELÉFONO, SI VIENE, TIENE FORMA (008, FR-010): diez dígitos exactos, la misma regla
 * del canal público. Sigue siendo opcional —{@code @Pattern} considera válido
 * {@code null}—, pero {@code ""} cuenta como «vino e inválido» y responde 400 nombrando
 * el campo: para no declarar teléfono se omite la clave, no se manda vacía. Enmienda
 * NO aditiva del contrato interno (spec de la 008, FR-013; research.md D3).
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
        @Valid List<SubjectRequestBody> subjects,
        @Email @Size(max = 255) String studentEmail,
        /** Diez dígitos si se declara (FR-010 de la 008); sin {@code @Size}: el patrón fija la longitud. */
        @Pattern(regexp = "[0-9]{10}") String studentPhone,
        @Size(max = 120) String campus,
        @Size(max = 120) String faculty,
        @Size(max = 50) String modality,
        String signature) {

    /** Compatibilidad con los clientes y tests de la primera versión del API (FR-006). */
    public CreateRequestBody(String definitionCode, String studentName, String studentDocument) {
        this(definitionCode, studentName, studentDocument, null, null, null, null, List.of());
    }

    /**
     * Compatibilidad con el cuerpo de la 003, por la misma razón que el de tres
     * argumentos: los campos que la 004 agrega son opcionales acá, de modo que un
     * cliente escrito contra el contrato anterior sigue compilando y funcionando.
     */
    public CreateRequestBody(String definitionCode, String studentName, String studentDocument,
            String studentCode, String program, String semester, String reason,
            List<SubjectRequestBody> subjects) {
        this(definitionCode, studentName, studentDocument, studentCode, program, semester,
                reason, subjects, null, null, null, null, null, null);
    }

    /** Nunca null: simplifica a los consumidores y evita repetir la guarda. */
    public List<SubjectRequestBody> subjects() {
        return subjects == null ? List.of() : subjects;
    }
}
