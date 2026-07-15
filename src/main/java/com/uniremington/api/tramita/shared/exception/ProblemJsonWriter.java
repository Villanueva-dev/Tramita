package com.uniremington.api.tramita.shared.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escritura de ProblemDetail (RFC 7807) directo a la respuesta desde el filter chain,
 * donde no hay MVC que serialice: la usan el entry point (401 sin sesión), el
 * AuthFailureHandler (401/400 del login) y el LoginThrottlingFilter (429). Un único
 * punto de escritura garantiza el mismo formato en todos los errores de seguridad.
 */
@Component
@RequiredArgsConstructor
public class ProblemJsonWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), problem);
    }
}
