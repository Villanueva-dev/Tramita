package com.uniremington.api.tramita.shared.exception;

import com.uniremington.api.tramita.controller.PublicRequestController;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Un único mapeo, y solo para el canal público: un formato que incumple el contrato de
 * captura responde 422, no el 400 que el resto del sistema devuelve ante una violación
 * de Bean Validation.
 *
 * POR QUÉ ES DISTINTO. En el resto del API, el cliente es el formulario interno de
 * la Coordinación y un campo vacío es un defecto del contrato de entrada: 400. Acá
 * el cliente es un estudiante diligenciando un formato oficial, y el envío es
 * sintácticamente impecable: lo que ocurre es que el FORMATO no se puede procesar. El
 * 422 dice exactamente eso —«entendí el cuerpo y no puedo procesarlo»— y deja el
 * 400 para lo que sí es un defecto de formato: JSON roto o tipos equivocados, que
 * este advice NO captura y siguen resolviéndose en GlobalExceptionHandler.
 *
 * DOS CAUSAS DISTINTAS, DOS RESPUESTAS DISTINTAS (issue #27). MethodArgumentNotValidException
 * no solo se lanza cuando falta un campo: también cuando el campo llegó LLENO y su valor
 * es inválido —un correo mal escrito, un texto sobre el tope—. Responder «el formato está
 * incompleto» en ese caso le pide al estudiante rellenar una casilla que ve diligenciada,
 * en el único canal donde no hay nadie que se lo explique. Por eso se parte en dos.
 *
 * Los dos grupos viajan además como miembros de extensión de la RFC 9457 (§3.2),
 * {@code missingFields} e {@code invalidFields}, SIEMPRE presentes aunque vayan vacíos:
 * el cliente ata el error a su campo leyendo un arreglo, no parseando una frase en
 * español. Una forma estable es más barata de consumir que una que aparece y desaparece.
 *
 * Está acotado por assignableTypes y con precedencia explícita porque
 * GlobalExceptionHandler hereda de ResponseEntityExceptionHandler, que ya maneja
 * MethodArgumentNotValidException: sin @Order, cuál de los dos gana quedaría
 * indeterminado. Con él, este advice atiende primero y solo a este controller.
 */
@RestControllerAdvice(assignableTypes = PublicRequestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicCaptureExceptionHandler {

    /**
     * Códigos que significan «el campo no llegó diligenciado». Son los nombres simples de
     * los constraints, medidos —no supuestos— contra el validador de este proyecto:
     * {@code FieldError.getCode()} devuelve {@code NotBlank}, {@code Email} o {@code Size}.
     */
    private static final Set<String> ABSENCE_CODES = Set.of("NotBlank", "NotNull", "NotEmpty");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleIncompleteForm(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();

        List<String> missingFields = fieldErrors.stream()
                .filter(PublicCaptureExceptionHandler::isAbsence)
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .toList();

        // LA AUSENCIA DOMINA. Un mismo campo puede violar las dos reglas a la vez: un
        // valor de solo espacios en studentEmail dispara NotBlank Y Email (medido en la
        // sonda del issue #27). Listarlo en los dos arreglos obligaría al cliente a
        // decidir cuál mostrar, que es justamente la decisión que esto le quita de encima.
        List<String> invalidFields = fieldErrors.stream()
                .filter(error -> !isAbsence(error))
                .map(FieldError::getField)
                .filter(field -> !missingFields.contains(field))
                .distinct()
                .sorted()
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, detailFor(missingFields, invalidFields));
        problem.setTitle(missingFields.isEmpty() ? "Formato inválido" : "Formato incompleto");
        problem.setProperty("missingFields", missingFields);
        problem.setProperty("invalidFields", invalidFields);
        return problem;
    }

    /**
     * El {@code null} no es defensa decorativa: {@code Set.of(...).contains(null)} lanza
     * NPE, y hacerlo DENTRO de un manejador de errores convertiría un 422 legítimo en un
     * 500. Un código ausente se trata como «valor inválido», que es la lectura conservadora.
     */
    private static boolean isAbsence(FieldError error) {
        return error.getCode() != null && ABSENCE_CODES.contains(error.getCode());
    }

    /**
     * Se nombran campos, NUNCA valores: los nombres son parte del contrato público y quien
     * diligencia necesita saber cuál casilla revisar, pero nada de lo que envió se refleja
     * de vuelta (§III de la constitución, minimización de datos personales).
     */
    private static String detailFor(List<String> missingFields, List<String> invalidFields) {
        if (invalidFields.isEmpty()) {
            return "El formato está incompleto. Revise estos campos: " + join(missingFields);
        }
        if (missingFields.isEmpty()) {
            return "El formato tiene campos con un valor que no se puede procesar. "
                    + "Revise estos campos: " + join(invalidFields);
        }
        return "El formato está incompleto y además tiene campos con un valor que no se "
                + "puede procesar. Faltan: " + join(missingFields)
                + ". No se pueden procesar: " + join(invalidFields);
    }

    private static String join(List<String> fields) {
        return String.join(", ", fields);
    }
}
