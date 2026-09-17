package com.uniremington.api.tramita.service;

import java.util.UUID;

/**
 * Emite el documento formal de una solicitud (SP3).
 *
 * Separa DECIDIR de DIBUJAR: acá se resuelve qué formato le corresponde a la solicitud
 * —leyendo el parámetro {@code DOCUMENT_TEMPLATE} de su definición—, y el
 * {@link IDocumentRenderer} elegido se encarga del trazado.
 */
public interface IDocumentService {

    /**
     * El PDF del formato que declara el trámite de esta solicitud.
     *
     * @throws com.uniremington.api.tramita.shared.exception.ResourceNotFoundException
     *         si la solicitud no existe, o si su trámite no declara formato — que es el
     *         caso por defecto: no todo trámite emite un documento formal.
     * @throws com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException
     *         si declara un formato que ninguna implementación dibuja. Eso no es un
     *         trámite sin documento: es configuración rota, y se reporta como tal.
     */
    byte[] generateFor(UUID requestId);
}
