package com.uniremington.api.tramita.auth;

import com.uniremington.api.tramita.auth.dto.ChangePasswordRequest;
import com.uniremington.api.tramita.shared.exception.TooManyAttemptsException;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambio de contraseña (US2, T030). Orden del caso de uso: throttling → verificar
 * la actual → validar la nueva → persistir → limpiar contador (research.md D7/D10).
 * NO rota el id de sesión: eso es responsabilidad de la capa web (JD2-003 — el
 * service no conoce HttpServletRequest).
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    public static final String MSG_CURRENT_PASSWORD_INCORRECT = "La contraseña actual es incorrecta";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptService loginAttemptService;

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request, String clientIp) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        String key = LoginAttemptService.key(normalizedEmail, clientIp);

        // (1) Bloqueada → 429 sin dar oráculo de validez (misma clave del login, D7)
        if (loginAttemptService.isBlocked(key)) {
            throw new TooManyAttemptsException(loginAttemptService.retryAfterSeconds(key));
        }

        // Sesión huérfana (JD3-009): inalcanzable en el MVP (una sola cuenta seed, sin
        // borrado ni panel admin). A diferencia de /me —que lee del snapshot de sesión—
        // aquí SÍ se necesita la fila para el hash, así que el orElseThrow se queda: el 500
        // resultante es un edge aceptado como inalcanzable, no un flujo esperado.
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "La sesión referencia una cuenta que ya no existe"));

        // (2) La actual incorrecta cuenta para el throttling: es una verificación
        // de credencial fallida, igual que un login fallido (FR-005, D7)
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(key);
            throw new UnprocessableRequestException(MSG_CURRENT_PASSWORD_INCORRECT);
        }

        // (3) Violación de política NO cuenta: la credencial era correcta (FR-006)
        List<String> violations =
                passwordPolicy.validateChange(request.currentPassword(), request.newPassword());
        if (!violations.isEmpty()) {
            throw new UnprocessableRequestException(String.join("; ", violations));
        }

        // (4) Persistir (updated_at lo cubre @PreUpdate) y (5) limpiar el contador.
        // save() explícito a propósito: aunque el dirty-checking de @Transactional ya
        // bastaría, dejarlo evita que un futuro retiro de @Transactional pierda el UPDATE
        // en silencio — un cambio de credencial jamás debe fallar sin avisar.
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        loginAttemptService.recordSuccess(key);
    }
}
