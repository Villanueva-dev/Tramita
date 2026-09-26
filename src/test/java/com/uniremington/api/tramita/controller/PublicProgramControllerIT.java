package com.uniremington.api.tramita.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Paquete de Boot 4 (modularizado): antes org.springframework.boot.test.autoconfigure.web.servlet
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IT del catálogo público de programas (009, FR-001, SC-004). Se escribió en RED antes de
 * T019-T022: entonces la ruta no existía y caía en {@code anyRequest().authenticated()}
 * (401). Hoy {@code PUBLIC_PROGRAMS} está en el {@code permitAll} de SecurityConfig, y este
 * test es la guarda que lo vigila (mutante de T029).
 *
 * Ningún dato de este test es real (constitución §III): el nombre que se busca es
 * un programa sembrado por V5.1.0, no el dato de un estudiante.
 */
@TramitaIntegrationTest
@AutoConfigureMockMvc
class PublicProgramControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("catálogo público de programas sin sesión ni CSRF: 200 con solo el nombre (009, FR-001, SC-004)")
    void publicProgramsAreListedWithoutSession() throws Exception {
        String response = mockMvc.perform(get("/api/public/programs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // Caso borde «Catálogo vacío» de la spec: la siembra V5.1.0 no lo deja
                // vacío, así que no hay forma de ejercitarlo con datos reales aquí.
                .andExpect(jsonPath("$.length()", org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[*].name",
                        org.hamcrest.Matchers.hasItem("Ingeniería de Sistemas")))
                // Solo el nombre viaja (FR-001, SC-004): sin id. No se asierta el tamaño
                // exacto —WorkflowGenericityIT inserta programas en caliente sobre la
                // misma base (research.md D10)— ni un orden total (D5), ni la lista
                // completa de trece: es provisional y corregirla es un cambio de datos,
                // no de tests (FR-006).
                .andExpect(jsonPath("$[*].id").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(response)
                .as("el catálogo público no expone identificadores internos (SC-004)")
                .doesNotContain("\"id\"");
    }
}
