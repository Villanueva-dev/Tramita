package com.uniremington.api.tramita.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/**
 * Ver {@link AtMostOneDecimal}: compara decimales SIGNIFICATIVOS ({@code stripTrailingZeros()}),
 * no la escala literal con que llegó el {@link BigDecimal}.
 */
public class AtMostOneDecimalValidator
        implements ConstraintValidator<AtMostOneDecimal, BigDecimal> {

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        // null es válido acá: la obligatoriedad del campo la declara otra anotación
        // (mismo criterio que @PositiveOrZero en SubjectRequestBody).
        return value == null || value.stripTrailingZeros().scale() <= 1;
    }
}
