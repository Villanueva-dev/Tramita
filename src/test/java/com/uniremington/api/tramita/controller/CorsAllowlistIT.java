package com.uniremington.api.tramita.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Paquete de Boot 4 (modularizado): antes org.springframework.boot.test.autoconfigure.web.servlet
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ejercita la allowlist de CORS (issue #20). Existe porque el valor de
 * APP_CORS_ALLOWED_ORIGINS apuntó dos meses a un puerto que nadie usaba y ningún
 * test lo detectó: los IT la declaraban solo para que arrancara el contexto.
 *
 * Un test que enviara el origen esperado habría fallado el mismo día.
 */
@TramitaIntegrationTest
@AutoConfigureMockMvc
class CorsAllowlistIT {

    private static final String ALLOWED_ORIGIN = TramitaIntegrationTest.ALLOWED_ORIGIN;

    private static final String FOREIGN_ORIGIN = "https://suplantador.example";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("el origen de la allowlist pasa y recibe las cabeceras CORS")
    void allowedOriginIsAccepted() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("un origen ajeno se rechaza con 403, sin cabeceras CORS")
    void foreignOriginIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("el preflight del canal público se aprueba para el origen de la allowlist")
    void preflightFromAllowedOriginIsApproved() throws Exception {
        mockMvc.perform(options("/api/public/requests/ADICION_CREDITOS")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("Retry-After se expone: sin eso el SPA no puede leerlo del 429 (D3, JD3-001)")
    void retryAfterIsExposedToTheBrowser() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
    }
}
