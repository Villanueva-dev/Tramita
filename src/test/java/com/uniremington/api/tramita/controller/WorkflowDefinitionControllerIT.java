package com.uniremington.api.tramita.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

/**
 * IT del catálogo de definiciones vigentes (T015 RED antes de T017/T019):
 * el formulario de registro se alimenta de aquí — el frontend no hardcodea
 * trámites. Mismas properties que AuthControllerIT para compartir contexto.
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
class WorkflowDefinitionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("lista las definiciones vigentes de la semilla: los dos trámites en v1")
    void listsCurrentDefinitionsFromSeed() throws Exception {
        mockMvc.perform(get("/api/workflow-definitions").session(login()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // Orden por nombre: Adición de créditos < Novedad de notas
                .andExpect(jsonPath("$[0].code").value("ADICION_CREDITOS"))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[1].code").value("NOVEDAD_NOTAS"))
                .andExpect(jsonPath("$[1].version").value(1));
    }

    @Test
    @DisplayName("sin sesión: 401 (FR-012)")
    void withoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/workflow-definitions"))
                .andExpect(status().isUnauthorized());
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
}
