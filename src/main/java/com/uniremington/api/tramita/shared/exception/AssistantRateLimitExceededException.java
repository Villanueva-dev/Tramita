package com.uniremington.api.tramita.shared.exception;

/** Límite de consultas del asistente superado por el usuario autenticado. */
public class AssistantRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public AssistantRateLimitExceededException(long retryAfterSeconds) {
        super("Demasiadas consultas al asistente");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}