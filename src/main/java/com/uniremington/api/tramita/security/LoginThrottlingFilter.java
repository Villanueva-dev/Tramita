package com.uniremington.api.tramita.security;

import com.uniremington.api.tramita.dto.LoginRequest;
import com.uniremington.api.tramita.service.impl.LoginAttemptService;
import com.uniremington.api.tramita.shared.exception.ProblemJsonWriter;
import com.uniremington.api.tramita.util.EmailNormalizer;
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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
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
 *
 * NO LLEVA ESTEREOTIPO A PROPÓSITO. SecurityConfig lo construye con new y lo inserta
 * en el chain con addFilterBefore. Anotarlo con @Component haría que Spring Boot lo
 * auto-registre además en la cadena del servlet container: se ejecutaría dos veces por
 * request y cada intento fallido contaría doble, disparando el 429 a la mitad de los
 * intentos configurados. No rompe la compilación y los tests unitarios no lo detectan.
 */
public class LoginThrottlingFilter extends OncePerRequestFilter {

    // Mismo matcher context-path-aware que registra el login en SecurityConfig (JD3-007):
    // getRequestURI() incluye el context-path y un despliegue con contexto saltearía el filtro
    private static final RequestMatcher LOGIN_MATCHER =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login");

    /**
     * Tope del body que se bufferea (M-1 de la auditoría del 2026-07-18). Un login son dos
     * campos: 8 KB sobran de largo. Sin tope, el único endpoint permitAll del sistema permite
     * agotar la memoria del proceso sin credenciales — el maxPostSize de Tomcat solo acota el
     * parseo de form-url-encoded, y el CSRF double-submit no frena a un atacante directo, que
     * fabrica su propio par cookie+header.
     */
    static final int MAX_BODY_BYTES = 8 * 1024;

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
        return !LOGIN_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        byte[] body = readBodyWithinLimit(request);
        if (body == null) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE);
            problem.setTitle("Cuerpo de la solicitud demasiado grande");
            problemJsonWriter.write(response, problem);
            return;
        }
        CachedBodyRequest cachedRequest = new CachedBodyRequest(request, body);

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

    /**
     * Lee el body acotado a {@link #MAX_BODY_BYTES}, o devuelve {@code null} si lo excede.
     *
     * El Content-Length se consulta primero para cortar sin leer un solo byte, pero NO alcanza
     * como única defensa: es declarativo y con Transfer-Encoding chunked ni siquiera existe.
     * Por eso la lectura pide un byte de más — así distingue "justo en el tope" de "lo excede"
     * sin confiar en lo que el cliente declara.
     *
     * El resto del body queda sin drenar a propósito. Tomcat traga hasta maxSwallowSize (2 MiB
     * por defecto) para que el cliente alcance a ver la respuesta, y si el body lo supera cierra
     * la conexión: el atacante pierde el 413, pero nada se materializó en heap, que es el punto.
     * https://tomcat.apache.org/tomcat-11.0-doc/config/http.html
     */
    private byte[] readBodyWithinLimit(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            return null;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        return body.length > MAX_BODY_BYTES ? null : body;
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

    /**
     * Re-sirve en cada getInputStream()/getReader() un body ya leído y acotado por el filtro.
     * No lee del stream original a propósito: quien lee es quien aplica el tope.
     */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
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
