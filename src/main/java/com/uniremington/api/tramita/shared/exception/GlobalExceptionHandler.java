package com.uniremington.api.tramita.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errores como application/problem+json — RFC 7807 (research.md D10).
 *
 * Extender ResponseEntityExceptionHandler hace que las excepciones estándar de MVC
 * (body ilegible, media type no soportado, etc.) ya salgan como ProblemDetail. Los
 * mapeos específicos del cambio de clave (422 de negocio, 429 de throttling) se
 * agregan en la US2; el 401 genérico del login lo emite el AuthenticationFailureHandler
 * en el filter chain (D5/D10), no este advice.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Fallback: nada interno (mensaje, stacktrace) se filtra al cliente. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Excepción no manejada", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Error interno del servidor");
        return problem;
    }
}
