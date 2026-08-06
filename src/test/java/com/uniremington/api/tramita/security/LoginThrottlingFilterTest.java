package com.uniremington.api.tramita.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniremington.api.tramita.service.impl.LoginAttemptService;
import com.uniremington.api.tramita.shared.exception.ProblemJsonWriter;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit test de la guarda de tamaño del body en el filtro de throttling (hallazgo M-1
 * de la auditoría de seguridad del 2026-07-18).
 *
 * El filtro bufferea el body de POST /api/auth/login para extraer el email antes de
 * autenticar. Como el login es el único endpoint permitAll, una lectura sin tope
 * permite agotar la memoria del proceso sin credenciales: ni el maxPostSize de Tomcat
 * (solo aplica a form-url-encoded) ni el CSRF double-submit lo impiden.
 *
 * El caso del Content-Length ausente es el que justifica leer con límite en lugar de
 * confiar en la cabecera: con Transfer-Encoding chunked no hay Content-Length que
 * validar, y un cliente hostil no está obligado a declarar el tamaño real.
 */
class LoginThrottlingFilterTest {

    private static final String LOGIN_BODY =
            "{\"email\":\"coordinacion.cali@uniremington.edu.co\",\"password\":\"unaClaveDeQuinceMas\"}";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final LoginThrottlingFilter filter = new LoginThrottlingFilter(
            new LoginAttemptService(Clock.systemUTC()),
            jsonMapper,
            new ProblemJsonWriter(jsonMapper));

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    @DisplayName("un login legítimo pasa al chain y su body sigue siendo legible downstream")
    void allowsLegitimateLoginAndKeepsBodyReadable() throws Exception {
        MockHttpServletRequest request = loginRequest(LOGIN_BODY.getBytes(StandardCharsets.UTF_8));

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).as("el request debe llegar al chain").isNotNull();
        assertThat(chain.getRequest().getInputStream().readAllBytes())
                .as("el wrapper debe re-servir el body completo al converter downstream")
                .isEqualTo(LOGIN_BODY.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("un body que supera el tope se corta con 413 y no llega al chain")
    void rejectsOversizedBody() throws Exception {
        MockHttpServletRequest request =
                loginRequest(new byte[LoginThrottlingFilter.MAX_BODY_BYTES + 1]);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest())
                .as("nada debe llegar al chain: el corte ocurre antes de autenticar")
                .isNull();
    }

    @Test
    @DisplayName("el tope se aplica aunque no haya Content-Length que declarar el tamaño")
    void rejectsOversizedBodyWithoutContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        request.setContent(new byte[LoginThrottlingFilter.MAX_BODY_BYTES + 1]);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus())
                .as("la guarda no puede depender de una cabecera que el cliente controla")
                .isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("un body exactamente en el tope se acepta: el límite no es off-by-one")
    void allowsBodyExactlyAtTheLimit() throws Exception {
        byte[] atLimit = new byte[LoginThrottlingFilter.MAX_BODY_BYTES];
        MockHttpServletRequest request = loginRequest(atLimit);

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest loginRequest(byte[] body)
            throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContent(body);
        return request;
    }
}
