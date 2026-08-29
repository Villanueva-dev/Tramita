package com.uniremington.api.tramita.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniremington.api.tramita.TestcontainersConfiguration;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Paquete de Boot 4 (modularizado): antes org.springframework.boot.test.autoconfigure.web.servlet
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IT del ciclo de vida de solicitudes sobre la semilla real (T014 RED antes de
 * T018-T021): contexto completo con PostgreSQL (Testcontainers) + Flyway + los
 * dos trámites de V2.1.0, ejercitando el filter chain de verdad — los
 * escenarios 401 son los acceptance de FR-012. Mismas properties que
 * AuthControllerIT para compartir el contexto cacheado entre ITs.
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
class RequestControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IRequestRepo requestRepo;

    @Autowired
    private IRequestTransitionLogRepo logRepo;

        @Autowired
        private JdbcTemplate jdbcTemplate;

    // --- US1: registrar ------------------------------------------------------------------

    @Test
    @DisplayName("registrar adición de créditos: 201 + Location, nace en REGISTRADA con sus transiciones")
    void registerCreatesRequestInInitialStateOfItsDefinition() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(createRequest("ADICION_CREDITOS", "Ana María Pérez", "1144099888")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.definition.code").value("ADICION_CREDITOS"))
                .andExpect(jsonPath("$.definition.version").value(1))
                .andExpect(jsonPath("$.studentName").value("Ana María Pérez"))
                .andExpect(jsonPath("$.currentState.code").value("REGISTRADA"))
                .andExpect(jsonPath("$.currentState.isFinal").value(false))
                // Las transiciones salen de la definición, no de código a medida
                .andExpect(jsonPath("$.availableTransitions[0].targetState.code")
                        .value("EN_FACULTAD"));
    }

                @Test
                @DisplayName("registrar solicitud completa: persiste datos académicos y asignaturas")
                void registerPersistsCompleteFormData() throws Exception {
                                MockHttpSession session = login();

                                mockMvc.perform(post("/api/requests")
                                                                                                .with(csrf())
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "definitionCode":"NOVEDAD_NOTAS",
                                                                                                                                        "studentName":"Estudiante Completo",
                                                                                                                                        "studentDocument":"1144002200",
                                                                                                                                        "studentCode":"1090234",
                                                                                                                                        "studentEmail":"estudiante@remington.edu.co",
                                                                                                                                        "program":"Ingeniería de Sistemas",
                                                                                                                                        "semester":"Semestre 7",
                                                                                                                                        "reason":"La nota registrada no coincide con el acta.",
                                                                                                                                        "priority":"urgente",
                                                                                                                                        "subjects":[{
                                                                                                                                                "code":"IS-704",
                                                                                                                                                "name":"Arquitectura de Software",
                                                                                                                                                "credits":3,
                                                                                                                                                "group":"A1",
                                                                                                                                                "currentGrade":"2.9",
                                                                                                                                                "proposedGrade":"3.6"
                                                                                                                                        }]
                                                                                                                                }
                                                                                                                                """)
                                                                                                .session(session))
                                                                .andExpect(status().isCreated())
                                                                .andExpect(jsonPath("$.studentCode").value("1090234"))
                                                                .andExpect(jsonPath("$.studentEmail").value("estudiante@remington.edu.co"))
                                                                .andExpect(jsonPath("$.program").value("Ingeniería de Sistemas"))
                                                                .andExpect(jsonPath("$.priority").value("urgente"))
                                                                .andExpect(jsonPath("$.subjects[0].code").value("IS-704"))
                                                                .andExpect(jsonPath("$.subjects[0].proposedGrade").value("3.6"));
                }

    @Test
    @DisplayName("los dos trámites coexisten: cada solicitud nace en el estado inicial de SU definición")
    void requestsOfBothProceduresCoexistWithTheirOwnInitialState() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(createRequest("ADICION_CREDITOS", "Estudiante Uno", "111")
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentState.code").value("REGISTRADA"))
                .andExpect(jsonPath("$.availableTransitions[0].targetState.code")
                        .value("EN_FACULTAD"));

        // Novedad de notas arranca igual (REGISTRADA) pero su camino es propio:
        // hacia EN_PREPARACION — la diferencia vive en la definición (US4/FR-010)
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

        // Sin SMTP en tests, el cierre deja evidencia del fallo email y del fallback manual.
        assertThat(countNotifications(id, "EMAIL", "FAILED")).isEqualTo(1);
        assertThat(countNotifications(id, "MANUAL", "PENDING")).isEqualTo(1);
    }

    @Test
    @DisplayName("transición no definida: 409 problem+json y el estado queda intacto")
    void undefinedTransitionReturns409AndStateSurvives() throws Exception {
        MockHttpSession session = login();
        String id = registerAndGetId(session, "ADICION_CREDITOS", "Saltarina Ilegal", "302");

        // REGISTRADA → FINALIZADA no está definida: el camino pasa por la facultad
        mockMvc.perform(advanceRequest(id, "FINALIZADA", null).session(session))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());

        // La prueba de que el estado no se corrompió: la transición legal desde
        // REGISTRADA sigue disponible y funciona
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

        // El estado sigue siendo REGISTRADA: el avance legal aún es EN_FACULTAD
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
                .andExpect(jsonPath("$[0].toState.code").value("REGISTRADA"))
                .andExpect(jsonPath("$[0].actorEmail").value(AuthControllerIT.SEED_EMAIL))
                .andExpect(jsonPath("$[0].occurredAt").exists())
                // El envío a facultad lo hace la Coordinación en nombre propio
                .andExpect(jsonPath("$[1].fromState.code").value("REGISTRADA"))
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
                .andExpect(jsonPath("$[0].currentState.code").value("REGISTRADA"));

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
                .andExpect(jsonPath("$.currentState.code").value("REGISTRADA"))
                .andExpect(jsonPath("$.availableTransitions[0].targetState.code")
                        .value("EN_FACULTAD"));

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

    @Test
    @DisplayName("aprobaciones documentales: registra firma externa con sello UTC y conserva el hash aprobado")
    void documentApprovalsPersistSignatureTrace() throws Exception {
        MockHttpSession session = login();
        String requestId = registerAndGetId(session, "ADICION_CREDITOS", "Documento Firmado", "505505");
        String documentBody = mockMvc.perform(uploadDocument(requestId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256").exists())
                .andReturn().getResponse().getContentAsString();
        String documentId = com.jayway.jsonpath.JsonPath.read(documentBody, "$.id");
        String approvedHash = com.jayway.jsonpath.JsonPath.read(documentBody, "$.sha256");

        mockMvc.perform(post("/api/requests/" + requestId + "/documents/" + documentId + "/approvals")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "signerName":"Decanatura de Facultad",
                                  "signerRole":"FACULTAD",
                                  "signatureType":"ESCANEADA",
                                  "signedAt":"2026-08-26T10:15:30",
                                  "note":"Firma recibida por correo institucional"
                                }
                                """)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signerName").value("Decanatura de Facultad"))
                .andExpect(jsonPath("$.signatureType").value("ESCANEADA"))
                .andExpect(jsonPath("$.documentSha256").value(approvedHash))
                .andExpect(jsonPath("$.recordedByEmail").value(AuthControllerIT.SEED_EMAIL))
                .andExpect(jsonPath("$.timestampedAt").exists());

        mockMvc.perform(get("/api/requests/" + requestId + "/documents/" + documentId + "/approvals")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].signerRole").value("FACULTAD"))
                .andExpect(jsonPath("$[0].note").value("Firma recibida por correo institucional"));
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
        assertThat(countNotifications(id, "EMAIL", "FAILED")).isZero();
        assertThat(countNotifications(id, "MANUAL", "PENDING")).isZero();
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
        // Corregida: la Coordinación la reenvía a la facultad
        mockMvc.perform(advanceRequest(id, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/" + id + "/timeline").session(session))
                .andExpect(jsonPath("$.length()").value(4))
                // Nada se sobrescribió: nacimiento, envío, devolución y reenvío conviven
                .andExpect(jsonPath("$[1].toState.code").value("EN_FACULTAD"))
                .andExpect(jsonPath("$[2].toState.code").value("DEVUELTA"))
                .andExpect(jsonPath("$[2].note").value("Falta soporte de pago"))
                .andExpect(jsonPath("$[3].toState.code").value("EN_FACULTAD"))
                // SC-007: las devoluciones se cuentan filtrando el timeline —
                // aquí, exactamente una y con su motivo
                .andExpect(jsonPath("$[?(@.toState.code == 'DEVUELTA')].note")
                        .value(org.hamcrest.Matchers.contains("Falta soporte de pago")));
    }

    @Test
    @DisplayName("métricas operativas: agrega estados, devoluciones y ciclo sin exponer datos personales")
    void requestMetricsAggregatesOperationalData() throws Exception {
        MockHttpSession session = login();
        String metricsBefore = mockMvc.perform(get("/api/metrics/requests").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.byDefinition.ADICION_CREDITOS").exists())
                .andReturn().getResponse().getContentAsString();
        long totalBefore = ((Number) com.jayway.jsonpath.JsonPath.read(metricsBefore, "$.total")).longValue();
        long returnedBefore = ((Number) com.jayway.jsonpath.JsonPath.read(metricsBefore, "$.returnCount")).longValue();
        long registeredBefore = ((Number) com.jayway.jsonpath.JsonPath.read(metricsBefore, "$.byCurrentState.REGISTRADA")).longValue();

        String returnedId = registerAndGetId(session, "ADICION_CREDITOS", "Métrica Devuelta", "801801");
        mockMvc.perform(advanceRequest(returnedId, "EN_FACULTAD", null).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(advanceRequest(returnedId, "DEVUELTA", "Falta un soporte").session(session))
                .andExpect(status().isOk());

        String completedId = registerAndGetId(session, "ADICION_CREDITOS", "Métrica Finalizada", "802802");
        for (String state : new String[] {
                "EN_FACULTAD", "APROBADA_FACULTAD", "EN_REGISTRO_CALI", "EN_REGISTRO_NACIONAL", "FINALIZADA" }) {
            mockMvc.perform(advanceRequest(completedId, state, null).session(session))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/metrics/requests").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value((int) totalBefore + 2))
                .andExpect(jsonPath("$.byCurrentState.REGISTRADA").value((int) registeredBefore))
                .andExpect(jsonPath("$.byCurrentState.DEVUELTA").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.completed").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.averageCycleHours").isNumber())
                .andExpect(jsonPath("$.returnCount").value((int) returnedBefore + 1))
                .andExpect(jsonPath("$.studentName").doesNotExist());
    }

    @Test
    @DisplayName("métricas operativas sin sesión: 401")
    void requestMetricsWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/metrics/requests"))
                .andExpect(status().isUnauthorized());
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

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder uploadDocument(String requestId) {
        MockMultipartFile file = new MockMultipartFile(
                "file", "soporte.pdf", "application/pdf", "%PDF-1.4 soporte".getBytes());
        return multipart("/api/requests/" + requestId + "/documents")
                .file(file)
                .with(csrf());
    }

    private long countNotifications(String requestId, String channel, String status) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM request_notification
                WHERE request_id = CAST(? AS UUID)
                  AND channel = ?
                  AND status = ?
                """,
                Long.class,
                requestId,
                channel,
                status);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest(
            String definitionCode, String studentName, String studentDocument) {
        return post("/api/requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"definitionCode\":\"%s\",\"studentName\":\"%s\",\"studentDocument\":\"%s\"}"
                        .formatted(definitionCode, studentName, studentDocument));
    }
}
