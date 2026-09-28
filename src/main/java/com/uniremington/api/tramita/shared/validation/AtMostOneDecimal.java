package com.uniremington.api.tramita.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A LO SUMO UN DECIMAL SIGNIFICATIVO, ignorando ceros de relleno (FR-013a, SC-008; Acuerdo
 * n.º 13 de 2023, art. 32: las calificaciones llevan «un número entero y un número decimal»).
 *
 * ⚠️ NO ES {@code @Digits(fraction = 1)}, Y LA DIFERENCIA SE MIDIÓ, NO SE SUPUSO. Esa
 * anotación estándar compara contra la ESCALA del {@link java.math.BigDecimal} tal como
 * Jackson lo deserializó, no contra los decimales que el valor realmente necesita: un JSON
 * con {@code 2.80} llega con escala 2, y {@code @Digits(fraction = 1)} lo rechaza aunque 2.80
 * sea numéricamente 2.8. Rompió dos IT ya existentes de {@code RequestControllerIT} que usaban
 * {@code 2.80}/{@code 3.50} como notas válidas de un decimal (medido con Hibernate Validator
 * 9.0.1.Final). Esta anotación compara contra
 * {@link java.math.BigDecimal#stripTrailingZeros()}, que mide lo que el Acuerdo n.º 13
 * realmente exige.
 */
@Documented
@Constraint(validatedBy = AtMostOneDecimalValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AtMostOneDecimal {

    String message() default "debe tener a lo sumo un decimal";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
