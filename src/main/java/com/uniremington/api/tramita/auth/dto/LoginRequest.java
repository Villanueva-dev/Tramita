package com.uniremington.api.tramita.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Body de POST /api/auth/login (contracts/openapi.yaml). Las anotaciones son
 * documentales: en el path del filtro no corre @Valid — la validación real del
 * body la hace JsonAuthenticationConverter (JD2-004).
 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password) {
}
