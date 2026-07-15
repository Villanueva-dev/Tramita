package com.uniremington.api.tramita.shared.exception;

/**
 * Rechazo de negocio del body de una request autenticada → 422 problem+json
 * (research.md D10, RFC 9110 §15.5.2: la credencial de la request es válida; lo
 * inaceptable es el contenido — 401 aquí sería semánticamente falso). El message
 * ES el detail del ProblemDetail: las causas se distinguen por texto, como exige
 * el contrato de /auth/password.
 */
public class UnprocessableRequestException extends RuntimeException {

    public UnprocessableRequestException(String detail) {
        super(detail);
    }
}
