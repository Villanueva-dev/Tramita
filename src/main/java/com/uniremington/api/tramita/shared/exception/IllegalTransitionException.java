package com.uniremington.api.tramita.shared.exception;

/**
 * Intento de transición que la definición del trámite no contempla — incluida
 * cualquier salida desde un estado final (FR-003/FR-004). Es un error de USO
 * del sistema (409), distinto de la devolución o el rechazo, que son decisiones
 * de negocio registradas como transiciones legales (nota terminológica de la
 * spec). El estado no cambia y no se escribe timeline.
 */
public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(String message) {
        super(message);
    }
}
