package com.uniremington.api.tramita.dto;

/** Definición vigente de un trámite (contracts/openapi.yaml) — insumo del formulario de registro. */
public record WorkflowDefinitionResponse(String code, String name, int version) {
}
