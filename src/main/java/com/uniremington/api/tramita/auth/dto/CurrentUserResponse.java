package com.uniremington.api.tramita.auth.dto;

/**
 * Body de GET /api/auth/me (contracts/openapi.yaml): solo email y active.
 * Jamás id ni hash — la entity User nunca se expone (data-model.md).
 */
public record CurrentUserResponse(String email, boolean active) {
}
