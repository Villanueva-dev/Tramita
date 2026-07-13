package com.uniremington.api.tramita.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Paquete de Boot 4 (modularizado): antes org.springframework.boot.test.autoconfigure.web.servlet
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Única prueba de integración de la feature (T017, RED antes de T022–T027):
 * levanta el contexto completo con PostgreSQL real (Testcontainers) + Flyway + seeder
 * y ejercita el filter chain de seguridad de verdad (MockMvc con filtros activos).
 *
 * El Set-Cookie de TRAMITA_SESSION con sus flags no es observable en MockMvc (lo pone el
 * contenedor real); ese detalle lo verifica el quickstart con curl. Aquí se verifica lo
 * que MockMvc sí prueba con honestidad: estados, rotación del id de sesión, cuerpo
 * problem+json idéntico (anti-enumeración) y throttling.
 */
@SpringBootTest(properties = {
        // Placeholders fail-fast de application.yml: deben resolver; los de datasource
        // los pisa @ServiceConnection con los del contenedor.
        "DB_URL=jdbc:postgresql://placeholder:5432/placeholder",
        "DB_USER=placeholder",
        "DB_PASSWORD=placeholder",
        "APP_CORS_ALLOWED_ORIGINS=http://localhost:5173",
        "SEED_COORD_EMAIL=" + AuthControllerIT.SEED_EMAIL,
        "SEED_COORD_PASSWORD=" + AuthControllerIT.SEED_PASSWORD
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerIT {

    static final String SEED_EMAIL = "coordinacion.cali@uniremington.edu.co";
    static final String SEED_PASSWORD = "frase de paso de integracion";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    // --- (a) login exitoso -------------------------------------------------------------

    @Test
    @DisplayName("login correcto: 204, rota el id de sesión y GET /me responde 200")
    void loginSuccessRotatesSessionAndMeReturnsIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String idBefore = session.getId();

        mockMvc.perform(loginRequest(SEED_EMAIL, SEED_PASSWORD).session(session))
                .andExpect(status().isNoContent());

        // Rotación contra session fixation: el id previo ya no identifica la sesión
        assertThat(session.getId()).isNotEqualTo(idBefore);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email").value(SEED_EMAIL))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("el login acepta el email con mayúsculas (normalización)")
    void loginNormalizesEmail() throws Exception {
        mockMvc.perform(loginRequest("Coordinacion.Cali@Uniremington.edu.co", SEED_PASSWORD)
                        .session(new MockHttpSession()))
                .andExpect(status().isNoContent());
    }

    // --- (b) 401 genérico anti-enumeración ---------------------------------------------

    @Test
    @DisplayName("clave incorrecta, email inexistente y cuenta inactiva: 401 con body idéntico")
    void authFailuresReturnIdenticalGeneric401() throws Exception {
        String wrongPassword = body401(SEED_EMAIL, "clave equivocada pero larga");
        String unknownEmail = body401("nadie@uniremington.edu.co", SEED_PASSWORD);

        User coord = userRepository.findByEmail(SEED_EMAIL).orElseThrow();
        coord.setActive(false);
        userRepository.save(coord);
        try {
            String inactiveAccount = body401(SEED_EMAIL, SEED_PASSWORD);

            // Anti-enumeración (FR-002/SC-002): las tres causas son indistinguibles
            assertThat(wrongPassword).isEqualTo(unknownEmail).isEqualTo(inactiveAccount);
        } finally {
            coord.setActive(true);
            userRepository.save(coord);
        }
    }

    // --- (c) CSRF ----------------------------------------------------------------------

    @Test
    @DisplayName("POST de login sin token CSRF: 403")
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(SEED_EMAIL, SEED_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // --- (d) throttling ----------------------------------------------------------------

    @Test
    @DisplayName("superar 5 fallos: 429 con header Retry-After")
    void throttlingKicksInAfterThresholdWithRetryAfter() throws Exception {
        // Email propio del escenario: su clave (email+IP) no contamina los demás tests
        String hammeredEmail = "martillada@uniremington.edu.co";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(loginRequest(hammeredEmail, "password siempre incorrecta"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(loginRequest(hammeredEmail, "password siempre incorrecta"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // --- (e) body inválido → 400 (T045, JD3-008) ----------------------------------------

    @Test
    @DisplayName("body malformado o campos vacíos: 400 problem+json, no 401")
    void malformedBodyReturns400ProblemJson() throws Exception {
        var invalidBodies = java.util.List.of(
                "{\"email\":\"rota@uniremington.edu.co\",\"password\":", // JSON roto
                "{\"email\":\"\",\"password\":\"\"}",                    // campos vacíos
                "{}");                                                   // campos ausentes

        for (String body : invalidBodies) {
            mockMvc.perform(rawLoginRequest(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    @DisplayName("los bodies inválidos no cuentan para la ventana de throttling")
    void malformedBodiesDoNotCountTowardThrottling() throws Exception {
        // Email propio del escenario (higiene JD3-010): no contamina otras claves
        String email = "bodyrota@uniremington.edu.co";

        // 5 bodies inválidos con email extraíble: 400 cada uno y ninguno debe contar
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(rawLoginRequest("{\"email\":\"" + email + "\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        // El sexto intento, ya bien formado, NO encuentra la clave bloqueada:
        // 401 de credenciales — si los 400 hubieran contado, aquí habría un 429
        mockMvc.perform(loginRequest(email, "clave bien formada pero incorrecta"))
                .andExpect(status().isUnauthorized());
    }

    // --- (f) logout (T035, RED antes de T036) --------------------------------------------

    @Test
    @DisplayName("logout: 204 sin redirect, la sesión queda invalidada y /me responde 401")
    void logoutReturns204AndInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(loginRequest(SEED_EMAIL, SEED_PASSWORD).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        // La sesión del servidor murió con el logout (FR-007)
        assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers -----------------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rawLoginRequest(
            String body) {
        return post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String email, String password) {
        return post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email, password));
    }

    private String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String body401(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        return result.getResponse().getContentAsString();
    }
}
