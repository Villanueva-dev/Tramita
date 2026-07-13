package com.uniremington.api.tramita.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errores como application/problem+json — RFC 7807 (research.md D10).
 *
 * Extender ResponseEntityExceptionHandler hace que las excepciones estándar de MVC
 * (body ilegible, media type no soportado, el 400 de @Valid) ya salgan como
 * ProblemDetail. Aquí viven los mapeos de negocio del mapa un-código-una-causa
 * (422 de negocio, 429 de throttling — US2); el 401 genérico del login lo emite
 * el AuthenticationFailureHandler en el filter chain (D5/D10), no este advice.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Rechazo de negocio del body → 422; el detail distingue la causa (D10). */
    @ExceptionHandler(UnprocessableRequestException.class)
    ProblemDetail handleUnprocessable(UnprocessableRequestException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Regla de negocio incumplida");
        return problem;
    }

    /**
     * Throttling → 429 + Retry-After. ResponseEntity porque el ProblemDetail pelado
     * no transporta headers; MVC lo serializa como problem+json igualmente.
     */
    @ExceptionHandler(TooManyAttemptsException.class)
    ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyAttemptsException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle(ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    /** Fallback: nada interno (mensaje, stacktrace) se filtra al cliente. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Excepción no manejada", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Error interno del servidor");
        return problem;
    }
}
