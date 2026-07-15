package com.uniremington.api.tramita.auth;

import com.uniremington.api.tramita.auth.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee el body JSON {email, password} del login y produce el token no autenticado
 * (research.md D5). Es el único componente con lógica propia del filtro de login.
 *
 * JD2-004: en el path del filtro NO corre @Valid — este converter es quien valida
 * el body (JSON malformado, campos ausentes o vacíos) y dispara el 400 vía
 * InvalidLoginRequestException. Jackson 3 (tools.jackson), nunca el 2.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationConverter implements AuthenticationConverter {

    /** El email normalizado queda en el request para los handlers de éxito/fallo (clave del throttling). */
    public static final String LOGIN_EMAIL_ATTRIBUTE =
            JsonAuthenticationConverter.class.getName() + ".email";

    private final JsonMapper jsonMapper;

    @Override
    public Authentication convert(HttpServletRequest request) {
        LoginRequest login;
        try {
            login = jsonMapper.readValue(request.getInputStream(), LoginRequest.class);
        } catch (JacksonException | IOException ex) {
            throw new InvalidLoginRequestException("El body del login no es un JSON válido", ex);
        }
        if (login == null || isBlank(login.email()) || isBlank(login.password())) {
            throw new InvalidLoginRequestException("email y password son obligatorios");
        }
        String email = EmailNormalizer.normalize(login.email());
        request.setAttribute(LOGIN_EMAIL_ATTRIBUTE, email);
        return UsernamePasswordAuthenticationToken.unauthenticated(email, login.password());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
