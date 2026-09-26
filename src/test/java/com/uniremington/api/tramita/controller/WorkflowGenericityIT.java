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

    // --- 008 (SC-005): el aviso se ofrece igual en un trámite que el código no conoce -------

    @Test
    @DisplayName("un trámite cargado por SQL expone isFinal y origin al cerrarse, sin que el código sepa que existe (008, SC-005)")
    void liveLoadedDefinitionExposesTheFactsOfTheNoticeWhenClosed() throws Exception {
        MockHttpSession session = login();
        insertDemoV1();
        String id = registerAndGetId(session, "DEMO", "Demostración Con Aviso", "608");

        // El test tampoco conoce el camino: avanza por la única transición disponible
        // hasta que la configuración diga «final». Vale para la v1 (ABIERTO → CERRADO) y
        // para la v2 (pasa por REVISION), según cuál esté vigente al registrar: la
        // solicitud queda atada a la versión con la que nació (FR-009 de la 002).
        String current = mockMvc.perform(get("/api/requests/" + id).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.springframework.test.web.servlet.ResultActions last = null;
        for (int step = 0; step < 5 && !isFinal(current); step++) {
            String next = com.jayway.jsonpath.JsonPath.read(current, "$.availableTransitions[0].targetState.code");
            last = mockMvc.perform(advanceRequest(id, next, null).session(session))
                    .andExpect(status().isOk());
            current = last.andReturn().getResponse().getContentAsString();
        }
        assertThat(last).as("la solicitud DEMO debió llegar a un estado final").isNotNull();

        // La respuesta de la transición de cierre trae los hechos del aviso (FR-008):
        // origin es COORDINATION porque DEMO no habilita captura pública y se registró con
        // sesión; lo que SC-005 demuestra es que «final» y «origen» salen de la
        // configuración y del timeline, no de que el código reconozca a DEMO.
        last.andExpect(jsonPath("$.currentState.isFinal").value(true))
                .andExpect(jsonPath("$.availableTransitions.length()").value(0))
                .andExpect(jsonPath("$.origin").value("COORDINATION"));
    }

    private static boolean isFinal(String detailBody) {
        return Boolean.TRUE.equals(
                com.jayway.jsonpath.JsonPath.read(detailBody, "$.currentState.isFinal"));
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

    // --- FR-014 (007, review A1): el motor rechaza el callejón en runtime -----------------

    /**
     * La guarda de runtime de FR-014. El invariante de @Order(3) solo cubre la configuración
     * presente cuando corre, y la base no lo impide (V2.2.0): una definición cargada por SQL
     * en caliente —la vía que SC-005 promueve— con un estado no final sin salidas dejaría una
     * solicitud detenida sin responsable posible y fuera de toda bandeja, en silencio. El
     * motor la rechaza como configuración rota, con el 500 de configuración, y la solicitud
     * sigue donde estaba y en su bandeja.
     */
    @Test
    @Order(5)
    @DisplayName("avanzar hacia un estado no final sin salidas es configuración rota: 500 problem+json, la solicitud no se mueve ni sale de la bandeja (FR-014)")
    void advancingIntoADeadEndStateIsRejectedAsBrokenConfiguration() throws Exception {
        MockHttpSession session = login();
        // ABIERTO → LIMBO y ABIERTO → CERRADO: LIMBO queda no final y sin salidas.
        insertDefinition("CALLEJON", 1, "Trámite con callejón", "VENTANILLA_CALLEJON",
                new String[][] {{"ABIERTO", "LIMBO"}, {"ABIERTO", "CERRADO"}});
        String id = registerAndGetId(session, "CALLEJON", "Solicitud Sin Salida", "609");
        try {
            mockMvc.perform(advanceRequest(id, "LIMBO", null).session(session))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Configuración del trámite incompleta"));

            mockMvc.perform(get("/api/requests/" + id).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentState.code").value("ABIERTO"));
            List<String> stillPending = com.jayway.jsonpath.JsonPath.read(
                    inboxOf(session, "VENTANILLA_CALLEJON"), "$[*].id");
            assertThat(stillPending).contains(id);

            // Por la salida sana sí avanza: la guarda rechaza el destino, no la solicitud.
            mockMvc.perform(advanceRequest(id, "CERRADO", null).session(session))
                    .andExpect(status().isOk());
        } finally {
            // Ninguna solicitud llegó a LIMBO —la guarda lo impide—, así que el callejón se
            // retira y la configuración sembrada vuelve a cumplir el invariante de @Order(3).
            dropState("CALLEJON", 1, "LIMBO");
        }
    }

    @Test
    @Order(6)
    @DisplayName("registrar en una definición cuyo estado inicial no tiene salidas es configuración rota: 500 y nada se persiste (FR-014)")
    void registeringIntoAnInitialStateWithoutExitsIsRejectedAsBrokenConfiguration() throws Exception {
        MockHttpSession session = login();
        insertLonelyInitialDefinition("SIN_SALIDA");
        try {
            mockMvc.perform(post("/api/requests")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"definitionCode\":\"SIN_SALIDA\",\"studentName\":\"Nadie La Atiende\",\"studentDocument\":\"610\"}")
                            .session(session))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Configuración del trámite incompleta"));

            Integer persisted = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM request r
                    JOIN workflow_definition d ON d.id = r.definition_id
                    WHERE d.code = 'SIN_SALIDA'
                    """, Integer.class);
            assertThat(persisted).isZero();
        } finally {
            dropDefinition("SIN_SALIDA");
        }
    }

    // --- La cota (007, review M1/M2): cuenta solicitudes y corta por radicación -----------

    /**
     * El {@code distinct} de la consulta de la bandeja NO evita duplicados en la lista:
     * Hibernate ya deduplica la entidad raíz de un join. Lo que decide es que la COTA
     * cuente solicitudes y no filas del join: sin él, {@code fetch first N} corta filas
     * antes de deduplicar, y con un estado de dos salidas del mismo responsable devuelve
     * menos de N. Es la razón real del {@code distinct}, y una aserción de «sin duplicados»
     * no la prueba (review M1).
     */
    @Test
    @Order(7)
    @DisplayName("la cota cuenta solicitudes, no filas del join: un estado con dos salidas del mismo responsable no la consume dos veces (review M1)")
    void inboxLimitCountsRequestsNotJoinRows() throws Exception {
        MockHttpSession session = login();
        // ABIERTO tiene DOS salidas del mismo responsable; REVISION cierra para no violar
        // el invariante de configuración.
        insertDefinition("COTA_DOBLE_SALIDA", 1, "Trámite con dos salidas", "VENTANILLA_COTA",
                new String[][] {{"ABIERTO", "CERRADO"}, {"ABIERTO", "REVISION"}, {"REVISION", "CERRADO"}});
        String a = registerAndGetId(session, "COTA_DOBLE_SALIDA", "Cota Uno", "611");
        String b = registerAndGetId(session, "COTA_DOBLE_SALIDA", "Cota Dos", "612");
        String c = registerAndGetId(session, "COTA_DOBLE_SALIDA", "Cota Tres", "613");

        List<String> ids = com.jayway.jsonpath.JsonPath.read(
                inboxOf(session, "VENTANILLA_COTA", 3), "$[*].id");

        assertThat(ids).containsExactlyInAnyOrder(a, b, c);
    }

    /**
     * Bajo la cota, el corte es por radicación ascendente (research.md D8): las N radicadas
     * hace más tiempo. El orden por espera (D5) se aplica después, sobre lo que sobrevivió.
     * Nada lo fijaba (review M2).
     */
    @Test
    @Order(8)
    @DisplayName("bajo la cota, el corte es por radicación ascendente: quedan las N radicadas hace más tiempo (research D8)")
    void inboxLimitCutsByRegistrationAscending() throws Exception {
        MockHttpSession session = login();
        insertDefinition("COTA_ORDEN", 1, "Trámite para el corte", "VENTANILLA_ORDEN",
                new String[][] {{"ABIERTO", "CERRADO"}});
        String first = registerAndGetId(session, "COTA_ORDEN", "Corte Uno", "614");
        String second = registerAndGetId(session, "COTA_ORDEN", "Corte Dos", "615");
        String third = registerAndGetId(session, "COTA_ORDEN", "Corte Tres", "616");

        List<String> ids = com.jayway.jsonpath.JsonPath.read(
                inboxOf(session, "VENTANILLA_ORDEN", 2), "$[*].id");

        // Las dos radicadas primero; la tercera queda fuera. El orden de salida es por
        // espera (D5), que acá coincide con la radicación: nacieron y no se movieron.
        assertThat(ids).containsExactly(first, second);
        assertThat(ids).doesNotContain(third);
    }

    /**
     * T032 (009, FR-011, SC-003): la tesis del motor genérico también alcanza al anexo
     * por programa. DEMO es un trámite que el código no conoce y "Programa De
     * Genericidad" un programa sembrado en caliente; la regla de anexo se siembra
     * DESPUÉS de registrar la solicitud —el detalle la deriva al leer, no al nacer
     * (research.md D2)—. De paso, §VII: leer el detalle no escribe en el timeline.
     */
    @Test
    @Order(9)
    @DisplayName("un trámite y un programa cargados por SQL en runtime también resuelven el anexo, sin tocar el motor (009, FR-011, SC-003)")
    void liveLoadedAnnexRuleResolvesForALiveLoadedProgram() throws Exception {
        MockHttpSession session = login();
        insertDemoV1();
        insertGenericityAnnexProgram();

        String body = registerWithProgramAndGetBody(session, "DEMO", "Genericidad De Anexo",
                "617", "Programa De Genericidad");
        assertThat(body)
                .as("al nacer, todavía no existe regla de anexo para este programa (FR-006)")
                .doesNotContain("\"annexRequirement\"");
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        // DEMO puede estar en v1 o v2 según qué otro test corrió antes (@TestMethodOrder,
        // :37): la regla se ata a la versión CON QUE NACIÓ esta solicitud, no a "DEMO v1".
        String definitionId = jdbcTemplate.queryForObject(
                "SELECT definition_id FROM request WHERE id = ?::uuid", String.class, id);
        insertGenericityAnnexRule(definitionId);

        mockMvc.perform(get("/api/requests/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annexRequirement.documentName").value("Documento de genericidad"))
                .andExpect(jsonPath("$.annexRequirement.sourceHint").value("Solo para WorkflowGenericityIT"));

        // §VII: leer el detalle no escribe en el timeline (precedente
        // RequestControllerIT#readingTheDetailWritesNothingToTheTimeline). Solo hubo
        // nacimiento —sin transiciones—, así que la longitud conocida es 1.
        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/requests/" + id).session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/requests/" + id).session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    /**
     * G-1 (review M-1, research.md D2): la regla de anexo se busca por la VERSIÓN
     * CONCRETA con la que nació la solicitud ({@code definition_id}), no por el código
     * del trámite. Programa NUEVO y exclusivo de este test —no "Programa De
     * Genericidad"— porque @Order(9) ya le siembra una regla a ese programa en la
     * versión de nacimiento; reusarlo haría que el código REAL también encontrara esa
     * otra regla y la aserción "doesNotExist" cayera incluso sin mutar nada.
     */
    @Test
    @Order(10)
    @DisplayName("la regla de anexo de otra versión de la misma definición no aplica (review M-1, research D2)")
    void annexRuleFromAnotherVersionOfTheSameDefinitionDoesNotApply() throws Exception {
        MockHttpSession session = login();
        insertDemoV1();
        insertDemoV2();
        String program = "Programa De Versión Exclusiva";
        insertProgramIfMissing(program);

        String body = registerWithProgramAndGetBody(session, "DEMO", "Version De Nacimiento",
                "618", program);
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        // DEMO ya tiene v1 y v2 sembradas (@Order(1)/@Order(2)): la solicitud nace en la
        // versión vigente, que se lee de la base en vez de asumirse.
        Integer bornVersion = jdbcTemplate.queryForObject(
                "SELECT d.version FROM request r JOIN workflow_definition d ON d.id = r.definition_id "
                        + "WHERE r.id = ?::uuid",
                Integer.class, id);

        // La regla se siembra en la OTRA versión: si el motor la buscara por código en
        // vez de por definition_id, la encontraría igual (el mutante de IWorkflowAnnexRuleRepo).
        int otherVersion = bornVersion == 1 ? 2 : 1;
        insertAnnexRule(definitionIdOf("DEMO", otherVersion), program,
                "Solo en la otra versión", "Solo para WorkflowGenericityIT");

        String detail = mockMvc.perform(get("/api/requests/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annexRequirement").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(detail)
                .as("la regla vive en la versión %d de DEMO; esta solicitud nació en la %d (research.md D2)"
                        .formatted(otherVersion, bornVersion))
                .doesNotContain("\"annexRequirement\"");
    }

    /**
     * G-4b (review B-1, FR-006, research.md D5): el catálogo público no se memoriza. La
     * primera lectura, ANTES de insertar el programa, "calienta" cualquier caché que
     * existiera en el bean; solo si la segunda lectura vuelve a consultar la base
     * aparece el nombre nuevo. Nombre exclusivo de este test —no "Programa De
     * Genericidad"— porque ese ya existe desde @Order(9) y no serviría para medir la
     * diferencia entre la lectura de "antes" y la de "después".
     */
    @Test
    @Order(11)
    @DisplayName("el catálogo público no se memoriza: un programa insertado después de la primera lectura aparece en la segunda (009, FR-006, research D5)")
    void publicProgramCatalogIsNeverCached() throws Exception {
        String program = "Programa De Catálogo En Caliente";

        String before = mockMvc.perform(get("/api/public/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(before)
                .as("el programa todavía no existe: la primera lectura no debe traerlo")
                .doesNotContain(program);

        insertProgramIfMissing(program);

        String after = mockMvc.perform(get("/api/public/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(after)
                .as("sin caché, la segunda lectura vuelve a consultar la base (research.md D5)")
                .contains(program);
    }

    /** Programa sembrado en caliente, sin regla asociada. Idempotente por nombre (009). */
    private void insertProgramIfMissing(String name) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM academic_program WHERE name = ?", Integer.class, name);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO academic_program (id, name) VALUES (gen_random_uuid(), ?)", name);
    }

    /**
     * Regla de anexo para una definición y un programa concretos, idempotente por el
     * mismo criterio que {@code uq_workflow_annex_rule_definition_program} (V5.0.0).
     * Generaliza {@link #insertGenericityAnnexRule} a un programa y un texto arbitrarios.
     */
    private void insertAnnexRule(String definitionId, String programName, String documentName,
            String sourceHint) {
        Integer exists = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM workflow_annex_rule
                WHERE definition_id = ?::uuid
                  AND program_id = (SELECT id FROM academic_program WHERE name = ?)
                """, Integer.class, definitionId, programName);
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO workflow_annex_rule (id, definition_id, program_id, document_name, source_hint)
                SELECT gen_random_uuid(), ?::uuid, p.id, ?, ?
                FROM academic_program p WHERE p.name = ?
                """, definitionId, documentName, sourceHint, programName);
    }

    /** El id de una definición concreta por código y versión, ya sembrada. */
    private String definitionIdOf(String code, int version) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM workflow_definition WHERE code = ? AND version = ?",
                String.class, code, version);
    }

    /** La bandeja de un responsable con la cota máxima: la base es compartida entre IT. */
    private String inboxOf(MockHttpSession session, String responsible) throws Exception {
        return inboxOf(session, responsible, 200);
    }

    private String inboxOf(MockHttpSession session, String responsible, int limit) throws Exception {
        return mockMvc.perform(get("/api/requests/inbox")
                        .param("responsible", responsible)
                        .param("limit", String.valueOf(limit))
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

    /** Programa sembrado en caliente para T032 (009, FR-011). Idempotente por nombre. */
    private void insertGenericityAnnexProgram() {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM academic_program WHERE name = ?",
                Integer.class, "Programa De Genericidad");
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO academic_program (id, name) VALUES (gen_random_uuid(), ?)",
                "Programa De Genericidad");
    }

    /**
     * Regla de anexo sembrada en caliente para la definición CON QUE NACIÓ la solicitud
     * de T032 —no necesariamente DEMO v1—, e idempotente por el mismo criterio que la
     * restricción {@code uq_workflow_annex_rule_definition_program} (V5.0.0).
     */
    private void insertGenericityAnnexRule(String definitionId) {
        Integer exists = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM workflow_annex_rule
                WHERE definition_id = ?::uuid
                  AND program_id = (SELECT id FROM academic_program WHERE name = ?)
                """, Integer.class, definitionId, "Programa De Genericidad");
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO workflow_annex_rule (id, definition_id, program_id, document_name, source_hint)
                SELECT gen_random_uuid(), ?::uuid, p.id, ?, ?
                FROM academic_program p WHERE p.name = ?
                """, definitionId, "Documento de genericidad", "Solo para WorkflowGenericityIT",
                "Programa De Genericidad");
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

    /** Una definición con un solo estado, inicial y no final, sin transiciones: el callejón en el origen. */
    private void insertLonelyInitialDefinition(String code) {
        jdbcTemplate.update("""
                INSERT INTO workflow_definition (id, code, version, name, created_at)
                VALUES (gen_random_uuid(), ?, 1, 'Trámite sin salida', now())
                """, code);
        jdbcTemplate.update("""
                INSERT INTO workflow_state (id, definition_id, code, name, is_initial, is_final)
                SELECT gen_random_uuid(), d.id, 'ABIERTO', 'Abierto', TRUE, FALSE
                FROM workflow_definition d WHERE d.code = ? AND d.version = 1
                """, code);
    }

    /** Retira un estado sembrado y las transiciones que entran a él. Solo vale si ninguna solicitud lo alcanzó. */
    private void dropState(String definitionCode, int version, String stateCode) {
        jdbcTemplate.update("""
                DELETE FROM workflow_transition WHERE to_state_id IN (
                    SELECT s.id FROM workflow_state s
                    JOIN workflow_definition d ON d.id = s.definition_id
                    WHERE d.code = ? AND d.version = ? AND s.code = ?)
                """, definitionCode, version, stateCode);
        jdbcTemplate.update("""
                DELETE FROM workflow_state WHERE id IN (
                    SELECT s.id FROM workflow_state s
                    JOIN workflow_definition d ON d.id = s.definition_id
                    WHERE d.code = ? AND d.version = ? AND s.code = ?)
                """, definitionCode, version, stateCode);
    }

    /** Retira una definición sin transiciones ni parámetros. Solo vale si ninguna solicitud nació de ella. */
    private void dropDefinition(String code) {
        jdbcTemplate.update("""
                DELETE FROM workflow_state WHERE definition_id IN (
                    SELECT id FROM workflow_definition WHERE code = ?)
                """, code);
        jdbcTemplate.update("DELETE FROM workflow_definition WHERE code = ?", code);
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

    /**
     * Registra con el cuerpo COMPLETO —incluye "program"—, a diferencia de
     * {@link #registerAndGetId} de esta clase, que no lo manda (009, T032). Devuelve
     * el cuerpo crudo del 201: el llamador decide qué necesita de él.
     */
    private String registerWithProgramAndGetBody(MockHttpSession session, String definitionCode,
            String studentName, String studentDocument, String program) throws Exception {
        return mockMvc.perform(post("/api/requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"definitionCode":"%s","studentName":"%s","studentDocument":"%s","program":"%s"}
                                """.formatted(definitionCode, studentName, studentDocument, program))
                        .session(session))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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
