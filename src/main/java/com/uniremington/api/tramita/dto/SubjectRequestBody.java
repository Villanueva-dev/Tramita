package com.uniremington.api.tramita.dto;

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
 */
public record SubjectRequestBody(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @Min(1) @Max(30) Integer credits,
        @Size(max = 30) String group,
        @PositiveOrZero BigDecimal currentGrade,
        @PositiveOrZero BigDecimal proposedGrade) {
}
