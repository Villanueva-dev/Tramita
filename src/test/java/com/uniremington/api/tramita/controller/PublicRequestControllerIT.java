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

    /**
     * Debe coincidir con `app.public-capture.max-submissions` de application.yml. Se
     * duplica acá a propósito y no se inyecta: si alguien cambia el valor configurado,
     * conviene que este test falle y obligue a revisar si el nuevo umbral sigue siendo
     * defendible (research.md D3-bis), en vez de adaptarse en silencio.
     */
    private static final int MAX_SUBMISSIONS_PER_WINDOW = 20;
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
        mockMvc.perform(publicSubmission("203.0.113.1", PUBLIC_TRADE,
                        filledForm("Estudiante Publico Uno", "SIN-DATO-REAL-101")))
                .andExpect(status().isCreated());
    }

    // --- T003: el recibo es deliberadamente pobre (FR-008, D5) ---------------------------

    @Test
    @DisplayName("el recibo no lleva identificador, ni estado, ni cabecera Location")
    void publicReceiptCarriesNoIdentifierNorState() throws Exception {
        mockMvc.perform(publicSubmission("203.0.113.2", PUBLIC_TRADE,
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
        assertRejectedWithoutRegistering("203.0.113.3", withoutField("signature", "SIN-DATO-REAL-103"));
    }

    @Test
    @DisplayName("cualquiera de los once campos vacío o de solo espacios: 422 y nada se registra")
    void submissionWithAnyBlankMandatoryFieldIsRejected() throws Exception {
        long registeredBefore = requestRepo.count();

        // UN ORIGEN POR CASO, y no uno solo para los 22. Este test hace 11 campos x 2
        // variantes = 22 envíos, más que los 20 que admite la ventana del canal
        // (app.public-capture.max-submissions): con un origen compartido, los dos
        // últimos casos recibirían 429 en vez de 422 y el test estaría midiendo el
        // límite de tasa en lugar de la obligatoriedad de los campos. Los 203.0.113.x
        // son del rango TEST-NET-3 que la RFC 5737 reserva para documentación.
        int scenario = 0;
        for (String field : MANDATORY_FIELDS) {
            for (String blank : List.of("", "   ")) {
                Map<String, Object> body = filledForm("Estudiante En Blanco", "SIN-DATO-REAL-104");
                body.put(field, blank);
                String origin = "203.0.113." + (100 + scenario++);

                int httpStatus = mockMvc.perform(publicSubmission(origin, PUBLIC_TRADE, body))
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
        assertRejectedWithoutRegistering("203.0.113.5", withoutField("studentEmail", "SIN-DATO-REAL-105"));
    }

    @Test
    @DisplayName("envío sin compromisos adquiridos: 422 problem+json y nada se registra")
    void submissionWithoutCommitmentsIsRejected() throws Exception {
        assertRejectedWithoutRegistering("203.0.113.6", withoutField("reason", "SIN-DATO-REAL-106"));
    }

    // --- T006: solo los trámites que lo declaran tienen canal público (FR-002, D1) -------

    @Test
    @DisplayName("trámite sin captura pública habilitada: 404, sin distinguir la causa")
    void tradeWithoutPublicCaptureReturnsNotFound() throws Exception {
        mockMvc.perform(publicSubmission("203.0.113.7", PRIVATE_TRADE,
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

        mockMvc.perform(publicSubmission("203.0.113.8", PUBLIC_TRADE, body))
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

        mockMvc.perform(publicSubmission("203.0.113.9", PUBLIC_TRADE,
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

    // --- US3: el canal abierto resiste el abuso (FR-016 a FR-019) -------------------------

    @Test
    @DisplayName("un envío por encima del tope de tamaño: 413 y no se registra nada")
    void oversizedSubmissionIsRejected() throws Exception {
        long registeredBefore = requestRepo.count();

        // La firma es el único campo sin cota propia, así que es por donde un envío
        // desmesurado entraría de verdad: un trazo enorme, no un texto cualquiera.
        Map<String, Object> body = filledForm("Estudiante Desmesurado", "SIN-DATO-REAL-301");
        body.put("signature", "data:image/png;base64," + "A".repeat(300 * 1024));

        mockMvc.perform(publicSubmission("203.0.113.40", PUBLIC_TRADE, body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(requestRepo.count())
                .as("el corte ocurre antes de materializar el envío")
                .isEqualTo(registeredBefore);
    }

    @Test
    @DisplayName("superar el límite de envíos desde un origen: 429 con Retry-After")
    void tooManySubmissionsFromSameOriginAreThrottled() throws Exception {
        // Origen propio del escenario: los IT comparten contexto y contador, así que un
        // test que satura una clave contaminaría a los demás (regla 4 de la feature).
        String origin = "203.0.113.41";

        // Cuerpos deliberadamente incompletos: se rechazan con 422 y NO crean filas, pero
        // CUENTAN para el límite igual que uno válido. Es la decisión de diseño que este
        // test fija: el recurso que el límite protege es el procesamiento del envío, y un
        // atacante que manda basura lo consume exactamente igual.
        int status = 0;
        for (int attempt = 0; attempt < MAX_SUBMISSIONS_PER_WINDOW + 1; attempt++) {
            status = mockMvc.perform(publicSubmission(origin, PUBLIC_TRADE, Map.of()))
                    .andReturn().getResponse().getStatus();
            if (attempt < MAX_SUBMISSIONS_PER_WINDOW) {
                assertThat(status)
                        .as("el envío %d está dentro del límite y se juzga por su contenido",
                                attempt + 1)
                        .isEqualTo(422);
            }
        }

        assertThat(status).as("el envío que supera el límite se corta con 429").isEqualTo(429);

        mockMvc.perform(publicSubmission(origin, PUBLIC_TRADE, Map.of()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("un origen saturado no bloquea a otro: el estudiante de al lado sigue enviando")
    void throttlingOfOneOriginDoesNotAffectAnother() throws Exception {
        String saturated = "203.0.113.42";
        for (int attempt = 0; attempt <= MAX_SUBMISSIONS_PER_WINDOW; attempt++) {
            mockMvc.perform(publicSubmission(saturated, PUBLIC_TRADE, Map.of()));
        }

        // Es el modo de fallo que research.md D3-bis identifica como la principal
        // amenaza de este límite: castigar a quien no hizo nada.
        mockMvc.perform(publicSubmission("203.0.113.43", PUBLIC_TRADE,
                        filledForm("Estudiante Del Lado", "SIN-DATO-REAL-302")))
                .andExpect(status().isCreated());
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
    /** Fija el origen del envío: sin esto todos los tests comparten la clave del contador. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(
            String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }

    /**
     * El envío va deliberadamente SIN sesión y SIN token CSRF, y CON un origen que el
     * test nombra.
     *
     * EL ORIGEN ES OBLIGATORIO en la firma, y lo es desde que existe el filtro de la
     * US3: los IT comparten contexto y contador, así que si todos los tests usaran el
     * 127.0.0.1 por omisión de MockMvc compartirían la clave del límite y el que más
     * envía bloquearía a los demás. Pasó de verdad —tres tests de la US1 empezaron a
     * devolver 429 al conectar el filtro—, y por eso el parámetro no tiene default.
     */
    private MockHttpServletRequestBuilder publicSubmission(
            String origin, String definitionCode, Map<String, Object> body) {
        return post("/api/public/requests/" + definitionCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body))
                .with(from(origin));
    }

    private void assertRejectedWithoutRegistering(String origin, Map<String, Object> body)
            throws Exception {
        long registeredBefore = requestRepo.count();

        mockMvc.perform(publicSubmission(origin, PUBLIC_TRADE, body))
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
