package com.uniremington.api.tramita.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TestcontainersConfiguration;
import com.uniremington.api.tramita.repo.IRequestRepo;
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

/**
 * IT del ciclo de vida de solicitudes sobre la semilla real (T014 RED antes de
 * T018-T021): contexto completo con PostgreSQL (Testcontainers) + Flyway + los
 * dos trámites de V2.1.0, ejercitando el filter chain de verdad — los
 * escenarios 401 son los acceptance de FR-012. Mismas properties que
 * AuthControllerIT para compartir el contexto cacheado entre ITs.
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
class RequestControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IRequestRepo requestRepo;

    // --- US1: registrar ------------------------------------------------------------------

    @Test
    @DisplayName("registrar adición de créditos: 201 + Location, nace en REGISTRADA con sus transiciones")
    void registerCreatesRequestInInitialStateOfItsDefinition() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(createRequest("ADICION_CREDITOS", "Ana María Pérez", "1144099888")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.definition.code").value("ADICION_CREDITOS"))
                .andExpect(jsonPath("$.definition.version").value(1))
                .andExpect(jsonPath("$.studentName").value("Ana María Pérez"))
                .andExpect(jsonPath("$.currentState.code").value("REGISTRADA"))
                .andExpect(jsonPath("$.currentState.isFinal").value(false))
                // Las transiciones salen de la definición, no de código a medida
                .andExpect(jsonPath("$.availableTransitions[0].targetState.code")
                        .value("EN_FACULTAD"));
    }

    @Test
    @DisplayName("los dos trámites coexisten: cada solicitud nace en el estado inicial de SU definición")
    void requestsOfBothProceduresCoexistWithTheirOwnInitialState() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(createRequest("ADICION_CREDITOS", "Estudiante Uno", "111")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentState.code").value("REGISTRADA"))
                .andExpect(jsonPath("$.availableTransitions[0].targetState.code")
                        .value("EN_FACULTAD"));

        // Novedad de notas arranca igual (REGISTRADA) pero su camino es propio:
        // hacia EN_PREPARACION — la diferencia vive en la definición (US4/FR-010)
        mockMvc.perform(createRequest("NOVEDAD_NOTAS", "Estudiante Dos", "222")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentState.code").value("REGISTRADA"))
                .andExpect(jsonPath("$.availableTransitions[0].targetState.code")
                        .value("EN_PREPARACION"));
    }

    @Test
    @DisplayName("registrar sin sesión: 401 y no se persiste nada (FR-012)")
    void registerWithoutSessionIsRejectedAndPersistsNothing() throws Exception {
        long requestsBefore = requestRepo.count();

        mockMvc.perform(createRequest("ADICION_CREDITOS", "Sin Sesión", "999"))
                .andExpect(status().isUnauthorized());

        assertThat(requestRepo.count()).isEqualTo(requestsBefore);
    }

    @Test
    @DisplayName("registrar un tipo de trámite inexistente: 422 problem+json con el motivo")
    void registerUnknownProcedureTypeReturns422() throws Exception {
        mockMvc.perform(createRequest("TRAMITE_FANTASMA", "Ana", "123").session(login()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());
    }

    // --- helpers -------------------------------------------------------------------------

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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest(
            String definitionCode, String studentName, String studentDocument) {
        return post("/api/requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"definitionCode\":\"%s\",\"studentName\":\"%s\",\"studentDocument\":\"%s\"}"
                        .formatted(definitionCode, studentName, studentDocument));
    }
}
