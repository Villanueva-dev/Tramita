package com.uniremington.api.tramita.dto;

/** Estado de una solicitud tal como lo ve la API (contracts/openapi.yaml). */
public record StateResponse(String code, String name, boolean isFinal) {
}
