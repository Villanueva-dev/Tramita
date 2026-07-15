package com.uniremington.api.tramita.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Materializa el CsrfToken diferido de csrf.spa() para que la cookie XSRF-TOKEN se emita
 * en las respuestas que atraviesan el chain: el SPA la recibe con su primer GET (p. ej.
 * el 401 de /api/auth/me) — research.md D4.
 *
 * Precisión (JD3-004, verificado contra la fuente 7.0.6 + IT, 2026-07-13): el login por
 * filtro (AuthenticationFilter, D5) NO ejecuta CsrfAuthenticationStrategy — el token ni
 * rota ni se borra al autenticar y el valor pre-login sigue válido tras el 204 (fixation
 * residual aceptado). El logout (US4) es distinto: CsrfLogoutHandler SÍ borra la cookie
 * y la re-emisión depende del siguiente request que pase por este filtro (nota en T036).
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
