package com.uniremington.api.tramita.auth;

import org.springframework.security.core.AuthenticationException;

/**
 * Body de login malformado o incompleto (JD2-004). Extiende AuthenticationException
 * para viajar por el AuthenticationFilter hasta el AuthFailureHandler, que la mapea
 * a 400 — separada del 401 genérico y sin contar como intento fallido.
 */
public class InvalidLoginRequestException extends AuthenticationException {

    public InvalidLoginRequestException(String message) {
        super(message);
    }

    public InvalidLoginRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
