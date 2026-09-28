package com.uniremington.api.tramita.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import com.uniremington.api.tramita.model.RequestDocumentSeal;
import com.uniremington.api.tramita.repo.IRequestDocumentSealRepo;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
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
 * IT de la verificación exacta de un documento contra su sello, con sesión (006, T031,
 * FR-014d, research.md D10).
 *
 * Lo que este IT prueba y ningún unitario podría: el ciclo completo emitir → hashear →
 * verificar atravesando el filter chain de verdad, y la distinción entre {@code 401} (sin
 * sesión) y {@code 403} (sin token CSRF) — la lección del caso CSRF→403 ya documentada para
 * el login: sin {@code .with(csrf())} el filtro de CSRF corta ANTES de que Spring Security
 * llegue a evaluar si hay sesión, y el código equivocado da un {@code 403} que no dice nada
 * sobre autenticación.
 */
@TramitaIntegrationTest
@AutoConfigureMockMvc
class SealControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IRequestDocumentSealRepo sealRepo;

    @Test
    @DisplayName("documento tal como se emitió: INTACT con el correo de quien lo emitió")
    void verifyingTheIssuedDocumentGivesIntactWithIssuer() throws Exception {
        MockHttpSession session = login();
        String requestId = registerAndGetId(session, "Ana Verifica Intacto", "SIN-DATO-REAL-611");
        byte[] document = documentOf(session, requestId);
        String code = onlySealCodeOf(requestId);

        mockMvc.perform(verify(code, sha256(document)).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("INTACT"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.issuedBy").value(AuthControllerIT.SEED_EMAIL));
    }

    @Test
    @DisplayName("un byte alterado: TAMPERED")
    void verifyingAnAlteredDocumentGivesTampered() throws Exception {
        MockHttpSession session = login();
        String requestId = registerAndGetId(session, "Ana Verifica Alterado", "SIN-DATO-REAL-612");
        byte[] document = documentOf(session, requestId);
        String code = onlySealCodeOf(requestId);

        mockMvc.perform(verify(code, sha256(alter(document))).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TAMPERED"));
    }

    @Test
    @DisplayName("código que el sistema nunca emitió: 404 problem+json")
    void verifyingAnUnknownCodeIsNotFound() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(verify("ZZZZZZZZZZZZZ", "0".repeat(64)).session(session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("sin sesión, CON token CSRF: 401 — es autenticación, no CSRF")
    void verifyingWithoutSessionIsUnauthorized() throws Exception {
        // .with(csrf()) a propósito: sin él el filtro de CSRF corta primero y el código sería
        // 403, midiendo otra cosa. Este test aísla específicamente la falta de sesión.
        mockMvc.perform(verify("ZZZZZZZZZZZZZ", "0".repeat(64)).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cuerpo inválido — huella que no tiene forma de SHA-256: 400, no 422")
    void verifyingWithAMalformedFingerprintIsBadRequest() throws Exception {
        MockHttpSession session = login();

        // D10: acá el cliente es el frontend de la Coordinación; un campo mal formado es un
        // defecto de contrato (400), no un formulario a medio llenar (422, exclusivo del
        // canal público de captura).
        mockMvc.perform(verify("ABC123", "no-es-una-huella").session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("emitido, alterado y con el trámite avanzado: NO VERIFICABLE por datos, no ALTERADO (FR-007)")
    void verifyingAnAlteredDocumentAfterTheRequestAdvancedIsNotVerifiable() throws Exception {
        MockHttpSession session = login();
        String requestId = registerAndGetId(session, "Ana Verifica Fr007", "SIN-DATO-REAL-613");
        byte[] document = documentOf(session, requestId);
        String code = onlySealCodeOf(requestId);

        // El trámite avanza DESPUÉS de emitir: la revisión sellada queda vieja (research.md D7).
        mockMvc.perform(advance(requestId, "EN_FACULTAD").session(session))
                .andExpect(status().isOk());

        // Con la huella alterada Y la revisión avanzada, ninguna de las dos coincide: FR-007
        // exige explicar por la guarda legítima (datos) antes de acusar.
        mockMvc.perform(verify(code, sha256(alter(document))).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_VERIFIABLE"))
                .andExpect(jsonPath("$.reason").value("DATA_CHANGED"));
    }

    // --- helpers -------------------------------------------------------------------------

    private byte[] documentOf(MockHttpSession session, String requestId) throws Exception {
        return mockMvc.perform(get("/api/requests/" + requestId + "/document").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private String onlySealCodeOf(String requestId) {
        List<RequestDocumentSeal> seals =
                sealRepo.findByRequestIdOrderByIssuedAtAscIdAsc(UUID.fromString(requestId));
        return seals.get(seals.size() - 1).getVerificationCode();
    }

    private static byte[] alter(byte[] document) {
        byte[] altered = document.clone();
        altered[altered.length / 2] ^= 0x01;
        return altered;
    }

    /** La misma huella que calcula {@code DocumentServiceImpl} al emitir (research.md D2). */
    private static String sha256(byte[] document) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 es parte de la plataforma", impossible);
        }
    }

    private MockHttpServletRequestBuilder verify(String code, String sha256) {
        return post("/api/seals/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"sha256\":\"%s\"}".formatted(code, sha256));
    }

    private MockHttpServletRequestBuilder advance(String id, String targetStateCode) {
        return post("/api/requests/" + id + "/transitions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStateCode\":\"%s\"}".formatted(targetStateCode));
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
