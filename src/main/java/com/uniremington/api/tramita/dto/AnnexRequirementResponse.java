package com.uniremington.api.tramita.dto;

/**
 * El anexo que la configuración exige para el programa de la solicitud
 * (FR-009, research.md D6).
 *
 * Es un HECHO DERIVADO, no almacenado: {@code RequestServiceImpl} lo calcula en cada
 * lectura del detalle a partir de la regla ({@code WorkflowAnnexRule}) configurada para
 * el programa de la solicitud en la versión del trámite con la que nació. Lo que
 * refleja es el texto VIGENTE de esa regla —si se corrige el nombre del documento, el
 * detalle lo muestra corregido—, no una copia tomada el día del registro. Que estos dos
 * campos aparezcan NO significa que el documento se haya pedido o adjuntado: el
 * sistema no recibe archivos (FR-012; 006), así que esto es la indicación de qué
 * debe llevar el estudiante, no un registro de que ya lo llevó.
 */
public record AnnexRequirementResponse(
        /** Nombre del documento exigido, tal como lo declara la regla vigente. */
        String documentName,
        /** Dónde obtenerlo (p. ej. «La descarga el estudiante desde CLASS»). */
        String sourceHint) {
}
