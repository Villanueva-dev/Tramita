package com.uniremington.api.tramita.shared.exception;

import java.util.List;
import java.util.Set;
import org.springframework.validation.FieldError;

/**
 * Parte las violaciones de Bean Validation en dos grupos: los campos que no llegaron
 * diligenciados y los que llegaron con un valor que no se puede procesar.
 *
 * VIVE APARTE PORQUE LA USAN DOS CANALES. El público la necesita para su 422 (issue #27) y
 * el interno para su 400; la partición es la misma decisión en ambos y duplicarla sería
 * garantizar que un día divergen. Lo que cambia entre canales es el código HTTP y la
 * redacción, no qué cuenta como ausente.
 *
 * LA AUSENCIA DOMINA. Un mismo campo puede violar las dos reglas a la vez —un valor de solo
 * espacios dispara {@code NotBlank} y {@code Email}—, así que un campo listado como ausente
 * nunca aparece además como inválido: listarlo en los dos arreglos obligaría al cliente a
 * decidir cuál mostrar, que es justo la decisión que esto le quita de encima.
 */
final class ValidationFields {

    /**
     * Códigos que significan «el campo no llegó diligenciado». Son los nombres simples de los
     * constraints, medidos —no supuestos— contra el validador de este proyecto:
     * {@code FieldError.getCode()} devuelve {@code NotBlank}, {@code Email} o {@code Size}.
     */
    private static final Set<String> ABSENCE_CODES = Set.of("NotBlank", "NotNull", "NotEmpty");

    private ValidationFields() {
    }

    static List<String> missing(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .filter(ValidationFields::isAbsence)
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .toList();
    }

    static List<String> invalid(List<FieldError> fieldErrors) {
        List<String> missing = missing(fieldErrors);
        return fieldErrors.stream()
                .filter(error -> !isAbsence(error))
                .map(FieldError::getField)
                .filter(field -> !missing.contains(field))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * El {@code null} no es defensa decorativa: {@code Set.of(...).contains(null)} lanza NPE, y
     * hacerlo DENTRO de un manejador de errores convertiría la respuesta legítima en un 500. Un
     * código ausente se trata como «valor inválido», que es la lectura conservadora.
     */
    private static boolean isAbsence(FieldError error) {
        return error.getCode() != null && ABSENCE_CODES.contains(error.getCode());
    }

    static String join(List<String> fields) {
        return String.join(", ", fields);
    }
}
