package com.uniremington.api.tramita.dto;

/**
 * Estado de un trámite tal como lo ve la API (contracts/openapi.yaml). Se anida en cada
 * respuesta de solicitud y, desde la 007, en el catálogo de definiciones.
 *
 * {@code isInitial} entró con la 007 (FR-011a, issue #22): es lo que permite al cliente dejar
 * de reconocer códigos de estado para saber si un trámite empezó — hasta entonces tenía que
 * saber que el inicial de adición de créditos se llama {@code EN_COORDINACION} y el de
 * novedad de notas {@code REGISTRADA}. {@code isFinal} no significa exitoso: un rechazo
 * también es final.
 *
 * Se construye en un solo sitio, {@code StateResponseMapper}, para que el cambio de forma sea
 * puntual y un mutante sobre las marcas alcance a todas las respuestas a la vez.
 */
public record StateResponse(String code, String name, boolean isInitial, boolean isFinal) {
}
