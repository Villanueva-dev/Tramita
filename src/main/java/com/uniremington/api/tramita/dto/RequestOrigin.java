package com.uniremington.api.tramita.dto;

/**
 * Cómo nació una solicitud (007, FR-007; 008, FR-008). Se deriva del actor de su
 * entrada de nacimiento en el timeline: el canal público actúa con una cuenta propia
 * desde la 004, así que no hay nada nuevo que persistir. Importa porque cambia qué se
 * verificó antes de que llegara: lo que entra por el enlace público lo diligenció el
 * estudiante, y desde la 008 es además la mitad de la condición del aviso de cierre
 * (la otra mitad es {@code StateResponse.isFinal}).
 *
 * {@code COORDINATION} significa «cualquier cuenta autenticada» mientras no haya
 * roles —hoy hay una sola, la de la Coordinación—; NO es una comprobación de rol.
 *
 * Es de primer nivel porque lo usan dos DTO: {@link InboxEntryResponse} desde la 007 y
 * {@link RequestResponse} desde la 008 (research.md D1). Con un solo consumidor vivía
 * anidado en la bandeja; con dos, el DTO del detalle habría tenido que importar al de
 * la bandeja solo para nombrar un tipo. El JSON no cambia: Jackson serializa el enum
 * por su nombre, así que para el contrato de la 007 el movimiento es invisible.
 */
public enum RequestOrigin {
    COORDINATION,
    PUBLIC_LINK
}
