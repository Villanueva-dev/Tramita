package com.uniremington.api.tramita.shared.exception;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
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

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(invalidBody(ex));
    }

    /**
     * El 400 de {@code @Valid} deja de ser una frase en inglés que no nombra nada.
     *
     * Heredado de {@code ResponseEntityExceptionHandler}, este caso respondía
     * «Invalid request content.» para TRES causas distintas —campo ausente, campo en blanco y
     * valor fuera de rango—, de modo que el cliente tenía que deducir el campo leyendo el
     * código del servidor. Ocurrió: el formulario de novedad de notas enviaba {@code
     * credits = 0} y el trámite era irradicable sin que el error dijera por qué.
     *
     * SIGUE SIENDO 400 Y NO 422. El 422 del canal público está justificado porque allí quien
     * envía es el estudiante y el formato es sintácticamente impecable; acá el cliente es el
     * formulario interno y un campo fuera de contrato sí es un defecto de la petición.
     *
     * Se sobrescribe el método heredado en lugar de declarar un {@code @ExceptionHandler}
     * nuevo: así la resolución entre advices no cambia y {@link PublicCaptureExceptionHandler},
     * que gana por {@code @Order}, sigue atendiendo primero a su controller.
     */
    ProblemDetail invalidBody(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        return buildProblem(ValidationFields.missing(fieldErrors), ValidationFields.invalid(fieldErrors));
    }

    /**
     * Un campo inválido contra configuración persistida, no contra Bean Validation (009,
     * FR-003; research.md D4) —el catálogo de programas—. Mismo 400 y misma forma de
     * respuesta que {@link #invalidBody}.
     */
    @ExceptionHandler(InvalidFieldValueException.class)
    ProblemDetail handleInvalidFieldValue(InvalidFieldValueException ex) {
        return buildProblem(List.of(), ex.getInvalidFields());
    }

    /** Arma el mismo {@link ProblemDetail} para las dos causas del 400 (Bean Validation y catálogo). */
    private static ProblemDetail buildProblem(List<String> missing, List<String> invalid) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detailFor(missing, invalid));
        problem.setTitle(missing.isEmpty() ? "Petición inválida" : "Petición incompleta");
        problem.setProperty("missingFields", missing);
        problem.setProperty("invalidFields", invalid);
        return problem;
    }

    /**
     * Se nombran campos, NUNCA valores: el cuerpo rechazado puede traer datos personales y el
     * problem+json viaja a la pantalla y a los registros de acceso (§III, minimización).
     */
    private static String detailFor(List<String> missing, List<String> invalid) {
        if (invalid.isEmpty()) {
            return "El cuerpo de la petición está incompleto. Campos ausentes: "
                    + ValidationFields.join(missing);
        }
        if (missing.isEmpty()) {
            return "El cuerpo de la petición tiene campos con un valor inválido. Campos: "
                    + ValidationFields.join(invalid);
        }
        return "El cuerpo de la petición está incompleto y además tiene campos con un valor "
                + "inválido. Ausentes: " + ValidationFields.join(missing)
                + ". Inválidos: " + ValidationFields.join(invalid);
    }

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

    /**
     * Configuración de negocio ausente o inválida → 500 (research.md D2).
     *
     * No es 422: la petición de la Coordinación está bien; lo que falta es un
     * parámetro que el operador no cargó. Devolver 422 la mandaría a corregir un
     * formulario correcto. El diagnóstico nombra el parámetro y la definición, así
     * que se loguea del lado servidor y al cliente va solo el título (FR-010, FR-011).
     */
    @ExceptionHandler(IncompleteConfigurationException.class)
    ProblemDetail handleIncompleteConfiguration(IncompleteConfigurationException ex) {
        log.error("Configuración de negocio incompleta", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Configuración del trámite incompleta");
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
