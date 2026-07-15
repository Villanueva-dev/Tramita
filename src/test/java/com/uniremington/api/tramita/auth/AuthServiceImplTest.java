package com.uniremington.api.tramita.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.auth.dto.ChangePasswordRequest;
import com.uniremington.api.tramita.shared.exception.TooManyAttemptsException;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit test de la lógica sensible del cambio de contraseña (T028, RED antes de T030):
 * el orden throttling→verificación (una clave bloqueada no obtiene oráculo de validez),
 * el registro del fallo SOLO cuando la actual es incorrecta, y el no-persistir ante
 * cualquier rechazo. Colaboradores reales salvo el repositorio (única frontera de I/O):
 * encoder real (matches honesto), política real, throttling real con Clock fijo —
 * estado observable, jamás verify de interacción cuando hay estado que assertar.
 */
class AuthServiceImplTest {

    private static final String EMAIL = "coordinacion.cali@uniremington.edu.co";
    private static final String IP = "127.0.0.1";
    private static final String KEY = LoginAttemptService.key(EMAIL, IP);
    private static final String CURRENT = "frase de paso actual segura";
    private static final String NEW_VALID = "otra frase de paso valida";

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-13T10:00:00Z"), ZoneOffset.UTC);
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final LoginAttemptService attempts = new LoginAttemptService(clock);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthServiceImpl service =
            new AuthServiceImpl(userRepository, encoder, new PasswordPolicy(), attempts);

    private final User user = userWithPassword(CURRENT);

    @Test
    @DisplayName("contraseña actual incorrecta: 422 y el fallo queda registrado en el contador")
    void wrongCurrentPasswordRejectsAndRecordsFailure() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        recordFailures(4);
        assertThat(attempts.isBlocked(KEY)).isFalse();

        assertThatExceptionOfType(UnprocessableRequestException.class)
                .isThrownBy(() -> service.changePassword(
                        EMAIL, new ChangePasswordRequest("actual equivocada pero larga", NEW_VALID), IP))
                .withMessage(AuthServiceImpl.MSG_CURRENT_PASSWORD_INCORRECT);

        // El 5.º fallo solo pudo registrarlo el service: la clave pasa a bloqueada
        assertThat(attempts.isBlocked(KEY)).isTrue();
        assertThat(encoder.matches(CURRENT, user.getPasswordHash())).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("clave bloqueada: 429 con Retry-After aun con la contraseña actual correcta")
    void blockedKeyRejectsBeforeVerifyingCurrentPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        recordFailures(5);

        // La actual CORRECTA prueba el orden: bloqueada → 429 sin verificar nada
        assertThatExceptionOfType(TooManyAttemptsException.class)
                .isThrownBy(() -> service.changePassword(
                        EMAIL, new ChangePasswordRequest(CURRENT, NEW_VALID), IP))
                .satisfies(ex -> assertThat(ex.getRetryAfterSeconds())
                        .isEqualTo(LoginAttemptService.WINDOW.toSeconds()));

        assertThat(encoder.matches(CURRENT, user.getPasswordHash())).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("nueva contraseña inválida: 422 con el motivo, sin persistir y sin contar como fallo")
    void invalidNewPasswordRejectsWithoutPersistingOrCounting() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatExceptionOfType(UnprocessableRequestException.class)
                .isThrownBy(() -> service.changePassword(
                        EMAIL, new ChangePasswordRequest(CURRENT, "corta"), IP))
                .withMessageContaining(PasswordPolicy.MSG_TOO_SHORT);

        verify(userRepository, never()).save(any());
        // La actual era correcta: un rechazo de política jamás alimenta el throttling
        assertThat(attempts.isBlocked(KEY)).isFalse();
        assertThat(attempts.retryAfterSeconds(KEY)).isZero();
    }

    private void recordFailures(int times) {
        for (int i = 0; i < times; i++) {
            attempts.recordFailure(KEY);
        }
    }

    private User userWithPassword(String rawPassword) {
        User u = new User();
        u.setEmail(EMAIL);
        u.setPasswordHash(encoder.encode(rawPassword));
        return u;
    }
}
