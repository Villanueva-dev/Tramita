package com.uniremington.api.tramita.shared.exception;

/**
 * Un parámetro de negocio que una validación necesita no está cargado para el
 * trámite, o lo está con un valor no interpretable (research.md D2).
 *
 * Es una falla de configuración del sistema, NO un error del usuario, y por eso
 * mapea a 500 y no a 422. Un 422 le diría a la Coordinación que su formulario está
 * mal cuando quien está mal es el operador: la mandaría a corregir un dato que no
 * tiene ningún defecto.
 *
 * Trade-off aceptado: un trámite mal configurado queda inoperante en vez de operar
 * sin límite. Para un sistema cuya tesis es la trazabilidad, es preferible no
 * aceptar una solicitud a aceptarla sin haber aplicado la regla que dice aplicar
 * (FR-010, FR-011).
 *
 * El message es diagnóstico interno y NUNCA llega al cliente: el handler responde
 * con un título fijo.
 */
public class IncompleteConfigurationException extends RuntimeException {

    public IncompleteConfigurationException(String diagnostic) {
        super(diagnostic);
    }
}
