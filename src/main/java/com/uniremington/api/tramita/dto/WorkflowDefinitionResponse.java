package com.uniremington.api.tramita.dto;

/**
 * Definición vigente de un trámite (contracts/openapi.yaml) — insumo del formulario de registro.
 *
 * Desde la 007 el catálogo ya NO lo devuelve: {@code GET /api/workflow-definitions} responde
 * {@code WorkflowDefinitionDetailResponse}, con estados. Este record queda como resumen anidado
 * en cada respuesta de solicitud, y por eso no se amplía (research.md D6 de la 007).
 */
public record WorkflowDefinitionResponse(String code, String name, int version) {
}
