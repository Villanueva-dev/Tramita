package com.uniremington.api.tramita.dto;

/**
 * Confirmación de recepción del canal público (FR-008, research.md D5).
 *
 * Deliberadamente pobre: sin identificador, sin estado y sin enlace de consulta.
 * Devolverlos abriría de hecho la ventana de consulta del estado que la Coordinación
 * decidió no dar —bastaría con probar el identificador—, y esa decisión es de alcance,
 * no de falta de tiempo.
 */
public record PublicReceiptResponse(String message) {
}
