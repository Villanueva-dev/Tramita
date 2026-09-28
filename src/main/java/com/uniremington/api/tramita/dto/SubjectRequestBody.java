package com.uniremington.api.tramita.dto;

import com.uniremington.api.tramita.shared.validation.AtMostOneDecimal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Una asignatura capturada en el formulario (contracts/openapi.yaml).
 *
 * Los créditos son positivos por contrato (FR-009). No es una validación de
 * cortesía: sin ella, un valor negativo restaría del total y dejaría pasar una
 * solicitud que en realidad excede el tope configurado. La cota inferior tiene
 * respaldo conceptual — una asignatura no aporta créditos negativos.
 *
 * La cota SUPERIOR es una defensa de sanidad de entrada, sin respaldo normativo
 * (research.md D8): rechaza un valor absurdo, no expresa una regla institucional.
 * Por eso no se convierte en parámetro configurable — ningún requisito pide
 * ajustarla (Principio I).
 *
 * Las notas son BigDecimal (research.md D3); su rango válido lo fija la
 * configuración del trámite, no esta clase.
 *
 * LA PRECISIÓN SÍ es regla de esta clase, a diferencia del rango (FR-013a, SC-008): el
 * Reglamento Estudiantil de Pregrado, Acuerdo n.º 13 de 2023, art. 32, fija que las
 * calificaciones llevan «un número entero y un número decimal» — nunca más de uno. Las
 * calificaciones entran solo por el formulario interno, así que un valor con más decimales
 * responde {@code 400}, no {@code 422} (que es del canal público de captura). Mismo patrón
 * que {@code ck_request_subject_grades_one_decimal} en {@code V4.1.0}: la regla vive en la
 * validación de entrada Y en la base, porque una de las dos sola se puede esquivar.
 *
 * {@code @AtMostOneDecimal} y NO {@code @Digits(fraction = 1)}: ver su javadoc — {@code
 * @Digits} mide la escala literal del {@link BigDecimal} tal como Jackson lo deserializó, y
 * un JSON con {@code 2.80} (escala 2) lo rechaza aunque sea numéricamente un solo decimal.
 */
public record SubjectRequestBody(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @Min(1) @Max(30) Integer credits,
        @Size(max = 30) String group,
        @PositiveOrZero @AtMostOneDecimal BigDecimal currentGrade,
        @PositiveOrZero @AtMostOneDecimal BigDecimal proposedGrade) {
}
