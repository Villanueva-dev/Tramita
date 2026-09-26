package com.uniremington.api.tramita.dto;

/**
 * El anexo que la configuración vigente exige para el programa de la solicitud
 * (FR-012, research.md D6).
 *
 * Es un HECHO DERIVADO, no almacenado: {@code RequestResponse} lo calcula en cada
 * lectura del detalle contra la regla de anexo vigente ({@code WorkflowAnnexRule}),
 * nunca contra la que regía el día del registro —al revés que el resto de la
 * solicitud, que queda atada a la versión con la que nació (FR-009). Que estos dos
 * campos aparezcan NO significa que el documento se haya pedido o adjuntado: el
 * sistema no recibe archivos (006, FR-010), así que esto es la indicación de qué
 * debe llevar el estudiante, no un registro de que ya lo llevó.
 */
public record AnnexRequirementResponse(
        /** Nombre del documento exigido, tal como lo declara la regla vigente. */
        String documentName,
        /** Dónde obtenerlo (p. ej. «La descarga el estudiante desde CLASS»). */
        String sourceHint) {
}
