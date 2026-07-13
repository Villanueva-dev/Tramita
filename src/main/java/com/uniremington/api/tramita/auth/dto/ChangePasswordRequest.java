package com.uniremington.api.tramita.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de POST /api/auth/password (US2). A diferencia del login, este endpoint es un
 * {@code @PostMapping} de MVC: {@code @Valid} SÍ corre y los campos vacíos producen el
 * 400 del contrato. Sin min/maxLength deliberadamente: la única fuente de verdad de la
 * política es {@code PasswordPolicy} en el servidor (decisión F04↔F16) — una violación
 * va por 422 con su motivo, no por 400.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword) {
}
