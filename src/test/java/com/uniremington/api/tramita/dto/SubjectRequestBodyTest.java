package com.uniremington.api.tramita.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-013a (SC-008): una calificación no puede tener más de un decimal.
 *
 * Respaldo normativo: Reglamento Estudiantil de Pregrado, Acuerdo n.º 13 del 1 de agosto de
 * 2023, artículo 32 — «Todas las evaluaciones practicadas se califican con un número entero y
 * un número decimal e irán de cero punto cero (0.0) hasta cinco punto cero (5.0)». Documento
 * obtenido de la fuente institucional (§IV: la normativa institucional se verifica contra el
 * documento, nunca contra Context7).
 *
 * ES EL PRIMER TEST DE `dto/`: el paquete no existía todavía. Consecuencia de §II
 * (package-by-layer), no un argumento en contra — la validación es de Bean Validation, no una
 * regla de negocio configurable, y por eso vive acá y no en `service/impl/`.
 *
 * SIN SPRING A PROPÓSITO: la regla es de Bean Validation puro, y levantar el contexto entero
 * para comprobarla sería trabajo que no aporta nada a lo que este test mide.
 */
class SubjectRequestBodyTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("una calificación con más de un decimal se rechaza")
    void aGradeWithMoreThanOneDecimalIsRejected() {
        SubjectRequestBody body = subject(new BigDecimal("3.46"), null);

        Set<ConstraintViolation<SubjectRequestBody>> violations = validator.validate(body);

        assertThat(violations)
                .as("Acuerdo n.º 13 de 2023, art. 32: un decimal como máximo")
                .anyMatch(v -> v.getPropertyPath().toString().equals("currentGrade"));
    }

    @Test
    @DisplayName("una calificación con un decimal se acepta")
    void aGradeWithOneDecimalIsAccepted() {
        SubjectRequestBody body = subject(new BigDecimal("3.5"), null);

        Set<ConstraintViolation<SubjectRequestBody>> violations = validator.validate(body);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("un decimal con cero de relleno (2.80) se acepta: no es @Digits(fraction=1)")
    void aGradeWithATrailingZeroIsAccepted() {
        // La regresión medida: @Digits mide la ESCALA literal del BigDecimal, y un JSON con
        // «2.80» llega con escala 2 aunque sea numéricamente 2.8. Rompía dos IT ya existentes
        // de RequestControllerIT que usan exactamente este valor como nota válida.
        SubjectRequestBody body = subject(new BigDecimal("2.80"), null);

        Set<ConstraintViolation<SubjectRequestBody>> violations = validator.validate(body);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("proposedGrade tiene la misma regla que currentGrade")
    void proposedGradeIsValidatedTheSameWay() {
        SubjectRequestBody body = subject(null, new BigDecimal("3.46"));

        Set<ConstraintViolation<SubjectRequestBody>> violations = validator.validate(body);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("proposedGrade"));
    }

    private static SubjectRequestBody subject(BigDecimal currentGrade, BigDecimal proposedGrade) {
        return new SubjectRequestBody(
                "MAT-101", "Cálculo I", 3, "A", currentGrade, proposedGrade);
    }
}
