package com.uniremington.api.tramita.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import com.uniremington.api.tramita.model.RequestDocumentSeal;
import com.uniremington.api.tramita.repo.IRequestDocumentSealRepo;
import java.util.List;
// Paquete de Boot 4 (modularizado): antes org.springframework.boot.test.autoconfigure.web.servlet
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * IT del canal público de verificación por posesión del código impreso (006, T030, FR-014b).
 *
 * Lo que este IT prueba y ningún test unitario podría: que {@code GET /api/public/seals/{code}}
 * atraviesa el filter chain de seguridad DE VERDAD sin sesión (research.md D9) — el permitAll
 * de {@code SecurityConfig} solo es observable con los filtros activos.
 *
 * Ningún dato de estos tests es real (constitución §III): nombres inventados y documentos con
 * prefijo SIN-DATO-REAL.
 */
@TramitaIntegrationTest
@AutoConfigureMockMvc
class PublicSealControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IRequestDocumentSealRepo sealRepo;

    @Test
    @DisplayName("código de un sello emitido, sin sesión: 200 ISSUED con fecha, estado y revisión")
    void lookupOfAnIssuedCodeWithoutSessionReturnsIssued() throws Exception {
        String studentName = "Ana Consulta Publica";
        MockHttpSession session = login();
        String requestId = registerAndGetId(session, studentName, "SIN-DATO-REAL-601");

        mockMvc.perform(get("/api/requests/" + requestId + "/document").session(session))
                .andExpect(status().isOk());

        String code = onlySealOf(requestId).getVerificationCode();

        // SIN .session(...): es lo que tiene quien recibió el papel y no tiene cuenta.
        String body = mockMvc.perform(get("/api/public/seals/" + code))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                // #34 M1: verifica que Jackson 3 serializa OffsetDateTime como string ISO CON
                // offset (contrastable contra el pie impreso), no como timestamp numérico ni
                // sin zona — es la comprobación empírica que motivó el cambio de tipo.
                .andExpect(jsonPath("$.issuedAt", org.hamcrest.Matchers.matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?-05:00$")))
                .andExpect(jsonPath("$.stateName").isNotEmpty())
                .andExpect(jsonPath("$.revision").isNumber())
                .andReturn().getResponse().getContentAsString();

        // FR-014c: ni el nombre ni el documento del estudiante viajan por este canal.
        assertThat(body)
                .as("el canal público no lleva ningún dato personal del solicitante")
                .doesNotContain(studentName)
                .doesNotContain("SIN-DATO-REAL-601");
    }

    @Test
    @DisplayName("código que el sistema nunca emitió: 404 problem+json")
    void lookupOfAnUnknownCodeIsNotFound() throws Exception {
        mockMvc.perform(get("/api/public/seals/ZZZZZZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("código en minúsculas: 200 ISSUED, el mismo sello que en mayúsculas (#34 M2)")
    void lookupWithLowercaseCodeStillFindsTheSeal() throws Exception {
        MockHttpSession session = login();
        String requestId = registerAndGetId(session, "Ana Consulta Minusculas", "SIN-DATO-REAL-602");

        mockMvc.perform(get("/api/requests/" + requestId + "/document").session(session))
                .andExpect(status().isOk());

        // El generador solo emite mayúsculas, pero nada en el papel impreso obliga a
        // transcribirlas así: quien copia «abc123» del pie no puede recibir un 404 falso.
        String code = onlySealOf(requestId).getVerificationCode();

        mockMvc.perform(get("/api/public/seals/" + code.toLowerCase(java.util.Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"));
    }

    // --- helpers -------------------------------------------------------------------------

    private RequestDocumentSeal onlySealOf(String requestId) {
        List<RequestDocumentSeal> seals =
                sealRepo.findByRequestIdOrderByIssuedAtAscIdAsc(java.util.UUID.fromString(requestId));
        return seals.get(seals.size() - 1);
    }

    private String registerAndGetId(MockHttpSession session, String studentName, String studentDocument)
            throws Exception {
        String body = mockMvc.perform(createRequest(studentName, studentDocument).session(session))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private MockHttpServletRequestBuilder createRequest(String studentName, String studentDocument) {
        return post("/api/requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"definitionCode\":\"ADICION_CREDITOS\",\"studentName\":\"%s\",\"studentDocument\":\"%s\"}"
                        .formatted(studentName, studentDocument));
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
}
