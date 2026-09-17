package com.uniremington.api.tramita.shared.exception;

import com.uniremington.api.tramita.controller.PublicRequestController;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Un único mapeo, y solo para el canal público: un formato al que le falta —o le
 * llega vacío— alguno de los once campos obligatorios responde 422, no el 400 que
 * el resto del sistema devuelve ante una violación de Bean Validation.
 *
 * POR QUÉ ES DISTINTO. En el resto del API, el cliente es el formulario interno de
 * la Coordinación y un campo vacío es un defecto del contrato de entrada: 400. Acá
 * el cliente es un estudiante diligenciando un formato oficial, y el envío es
 * sintácticamente impecable: lo que ocurre es que el FORMATO está incompleto. El
 * 422 dice exactamente eso —«entendí el cuerpo y no puedo procesarlo»— y deja el
 * 400 para lo que sí es un defecto de formato: JSON roto o tipos equivocados, que
 * este advice NO captura y siguen resolviéndose en GlobalExceptionHandler.
 *
 * Está acotado por assignableTypes y con precedencia explícita porque
 * GlobalExceptionHandler hereda de ResponseEntityExceptionHandler, que ya maneja
 * MethodArgumentNotValidException: sin @Order, cuál de los dos gana quedaría
 * indeterminado. Con él, este advice atiende primero y solo a este controller.
 */
@RestControllerAdvice(assignableTypes = PublicRequestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicCaptureExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleIncompleteForm(MethodArgumentNotValidException ex) {
        // Se nombran los campos, no los mensajes del validador: quien diligencia el
        // formato necesita saber cuál casilla le falta, y los nombres son parte del
        // contrato público. Ningún valor enviado se refleja de vuelta.
        String missingFields = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "El formato está incompleto. Revise estos campos: " + missingFields);
        problem.setTitle("Formato incompleto");
        return problem;
    }
}
