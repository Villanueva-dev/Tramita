package com.uniremington.api.tramita.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errores como application/problem+json — RFC 9457 (research.md D10).
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
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        problem.setTitle("Regla de negocio incumplida");
        return problem;
    }

    /** Recurso inexistente → 404 (002: solicitud no encontrada). */
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso no encontrado");
        return problem;
    }

    /**
     * Transición que la definición no contempla → 409 (002, FR-003/FR-004): el
     * conflicto es con el estado actual del recurso, no con el formato del body.
     */
    @ExceptionHandler(IllegalTransitionException.class)
    ProblemDetail handleIllegalTransition(IllegalTransitionException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Transición no permitida");
        return problem;
    }

    /**
     * Locking optimista (002, research.md D6): dos avances casi simultáneos —
     * solo prosperó el que vio el estado vigente. 409 con instrucción de
     * reintento; nada interno (entidad, versión) se filtra al cliente.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "La solicitud cambió mientras se procesaba la operación; "
                        + "consulte el estado vigente y reintente.");
        problem.setTitle("Conflicto de concurrencia");
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

    /** El proveedor no está habilitado o configurado: 503 sin revelar secretos. */
    @ExceptionHandler(AiUnavailableException.class)
    ProblemDetail handleAiUnavailable(AiUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Asistente no disponible");
        return problem;
    }

    /** Error del proveedor externo: 502 y ningún detalle interno de la integración. */
    @ExceptionHandler(AiProviderException.class)
    ProblemDetail handleAiProvider(AiProviderException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "No fue posible consultar el asistente en este momento.");
        problem.setTitle("Proveedor de IA no disponible");
        return problem;
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
