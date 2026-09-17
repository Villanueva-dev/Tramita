package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.model.Request;

/**
 * Dibuja el documento formal de un trámite.
 *
 * HAY UNA IMPLEMENTACIÓN POR FORMATO OFICIAL, NO POR TRÁMITE. Qué formato le corresponde
 * a cada trámite lo dice la configuración —el parámetro {@code DOCUMENT_TEMPLATE} de su
 * definición—, no un {@code if} sobre el código del trámite. Es el §VI de la constitución:
 * el motor se configura por dato.
 *
 * ⚠️ ESO NO SIGNIFICA QUE CUALQUIER TRÁMITE PUEDA USAR CUALQUIER FORMATO SIN TOCAR CÓDIGO.
 * Un formato oficial puede necesitar saber algo del trámite que lo pide —el DO-FR-100, por
 * ejemplo, tiene que marcar UNA de sus cuatro casillas de tipo, y esas cuatro son parte del
 * papel impreso, no configuración del motor—. Declarar el parámetro alcanza para un trámite
 * cuyo caso el formato ya contempla; para uno nuevo, el renderer tiene que aprenderlo.
 *
 * Mismo patrón que {@link IWorkflowGuard}, que se resuelve por {@code guardKey()}.
 */
public interface IDocumentRenderer {

    /**
     * La clave con la que una definición pide este formato. Debe ser ÚNICA entre todas las
     * implementaciones: el registro lo valida al arrancar y falla si hay colisión.
     */
    String documentKey();

    /** El PDF del formato, ya diligenciado con los datos de la solicitud. */
    byte[] render(Request request);
}
