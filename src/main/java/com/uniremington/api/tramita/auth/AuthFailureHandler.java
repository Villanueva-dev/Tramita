package com.uniremington.api.tramita.auth;

import com.uniremington.api.tramita.shared.exception.ProblemJsonWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Falla de autenticación → un ÚNICO 401 genérico «Credenciales inválidas»
 * (FR-002/SC-002, D10): se aplanan TODAS las causas (BadCredentialsException,
 * DisabledException, etc.) sin ramificar por tipo — distinguirlas filtraría al
 * atacante si el email existe o si la cuenta está inactiva. El fallo se registra
 * en el throttling (D7).
 *
 * Única excepción: el body inválido del converter (JD2-004) responde 400 y NO
 * cuenta como intento fallido (no hubo verificación de credenciales).
 */
@Component
@RequiredArgsConstructor
public class AuthFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;
    private final ProblemJsonWriter problemJsonWriter;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        if (exception instanceof InvalidLoginRequestException) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            problem.setTitle("Solicitud inválida");
            problem.setDetail(exception.getMessage());
            problemJsonWriter.write(response, problem);
            return;
        }

        String email = (String) request.getAttribute(JsonAuthenticationConverter.LOGIN_EMAIL_ATTRIBUTE);
        if (email != null) {
            loginAttemptService.recordFailure(
                    LoginAttemptService.key(email, request.getRemoteAddr()));
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Credenciales inválidas");
        problemJsonWriter.write(response, problem);
    }
}
