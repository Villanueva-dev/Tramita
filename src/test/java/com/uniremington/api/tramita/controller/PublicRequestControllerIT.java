package com.uniremington.api.tramita.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TestcontainersConfiguration;
import com.uniremington.api.tramita.repo.IRequestRepo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * IT del canal público de captura del formato DO-FR-100 (T002-T008 RED antes de
 * T011-T018). Mismas properties literales que AuthControllerIT para compartir el
 * contexto de Spring cacheado entre ITs: un bloque distinto levantaría un contexto
 * nuevo y multiplicaría el tiempo de la suite.
 *
 * Lo que este IT prueba y ningún test unitario podría: que la petición atraviesa el
 * filter chain de seguridad DE VERDAD sin sesión y sin token CSRF. La excepción de
 * CSRF en esta ruta (research.md D2) solo es observable con los filtros activos.
 *
 * Ningún dato de estos tests es real (constitución §III): nombres inventados,
 * documentos con prefijo SIN-DATO-REAL y correos en dominios reservados para pruebas.
 */
@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://placeholder:5432/placeholder",
        "DB_USER=placeholder",
        "DB_PASSWORD=placeholder",
        "APP_CORS_ALLOWED_ORIGINS=http://localhost:5173",
        "SEED_COORD_EMAIL=" + AuthControllerIT.SEED_EMAIL,
        "SEED_COORD_PASSWORD=" + AuthControllerIT.SEED_PASSWORD
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicRequestControllerIT {

    /** Responsable sintético del tramo inicial (research.md D4, FR-009). */
    private static final String PORTAL_EMAIL = "portal-publico@tramita.local";

    private static final String PUBLIC_TRADE = "ADICION_CREDITOS";
    private static final String PRIVATE_TRADE = "NOVEDAD_NOTAS";

    /**
     * Los once campos que el formato declara obligatorios (FR-003, D10). El orden es el
     * de la tabla del DO-FR-100 v2024, para que un fallo se lea contra el formato en mano.
     */
    private static final List<String> MANDATORY_FIELDS = List.of(
            "studentName", "studentDocument", "studentEmail", "studentPhone",
            "program", "campus", "faculty", "modality", "semester", "reason", "signature");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IRequestRepo requestRepo;

    // --- T002: el canal existe y no exige nada de lo que un visitante no tiene -----------

    @Test
    @DisplayName("envío público sin sesión ni token CSRF: 201")
    void publicSubmissionWithoutSessionOrCsrfIsAccepted() throws Exception {
        mockMvc.perform(publicSubmission(PUBLIC_TRADE,
                        filledForm("Estudiante Publico Uno", "SIN-DATO-REAL-101")))
                .andExpect(status().isCreated());
    }

    // --- T003: el recibo es deliberadamente pobre (FR-008, D5) ---------------------------

    @Test
    @DisplayName("el recibo no lleva identificador, ni estado, ni cabecera Location")
    void publicReceiptCarriesNoIdentifierNorState() throws Exception {
        mockMvc.perform(publicSubmission(PUBLIC_TRADE,
                        filledForm("Estudiante Publico Dos", "SIN-DATO-REAL-102")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // Hay confirmación legible...
                .andExpect(jsonPath("$.message").isNotEmpty())
                // ...y nada con lo que consultar la solicitud después.
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.currentState").doesNotExist())
                .andExpect(jsonPath("$.definition").doesNotExist())
                .andExpect(header().doesNotExist("Location"));
    }

    // --- T004 / T004a / T005: ningún campo del formato admite quedar vacío (FR-003) ------

    @Test
    @DisplayName("envío sin firma: 422 problem+json y nada se registra")
    void submissionWithoutSignatureIsRejected() throws Exception {
        assertRejectedWithoutRegistering(withoutField("signature", "SIN-DATO-REAL-103"));
    }

    @Test
    @DisplayName("cualquiera de los once campos vacío o de solo espacios: 422 y nada se registra")
    void submissionWithAnyBlankMandatoryFieldIsRejected() throws Exception {
        long registeredBefore = requestRepo.count();

        for (String field : MANDATORY_FIELDS) {
            for (String blank : List.of("", "   ")) {
                Map<String, Object> body = filledForm("Estudiante En Blanco", "SIN-DATO-REAL-104");
                body.put(field, blank);

                int httpStatus = mockMvc.perform(publicSubmission(PUBLIC_TRADE, body))
                        .andReturn().getResponse().getStatus();

                // Un valor de solo espacios NO cuenta como diligenciado: es la diferencia
                // entre @NotBlank y @NotNull, y es justo lo que esta aserción defiende.
                assertThat(httpStatus)
                        .as("el campo obligatorio «%s» enviado %s debe rechazarse", field,
                                blank.isEmpty() ? "vacío" : "con solo espacios")
                        .isEqualTo(422);
            }
        }

        assertThat(requestRepo.count())
                .as("ninguno de los %d envíos incompletos pudo registrarse",
                        MANDATORY_FIELDS.size() * 2)
                .isEqualTo(registeredBefore);
    }

    @Test
    @DisplayName("envío sin correo: 422 problem+json y nada se registra")
    void submissionWithoutEmailIsRejected() throws Exception {
        assertRejectedWithoutRegistering(withoutField("studentEmail", "SIN-DATO-REAL-105"));
    }

    @Test
    @DisplayName("envío sin compromisos adquiridos: 422 problem+json y nada se registra")
    void submissionWithoutCommitmentsIsRejected() throws Exception {
        assertRejectedWithoutRegistering(withoutField("reason", "SIN-DATO-REAL-106"));
    }

    // --- T006: solo los trámites que lo declaran tienen canal público (FR-002, D1) -------

    @Test
    @DisplayName("trámite sin captura pública habilitada: 404, sin distinguir la causa")
    void tradeWithoutPublicCaptureReturnsNotFound() throws Exception {
        mockMvc.perform(publicSubmission(PRIVATE_TRADE,
                        filledForm("Estudiante Sin Canal", "SIN-DATO-REAL-107")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // --- T007: el trámite lo manda la ruta, nunca el cuerpo (FR-002a) --------------------

    @Test
    @DisplayName("un definitionCode en el cuerpo no cambia el trámite que fija la ruta")
    void bodyCannotOverrideTheTradeFromThePath() throws Exception {
        String studentName = "Estudiante Ruta Manda";
        Map<String, Object> body = filledForm(studentName, "SIN-DATO-REAL-108");
        body.put("definitionCode", PRIVATE_TRADE);

        mockMvc.perform(publicSubmission(PUBLIC_TRADE, body))
                .andExpect(status().isCreated());

        MockHttpSession session = login();
        mockMvc.perform(get("/api/requests").param("search", studentName).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].definition.code").value(PUBLIC_TRADE));
    }

    // --- T008: el histórico nombra al portal como responsable del tramo inicial ----------

    @Test
    @DisplayName("el tramo inicial del histórico nace sin estado previo y lo firma el portal")
    void publicSubmissionTimelineNamesThePortalActor() throws Exception {
        String studentName = "Estudiante Con Historico";

        mockMvc.perform(publicSubmission(PUBLIC_TRADE,
                        filledForm(studentName, "SIN-DATO-REAL-109")))
                .andExpect(status().isCreated());

        MockHttpSession session = login();
        String requestId = findIdByName(session, studentName);

        mockMvc.perform(get("/api/requests/" + requestId + "/timeline").session(session))
                .andExpect(status().isOk())
                // fromState nulo: es un nacimiento, no una transición (§VII).
                .andExpect(jsonPath("$[0].fromState").doesNotExist())
                .andExpect(jsonPath("$[0].actorEmail").value(PORTAL_EMAIL));
    }

    // --- helpers -------------------------------------------------------------------------

    /** El formato entero diligenciado: los once obligatorios más el código opcional. */
    private Map<String, Object> filledForm(String studentName, String studentDocument) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("studentName", studentName);
        form.put("studentDocument", studentDocument);
        form.put("studentEmail", "estudiante.de.prueba@ejemplo.test");
        form.put("studentPhone", "000 000 0000");
        form.put("studentCode", "COD-PRUEBA");
        form.put("program", "Ingeniería de Sistemas");
        form.put("campus", "Cali");
        form.put("faculty", "Facultad de Ingeniería");
        form.put("modality", "Distancia");
        form.put("semester", "5");
        form.put("reason", "Necesito adicionar una asignatura del siguiente nivel.");
        form.put("signature", "data:image/png;base64,iVBORw0KGgo=");
        return form;
    }

    private Map<String, Object> withoutField(String field, String studentDocument) {
        Map<String, Object> form = filledForm("Estudiante Incompleto", studentDocument);
        form.remove(field);
        return form;
    }

    /**
     * El envío va deliberadamente SIN .session(...) y SIN .with(csrf()): es lo que
     * tiene un estudiante que abre el enlace, y lo que esta feature debe admitir.
     */
    private MockHttpServletRequestBuilder publicSubmission(
            String definitionCode, Map<String, Object> body) {
        return post("/api/public/requests/" + definitionCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }

    private void assertRejectedWithoutRegistering(Map<String, Object> body) throws Exception {
        long registeredBefore = requestRepo.count();

        mockMvc.perform(publicSubmission(PUBLIC_TRADE, body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(requestRepo.count())
                .as("un envío rechazado no puede dejar rastro")
                .isEqualTo(registeredBefore);
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(AuthControllerIT.SEED_EMAIL, AuthControllerIT.SEED_PASSWORD))
                        .session(session))
                .andExpect(status().isNoContent());
        return session;
    }

    /** El recibo público no devuelve el id: localizar la solicitud exige la sesión. */
    private String findIdByName(MockHttpSession session, String studentName) throws Exception {
        String body = mockMvc.perform(
                        get("/api/requests").param("search", studentName).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$[0].id");
    }
}
