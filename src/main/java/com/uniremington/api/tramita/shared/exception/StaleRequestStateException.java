package com.uniremington.api.tramita.shared.exception;

/**
 * La petición parte de un estado que la solicitud ya no tiene: quien la envió
 * decidió mirando una pantalla desactualizada (H-10). Es un conflicto con el
 * estado actual del recurso (409), como {@link IllegalTransitionException}, pero
 * su causa es otra: la transición pedida puede ser perfectamente legal desde el
 * estado vigente y aun así no es la que esa persona quiso ejecutar. El estado no
 * cambia y no se escribe timeline.
 */
public class StaleRequestStateException extends RuntimeException {

    public StaleRequestStateException(String message) {
        super(message);
    }
}
