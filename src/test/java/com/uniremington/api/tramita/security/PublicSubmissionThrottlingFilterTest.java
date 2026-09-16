package com.uniremington.api.tramita.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniremington.api.tramita.service.impl.PublicSubmissionCounter;
import com.uniremington.api.tramita.shared.config.PublicCaptureProperties;
import com.uniremington.api.tramita.shared.exception.ProblemJsonWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit test del filtro que protege el canal público (T031, RED antes de T038), con
 * mocks de servlet y sin contexto de Spring.
 *
 * Lo que este filtro protege es la DISPONIBILIDAD del enlace, no un secreto
 * (research.md D3-bis): el canal no guarda nada que valga la pena robar, pero si deja
 * de responder el trámite no arranca. De ahí que estos tests insistan tanto en el
 * límite superior como en que el bloqueo no se pase de largo.
 */
class PublicSubmissionThrottlingFilterTest {

    private static final String PUBLIC_PATH = "/api/public/requests/ADICION_CREDITOS";
    private static final int MAX_SUBMISSIONS = 3;
    private static final DataSize MAX_BODY = DataSize.ofKilobytes(1);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final PublicCaptureProperties properties = new PublicCaptureProperties(
            MAX_SUBMISSIONS, Duration.ofMinutes(15), MAX_BODY);
    private final PublicSubmissionCounter counter =
            new PublicSubmissionCounter(Clock.systemUTC(), properties);
    private final PublicSubmissionThrottlingFilter filter =
            new PublicSubmissionThrottlingFilter(counter, properties, new ProblemJsonWriter(jsonMapper));

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    // --- tope de tamaño (FR-017) ---------------------------------------------------------

    @Test
    @DisplayName("un cuerpo que supera el tope se corta con 413 y no llega al chain")
    void rejectsOversizedBody() throws Exception {
        MockHttpServletRequest request =
                submission(new byte[(int) MAX_BODY.toBytes() + 1], "203.0.113.10");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest())
                .as("nada debe llegar al chain: el corte ocurre antes de materializar el envío")
                .isNull();
    }

    @Test
    @DisplayName("el tope se aplica aunque no haya Content-Length que declarar el tamaño")
    void rejectsOversizedBodyWithoutContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PUBLIC_PATH) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        request.setRemoteAddr("203.0.113.11");
        request.setContent(new byte[(int) MAX_BODY.toBytes() + 1]);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus())
                .as("la guarda no puede depender de una cabecera que el cliente controla")
                .isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("un cuerpo exactamente en el tope se acepta: el límite no es off-by-one")
    void allowsBodyExactlyAtTheLimit() throws Exception {
        MockHttpServletRequest request =
                submission(new byte[(int) MAX_BODY.toBytes()], "203.0.113.12");

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("el cuerpo sigue siendo legible downstream después de que el filtro lo mide")
    void keepsBodyReadableDownstream() throws Exception {
        byte[] body = "{\"studentName\":\"Prueba\"}".getBytes(StandardCharsets.UTF_8);

        filter.doFilterInternal(submission(body, "203.0.113.13"), response, chain);

        assertThat(chain.getRequest().getInputStream().readAllBytes())
                .as("medir el tamaño consume el stream: hay que re-servirlo al controller")
                .isEqualTo(body);
    }

    // --- límite de envíos por origen (FR-016, FR-018) -------------------------------------

    @Test
    @DisplayName("superar el límite desde un origen devuelve 429 con Retry-After")
    void throttlesAfterLimitFromSameOrigin() throws Exception {
        String origin = "203.0.113.20";
        for (int i = 0; i < MAX_SUBMISSIONS; i++) {
            filter.doFilterInternal(submission(smallBody(), origin),
                    new MockHttpServletResponse(), new MockFilterChain());
        }

        filter.doFilterInternal(submission(smallBody(), origin), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After"))
                .as("FR-018: quien recibe el 429 debe saber cuánto esperar")
                .isNotNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("el límite es por origen: agotar uno no bloquea a otro")
    void throttlingIsPerOrigin() throws Exception {
        String saturated = "203.0.113.30";
        for (int i = 0; i <= MAX_SUBMISSIONS; i++) {
            filter.doFilterInternal(submission(smallBody(), saturated),
                    new MockHttpServletResponse(), new MockFilterChain());
        }

        filter.doFilterInternal(submission(smallBody(), "203.0.113.31"), response, chain);

        // El estudiante de al lado no puede pagar por el envío de su compañero — es el
        // modo de fallo que D3-bis identifica como la principal amenaza de este límite.
        assertThat(response.getStatus()).isNotEqualTo(429);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("el filtro ignora todo lo que no sea un envío al canal público")
    void ignoresRequestsOutsideThePublicChannel() {
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/auth/login");

        assertThat(filter.shouldNotFilter(login))
                .as("el throttling del login tiene su propio filtro y su propio contador")
                .isTrue();
    }

    // --- helpers -------------------------------------------------------------------------

    private byte[] smallBody() {
        return "{}".getBytes(StandardCharsets.UTF_8);
    }

    private MockHttpServletRequest submission(byte[] body, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PUBLIC_PATH);
        request.setRemoteAddr(remoteAddr);
        request.setContent(body);
        return request;
    }
}
