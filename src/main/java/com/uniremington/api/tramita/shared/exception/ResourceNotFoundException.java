package com.uniremington.api.tramita.shared.exception;

/** Recurso inexistente → 404 problem+json (GlobalExceptionHandler). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
