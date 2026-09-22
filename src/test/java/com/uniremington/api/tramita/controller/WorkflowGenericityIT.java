package com.uniremington.api.tramita.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
// Paquete de Boot 4 (modularizado): antes org.springframework.boot.test.autoconfigure.web.servlet
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IT de la tesis del motor genérico (US4, T034/T035): dos trámites de
 * profundidad distinta y un trámite DEMO cargado por SQL en runtime operan
 * sobre el mismo código sin una línea a medida. Si esta clase necesitara tocar
 * src/main para pasar, la tesis estaría rota. Mismas properties que
 * AuthControllerIT para compartir el contexto cacheado.
 */
@TramitaIntegrationTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowGenericityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // --- T034: los dos trámites de la semilla, mismo motor -------------------------------

    @Test
    @DisplayName("novedad de notas recorre su cadena completa — más profunda, mismo motor")
    void novedadWalksItsOwnChainToFinalState() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "NOVEDAD_NOTAS", "Genérica Uno", "601");

        for (String state : new String[] {
                "EN_PREPARACION", "EN_FACULTAD", "EN_REVISION_FINANCIERA", "EN_REGISTRO_CONTROL"}) {
            mockMvc.perform(advanceRequest(id, state, null).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentState.code").value(state));
        }

        mockMvc.perform(advanceRequest(id, "FINALIZADA", null).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.isFinal").value(true))
                .andExpect(jsonPath("$.availableTransitions").isEmpty());
    }

    @Test
    @DisplayName("la devolución de novedad retorna a EN_PREPARACION: donde vive la carpeta editable")
    void novedadReturnGoesBackToPreparacion() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "NOVEDAD_NOTAS", "Genérica Dos", "602");
        mockMvc.perform(advanceRequest(id, "EN_PREPARACION", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());

        // En novedad la devolución NO es un estado (como DEVUELTA en adición):
        // es la transición de retorno — misma estructura, dos modelados (SC-004)
        mockMvc.perform(advanceRequest(id, "EN_PREPARACION", "Falta la firma del docente")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.code").value("EN_PREPARACION"));
    }

    @Test
    @DisplayName("el rechazo existe en adición y NO en novedad: la asimetría vive en la definición (FR-015)")
    void rejectionExistsOnlyWhereTheDefinitionDeclaresIt() throws Exception {
        MockHttpSession session = login();

        // Adición: la facultad puede negar una solicitud extemporánea
        String adicion = registerAndGetId(session, "ADICION_CREDITOS", "Rechazable", "603");
        mockMvc.perform(advanceRequest(adicion, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(adicion, "RECHAZADA", null).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.isFinal").value(true));

        // Novedad: «por más que se demoren, siempre termina» (E3-Q19) — la
        // transición no existe y el motor la bloquea sin saber por qué no existe
        String novedad = registerAndGetId(session, "NOVEDAD_NOTAS", "Imparable", "604");
        mockMvc.perform(advanceRequest(novedad, "EN_PREPARACION", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(novedad, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(novedad, "RECHAZADA", null).session(session))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // --- T035: SC-005 — trámite nuevo por SQL en runtime, sin deploy ---------------------

    @Test
    @Order(1)
    @DisplayName("un trámite DEMO cargado por SQL en runtime queda operable de inmediato (SC-005)")
    void liveLoadedDefinitionIsImmediatelyOperable() throws Exception {
        MockHttpSession session = login();
        insertDemoV1();

        // El catálogo lo ve sin reiniciar: no hay caché de definiciones (D10).
        // El filtro de JsonPath devuelve array: se asserta con hasItem
        mockMvc.perform(get("/api/workflow-definitions").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'DEMO')].version")
                        .value(org.hamcrest.Matchers.hasItem(1)));

        String id = registerAndGetId(session, "DEMO", "Demostración Viva", "605");
        mockMvc.perform(advanceRequest(id, "CERRADO", null).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.isFinal").value(true));
    }

    @Test
    @Order(2)
    @DisplayName("una v2 rige solo a las solicitudes nuevas; las viejas conservan sus reglas (FR-009)")
    void newVersionGovernsOnlyNewRequests() throws Exception {
        MockHttpSession session = login();
        insertDemoV1();
        // Solicitud vieja, nacida bajo v1 (ABIERTO → CERRADO directo)
        String oldRequest = registerAndGetId(session, "DEMO", "Nacida En V1", "606");

        // Entra la v2: el cierre ahora exige pasar por REVISION
        insertDemoV2();

        // Las nuevas nacen bajo v2 y su camino directo a CERRADO no existe
        String newRequest = registerAndGetId(session, "DEMO", "Nacida En V2", "607");
        mockMvc.perform(get("/api/requests/" + newRequest).session(session))
                .andExpect(jsonPath("$.definition.version").value(2))
                .andExpect(jsonPath("$.availableTransitions[0].targetState.code").value("REVISION"));
        mockMvc.perform(advanceRequest(newRequest, "CERRADO", null).session(session))
                .andExpect(status().isConflict());

        // La vieja sigue rigiéndose por la definición con la que nació
        mockMvc.perform(advanceRequest(oldRequest, "CERRADO", null).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definition.version").value(1))
                .andExpect(jsonPath("$.currentState.isFinal").value(true));
    }

    /**
     * Invariante de configuración (007, T018 / FR-014). NO es un RED: el seed lo cumple.
     * Existe porque la base no lo garantiza —V2.2.0 solo impide dos iniciales por
     * definición y las transiciones a sí mismo— y porque de él dependen dos cosas de la
     * bandeja: que un trámite cerrado salga «por construcción» (de un estado final no
     * hay transiciones) y que ninguna solicitud quede detenida sin responsable posible
     * (todo estado no final tiene una salida). Recorre TODA definición presente en la
     * base al correr, incluida la DEMO cargada por SQL: si mañana entra un área o un
     * trámite nuevo por configuración, el invariante lo cubre sin tocar este test.
     */
    @Test
    @Order(3)
    @DisplayName("invariante de configuración: ningún estado final tiene salidas y todo estado no final tiene al menos una (FR-014)")
    void everyDefinitionHasNoExitFromFinalStatesAndAnExitFromEveryOtherState() {
        insertDemoV1();
        insertDemoV2();

        List<String> finalStatesWithExits = jdbcTemplate.queryForList("""
                SELECT d.code || ' v' || d.version || ': ' || s.code
                FROM workflow_state s JOIN workflow_definition d ON d.id = s.definition_id
                WHERE s.is_final
                  AND EXISTS (SELECT 1 FROM workflow_transition t WHERE t.from_state_id = s.id)
                """, String.class);
        List<String> deadEnds = jdbcTemplate.queryForList("""
                SELECT d.code || ' v' || d.version || ': ' || s.code
                FROM workflow_state s JOIN workflow_definition d ON d.id = s.definition_id
                WHERE NOT s.is_final
                  AND NOT EXISTS (SELECT 1 FROM workflow_transition t WHERE t.from_state_id = s.id)
                """, String.class);

        assertThat(finalStatesWithExits)
                .as("un estado final con salidas rompe «cerrado = fuera de toda bandeja»")
                .isEmpty();
        assertThat(deadEnds)
                .as("un estado no final sin salida deja solicitudes detenidas sin responsable posible (FR-014)")
                .isEmpty();
    }

    // --- helpers -------------------------------------------------------------------------

    // --- T040: SC-005 aplicado a la bandeja (007) ---------------------------------------

    /**
     * SC-005 de la 007: un trámite incorporado por configuración, a cargo de un área que
     * ningún código conoce, aparece en la bandeja de esa área sin desplegar nada. Si este
     * test necesitara tocar src/main para pasar, el §VI estaría roto en la bandeja. El
     * responsable se elige inexistente a propósito: con COORDINACION no se distinguiría
     * leer la configuración de tener el nombre del área cableado.
     */
    @Test
    @Order(4)
    @DisplayName("un trámite nuevo por SQL, con un área nueva, aparece en la bandeja de esa área sin tocar el motor (SC-005)")
    void liveLoadedDefinitionShowsUpInTheInboxOfItsOwnResponsible() throws Exception {
        MockHttpSession session = login();
        insertDefinition("SC005_BANDEJA", 1, "Trámite sembrado en caliente", "MESA_DE_AYUDA",
                new String[][] {{"ABIERTO", "CERRADO"}});

        String id = registerAndGetId(session, "SC005_BANDEJA", "Sembrada En Caliente", "608");

        String ownInbox = inboxOf(session, "MESA_DE_AYUDA");
        List<String> ownIds = com.jayway.jsonpath.JsonPath.read(ownInbox, "$[*].id");
        assertThat(ownIds).contains(id);
        List<String> pending = com.jayway.jsonpath.JsonPath.read(
                ownInbox, "$[?(@.id == '" + id + "')].pendingResponsible");
        assertThat(pending).containsExactly("MESA_DE_AYUDA");

        // El área que ya existía no la ve: la bandeja lee la configuración, no un supuesto.
        List<String> coordinationIds = com.jayway.jsonpath.JsonPath.read(
                inboxOf(session, "COORDINACION"), "$[*].id");
        assertThat(coordinationIds).doesNotContain(id);

        // Cerrada, sale de la bandeja de su área por construcción: CERRADO no tiene salidas.
        mockMvc.perform(advanceRequest(id, "CERRADO", null).session(session))
                .andExpect(status().isOk());
        List<String> afterClosing = com.jayway.jsonpath.JsonPath.read(
                inboxOf(session, "MESA_DE_AYUDA"), "$[*].id");
        assertThat(afterClosing).doesNotContain(id);
    }

    /** La bandeja de un responsable con la cota máxima: la base es compartida entre IT. */
    private String inboxOf(MockHttpSession session, String responsible) throws Exception {
        return mockMvc.perform(get("/api/requests/inbox")
                        .param("responsible", responsible)
                        .param("limit", "200")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** DEMO v1: ABIERTO → CERRADO directo. Idempotente para no chocar entre tests. */
    private void insertDemoV1() {
        insertDefinition("DEMO", 1, "Trámite de demostración", "COORDINACION",
                new String[][] {{"ABIERTO", "CERRADO"}});
    }

    /** DEMO v2: el cierre pasa por REVISION — la edición es un INSERT (research.md D2). */
    private void insertDemoV2() {
        insertDefinition("DEMO", 2, "Trámite de demostración", "COORDINACION",
                new String[][] {{"ABIERTO", "REVISION"}, {"REVISION", "CERRADO"}});
    }

    /**
     * Siembra por SQL una definición con estados ABIERTO (inicial) y CERRADO (final) y
     * las transiciones dadas, todas a cargo del mismo responsable. El responsable es
     * parámetro a propósito: SC-005 de la 007 necesita un área que ningún código
     * conozca. Idempotente por código y versión.
     */
    private void insertDefinition(String code, int version, String name, String responsible,
            String[][] transitions) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM workflow_definition WHERE code = ? AND version = ?",
                Integer.class, code, version);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO workflow_definition (id, code, version, name, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, now())
                """, code, version, name);
        // Solo los estados que las transiciones conectan: la v1 declaraba REVISION sin
        // usarlo, y un estado no final sin salida es justo lo que el invariante de
        // configuración (FR-014) señala — con razón. Un fixture no puede ser la
        // excepción de la regla que el sistema afirma.
        Set<String> states = new LinkedHashSet<>();
        for (String[] t : transitions) {
            states.add(t[0]);
            states.add(t[1]);
        }
        for (String state : states) {
            jdbcTemplate.update("""
                    INSERT INTO workflow_state (id, definition_id, code, name, is_initial, is_final)
                    SELECT gen_random_uuid(), d.id, ?, initcap(?), ?, ?
                    FROM workflow_definition d WHERE d.code = ? AND d.version = ?
                    """, state, state, "ABIERTO".equals(state), "CERRADO".equals(state), code, version);
        }
        for (String[] t : transitions) {
            jdbcTemplate.update("""
                    INSERT INTO workflow_transition
                        (id, definition_id, from_state_id, to_state_id, responsible, requires_note)
                    SELECT gen_random_uuid(), d.id, f.id, s.id, ?, false
                    FROM workflow_definition d
                    JOIN workflow_state f ON f.definition_id = d.id AND f.code = ?
                    JOIN workflow_state s ON s.definition_id = d.id AND s.code = ?
                    WHERE d.code = ? AND d.version = ?
                    """, responsible, t[0], t[1], code, version);
        }
    }

    private String registerAndGetId(MockHttpSession session, String definitionCode,
            String studentName, String studentDocument) throws Exception {
        String body = mockMvc.perform(post("/api/requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionCode\":\"%s\",\"studentName\":\"%s\",\"studentDocument\":\"%s\"}"
                                .formatted(definitionCode, studentName, studentDocument))
                        .session(session))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder advanceRequest(
            String id, String targetStateCode, String note) {
        String body = note == null
                ? "{\"targetStateCode\":\"%s\"}".formatted(targetStateCode)
                : "{\"targetStateCode\":\"%s\",\"note\":\"%s\"}".formatted(targetStateCode, note);
        return post("/api/requests/" + id + "/transitions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
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
