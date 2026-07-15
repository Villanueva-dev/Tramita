package com.uniremington.api.tramita.auth;

import com.uniremington.api.tramita.auth.dto.ChangePasswordRequest;

/**
 * Casos de uso de autenticación que viven fuera del filter chain (US2+).
 * El login y el logout NO pasan por aquí: los procesa Spring Security (D5).
 */
public interface AuthService {

    /**
     * Cambia la contraseña de la cuenta autenticada (FR-004/FR-005/FR-006).
     * Recibe el email crudo del Authentication y la IP del cliente; normalizar
     * y armar la clave de throttling (paridad con el login, D7) es responsabilidad
     * de la implementación.
     */
    void changePassword(String email, ChangePasswordRequest request, String clientIp);
}
