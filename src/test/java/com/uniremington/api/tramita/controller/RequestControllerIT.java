package com.uniremington.api.tramita.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    @DisplayName("el correo del estudiante se conserva Y sale en la respuesta del registro (008, FR-008)")
    void registerPersistsAndReturnsStudentEmail() throws Exception {
        // ⚠️ ESTE TEST SE INVIRTIÓ A CONCIENCIA EL 2026-09-24 (008, FR-008; research.md D4).
        // Hasta entonces se llamaba «registerPersistsStudentEmailButNeverReturnsIt» y
        // afirmaba que el correo se guardaba pero NUNCA salía. Y antes, hasta el 2026-09-16,
        // se llamaba «registerNeverPersistsNorReturnsStudentContactData» y afirmaba que ni
        // siquiera se guardaba. Cada versión defendió el invariante de su feature: la 003
        // no lo almacenaba «hasta que exista quien lo use» (su FR-020), la 004 lo almacenó
        // para el PDF formal (FR-005a) sin exponerlo porque nadie lo consumía.
        //
        // La 008 es ese consumidor: la Coordinación necesita el correo en el detalle para
        // armar el aviso de cierre. Por eso ahora se afirma que SALE bajo su clave. La
        // segunda mitad —que la fila lo guarda— no cambia: son dos garantías distintas y
        // se asertan las dos. Invertir una aserción verde cambia una conducta entregada;
        // se hizo con la spec delante, no con la suite en rojo.
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
                // Sale bajo su clave: es lo que el cliente lee para armar el mailto (FR-008)
                .andExpect(jsonPath("$.studentEmail").value(email));

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
    @DisplayName("calificación con más de un decimal: 400, no 422 (FR-013a, SC-008)")
    void gradeWithMoreThanOneDecimalIsRejectedWithBadRequest() throws Exception {
        // Acuerdo n.º 13 de 2023, art. 32: un decimal como máximo. Las calificaciones entran
        // solo por el formulario interno, así que un valor mal formado es un defecto del
        // contrato de entrada (400) y no un formulario a medio llenar (422, exclusivo del
        // canal público de captura).
        long requestsBefore = requestRepo.count();

        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "NOVEDAD_NOTAS",
                          "studentName": "Estudiante De Prueba",
                          "studentDocument": "DOC-TEST-0009",
                          "subjects": [
                            {"code":"MAT-101","name":"Cálculo Diferencial","proposedGrade":3.46}
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
        // T014 (009): "program" pasa a un nombre DEL CATÁLOGO —antes "Programa Reservado"
        // no lo era, y con la 009 este registro pasaría a 400 en vez de 201—. El
        // centinela de no filtración se muda a "Estudiante Reservado" (ya usado arriba):
        // un nombre del catálogo es texto común y dejaría de ser distintivo.
        String id = mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante Reservado",
                          "studentDocument": "DOC-TEST-0005",
                          "program": "Ingeniería de Sistemas"
                        }""").session(session))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()
                .replaceAll("^.*?\"id\":\"([0-9a-f-]+)\".*$", "$1");

        mockMvc.perform(get("/api/requests/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("Estudiante Reservado"))));
    }

    // --- 009 / FR-003, US1: el canal interno exige el catálogo SOLO si el campo viene ----

    @Test
    @DisplayName("canal interno: programa fuera del catálogo es 400 «Petición inválida» que lo nombra, sin eco (009, FR-003)")
    void internalChannelRejectsProgramOutsideCatalog() throws Exception {
        String response = mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante Interno Programa Fuera De Catalogo",
                          "studentDocument": "SIN-DATO-REAL-904",
                          "program": "Psicología"
                        }""").session(login()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Petición inválida"))
                .andExpect(jsonPath("$.invalidFields.length()").value(1))
                .andExpect(jsonPath("$.invalidFields[0]").value("program"))
                .andExpect(jsonPath("$.missingFields.length()").value(0))
                .andReturn().getResponse().getContentAsString();

        assertThat(response)
                .as("el programa rechazado no puede reflejarse de vuelta al cliente (§III)")
                .doesNotContain("Psicología");
    }

    @Test
    @DisplayName("canal interno: programa vacío «vino e inválido», 400 que lo nombra (009, FR-003, D4)")
    void internalChannelRejectsBlankProgramAsInvalidNotMissing() throws Exception {
        // "" es «vino e inválido» y no «ausente»: mismo criterio que el teléfono de la
        // 008 (research.md D4). Solo OMITIR la clave cuenta como ausencia (ver abajo).
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante Interno Programa Vacio",
                          "studentDocument": "SIN-DATO-REAL-905",
                          "program": ""
                        }""").session(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición inválida"))
                .andExpect(jsonPath("$.invalidFields.length()").value(1))
                .andExpect(jsonPath("$.invalidFields[0]").value("program"))
                .andExpect(jsonPath("$.missingFields.length()").value(0));
    }

    @Test
    @DisplayName("canal interno: sin la clave program es 201 —la ausencia no se valida (009, FR-003, US1 escenario 5)")
    void internalChannelAcceptsRequestWithoutProgramKey() throws Exception {
        // GUARDA, no RED: hoy ya es 201 (program es opcional en CreateRequestBody), y la
        // 009 no le agrega validación a la ausencia. Vigila el mutante de T028 que
        // validara también cuando la clave no viene.
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante Interno Sin Programa",
                          "studentDocument": "SIN-DATO-REAL-906"
                        }""").session(login()))
                .andExpect(status().isCreated());
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

    // --- 007 / SP5: la bandeja de trabajo ------------------------------------------------
    // El criterio vive en la consulta del repositorio (research.md D1, T010), así que
    // estos tests corren contra Postgres real: un unit test con el repositorio mockeado
    // devolvería lo que el mock diga y el mutante de T017 no lo tocaría.
    //
    // `limit=200` —el tope del contrato— a propósito en los escenarios: la base es
    // compartida entre ITs y acumula solicitudes en EN_COORDINACION; con la cota por
    // defecto y el corte por radicación ascendente (research.md D8), las recién
    // registradas quedarían fuera y el test afirmaría algo sobre la cota, no sobre el
    // criterio.

    private static final String INBOX = "/api/requests/inbox";

    @Test
    @DisplayName("la bandeja lista exactamente lo que espera al responsable, sin duplicados; la devuelta cuenta (FR-001, D1)")
    void inboxListsExactlyWhatWaitsForTheResponsible() throws Exception {
        MockHttpSession session = login();
        String waiting = registerAndGetId(session, "ADICION_CREDITOS",
                "Bandeja Espera Coordinacion", "SIN-DATO-REAL-201");
        String atFaculty = registerAndGetId(session, "ADICION_CREDITOS",
                "Bandeja En Facultad", "SIN-DATO-REAL-202");
        String returned = registerAndGetId(session, "ADICION_CREDITOS",
                "Bandeja Devuelta", "SIN-DATO-REAL-203");
        mockMvc.perform(advanceRequest(atFaculty, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(returned, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        // La devolución la ejecuta la facultad; el reingreso lo registra la Coordinación,
        // así que la solicitud devuelta ESPERA a la Coordinación (spec US1, escenario 6).
        mockMvc.perform(advanceRequest(returned, "DEVUELTA", "Falta la firma del estudiante")
                        .session(session))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get(INBOX)
                        .param("responsible", "COORDINACION").param("limit", "200")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$[*].id");

        // Tamaño EXACTO sobre las solicitudes de este escenario —la base es compartida—.
        // Hibernate deduplica la entidad raíz aunque el join multiplique filas: la
        // ausencia de duplicados NO prueba el `distinct` de la consulta. Lo que el
        // `distinct` decide es que la cota cuente solicitudes y no filas, y eso lo prueba
        // WorkflowGenericityIT (review M1).
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids.stream().filter(List.of(waiting, atFaculty, returned)::contains).toList())
                .as("de las tres, esperan a la Coordinación la recién registrada y la devuelta")
                .containsExactlyInAnyOrder(waiting, returned);

        // Cada entrada dice a quién espera y de dónde vino (FR-007): las registradas con
        // sesión nacen de la Coordinación.
        List<String> responsibles = com.jayway.jsonpath.JsonPath.read(body, "$[*].pendingResponsible");
        assertThat(responsibles).isNotEmpty().containsOnly("COORDINACION");
        List<String> origins = com.jayway.jsonpath.JsonPath.read(body,
                "$[?(@.id == '%s' || @.id == '%s')].origin".formatted(waiting, returned));
        assertThat(origins).containsExactly("COORDINATION", "COORDINATION");
    }

    @Test
    @DisplayName("lo que espera a otra área no está en la bandeja pedida, y sí en la de esa área (FR-001)")
    void aRequestWaitingForAnotherAreaIsNotInTheResponsibleInbox() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Bandeja Otra Area", "SIN-DATO-REAL-205");
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());

        assertThat(inboxIds(session, "COORDINACION")).doesNotContain(id);
        assertThat(inboxIds(session, "FACULTAD")).contains(id);
    }

    @Test
    @DisplayName("un trámite cerrado sale de todas las bandejas por construcción: de un estado final no hay transiciones (FR-001)")
    void aClosedRequestLeavesEveryInboxByConstruction() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Bandeja Cerrada", "SIN-DATO-REAL-206");
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(id, "RECHAZADA", null).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState.isFinal").value(true));

        // Los responsables salen de la configuración, no de una lista escrita acá: si
        // mañana entra un área nueva por SQL, este test la recorre sin tocarlo.
        List<String> responsibles = jdbcTemplate.queryForList(
                "SELECT DISTINCT responsible FROM workflow_transition", String.class);
        assertThat(responsibles).isNotEmpty();
        for (String responsible : responsibles) {
            assertThat(inboxIds(session, responsible))
                    .as("la bandeja de %s no debe listar un trámite cerrado", responsible)
                    .doesNotContain(id);
        }
    }

    @Test
    @DisplayName("la bandeja exige el responsable y una cota dentro del rango: si no, 400 problem+json (contrato 007)")
    void inboxRequiresTheResponsibleAndABoundedLimit() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get(INBOX).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get(INBOX).param("responsible", "COORDINACION").param("limit", "201")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // La cota inferior también es load-bearing: sin @Min(1), limit=0 llega al
        // repositorio y Limit.of(0) revienta en un 500 (review B1).
        mockMvc.perform(get(INBOX).param("responsible", "COORDINACION").param("limit", "0")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Con el responsable y sin cota, la cota por defecto alcanza: 200.
        mockMvc.perform(get(INBOX).param("responsible", "COORDINACION").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("un responsable que no existe en ninguna configuración: 200 con lista vacía, no 404 (contrato 007)")
    void anUnknownResponsibleAnswersAnEmptyListNotNotFound() throws Exception {
        // Un 404 filtraría qué etiquetas existen, y una etiqueta inventada no se
        // distingue de un área real sin nada pendiente.
        mockMvc.perform(get(INBOX).param("responsible", "NO_EXISTE").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- US2 de la 007: desde cuándo espera ---------------------------------------------

    @Test
    @DisplayName("una solicitud antigua devuelta hoy espera desde la devolución: waitingSince reciente, createdAt antiguo, y va DESPUÉS de la que lleva más tiempo detenida (D3, D5)")
    void anOldReturnedRequestWaitsSinceItsReturnNotSinceRegistration() throws Exception {
        MockHttpSession session = login();
        String stuck = registerAndGetId(session, "ADICION_CREDITOS",
                "Bandeja Detenida Hace Rato", "SIN-DATO-REAL-211");
        String returned = registerAndGetId(session, "ADICION_CREDITOS",
                "Bandeja Antigua Devuelta", "SIN-DATO-REAL-212");
        // Envejecer la radicación: created_at NO es inmutable (solo el timeline lo es, por
        // trg_timeline_immutable), y es exactamente el dato que NO debe mandar en el orden.
        jdbcTemplate.update("UPDATE request SET created_at = created_at - interval '60 days' WHERE id = ?",
                UUID.fromString(returned));
        mockMvc.perform(advanceRequest(returned, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(returned, "DEVUELTA", "Falta la firma del estudiante")
                        .session(session))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get(INBOX)
                        .param("responsible", "COORDINACION").param("limit", "200")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Orden: la detenida desde que nació va ANTES que la devuelta hace un instante,
        // aunque la devuelta se radicó dos meses antes. Ordenar por radicación lo invertiría.
        List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$[*].id");
        assertThat(ids).contains(stuck, returned);
        assertThat(ids.indexOf(stuck)).isLessThan(ids.indexOf(returned));

        // La devuelta: createdAt antiguo y waitingSince reciente, LOS DOS con el offset de
        // la sede (D4, FR-006). Un instante sin marcador se lee como hora local: es el
        // defecto que la 006 ya corrigió en sus DTO (CampusTime, revisión #34 M1) y que el
        // review de la 007 (M4) encontró reintroducido en createdAt.
        String createdAtJson = com.jayway.jsonpath.JsonPath.<List<String>>read(body,
                "$[?(@.id == '%s')].createdAt".formatted(returned)).get(0);
        String waitingSinceJson = com.jayway.jsonpath.JsonPath.<List<String>>read(body,
                "$[?(@.id == '%s')].waitingSince".formatted(returned)).get(0);
        assertThat(waitingSinceJson).endsWith("-05:00");
        assertThat(createdAtJson).endsWith("-05:00");
        OffsetDateTime waitingSince = OffsetDateTime.parse(waitingSinceJson);
        OffsetDateTime createdAt = OffsetDateTime.parse(createdAtJson);
        assertThat(waitingSince.toInstant())
                .isAfter(createdAt.toInstant().plus(Duration.ofDays(59)));

        // La detenida espera desde que nació: su waitingSince y su createdAt son el mismo
        // instante, salvo microsegundos (la entrada de nacimiento se escribe al registrar).
        String stuckWaiting = com.jayway.jsonpath.JsonPath.<List<String>>read(body,
                "$[?(@.id == '%s')].waitingSince".formatted(stuck)).get(0);
        String stuckCreated = com.jayway.jsonpath.JsonPath.<List<String>>read(body,
                "$[?(@.id == '%s')].createdAt".formatted(stuck)).get(0);
        assertThat(Duration.between(
                OffsetDateTime.parse(stuckCreated).toInstant(),
                OffsetDateTime.parse(stuckWaiting).toInstant()).abs())
                .isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("la bandeja emite el mismo número de sentencias SQL con N y con N+3 solicitudes: sin N+1 (T029)")
    void inboxStatementCountDoesNotGrowWithTheNumberOfEntries() throws Exception {
        // Medición real, no opinión: estadísticas de Hibernate habilitadas en runtime para no
        // tocar las properties compartidas de @TramitaIntegrationTest (cambiarlas invalidaría
        // el caché de contexto de todos los IT).
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            MockHttpSession session = login();
            registerAndGetId(session, "ADICION_CREDITOS", "Bandeja Conteo Uno", "SIN-DATO-REAL-221");
            registerAndGetId(session, "ADICION_CREDITOS", "Bandeja Conteo Dos", "SIN-DATO-REAL-222");
            long withN = statementsIssuedBy(statistics, session);

            registerAndGetId(session, "ADICION_CREDITOS", "Bandeja Conteo Tres", "SIN-DATO-REAL-223");
            registerAndGetId(session, "ADICION_CREDITOS", "Bandeja Conteo Cuatro", "SIN-DATO-REAL-224");
            registerAndGetId(session, "ADICION_CREDITOS", "Bandeja Conteo Cinco", "SIN-DATO-REAL-225");
            long withNPlusThree = statementsIssuedBy(statistics, session);

            assertThat(withNPlusThree)
                    .as("sentencias con N+3 pendientes frente a N: %d vs %d", withNPlusThree, withN)
                    .isEqualTo(withN);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    /** Sentencias preparadas que cuesta UNA consulta de la bandeja de la Coordinación. */
    private long statementsIssuedBy(Statistics statistics, MockHttpSession session) throws Exception {
        statistics.clear();
        mockMvc.perform(get(INBOX)
                        .param("responsible", "COORDINACION").param("limit", "200")
                        .session(session))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    /**
     * GUARDA, no RED: el DTO ya no lleva el campo desde la 004. Se conserva para que no se
     * pierda al ampliarlo (§III); su mutante es T019.
     */
    @Test
    @DisplayName("la bandeja nunca expone el número de documento (FR-014 de la 004, §III)")
    void inboxNeverExposesStudentDocument() throws Exception {
        MockHttpSession session = login();
        String document = "SIN-DATO-REAL-204";
        registerAndGetId(session, "ADICION_CREDITOS", "Bandeja Sin Cedula", document);

        String body = mockMvc.perform(get(INBOX)
                        .param("responsible", "COORDINACION").param("limit", "200")
                        .session(session))
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
        mockMvc.perform(get(INBOX).param("responsible", "COORDINACION"))
                .andExpect(status().isUnauthorized());
    }

    private List<String> inboxIds(MockHttpSession session, String responsible) throws Exception {
        String body = mockMvc.perform(get(INBOX)
                        .param("responsible", responsible).param("limit", "200")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$[*].id");
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

    // --- 006/US3, T035: historial de emisiones del documento --------------------------------

    @Test
    @DisplayName("dos emisiones del documento: el historial devuelve dos entradas en orden")
    void sealsHistoryListsTwoEmissionsInOrder() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Ana Con Historial", "SEAL-HIST-001");

        mockMvc.perform(get("/api/requests/" + id + "/document").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/requests/" + id + "/document").session(session))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/requests/" + id + "/seals").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].issuedBy").value(AuthControllerIT.SEED_EMAIL))
                .andExpect(jsonPath("$[0].formatVersion").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        List<String> codes = com.jayway.jsonpath.JsonPath.read(body, "$[*].verificationCode");
        List<String> issuedAts = com.jayway.jsonpath.JsonPath.read(body, "$[*].issuedAt");
        assertThat(codes.get(0))
                .as("cada emisión tiene SU propio código, no se deduplican")
                .isNotEqualTo(codes.get(1));
        assertThat(issuedAts.get(0).compareTo(issuedAts.get(1)))
                .as("de la más antigua a la más reciente")
                .isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("solicitud sin emisiones del documento: historial vacío, no 404")
    void sealsHistoryOfARequestWithoutEmissionsIsEmpty() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Ana Sin Emisiones", "SEAL-HIST-002");

        // La solicitud EXISTE; simplemente nadie pidió el documento todavía.
        mockMvc.perform(get("/api/requests/" + id + "/seals").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("historial de sellos con un {id} que no es UUID: 400")
    void sealsHistoryWithNonUuidIdIsBadRequest() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/requests/no-es-un-uuid/seals").session(session))
                .andExpect(status().isBadRequest());
    }

    // --- 008 / FR-008: el detalle expone el origen y el contacto en las tres acciones ------

    @Test
    @DisplayName("registrar con correo y teléfono los devuelve bajo su clave con origin COORDINATION, y el detalle repite lo mismo (008, FR-008)")
    void registerAndDetailExposeContactAndCoordinationOrigin() throws Exception {
        MockHttpSession session = login();
        String email = "contacto.expuesto@ejemplo.test";
        // Sintético a simple vista, como los documentos SIN-DATO-REAL (auditoría del
        // 2026-09-24, M4): cumple [0-9]{10} y ^3\\d{9}$ sin parecer un número real.
        String phone = "3000000001";
        String body = mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante Con Contacto Interno",
                          "studentDocument": "SIN-DATO-REAL-231",
                          "studentEmail": "%s",
                          "studentPhone": "%s"
                        }""".formatted(email, phone)).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("COORDINATION"))
                .andExpect(jsonPath("$.studentEmail").value(email))
                .andExpect(jsonPath("$.studentPhone").value(phone))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/requests/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("COORDINATION"))
                .andExpect(jsonPath("$.studentEmail").value(email))
                .andExpect(jsonPath("$.studentPhone").value(phone));
    }

    @Test
    @DisplayName("sin contacto declarado, la respuesta trae origin COORDINATION y NO trae las claves del contacto (008, NON_NULL)")
    void registerWithoutContactOmitsTheContactKeys() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(createRequest("ADICION_CREDITOS", "Estudiante Sin Contacto", "SIN-DATO-REAL-232")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("COORDINATION"))
                // También sobre el cuerpo crudo: sobre una clave presente con valor null,
                // jsonPath(...).doesNotExist() pasa igual y no detectaría que alguien quitó
                // el NON_NULL del record (mutante T020a). Es lo que ya hace el test del
                // correo con el valor, aplicado acá a la clave.
                .andExpect(jsonPath("$.studentEmail").doesNotExist())
                .andExpect(jsonPath("$.studentPhone").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"studentEmail\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"studentPhone\""))));
    }

    /**
     * GUARDA, no RED: ni la búsqueda ni la bandeja llevan el contacto hoy. Se fija para que
     * exponerlo en el detalle (T016) no lo filtre por accidente en los listados (§III); su
     * mutante es T020(b). Precedente: {@code inboxNeverExposesStudentDocument}.
     */
    @Test
    @DisplayName("la búsqueda y la bandeja nunca exponen correo ni teléfono, aunque la solicitud los tenga (008, §III)")
    void searchAndInboxNeverExposeContact() throws Exception {
        MockHttpSession session = login();
        String studentName = "Estudiante Listado Sin Contacto";
        String email = "listado.sin.contacto@ejemplo.test";
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "%s",
                          "studentDocument": "SIN-DATO-REAL-233",
                          "studentEmail": "%s",
                          "studentPhone": "3000000002"
                        }""".formatted(studentName, email)).session(session))
                .andExpect(status().isCreated());

        String search = mockMvc.perform(get("/api/requests").param("search", studentName).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[*].studentEmail").doesNotExist())
                .andExpect(jsonPath("$[*].studentPhone").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String inbox = mockMvc.perform(get(INBOX)
                        .param("responsible", "COORDINACION").param("limit", "200")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].studentEmail").doesNotExist())
                .andExpect(jsonPath("$[*].studentPhone").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Sobre el JSON servido: lo que se promete es que el dato no SALE por ninguna clave.
        assertThat(search).doesNotContain("\"studentEmail\"", "\"studentPhone\"", email, "3000000002");
        assertThat(inbox).doesNotContain("\"studentEmail\"", "\"studentPhone\"", email, "3000000002");
    }

    /**
     * GUARDA, no RED (SC-006): consultar los hechos del aviso no escribe. Se fija porque
     * desde T017 el detalle LEE el timeline para derivar el origen, y una lectura que
     * escribiera sería exactamente el error que este test detecta.
     */
    @Test
    @DisplayName("consultar el detalle no agrega entradas al timeline (008, SC-006)")
    void readingTheDetailWritesNothingToTheTimeline() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Consulta Sin Rastro", "SIN-DATO-REAL-234");
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/requests/" + id).session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/requests/" + id).session(session)).andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /**
     * GUARDA, no RED (FR-011; spec US2, escenario 3): el teléfono sale TAL COMO se guardó. Se
     * escribe por SQL y no por la API a propósito: representa las filas radicadas antes de la
     * 008 —que no se reescriben— y es estable en cualquier orden de ejecución, porque antes de
     * US3 el endpoint aceptaría este valor y después lo rechaza. Es legal: {@code request} no
     * tiene trigger de inmutabilidad ni CHECK sobre la columna (V3.3.0 solo la agrega como
     * VARCHAR(30)); {@code updatable = false} es una promesa de JPA, no de la base. Su valor es
     * el mutante T023: normalizar en el servidor, que es lo que FR-011 prohíbe.
     */
    @Test
    @DisplayName("un teléfono anterior a la feature sale verbatim en el detalle, sin normalizar (008, FR-011)")
    void detailReturnsLegacyPhoneVerbatim() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Estudiante Con Telefono Viejo",
                "SIN-DATO-REAL-235");
        String legacyPhone = "300 123 4567";
        assertThat(jdbcTemplate.update(
                "UPDATE request SET student_phone = ? WHERE id = ?::uuid", legacyPhone, id))
                .as("la fila existe y se pudo escribir el teléfono viejo por SQL")
                .isEqualTo(1);

        for (int lectura = 1; lectura <= 2; lectura++) {
            mockMvc.perform(get("/api/requests/" + id).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.studentPhone").value(legacyPhone));
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT student_phone FROM request WHERE id = ?::uuid", String.class, id))
                .as("consultar el detalle no reescribe la fila (FR-011)")
                .isEqualTo(legacyPhone);
    }

    /**
     * GUARDA, no RED (009, FR-005; US1 escenario 7): mismo criterio que
     * {@link #detailReturnsLegacyPhoneVerbatim()} arriba, pero para el programa. Se
     * escribe por SQL y no por la API a propósito: después de la 009 la API rechazaría
     * "Ing" al radicar, así que el test es estable en cualquier orden de ejecución. Es
     * legal: {@code request} no tiene trigger ni CHECK sobre {@code program}
     * (V2.3.0__Persist_request_form_data.sql:9 solo la agrega como VARCHAR(120)). Su
     * valor es el mutante de T028 «validar también al avanzar».
     */
    @Test
    @DisplayName("un programa anterior a la 009 sigue leyéndose y avanzando, sin validarse (009, FR-005)")
    void legacyProgramOutsideCatalogSurvivesReadAndAdvance() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS",
                "Estudiante Con Programa Legado", "SIN-DATO-REAL-907");
        assertThat(jdbcTemplate.update(
                "UPDATE request SET program = ? WHERE id = ?::uuid", "Ing", id))
                .as("la fila existe y se pudo escribir el programa legado por SQL")
                .isEqualTo(1);

        mockMvc.perform(get("/api/requests/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program").value("Ing"));

        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT program FROM request WHERE id = ?::uuid", String.class, id))
                .as("avanzar no valida ni reescribe el programa de una fila anterior a la 009")
                .isEqualTo("Ing");
    }

    /**
     * FR-010: el canal interno mantiene el teléfono opcional y, si viene, con forma. Que sea
     * 400 y no 422 es la convención del canal: un valor inválido es un defecto del contrato de
     * entrada, no un formato que no se puede procesar (GlobalExceptionHandler vs.
     * PublicCaptureExceptionHandler).
     */
    @Test
    @DisplayName("canal interno: sin teléfono 201, y con diez dígitos 201 devuelto bajo su clave (008, FR-010)")
    void internalChannelKeepsThePhoneOptional() throws Exception {
        MockHttpSession session = login();
        mockMvc.perform(createRequest("ADICION_CREDITOS", "Estudiante Sin Telefono Interno",
                        "SIN-DATO-REAL-236").session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentPhone").doesNotExist());

        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante Con Telefono Interno",
                          "studentDocument": "SIN-DATO-REAL-237",
                          "studentPhone": "3000000001"
                        }""").session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentPhone").value("3000000001"));
    }

    @Test
    @DisplayName("canal interno: un teléfono que no son diez dígitos es 400 «Petición inválida» que nombra el campo (008, FR-010)")
    void internalChannelRejectsMalformedPhoneNamingTheField() throws Exception {
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "ADICION_CREDITOS",
                          "studentName": "Estudiante Telefono Interno Mal Escrito",
                          "studentDocument": "SIN-DATO-REAL-238",
                          "studentPhone": "300 123 4567"
                        }""").session(login()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Petición inválida"))
                .andExpect(jsonPath("$.invalidFields.length()").value(1))
                .andExpect(jsonPath("$.invalidFields[0]").value("studentPhone"))
                .andExpect(jsonPath("$.missingFields.length()").value(0));
    }

    /**
     * FR-010 dice «la misma regla» que el canal público, y hasta el review con agente limpio
     * el canal interno solo probaba un valor con espacios: cualquier regex sin espacios lo
     * rechaza, así que tres mutantes sobre el patrón —{@code [0-9]{9,10}}, {@code .{10}} y
     * {@code ([0-9]{10})?}— sobrevivían con la suite en verde. Esta matriz es la del canal
     * público (FR-009), incluido el vacío: en el interno {@code ""} es «vino e inválido» y
     * responde 400, porque para no declarar teléfono se omite la clave.
     */
    @Test
    @DisplayName("canal interno: toda forma que no sean diez dígitos —9, 11, letras, vacío— es 400 que nombra el campo (008, FR-010)")
    void internalChannelRejectsEveryPhoneShapeThatIsNotTenDigits() throws Exception {
        MockHttpSession session = login();
        java.util.List<String> malformed = java.util.List.of(
                "300123456", "30012345678", "abcdefghij", "");
        for (String phone : malformed) {
            String response = mockMvc.perform(createRequestWithForm("""
                            {
                              "definitionCode": "ADICION_CREDITOS",
                              "studentName": "Estudiante Telefono Interno Forma Invalida",
                              "studentDocument": "SIN-DATO-REAL-239",
                              "studentPhone": "%s"
                            }""".formatted(phone)).session(session))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.title").value("Petición inválida"))
                    .andExpect(jsonPath("$.invalidFields.length()").value(1))
                    .andExpect(jsonPath("$.invalidFields[0]").value("studentPhone"))
                    .andExpect(jsonPath("$.missingFields.length()").value(0))
                    .andReturn().getResponse().getContentAsString();
            if (!phone.isEmpty()) {
                assertThat(response)
                        .as("[%s] el valor rechazado no se refleja de vuelta", phone)
                        .doesNotContain(phone);
            }
        }
    }

    /**
     * El contrato de la 008 promete que sin entrada de nacimiento {@code origin} va AUSENTE,
     * nunca {@code null}: es una anomalía de datos, no un tercer origen, y el cliente la trata
     * como «no pública» (FR-004). Ningún test lo fijaba en el detalle —solo el unitario de la
     * bandeja cubre {@code originOf}— y dos mutantes sobrevivían: un {@code COORDINATION} por
     * defecto en {@code toResponse} y un {@code @JsonInclude(ALWAYS)} sobre el campo. El
     * review con agente limpio reprodujo el caso con esta misma sonda: se inserta la fila de
     * {@code request} por SQL, sin escribir el timeline, que es lo único que el trigger
     * protege. Se afirma sobre el cuerpo crudo además del jsonPath porque
     * {@code doesNotExist()} acepta una clave presente con valor {@code null}.
     */
    @Test
    @DisplayName("sin entrada de nacimiento, el detalle omite origin: ausente, no null (008, contrato FR-004)")
    void detailOmitsOriginWhenTheBirthEntryIsMissing() throws Exception {
        MockHttpSession session = login();
        String template = registerAndGetId(session, "ADICION_CREDITOS",
                "Estudiante Con Nacimiento", "SIN-DATO-REAL-240");
        String orphan = java.util.UUID.randomUUID().toString();
        assertThat(jdbcTemplate.update("""
                INSERT INTO request (id, definition_id, current_state_id, student_name,
                                     student_document, version, created_at)
                SELECT ?::uuid, definition_id, current_state_id, 'Estudiante Sin Nacimiento',
                       'SIN-DATO-REAL-241', 0, created_at
                FROM request WHERE id = ?::uuid
                """, orphan, template))
                .as("la fila huérfana se insertó copiando definición y estado de una real")
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM request_transition_log WHERE request_id = ?::uuid",
                Long.class, orphan))
                .as("la huérfana no tiene entrada de nacimiento")
                .isZero();

        String body = mockMvc.perform(get("/api/requests/" + orphan).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .as("origin va ausente, no como clave con null")
                .doesNotContain("\"origin\"");
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
     * Publica una v2 de adición de créditos con su propio tope. Hasta la 007 le bastaba un
     * estado inicial; desde FR-014 el motor rechaza registrar en un inicial sin salida —con
     * razón: sería un callejón—, así que la v2 cierra en CERRADA. Un fixture no puede ser
     * la excepción de la regla que el sistema afirma. Este test sigue sin avanzar nada.
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
                INSERT INTO workflow_state (id, definition_id, code, name, is_initial, is_final)
                SELECT gen_random_uuid(), id, 'CERRADA', 'Cerrada', FALSE, TRUE
                FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2""");
        jdbcTemplate.update("""
                INSERT INTO workflow_transition
                    (id, definition_id, from_state_id, to_state_id, responsible, requires_note)
                SELECT gen_random_uuid(), d.id, f.id, s.id, 'COORDINACION', false
                FROM workflow_definition d
                JOIN workflow_state f ON f.definition_id = d.id AND f.code = 'REGISTRADA'
                JOIN workflow_state s ON s.definition_id = d.id AND s.code = 'CERRADA'
                WHERE d.code = 'ADICION_CREDITOS' AND d.version = 2""");
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
                DELETE FROM workflow_transition WHERE definition_id IN (
                    SELECT id FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2)""");
        jdbcTemplate.update("""
                DELETE FROM workflow_parameter WHERE definition_id IN (
                    SELECT id FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2)""");
        jdbcTemplate.update("""
                DELETE FROM workflow_state WHERE definition_id IN (
                    SELECT id FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2)""");
        jdbcTemplate.update(
                "DELETE FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 2");
    }

    @Test
    @DisplayName("400 de validación: llega servido como problem+json, en español y nombrando el campo")
    void validationFailureNamesTheOffendingField() throws Exception {
        // Es el caso real que originó el cambio: el formulario de novedad de notas no pide
        // créditos y enviaba el centinela 0, que viola @Min(1). La respuesta decía
        // «Invalid request content.» y el trámite era irradicable sin pista de la causa.
        //
        // El unit test del handler fija la DECISIÓN; este fija que llega servida por MVC,
        // que es donde se resolvería mal la precedencia entre advices si alguien la tocara.
        mockMvc.perform(createRequestWithForm("""
                        {
                          "definitionCode": "NOVEDAD_NOTAS",
                          "studentName": "Estudiante De Prueba",
                          "studentDocument": "DOC-TEST-0400",
                          "subjects": [{"code":"IS-704","name":"Arquitectura","credits":0}]
                        }""").session(login()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Petición inválida"))
                .andExpect(jsonPath("$.detail").value(
                        "El cuerpo de la petición tiene campos con un valor inválido. "
                                + "Campos: subjects[0].credits"))
                .andExpect(jsonPath("$.invalidFields.length()").value(1))
                .andExpect(jsonPath("$.invalidFields[0]").value("subjects[0].credits"))
                .andExpect(jsonPath("$.missingFields.length()").value(0));
    }

    @Test
    @DisplayName("400 por JSON ilegible: sigue siendo genérico, porque no hay campo que nombrar")
    void unreadableBodyStaysGeneric() throws Exception {
        // La enmienda del contrato cubre solo la violación de bean validation. Un cuerpo que
        // no se pudo leer lo sigue atendiendo el manejador heredado, y no tiene campos que
        // listar: afirmar lo contrario sería inventarlos.
        mockMvc.perform(createRequestWithForm("{\"definitionCode\":").session(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.missingFields").doesNotExist())
                .andExpect(jsonPath("$.invalidFields").doesNotExist());
    }
}
