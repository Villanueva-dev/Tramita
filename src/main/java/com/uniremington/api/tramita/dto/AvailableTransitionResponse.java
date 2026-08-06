package com.uniremington.api.tramita.dto;

/**
 * Transición disponible desde el estado actual, derivada de la definición con la
 * que nació la solicitud (FR-009): la UI se configura sola desde la definición,
 * sin conocer trámites concretos.
 */
public record AvailableTransitionResponse(
        StateResponse targetState,
        String responsible,
        boolean requiresNote) {
}
