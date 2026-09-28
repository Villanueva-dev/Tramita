package com.uniremington.api.tramita.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.uniremington.api.tramita.TramitaIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Paquete de Boot 4 (modularizado): antes org.springframework.boot.test.autoconfigure.web.servlet
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IT del catálogo de definiciones vigentes (T015 RED antes de T017/T019):
 * el formulario de registro se alimenta de aquí — el frontend no hardcodea
 * trámites. Mismas properties que AuthControllerIT para compartir contexto.
 */
@TramitaIntegrationTest
@AutoConfigureMockMvc
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

    // --- 007, US3: el catálogo expone los estados con sus marcas (issue #22) -------------
    // Los tres leen el cuerpo con JsonPath y filtran por code: la base es compartida
    // entre IT y el catálogo puede traer también la definición DEMO de
    // WorkflowGenericityIT, así que $[0]/$[1] no bastan para afirmar sobre un trámite.

    @Test
    @DisplayName("cada definición trae sus estados, y cada estado sus marcas de inicial y final (FR-011a)")
    void everyDefinitionCarriesItsStatesWithBothMarks() throws Exception {
        String body = mockMvc.perform(get("/api/workflow-definitions").session(login()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> definitions = JsonPath.read(body, "$[*]");
        assertThat(definitions).isNotEmpty();
        for (Map<String, Object> definition : definitions) {
            Object states = definition.get("states");
            assertThat(states)
                    .as("la definición %s debe traer su lista de estados", definition.get("code"))
                    .isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stateList = (List<Map<String, Object>>) states;
            assertThat(stateList)
                    .as("la definición %s no puede tener cero estados", definition.get("code"))
                    .isNotEmpty();
            for (Map<String, Object> state : stateList) {
                assertThat(state)
                        .as("estado %s de %s", state.get("code"), definition.get("code"))
                        .containsKeys("code", "name", "isInitial", "isFinal");
                assertThat(state.get("isInitial")).isInstanceOf(Boolean.class);
                assertThat(state.get("isFinal")).isInstanceOf(Boolean.class);
            }
            // Las marcas tienen que decir algo, no solo existir: exactamente un inicial por
            // definición (uq_workflow_state_one_initial_per_definition) y al menos un cierre.
            // Sin esto, un isInitial siempre false pasaría por aquí sin que nadie lo note.
            assertThat(stateList.stream().filter(s -> Boolean.TRUE.equals(s.get("isInitial"))))
                    .as("la definición %s debe declarar exactamente un estado inicial", definition.get("code"))
                    .hasSize(1);
            assertThat(stateList.stream().filter(s -> Boolean.TRUE.equals(s.get("isFinal"))))
                    .as("la definición %s debe declarar al menos un estado final", definition.get("code"))
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("el estado inicial es distinto en cada trámite: ninguna constante global del cliente acertaría (issue #22)")
    void initialStateDiffersBetweenTheTwoSeededDefinitions() throws Exception {
        String body = mockMvc.perform(get("/api/workflow-definitions").session(login()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // V3.2.0 renombró el inicial SOLO de adición de créditos: un cliente con la
        // constante REGISTRADA dejó de acertar sin que nada fallara. Este test es lo
        // que habría avisado.
        List<String> creditsInitial = JsonPath.read(body,
                "$[?(@.code == 'ADICION_CREDITOS')].states[?(@.isInitial == true)].code");
        List<String> gradesInitial = JsonPath.read(body,
                "$[?(@.code == 'NOVEDAD_NOTAS')].states[?(@.isInitial == true)].code");

        assertThat(creditsInitial).containsExactly("EN_COORDINACION");
        assertThat(gradesInitial).containsExactly("REGISTRADA");
        assertThat(creditsInitial).isNotEqualTo(gradesInitial);
    }

    /**
     * GUARDA, no RED: los tres campos ya existían antes de la 007 y este test está verde
     * desde que se escribió. Fija la aditividad (FR-011c) para que el DTO del catálogo no
     * pierda lo que un cliente de la 002 ya lee.
     */
    @Test
    @DisplayName("guarda de aditividad: code, name y version siguen ahí con el mismo significado (FR-011c)")
    void catalogRemainsAdditiveForExistingClients() throws Exception {
        String body = mockMvc.perform(get("/api/workflow-definitions").session(login()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(body, "$[?(@.code == 'ADICION_CREDITOS')].name");
        List<Integer> versions = JsonPath.read(body, "$[?(@.code == 'ADICION_CREDITOS')].version");
        assertThat(names).containsExactly("Adición de créditos");
        assertThat(versions).containsExactly(1);
        assertThat((List<String>) JsonPath.read(body, "$[?(@.code == 'NOVEDAD_NOTAS')].name"))
                .containsExactly("Novedad de notas");
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
