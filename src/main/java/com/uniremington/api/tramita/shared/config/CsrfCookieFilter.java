package com.uniremington.api.tramita.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fuerza el Set-Cookie de XSRF-TOKEN en cada respuesta (research.md D4).
 *
 * csrf.spa() usa carga diferida del token: tras autenticar, la CsrfAuthenticationStrategy
 * rota el token pero la cookie NO se re-escribe sola — el token que el SPA cacheó antes
 * del login queda obsoleto y sus POST siguientes fallarían con 403. Leer el token aquí
 * materializa el DeferredCsrfToken y provoca la re-emisión de la cookie, como muestra la
 * guía de Spring Security para SPAs (docs: servlet/exploits/csrf.html).
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
