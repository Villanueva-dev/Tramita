package com.uniremington.api.tramita.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Login correcto: 204 sin body (el SPA no necesita payload — la sesión viaja en la
 * cookie) y limpieza del contador de throttling para la clave email+IP (D7).
 */
@Component
@RequiredArgsConstructor
public class AuthSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;

    // El default de esta sobrecarga hace chain.doFilter tras el handler (diseño para
    // autenticación por-request, donde el request sigue hacia el recurso). Nuestro login
    // es terminal: sin esto, el DispatcherServlet pisaría el 204 con un 404.
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain, Authentication authentication) {
        onAuthenticationSuccess(request, response, authentication);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        loginAttemptService.recordSuccess(
                LoginAttemptService.key(authentication.getName(), request.getRemoteAddr()));
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }
}
