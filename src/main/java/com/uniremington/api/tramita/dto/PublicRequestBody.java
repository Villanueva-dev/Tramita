package com.uniremington.api.tramita.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Formato DO-FR-100 diligenciado por el estudiante desde el enlace público
 * (contracts/openapi.yaml de la 004).
 *
 * NO lleva definitionCode: el trámite lo determina la RUTA (FR-002a). Un cuerpo
 * manipulado no puede cambiar a qué trámite corresponde la solicitud, y la forma más
 * barata de garantizarlo es que el dato no exista acá.
 *
 * LOS ONCE CAMPOS son obligatorios y ninguno admite quedar vacío (FR-003). Se usa
 * {@code @NotBlank} y no {@code @NotNull} a propósito: un valor compuesto solo por
 * espacios no cuenta como diligenciado, y {@code @NotNull} lo daría por bueno.
 * {@code studentCode} es el único opcional — el formato no siempre lo exige.
 *
 * EL TELÉFONO TIENE FORMA DESDE LA 008 (FR-009): exactamente diez dígitos, el formato
 * nacional colombiano, sin espacios, guiones ni prefijo internacional. Diez dígitos son
 * condición necesaria y no suficiente: el sistema NO verifica la nacionalidad del número;
 * los extranjeros quedan fuera por decisión del equipo, no por comprobación. Es una
 * enmienda NO aditiva del contrato de la 004, que aceptaba cualquier texto de hasta 30
 * caracteres, y la razón es que el teléfono ahora tiene consumidor: el aviso de cierre
 * por WhatsApp, que necesita un móvil utilizable (spec de la 008, US3). Se conserva tal
 * como llega, validado, sin normalizar (FR-011).
 *
 * Los once salen del formato, uno a uno, verificados contra la plantilla v2024
 * (material-coord/2026-06-03-coord-DO-FR-100-formato-solicitud-excepcion-de-matricula-v2024.docx,
 * research.md D10). Lo del formato que NO se declara acá es lo que el sistema ya conoce
 * o no le corresponde al estudiante: la ciudad viene impresa («Cali»), la fecha la pone
 * el servidor, el tipo de solicitud viaja en la ruta, y los trece motivos pertenecen a
 * otros tipos de solicitud del formato, no a la adición de créditos.
 */
public record PublicRequestBody(
        @NotBlank @Size(max = 120) String studentName,
        @NotBlank @Size(max = 20) String studentDocument,
        @NotBlank @Email @Size(max = 255) String studentEmail,
        /**
         * Diez dígitos exactos (FR-009 de la 008). {@code [0-9]} y no {@code \d} para que
         * quien lea la anotación no tenga que saber que en Java son equivalentes sin
         * UNICODE_CHARACTER_CLASS; sin {@code @Size} porque el patrón ya fija la longitud
         * y dos anotaciones que dicen lo mismo son dos verdades que mantener (research.md
         * D3 de la 008). Un blanco lo atrapa {@code @NotBlank} y se reporta como faltante,
         * no como inválido: la ausencia domina (ValidationFields).
         */
        @NotBlank @Pattern(regexp = "[0-9]{10}") String studentPhone,
        @Size(max = 30) String studentCode,
        @NotBlank @Size(max = 120) String program,
        @NotBlank @Size(max = 120) String campus,
        @NotBlank @Size(max = 120) String faculty,
        @NotBlank @Size(max = 50) String modality,
        @NotBlank @Size(max = 50) String semester,

        /**
         * Celda «Compromisos adquiridos» del formato. Obligatorio en este canal: es el
         * único lugar donde el estudiante narra su caso y —como el formato no tiene
         * tabla de asignaturas— también donde menciona la que necesita.
         */
        @NotBlank @Size(max = 2000) String reason,

        /**
         * Trazo de la firma como URL de datos. Sin {@code @Size}: su cota la fija el
         * filtro sobre el cuerpo entero (research.md D7), no este campo.
         */
        @NotBlank String signature) {
}
