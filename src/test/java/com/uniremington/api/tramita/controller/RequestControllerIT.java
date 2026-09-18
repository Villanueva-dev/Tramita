package com.uniremington.api.tramita.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Paquete de Boot 4 (modularizado): antes org.springframework.boot.test.autoconfigure.web.servlet
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@TramitaIntegrationTest
@AutoConfigureMockMvc
class RequestControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IRequestRepo requestRepo;

    @Autowired
    private IRequestTransitionLogRepo logRepo;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // --- US1: registrar ------------------------------------------------------------------

    @Test
    @DisplayName("registrar adición de créditos: 201 + Location, nace en EN_COORDINACION con sus transiciones")
    void registerCreatesRequestInInitialStateOfItsDefinition() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(createRequest("ADICION_CREDITOS", "Ana María Pérez", "DOC-PRUEBA-001")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.definition.code").value("ADICION_CREDITOS"))
                .andExpect(jsonPath("$.definition.version").value(1))
                .andExpect(jsonPath("$.studentName").value("Ana María Pérez"))
                .andExpect(jsonPath("$.currentState.code").value("EN_COORDINACION"))
                .andExpect(jsonPath("$.currentState.isFinal").value(false))
                // Las transiciones salen de la definición, no de código a medida.
                // Desde EN_COORDINACION hay dos: avanzar o devolver — se asertan
                // sin orden porque la definición no promete ninguno.
                .andExpect(jsonPath("$.availableTransitions[*].targetState.code",
                        org.hamcrest.Matchers.containsInAnyOrder("EN_FACULTAD", "DEVUELTA")));
    }

    @Test
    @DisplayName("los dos trámites coexisten: cada solicitud nace en el estado inicial de SU definición")
    void requestsOfBothProceduresCoexistWithTheirOwnInitialState() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(createRequest("ADICION_CREDITOS", "Estudiante Uno", "111")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentState.code").value("EN_COORDINACION"))
                .andExpect(jsonPath("$.availableTransitions[*].targetState.code",
                        org.hamcrest.Matchers.containsInAnyOrder("EN_FACULTAD", "DEVUELTA")));

        // Novedad de notas conserva REGISTRADA y su camino es propio: hacia
        // EN_PREPARACION — cada definición nombra sus estados (US4/FR-010)
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
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());
    }

    // --- 003 / US1: el formulario del trámite vive en el sistema --------------------------

    @Test
    @DisplayName("registrar con el formulario completo: 201 y devuelve las dos asignaturas íntegras (FR-001, FR-002)")
    void registerPersistsTheWholeFormWithItsSubjects() throws Exception {
        MockHttpSession session = login();
        String created = mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante De Prueba",
                          "studentDocument": "DOC-TEST-0001",
                          "studentCode": "EST-0001",
                          "program": "Ingeniería de Sistemas",
                          "semester": "2026-2",
                          "reason": "Requiere una asignatura adicional para completar el plan.",
                          "subjects": [
                            {"code":"MAT-101","name":"Cálculo Diferencial","credits":3,"group":"G1"},
                            {"code":"FIS-201","name":"Física I","credits":4,"group":"G2"}
                          ]
                        }""").session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentCode").value("EST-0001"))
                .andExpect(jsonPath("$.program").value("Ingeniería de Sistemas"))
                .andExpect(jsonPath("$.semester").value("2026-2"))
                .andExpect(jsonPath("$.reason")
                        .value("Requiere una asignatura adicional para completar el plan."))
                // La cardinalidad se conserva y cada asignatura mantiene SUS propios datos:
                // no alcanza con contar dos, hay que ver que no se mezclaron entre sí.
                .andExpect(jsonPath("$.subjects.length()").value(2))
                .andExpect(jsonPath("$.subjects[0].code").value("MAT-101"))
                .andExpect(jsonPath("$.subjects[0].name").value("Cálculo Diferencial"))
                .andExpect(jsonPath("$.subjects[0].credits").value(3))
                .andExpect(jsonPath("$.subjects[0].group").value("G1"))
                .andExpect(jsonPath("$.subjects[1].code").value("FIS-201"))
                .andExpect(jsonPath("$.subjects[1].credits").value(4))
                .andExpect(jsonPath("$.subjects[1].group").value("G2"))
                .andReturn().getResponse().getContentAsString();

        // El 201 devuelve la entidad que quedó en memoria, así que por sí solo NO prueba
        // que las asignaturas se hayan escrito: sin la cascada, request_subject quedaría
        // vacía y todas las aserciones de arriba seguirían pasando. Hay que releerla.
        // Se compara por código y no por posición porque el orden de lectura no está
        // garantizado: la colección no declara criterio de ordenamiento.
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        mockMvc.perform(get("/api/requests/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects.length()").value(2))
                .andExpect(jsonPath("$.subjects[*].code",
                        org.hamcrest.Matchers.containsInAnyOrder("MAT-101", "FIS-201")))
                .andExpect(jsonPath("$.subjects[?(@.code=='MAT-101')].credits",
                        org.hamcrest.Matchers.contains(3)))
                .andExpect(jsonPath("$.subjects[?(@.code=='MAT-101')].name",
                        org.hamcrest.Matchers.contains("Cálculo Diferencial")))
                .andExpect(jsonPath("$.subjects[?(@.code=='FIS-201')].credits",
                        org.hamcrest.Matchers.contains(4)));
    }

    @Test
    @DisplayName("novedad de notas captura la nota actual y la propuesta sobre la misma estructura (FR-003)")
    void registerCapturesGradesOnTheSameSubjectStructure() throws Exception {
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "NOVEDAD_NOTAS",
                          "studentName": "Estudiante De Prueba",
                          "studentDocument": "DOC-TEST-0002",
                          "subjects": [
                            {"code":"MAT-101","name":"Cálculo Diferencial",
                             "currentGrade":2.80,"proposedGrade":3.50}
                          ]
                        }""").session(login()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjects.length()").value(1))
                .andExpect(jsonPath("$.subjects[0].currentGrade").value(2.80))
                .andExpect(jsonPath("$.subjects[0].proposedGrade").value(3.50));
    }

    @Test
    @DisplayName("el cuerpo mínimo de la 002 sigue registrando, con la lista de asignaturas vacía (FR-006)")
    void registerWithTheLegacyMinimalBodyStillWorks() throws Exception {
        mockMvc.perform(createRequest("ADICION_CREDITOS", "Estudiante De Prueba", "DOC-TEST-0003")
                        .session(login()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjects").isArray())
                .andExpect(jsonPath("$.subjects.length()").value(0))
                // Los campos nuevos quedan nulos, no en blanco ni con un default inventado
                .andExpect(jsonPath("$.studentCode").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    @DisplayName("un trámite que captura créditos rechaza la asignatura que no los declara (FR-009)")
    void registerRejectsSubjectsWithoutCreditsWhenTheTradeCapturesThem() throws Exception {
        // Omitir el dato era la forma de esquivar el tope: la validación no llegaba a
        // correr y la solicitud se registraba con 201 sin límite alguno aplicado.
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante De Prueba",
                          "studentDocument": "DOC-TEST-0011",
                          "subjects": [{"code":"MAT-101","name":"Cálculo Diferencial"}]
                        }""").session(login()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail",
                        org.hamcrest.Matchers.containsString("créditos")));
    }

    @Test
    @DisplayName("una novedad de notas rechaza los créditos con 422 del cliente, no con un 500 (FR-009)")
    void registerRejectsCreditsOnATradeThatDoesNotCaptureThem() throws Exception {
        // El formato oficial de novedad de notas no tiene columna de créditos. Recibirlos
        // es un dato de más de quien envía, no una configuración rota del servidor: antes
        // devolvía 500 y dejaba un log.error por un error que no era del sistema.
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "NOVEDAD_NOTAS",
                          "studentName": "Estudiante De Prueba",
                          "studentDocument": "DOC-TEST-0012",
                          "subjects": [{"code":"MAT-101","name":"Cálculo Diferencial","credits":3,
                                        "currentGrade":2.80,"proposedGrade":3.50}]
                        }""").session(login()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail",
                        org.hamcrest.Matchers.containsString("no captura créditos")));
    }

    @Test
    @DisplayName("el correo del estudiante se conserva pero NUNCA sale en la respuesta (FR-005a)")
    void registerPersistsStudentEmailButNeverReturnsIt() throws Exception {
        // ⚠️ ESTE TEST AFIRMABA LO CONTRARIO HASTA EL 2026-09-16. Se llamaba
        // «registerNeverPersistsNorReturnsStudentContactData» y su comentario decía que
        // «el sistema no tiene dónde guardarlo: el campo se ignora». Dejó de ser cierto
        // cuando la 004 agregó student_email a la tabla, y el test siguió en verde
        // porque solo miraba la respuesta HTTP, nunca la fila. Lo encontró un review
        // independiente (A-2).
        //
        // El FR-020 que citaba era el de la 003 —«MUST NOT almacenar el correo»— que el
        // FR-005a de la 004 revoca explícitamente: el consumidor apareció (el PDF formal
        // del SP3). En la 004, FR-020 significa otra cosa: no escribirlo en las bitácoras.
        //
        // Lo que sigue siendo cierto, y es lo que este test defiende: el dato se guarda,
        // pero este endpoint NO lo devuelve. Son dos garantías distintas y ahora se
        // asertan las dos.
        String email = "contacto.de.prueba@ejemplo.test";
        String studentName = "Estudiante Con Correo";

        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "%s",
                          "studentDocument": "DOC-TEST-0004",
                          "studentEmail": "%s"
                        }""".formatted(studentName, email)).session(login()))
                .andExpect(status().isCreated())
                // No sale: ni bajo su clave, ni bajo ninguna otra
                .andExpect(jsonPath("$.studentEmail").doesNotExist())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(email))));

        // Y sí se conserva: la aserción que faltaba y que dejaba pasar la contradicción
        Request saved = requestRepo.findAll().stream()
                .filter(r -> studentName.equals(r.getStudentName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("la solicitud no llegó a la base"));
        assertThat(saved.getStudentEmail())
                .as("FR-005a: el correo se conserva porque el PDF formal del SP3 lo necesita")
                .isEqualTo(email);
    }

    @Test
    @DisplayName("créditos negativos no compensan a otra asignatura para burlar el tope (FR-009)")
    void negativeCreditsCannotOffsetAnotherSubjectToBypassTheLimit() throws Exception {
        long requestsBefore = requestRepo.count();

        // 30 y -20 suman 10 y pasarían un tope de 21. La validación de forma los
        // rechaza antes de que la suma llegue a calcularse.
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante De Prueba",
                          "studentDocument": "DOC-TEST-0006",
                          "subjects": [
                            {"code":"A-1","name":"Uno","credits":30},
                            {"code":"A-2","name":"Dos","credits":-20}
                          ]
                        }""").session(login()))
                .andExpect(status().isBadRequest());

        assertThat(requestRepo.count()).isEqualTo(requestsBefore);
    }

    @Test
    @DisplayName("supera el tope configurado: 422 con el límite en el detail (FR-008)")
    void exceedingTheConfiguredCreditLimitIsRejected() throws Exception {
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante De Prueba",
                          "studentDocument": "DOC-TEST-0007",
                          "subjects": [
                            {"code":"A-1","name":"Uno","credits":12},
                            {"code":"A-2","name":"Dos","credits":10}
                          ]
                        }""").session(login()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("21")));
    }

    @Test
    @DisplayName("un motivo que excede la longitud máxima: 400 y no se registra truncado (FR-004)")
    void anOversizedReasonIsRejectedInsteadOfTruncated() throws Exception {
        long requestsBefore = requestRepo.count();

        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante De Prueba",
                          "studentDocument": "DOC-TEST-0008",
                          "reason": "%s"
                        }""".formatted("x".repeat(2001))).session(login()))
                .andExpect(status().isBadRequest());

        assertThat(requestRepo.count()).isEqualTo(requestsBefore);
    }

    // --- 003 / US3: la regla se ajusta sin desplegar --------------------------------------

    @Test
    @DisplayName("cambiar el tope en la configuración cambia el veredicto sin redesplegar (SC-005)")
    void changingTheConfiguredLimitChangesTheOutcomeWithoutRedeploying() throws Exception {
        MockHttpSession session = login();
        String body = """
                {
                  "definitionCode": "ADICION_CREDITOS",
                  "studentName": "Estudiante De Prueba",
                  "studentDocument": "DOC-TEST-0009",
                  "subjects": [
                    {"code":"A-1","name":"Uno","credits":12},
                    {"code":"A-2","name":"Dos","credits":10}
                  ]
                }""";

        // 22 créditos contra el tope sembrado de 21
        mockMvc.perform(createRequestWithForm(body).session(session))
                .andExpect(status().isUnprocessableContent());

        String original = maxCreditsOf("ADICION_CREDITOS");
        try {
            setMaxCredits("ADICION_CREDITOS", "24");

            // Misma aplicación corriendo, mismo contexto: solo cambió una fila
            mockMvc.perform(createRequestWithForm(body).session(session))
                    .andExpect(status().isCreated());
        } finally {
            setMaxCredits("ADICION_CREDITOS", original);
        }
    }

    @Test
    @DisplayName("cada trámite se valida contra el tope de SU definición (FR-014)")
    void eachProcedureIsValidatedAgainstItsOwnConfiguredLimit() throws Exception {
        MockHttpSession session = login();
        String originalAdicion = maxCreditsOf("ADICION_CREDITOS");
        try {
            setMaxCredits("ADICION_CREDITOS", "10");
            // Novedad de notas no captura créditos en la configuración real, así que para
            // que sirva como "el otro trámite con su propio tope" hay que declararle
            // ambas cosas. Se revierte en el finally.
            jdbcTemplate.update("""
                    INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
                    SELECT gen_random_uuid(), id, p.parameter_key, p.parameter_value
                    FROM workflow_definition d
                             CROSS JOIN (VALUES ('MAX_CREDITS', '30'),
                                                ('CAPTURES_CREDITS', 'true'))
                        AS p(parameter_key, parameter_value)
                    WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 1""");

            String subjects = """
                    "subjects": [{"code":"A-1","name":"Uno","credits":20}]""";

            // 20 créditos: excede el tope de adición (10) y no el de novedad (30)
            mockMvc.perform(createRequestWithForm("""
                            {"definitionCode":"ADICION_CREDITOS","studentName":"Estudiante De Prueba",
                             "studentDocument":"DOC-TEST-0010", %s}""".formatted(subjects))
                            .session(session))
                    .andExpect(status().isUnprocessableContent());

            mockMvc.perform(createRequestWithForm("""
                            {"definitionCode":"NOVEDAD_NOTAS","studentName":"Estudiante De Prueba",
                             "studentDocument":"DOC-TEST-0011", %s}""".formatted(subjects))
                            .session(session))
                    .andExpect(status().isCreated());
        } finally {
            setMaxCredits("ADICION_CREDITOS", originalAdicion);
            jdbcTemplate.update("""
                    DELETE FROM workflow_parameter
                     WHERE parameter_key IN ('MAX_CREDITS', 'CAPTURES_CREDITS')
                       AND definition_id IN (SELECT id FROM workflow_definition
                                             WHERE code = 'NOVEDAD_NOTAS')""");
        }
    }

    @Test
    @DisplayName("cada versión de una definición lleva sus propios parámetros (FR-013)")
    void eachDefinitionVersionCarriesItsOwnParameters() throws Exception {
        MockHttpSession session = login();
        // 18 créditos: los admite el tope 21 de la v1 y los rechaza el 15 de la v2.
        // El veredicto delata contra qué versión se validó.
        String body = """
                {
                  "definitionCode": "ADICION_CREDITOS",
                  "studentName": "Estudiante De Prueba",
                  "studentDocument": "DOC-TEST-0012",
                  "subjects": [{"code":"A-1","name":"Uno","credits":18}]
                }""";

        try {
            publishSecondVersionWithMaxCredits("15");

            mockMvc.perform(createRequestWithForm(body).session(session))
                    .andExpect(status().isUnprocessableContent())
                    // El detail nombra 15 y no 21: se aplicó el parámetro de la
                    // versión vigente, no el de la definición anterior.
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("15")));

            // Y el parámetro de la v1 quedó intacto: publicar una versión nueva no
            // reescribe las reglas de la anterior.
            assertThat(maxCreditsOf("ADICION_CREDITOS")).isEqualTo("21");
        } finally {
            dropSecondVersion();
        }
    }

    @Test
    @DisplayName("consultar una solicitud sin sesión: 401 y no se filtra su contenido (FR-021)")
    void readingARequestWithoutSessionLeaksNothing() throws Exception {
        MockHttpSession session = login();
        String id = mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante Reservado",
                          "studentDocument": "DOC-TEST-0005",
                          "program": "Programa Reservado"
                        }""").session(session))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()
                .replaceAll("^.*?\"id\":\"([0-9a-f-]+)\".*$", "$1");

        mockMvc.perform(get("/api/requests/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("Programa Reservado"))));
    }

    // --- US2: avanzar (el motor sobre la semilla real) -----------------------------------

    @Test
    @DisplayName("adición de créditos recorre su cadena completa hasta FINALIZADA, que no admite más")
    void adicionWalksItsFullChainToFinalState() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Caminante Feliz", "301");

        for (String state : new String[] {
                "EN_FACULTAD", "APROBADA_FACULTAD", "EN_REGISTRO_CALI", "EN_REGISTRO_NACIONAL"}) {
            mockMvc.perform(advanceRequest(id, state, null).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentState.code").value(state));
        }

        mockMvc.perform(advanceRequest(id, "FINALIZADA", null).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.code").value("FINALIZADA"))
                .andExpect(jsonPath("$.currentState.isFinal").value(true))
                // Trámite cerrado: de un estado final no sale nada
                .andExpect(jsonPath("$.availableTransitions").isEmpty());
    }

    @Test
    @DisplayName("transición no definida: 409 problem+json y el estado queda intacto")
    void undefinedTransitionReturns409AndStateSurvives() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Saltarina Ilegal", "302");

        // EN_COORDINACION → FINALIZADA no está definida: el camino pasa por la facultad
        mockMvc.perform(advanceRequest(id, "FINALIZADA", null).session(session))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());

        // La prueba de que el estado no se corrompió: la transición legal desde
        // EN_COORDINACION sigue disponible y funciona
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.code").value("EN_FACULTAD"));
    }

    @Test
    @DisplayName("avanzar sin sesión: 401, el estado no cambia y el timeline no crece (FR-012)")
    void advanceWithoutSessionIsRejectedWithoutSideEffects() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Protegida Total", "303");
        long logEntriesBefore = logRepo.count();

        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null))
                .andExpect(status().isUnauthorized());

        assertThat(logRepo.count()).isEqualTo(logEntriesBefore);

        // El estado sigue siendo EN_COORDINACION: el avance legal aún es EN_FACULTAD
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
    }

    // --- US3: timeline y localización ----------------------------------------------------

    @Test
    @DisplayName("el timeline muestra la historia completa en orden: nacimiento, avances, autor y responsable")
    void timelineShowsChronologicalHistoryWithActorAndResponsible() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Historia Completa", "401");
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(id, "APROBADA_FACULTAD", null).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Nacimiento (research.md D7): sin from y sin responsable de paso
                .andExpect(jsonPath("$[0].fromState").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].toState.code").value("EN_COORDINACION"))
                .andExpect(jsonPath("$[0].actorEmail").value(AuthControllerIT.SEED_EMAIL))
                .andExpect(jsonPath("$[0].occurredAt").exists())
                // El envío a facultad lo hace la Coordinación en nombre propio
                .andExpect(jsonPath("$[1].fromState.code").value("EN_COORDINACION"))
                .andExpect(jsonPath("$[1].toState.code").value("EN_FACULTAD"))
                .andExpect(jsonPath("$[1].responsible").value("COORDINACION"))
                // La aprobación es del decano; la registró la Coordinación en su
                // nombre — actor real + responsable del paso (FR-006)
                .andExpect(jsonPath("$[2].toState.code").value("APROBADA_FACULTAD"))
                .andExpect(jsonPath("$[2].responsible").value("FACULTAD"))
                .andExpect(jsonPath("$[2].actorEmail").value(AuthControllerIT.SEED_EMAIL));
    }

    @Test
    @DisplayName("localiza por cédula exacta y por fragmento del nombre, sin distinguir mayúsculas")
    void searchFindsByDocumentAndNameFragment() throws Exception {
        MockHttpSession session = login();
        registerAndGetId(session, "ADICION_CREDITOS", "Búsqueda Extraordinaria", "402505");
        registerAndGetId(session, "NOVEDAD_NOTAS", "Otra Persona", "888777");

        // Por cédula: igualdad exacta — un prefijo no matchea
        mockMvc.perform(get("/api/requests").param("search", "402505").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentName").value("Búsqueda Extraordinaria"))
                .andExpect(jsonPath("$[0].currentState.code").value("EN_COORDINACION"));

        // Por fragmento del nombre, case-insensitive (FR-011)
        mockMvc.perform(get("/api/requests").param("search", "extraordinaria").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentDocument").value("402505"));

        // Sin coincidencias: lista vacía, no error
        mockMvc.perform(get("/api/requests").param("search", "inexistente").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("buscar con comodines de LIKE no vuelca el padrón: '%' y '_' se tratan literales")
    void searchDoesNotLeakEveryRequestThroughLikeWildcards() throws Exception {
        MockHttpSession session = login();
        // Garantiza que haya al menos una solicitud que un volcado expondría
        registerAndGetId(session, "ADICION_CREDITOS", "Privacidad Protegida", "405405");

        // '%%' hacía match con TODAS las filas: nombre y cédula de cada
        // estudiante. Se usan dos porque el @Size(min = 2) del controller ya
        // rechaza un comodín suelto — mitigaba el caso trivial, no el real
        mockMvc.perform(get("/api/requests").param("search", "%%").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // '_' es el comodín de un carácter: mismo riesgo en su versión acotada
        mockMvc.perform(get("/api/requests").param("search", "__").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // El escapado no rompe la búsqueda legítima
        mockMvc.perform(get("/api/requests").param("search", "Privacidad").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentDocument").value("405405"));
    }

    @Test
    @DisplayName("nota que excede el tope: 400 y nada se persiste en el timeline inmutable")
    void oversizedNoteIsRejectedBeforeReachingTheImmutableLog() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Nota Enorme", "406406");
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        long logEntriesBefore = logRepo.count();

        // El log es append-only por trigger: lo que entra acá no se puede borrar
        String hugeNote = "x".repeat(2001);
        mockMvc.perform(advanceRequest(id, "DEVUELTA", hugeNote).session(session))
                .andExpect(status().isBadRequest());

        assertThat(logRepo.count()).isEqualTo(logEntriesBefore);

        // El tope no estorba a una observación real
        mockMvc.perform(advanceRequest(id, "DEVUELTA", "Motivo de tamaño razonable")
                        .session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("detalle por id con transiciones disponibles; id desconocido: 404")
    void getByIdReturnsDetailAndUnknownIdReturns404() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Detalle Visible", "403");

        mockMvc.perform(get("/api/requests/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.currentState.code").value("EN_COORDINACION"))
                .andExpect(jsonPath("$.availableTransitions[*].targetState.code",
                        org.hamcrest.Matchers.containsInAnyOrder("EN_FACULTAD", "DEVUELTA")));

        mockMvc.perform(get("/api/requests/00000000-0000-0000-0000-00000000dead")
                        .session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("localizar, detalle y timeline sin sesión: 401 y nada se devuelve (FR-012)")
    void queryEndpointsWithoutSessionReturn401() throws Exception {
        String id = registerAndGetId(login(), "ADICION_CREDITOS", "Consulta Protegida", "404404");

        mockMvc.perform(get("/api/requests").param("search", "404404"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/requests/" + id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/requests/" + id + "/timeline"))
                .andExpect(status().isUnauthorized());
    }

    // --- US5: devolución con motivo y cierre por rechazo ---------------------------------
    // US5-4 (rechazo en un trámite que no lo define → 409) vive en
    // WorkflowGenericityIT.rejectionExistsOnlyWhereTheDefinitionDeclaresIt.

    @Test
    @DisplayName("devolución con motivo: vuelve al estado de corrección y el motivo queda en el timeline")
    void returnWithReasonMovesBackAndRecordsReason() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Devuelta Con Motivo", "701");
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(advanceRequest(id, "DEVUELTA", "Formato sin firma en la casilla 2")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.code").value("DEVUELTA"));

        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(jsonPath("$[2].toState.code").value("DEVUELTA"))
                .andExpect(jsonPath("$[2].note").value("Formato sin firma en la casilla 2"))
                .andExpect(jsonPath("$[2].responsible").value("FACULTAD"));
    }

    @Test
    @DisplayName("devolución sin motivo: 422 — el motivo es el dato que la hace útil (FR-014)")
    void returnWithoutReasonIsRejected() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Devuelta Sin Motivo", "702");
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(advanceRequest(id, "DEVUELTA", null).session(session))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // El estado no cambió: sigue en EN_FACULTAD y su timeline no creció
        mockMvc.perform(get("/api/requests/" + id).session(session))
                .andExpect(jsonPath("$.currentState.code").value("EN_FACULTAD"));
        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("rechazo definitivo: la solicitud queda en estado final y el trámite cerrado (FR-015)")
    void definitiveRejectionClosesTheRequest() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Rechazada Definitiva", "703");
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(advanceRequest(id, "RECHAZADA", null).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.code").value("RECHAZADA"))
                .andExpect(jsonPath("$.currentState.isFinal").value(true))
                .andExpect(jsonPath("$.availableTransitions").isEmpty());

        // Cerrado es cerrado: ningún avance posterior es legal (US2-4)
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("devuelta, corregida y reavanzada: el timeline conserva los tres tramos y las devoluciones son contables (SC-007)")
    void timelineSurvivesReturnAndResubmissionCycles() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Resiliente Total", "704");
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(id, "DEVUELTA", "Falta soporte de pago").session(session))
                .andExpect(status().isOk());
        // Corregida: vuelve a la Coordinación, que la revisa antes de reenviarla
        mockMvc.perform(advanceRequest(id, "EN_COORDINACION", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(jsonPath("$.length()").value(5))
                // Nada se sobrescribió: nacimiento, envío, devolución, re-revisión
                // y reenvío conviven
                .andExpect(jsonPath("$[1].toState.code").value("EN_FACULTAD"))
                .andExpect(jsonPath("$[2].toState.code").value("DEVUELTA"))
                .andExpect(jsonPath("$[2].note").value("Falta soporte de pago"))
                .andExpect(jsonPath("$[3].toState.code").value("EN_COORDINACION"))
                .andExpect(jsonPath("$[4].toState.code").value("EN_FACULTAD"))
                // SC-007: las devoluciones se cuentan filtrando el timeline —
                // aquí, exactamente una y con su motivo
                .andExpect(jsonPath("$[?(@.toState.code == 'DEVUELTA')].note")
                        .value(org.hamcrest.Matchers.contains("Falta soporte de pago")));
    }

    // --- H1: la revisión de la Coordinación y su devolución al estudiante -----------------
    // Fuente: entrevista 1 a la Coordinación de la Sede Cali — «Yo reviso si está
    // bien. Si está mal, se lo regreso». El sistema no devuelve nada: registra que
    // la Coordinación devolvió, cuándo y por qué (cockpit, no orquestador).

    @Test
    @DisplayName("la Coordinación devuelve en su propia revisión: EN_COORDINACION → DEVUELTA con motivo")
    void coordinationReturnsDuringItsOwnReview() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Devuelta En Revision", "705");

        mockMvc.perform(advanceRequest(id, "DEVUELTA", "El formato vino sin la firma escaneada")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.code").value("DEVUELTA"));

        // El motivo es el único dato que el correo no deja medible (FR-014)
        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].fromState.code").value("EN_COORDINACION"))
                .andExpect(jsonPath("$[1].toState.code").value("DEVUELTA"))
                .andExpect(jsonPath("$[1].note").value("El formato vino sin la firma escaneada"))
                .andExpect(jsonPath("$[1].responsible").value("COORDINACION"));
    }

    @Test
    @DisplayName("devolver en la revisión sin motivo: 422 y la solicitud no se mueve")
    void coordinationReturnWithoutReasonIsRejected() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Sin Motivo Revision", "706");

        mockMvc.perform(advanceRequest(id, "DEVUELTA", null).session(session))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/requests/" + id).session(session))
                .andExpect(jsonPath("$.currentState.code").value("EN_COORDINACION"));
        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("lo corregido vuelve a la Coordinación, no directo a la facultad: DEVUELTA → EN_FACULTAD es 409")
    void correctedRequestReturnsToCoordinationAndNotStraightToFaculty() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Retorno Filtrado", "707");
        mockMvc.perform(advanceRequest(id, "DEVUELTA", "Falta la hoja de vida académica")
                        .session(session))
                .andExpect(status().isOk());

        // Saltarse la re-revisión es justamente el filtro que H1 vino a cerrar
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(advanceRequest(id, "EN_COORDINACION", null).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.code").value("EN_COORDINACION"));
    }

    // --- US2 (004): la bandeja de recientes -----------------------------------------------

    @Test
    @DisplayName("la bandeja lista sin criterio y de la más nueva a la más vieja (FR-012, FR-013)")
    void inboxListsRecentRequestsNewestFirst() throws Exception {
        MockHttpSession session = login();

        // Tres solicitudes en orden conocido. Nombres propios del escenario: el IT
        // comparte base con los demás tests y una bandeja global trae también lo suyo.
        String primera = "Bandeja Primera EnLlegar";
        String segunda = "Bandeja Segunda EnLlegar";
        String tercera = "Bandeja Tercera EnLlegar";
        registerAndGetId(session, "ADICION_CREDITOS", primera, "SIN-DATO-REAL-201");
        registerAndGetId(session, "ADICION_CREDITOS", segunda, "SIN-DATO-REAL-202");
        registerAndGetId(session, "ADICION_CREDITOS", tercera, "SIN-DATO-REAL-203");

        String body = mockMvc.perform(get("/api/requests/inbox").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        List<String> names = com.jayway.jsonpath.JsonPath.read(body, "$[*].studentName");

        // Se asertan las POSICIONES RELATIVAS y no los tres primeros puestos: lo que
        // FR-013 promete es el orden, y exigir que encabecen la lista ataría el test a
        // que ningún otro escenario registre algo después.
        assertThat(names).contains(primera, segunda, tercera);
        assertThat(names.indexOf(tercera))
                .as("la última registrada debe aparecer antes que la segunda")
                .isLessThan(names.indexOf(segunda));
        assertThat(names.indexOf(segunda))
                .as("la segunda registrada debe aparecer antes que la primera")
                .isLessThan(names.indexOf(primera));
    }

    @Test
    @DisplayName("la bandeja nunca expone el número de documento (FR-014)")
    void inboxNeverExposesStudentDocument() throws Exception {
        MockHttpSession session = login();
        String document = "SIN-DATO-REAL-204";
        registerAndGetId(session, "ADICION_CREDITOS", "Bandeja Sin Cedula", document);

        String body = mockMvc.perform(get("/api/requests/inbox").session(session))
                .andExpect(status().isOk())
                // Sobre el JSON servido, no sobre el DTO: lo que se promete es que el
                // dato no SALE, y quien lo verifica del lado del DTO no vería un campo
                // agregado por otra vía.
                .andExpect(jsonPath("$[*].studentDocument").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Y el valor concreto tampoco aparece bajo ninguna otra clave.
        assertThat(body)
                .as("ningún documento de identidad puede viajar en la respuesta de la bandeja")
                .doesNotContain(document);
    }

    @Test
    @DisplayName("la bandeja exige sesión: sin ella, 401 (FR-015)")
    void inboxRequiresAnAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/requests/inbox"))
                .andExpect(status().isUnauthorized());
    }

    // --- 010 / SP3: el documento formal del trámite ---------------------------------------

    @Test
    @DisplayName("documento de un trámite que declara formato: 200 application/pdf descargable")
    void documentOfADeclaredTradeIsServedAsPdf() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Ana Con Documento", "DOC-PDF-001");

        byte[] pdf = mockMvc.perform(get("/api/requests/" + id + "/document").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                // SE ASIERTA EL NOMBRE COMPLETO, no que contenga «attachment». FR-034 dice
                // que el archivo NO puede llevar datos personales, y esa garantía descansaba
                // en una sola línea de código sin ninguna red: un mutante que cambiara el
                // nombre entero sobrevivía. El archivo se descarga, se reenvía y queda en
                // carpetas compartidas, y el nombre viaja con él.
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"DO-FR-100-" + id + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        // El CONTENIDO lo verifica DoFr100RendererTest extrayendo el texto. Acá se
        // comprueba el cableado: que la ruta resuelve, que atraviesa el filter chain con
        // sesión, y que el trámite eligió su formato por configuración y no por código.
        assertThat(pdf).startsWith(new byte[] {'%', 'P', 'D', 'F'});
    }

    @Test
    @DisplayName("el documento exige sesión: es de la Coordinación, no del estudiante")
    void documentRequiresSession() throws Exception {
        String id = registerAndGetId(login(), "ADICION_CREDITOS", "Ana Sin Sesión", "DOC-PDF-002");

        // El recibo del canal público no devuelve id a propósito (FR-008), así que quien
        // envía por el formulario no tiene con qué pedir esto. Que además exija sesión lo
        // cierra por el otro lado.
        mockMvc.perform(get("/api/requests/" + id + "/document"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un trámite que no declara formato no tiene documento: 404 problem+json")
    void tradeWithoutDeclaredTemplateHasNoDocument() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "NOVEDAD_NOTAS", "Sin Formato", "DOC-PDF-003");

        // NOVEDAD_NOTAS no declara DOCUMENT_TEMPLATE: su formato oficial todavía no se
        // modela —el papel es por asignatura con varios estudiantes, y eso está bloqueado
        // por la Coordinación en el issue #10—. La ausencia significa «este trámite no
        // emite documento», no configuración rota, y por eso es 404 y no 500.
        mockMvc.perform(get("/api/requests/" + id + "/document").session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // --- helpers -------------------------------------------------------------------------

    private String registerAndGetId(MockHttpSession session, String definitionCode,
            String studentName, String studentDocument) throws Exception {
        String body = mockMvc.perform(
                        createRequest(definitionCode, studentName, studentDocument).session(session))
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest(
            String definitionCode, String studentName, String studentDocument) {
        return post("/api/requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"definitionCode\":\"%s\",\"studentName\":\"%s\",\"studentDocument\":\"%s\"}"
                        .formatted(definitionCode, studentName, studentDocument));
    }

    /** Registro con el cuerpo completo de la 003: el JSON se pasa tal cual. */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            createRequestWithForm(String jsonBody) {
        return post("/api/requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
    }

    // --- Manipulación de la configuración (US3) -------------------------------------------
    // Se toca la fila directamente y no un endpoint: SC-005 afirma que ajustar una
    // regla es un cambio de CONFIGURACIÓN, y esta feature no introduce una interfaz
    // de administración. El UPDATE es exactamente el gesto que el criterio describe.

    private String maxCreditsOf(String definitionCode) {
        return jdbcTemplate.queryForObject("""
                SELECT p.parameter_value FROM workflow_parameter p
                JOIN workflow_definition d ON d.id = p.definition_id
                WHERE d.code = ? AND d.version = 1 AND p.parameter_key = 'MAX_CREDITS'""",
                String.class, definitionCode);
    }

    private void setMaxCredits(String definitionCode, String value) {
        jdbcTemplate.update("""
                UPDATE workflow_parameter SET parameter_value = ?
                WHERE parameter_key = 'MAX_CREDITS' AND definition_id =
                      (SELECT id FROM workflow_definition WHERE code = ? AND version = 1)""",
                value, definitionCode);
    }

    /**
     * Publica una v2 de adición de créditos con su propio tope. Le basta un estado
     * inicial: registrar solo exige exactamente uno, y este test no avanza la
     * solicitud.
     */
    private void publishSecondVersionWithMaxCredits(String maxCredits) {
        jdbcTemplate.update("""
                INSERT INTO workflow_definition (id, code, version, name, created_at)
                VALUES (gen_random_uuid(), 'ADICION_CREDITOS', 2, 'Adición de créditos v2', now())""");
        jdbcTemplate.update("""
                INSERT INTO workflow_state (id, definition_id, code, name, is_initial, is_final)
                SELECT gen_random_uuid(), id, 'REGISTRADA', 'Registrada', TRUE, FALSE
                FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2""");
        jdbcTemplate.update("""
                INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
                SELECT gen_random_uuid(), id, 'MAX_CREDITS', ?
                FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2""",
                maxCredits);
        // Los parámetros son de la VERSIÓN, no del código del trámite (FR-013): una v2
        // no hereda nada de la v1 y tiene que declarar también que captura créditos.
        jdbcTemplate.update("""
                INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
                SELECT gen_random_uuid(), id, 'CAPTURES_CREDITS', 'true'
                FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2""");
    }

    /**
     * Borra la v2 de prueba. NO limpia solicitudes: el test que la usa está
     * diseñado para que ninguna llegue a registrarse, porque el timeline es
     * append-only y su trigger rechaza el DELETE (SC-002). Una solicitud creada
     * contra esta versión sería imposible de limpiar sin violar esa garantía.
     */
    private void dropSecondVersion() {
        jdbcTemplate.update("""
                DELETE FROM workflow_parameter WHERE definition_id IN (
                    SELECT id FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2)""");
        jdbcTemplate.update("""
                DELETE FROM workflow_state WHERE definition_id IN (
                    SELECT id FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2)""");
        jdbcTemplate.update(
                "DELETE FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2");
    }
}
