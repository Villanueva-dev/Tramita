package com.uniremington.api.tramita.shared.exception;

/**
 * Clave de throttling bloqueada → 429 + header Retry-After (research.md D7/D10).
 * Porta los segundos restantes de la ventana porque el header lo arma la capa web
 * (GlobalExceptionHandler); el message replica el title del 429 que ya emite el
 * LoginThrottlingFilter — un solo lenguaje para la misma causa.
 */
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Demasiados intentos fallidos");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
