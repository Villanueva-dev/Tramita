package com.uniremington.api.tramita.auth;

import com.uniremington.api.tramita.auth.dto.LoginRequest;
import com.uniremington.api.tramita.shared.exception.ProblemJsonWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Corta con 429 + Retry-After ANTES de intentar autenticar cuando la clave
 * (email + IP) superó el umbral (research.md D7, FR-010). Solo actúa sobre
 * POST /api/auth/login.
 *
 * JD2-002: este filtro lee el body y el JsonAuthenticationConverter lo relee —
 * el request se envuelve en un wrapper que bufferea el body completo y lo
 * re-sirve (ContentCachingRequestWrapper NO garantiza la relectura downstream:
 * su cache solo guarda lo ya consumido; descartado a propósito).
 *
 * La IP es request.getRemoteAddr() directo: sin proxy delante en el MVP,
 * X-Forwarded-For sería spoofeable por el cliente (trade-off documentado en D7).
 */
public class LoginThrottlingFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;
    private final JsonMapper jsonMapper;
    private final ProblemJsonWriter problemJsonWriter;

    public LoginThrottlingFilter(LoginAttemptService loginAttemptService, JsonMapper jsonMapper,
            ProblemJsonWriter problemJsonWriter) {
        this.loginAttemptService = loginAttemptService;
        this.jsonMapper = jsonMapper;
        this.problemJsonWriter = problemJsonWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod())
                && "/api/auth/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);

        String email = tryExtractEmail(cachedRequest);
        if (email != null) {
            String key = LoginAttemptService.key(email, request.getRemoteAddr());
            if (loginAttemptService.isBlocked(key)) {
                ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
                problem.setTitle("Demasiados intentos fallidos");
                response.setHeader("Retry-After",
                        String.valueOf(loginAttemptService.retryAfterSeconds(key)));
                problemJsonWriter.write(response, problem);
                return;
            }
        }
        filterChain.doFilter(cachedRequest, response);
    }

    // Parse tolerante: si el body no es un login válido, se deja pasar — el converter
    // downstream disparará el 400 (JD2-004); un body roto no cuenta para el throttling.
    private String tryExtractEmail(CachedBodyRequest request) {
        try {
            LoginRequest login = jsonMapper.readValue(request.getInputStream(), LoginRequest.class);
            if (login == null || login.email() == null || login.email().isBlank()) {
                return null;
            }
            return EmailNormalizer.normalize(login.email());
        } catch (Exception ex) {
            return null;
        }
    }

    /** Bufferea el body completo y lo re-sirve en cada getInputStream()/getReader(). */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return buffer.read();
                }

                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Lectura asíncrona no soportada");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
