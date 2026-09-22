package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.dto.AdvanceRequestBody;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.InboxEntryResponse;
import com.uniremington.api.tramita.dto.PublicRequestBody;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestTransitionLog;
import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowParameter;
import com.uniremington.api.tramita.model.WorkflowState;
import com.uniremington.api.tramita.model.WorkflowTransition;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import com.uniremington.api.tramita.repo.IUserRepo;
import com.uniremington.api.tramita.repo.IWorkflowParameterRepo;
import com.uniremington.api.tramita.repo.IWorkflowDefinitionRepo;
import com.uniremington.api.tramita.service.IRequestBusinessRules;
import com.uniremington.api.tramita.service.IWorkflowGuard;
import com.uniremington.api.tramita.shared.exception.GuardRejectedException;
import com.uniremington.api.tramita.shared.exception.IllegalTransitionException;
import com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import com.uniremington.api.tramita.util.CampusTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

/**
 * Unit test del algoritmo del motor (T013 RED antes de T018; T022 RED antes de
 * T025). Los repositorios son la única frontera mockeada; las definiciones se
 * construyen en memoria con los builders — el motor compara estados por code,
 * así que la definición en memoria y la de BD son equivalentes para la lógica.
 * Estado observable (respuesta + entidades capturadas al persistir), no verify
 * de interacción cuando hay estado que assertar.
 */
class RequestServiceImplTest {

    private static final String EMAIL = "coordinacion.cali@uniremington.edu.co";

    private final IWorkflowDefinitionRepo definitionRepo = mock(IWorkflowDefinitionRepo.class);
    private final IRequestRepo requestRepo = mock(IRequestRepo.class);
    private final IRequestTransitionLogRepo logRepo = mock(IRequestTransitionLogRepo.class);
    private final IUserRepo userRepo = mock(IUserRepo.class);
    /**
     * Las reglas de negocio (003/US2) tienen su propio test unitario. Acá van
     * mockeadas y permisivas a propósito: estos casos verifican el MOTOR —que no
     * conoce ningún trámite—, y meterle reglas concretas los ataría a un dominio
     * que el motor no debe conocer.
     */
    private final IRequestBusinessRules businessRules = mock(IRequestBusinessRules.class);

    /**
     * Lo usa el canal público de la 004 para leer PUBLIC_CAPTURE_ENABLED. Los casos
     * de este test no lo ejercitan; está para satisfacer al constructor.
     */
    private final IWorkflowParameterRepo parameterRepo = mock(IWorkflowParameterRepo.class);
    /** Sin guardas registradas: los casos de US1–US3 no las ejercitan (FR-017). */
    private final RequestServiceImpl service = serviceWith();

    private final User actor = new User();

    /**
     * Definición mínima en memoria: INICIAL → SIGUIENTE → FINAL, más la
     * devolución SIGUIENTE → INICIAL con nota obligatoria (FR-013/FR-014).
     * Códigos genéricos a propósito: el motor no conoce trámites (US4) y este
     * test tampoco debería.
     */
    private final WorkflowState initial =
            WorkflowState.builder().code("INICIAL").name("Inicial").initial(true).build();
    private final WorkflowState next =
            WorkflowState.builder().code("SIGUIENTE").name("Siguiente").build();
    private final WorkflowState terminal =
            WorkflowState.builder().code("FINAL").name("Final").finalState(true).build();
    private final WorkflowDefinition definition = WorkflowDefinition.builder()
            .code("TRAMITE_PRUEBA")
            .version(1)
            .name("Trámite de prueba")
            .states(List.of(initial, next, terminal))
            .transitions(List.of(
                    WorkflowTransition.builder()
                            .fromState(initial).toState(next)
                            .responsible("EXTERNO").requiresNote(false).build(),
                    WorkflowTransition.builder()
                            .fromState(next).toState(terminal)
                            .responsible("COORDINACION").requiresNote(false).build(),
                    // Devolución: retorno con motivo obligatorio (FR-013/FR-014)
                    WorkflowTransition.builder()
                            .fromState(next).toState(initial)
                            .responsible("EXTERNO").requiresNote(true).build()))
            .build();

    // --- US1: registrar ------------------------------------------------------------------

    @Test
    @DisplayName("registrar: nace en el estado inicial de SU definición y escribe la entrada inicial del log")
    void registerCreatesRequestInInitialStateWithBirthLogEntry() {
        stubHappyPath();

        RequestResponse response = service.register(
                new CreateRequestBody("TRAMITE_PRUEBA", "Ana María Pérez", "DOC-PRUEBA-001"), EMAIL);

        // La respuesta refleja el nacimiento: estado inicial y transiciones derivadas
        assertThat(response.currentState().code()).isEqualTo("INICIAL");
        assertThat(response.definition().code()).isEqualTo("TRAMITE_PRUEBA");
        assertThat(response.availableTransitions())
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.targetState().code()).isEqualTo("SIGUIENTE");
                    assertThat(t.responsible()).isEqualTo("EXTERNO");
                });

        // La solicitud queda atada a la definición con la que nació (FR-009)
        ArgumentCaptor<Request> savedRequest = ArgumentCaptor.forClass(Request.class);
        verify(requestRepo).save(savedRequest.capture());
        assertThat(savedRequest.getValue().getDefinition()).isSameAs(definition);
        assertThat(savedRequest.getValue().getCurrentState()).isSameAs(initial);

        // Entrada de nacimiento (research.md D7): from NULL, autor real
        ArgumentCaptor<RequestTransitionLog> savedLog =
                ArgumentCaptor.forClass(RequestTransitionLog.class);
        verify(logRepo).save(savedLog.capture());
        assertThat(savedLog.getValue().getFromState()).isNull();
        assertThat(savedLog.getValue().getToState()).isSameAs(initial);
        assertThat(savedLog.getValue().getActor()).isSameAs(actor);
    }

    @Test
    @DisplayName("registrar un tipo de trámite inexistente: 422 y no se persiste nada")
    void registerUnknownDefinitionRejectsWithoutPersisting() {
        when(definitionRepo.findTopByCodeOrderByVersionDesc("TRAMITE_FANTASMA"))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(UnprocessableRequestException.class)
                .isThrownBy(() -> service.register(
                        new CreateRequestBody("TRAMITE_FANTASMA", "Ana", "123"), EMAIL));

        verify(requestRepo, never()).save(any());
        verify(logRepo, never()).save(any());
    }

    // --- US2: avanzar (el algoritmo del motor) -------------------------------------------

    @Test
    @DisplayName("avanzar por una transición definida: el estado cambia y el log registra al autor")
    void advanceAppliesDefinedTransitionAndLogsActor() {
        Request request = requestAt(initial);

        RequestResponse response = service.advance(
                REQUEST_ID, new AdvanceRequestBody("SIGUIENTE", null), EMAIL);

        assertThat(response.currentState().code()).isEqualTo("SIGUIENTE");
        assertThat(request.getCurrentState()).isSameAs(next);

        ArgumentCaptor<RequestTransitionLog> savedLog =
                ArgumentCaptor.forClass(RequestTransitionLog.class);
        verify(logRepo).save(savedLog.capture());
        assertThat(savedLog.getValue().getFromState()).isSameAs(initial);
        assertThat(savedLog.getValue().getToState()).isSameAs(next);
        assertThat(savedLog.getValue().getActor()).isSameAs(actor);
        assertThat(savedLog.getValue().getNote()).isNull();
        // La fecha (occurred_at) la pone @PrePersist al persistir de verdad: se
        // verifica en el IT del timeline (US3), no aquí contra un mock.
    }

    @Test
    @DisplayName("transición no definida: 409, el estado no cambia y el timeline no crece")
    void advanceUndefinedTransitionRejectsWithoutSideEffects() {
        Request request = requestAt(initial);

        // INICIAL → FINAL no existe en la definición (el camino pasa por SIGUIENTE)
        assertThatExceptionOfType(IllegalTransitionException.class)
                .isThrownBy(() -> service.advance(
                        REQUEST_ID, new AdvanceRequestBody("FINAL", null), EMAIL));

        assertThat(request.getCurrentState()).isSameAs(initial);
        verify(logRepo, never()).save(any());
    }

    @Test
    @DisplayName("solicitud en estado final: el trámite está cerrado y no admite más transiciones")
    void advanceFromFinalStateRejects() {
        Request request = requestAt(terminal);

        assertThatExceptionOfType(IllegalTransitionException.class)
                .isThrownBy(() -> service.advance(
                        REQUEST_ID, new AdvanceRequestBody("INICIAL", null), EMAIL));

        assertThat(request.getCurrentState()).isSameAs(terminal);
        verify(logRepo, never()).save(any());
    }

    @Test
    @DisplayName("devolución sin motivo: 422, sin efectos — el motivo es lo que la hace útil")
    void advanceReturnTransitionWithoutNoteRejects() {
        Request request = requestAt(next);

        assertThatExceptionOfType(UnprocessableRequestException.class)
                .isThrownBy(() -> service.advance(
                        REQUEST_ID, new AdvanceRequestBody("INICIAL", " "), EMAIL));

        assertThat(request.getCurrentState()).isSameAs(next);
        verify(logRepo, never()).save(any());
    }

    @Test
    @DisplayName("devolución con motivo: aplica y la nota queda en el log (FR-014)")
    void advanceReturnTransitionWithNoteAppliesAndLogsNote() {
        requestAt(next);

        RequestResponse response = service.advance(
                REQUEST_ID,
                new AdvanceRequestBody("INICIAL", "Falta la firma de la casilla 2"), EMAIL);

        assertThat(response.currentState().code()).isEqualTo("INICIAL");

        ArgumentCaptor<RequestTransitionLog> savedLog =
                ArgumentCaptor.forClass(RequestTransitionLog.class);
        verify(logRepo).save(savedLog.capture());
        assertThat(savedLog.getValue().getNote()).isEqualTo("Falta la firma de la casilla 2");
    }

    @Test
    @DisplayName("solicitud inexistente: 404")
    void advanceUnknownRequestThrowsNotFound() {
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.advance(
                        REQUEST_ID, new AdvanceRequestBody("SIGUIENTE", null), EMAIL));
    }

    // --- US4: guardas de transición ------------------------------------------------------

    private static final String GUARD_KEY = "REGLA_DE_PRUEBA";

    /**
     * Guarda de prueba: vive SOLO en el árbol de tests. Esta feature entrega el
     * mecanismo y ninguna guarda de producción (research.md D5), así que esta es la
     * única implementación que existe. Cuenta evaluaciones para poder afirmar que
     * una transición sin guard_key no la consulta (FR-017).
     */
    private static final class TestGuard implements IWorkflowGuard {

        private final String key;
        private final boolean verdict;
        private int evaluations;

        private TestGuard(String key, boolean verdict) {
            this.key = key;
            this.verdict = verdict;
        }

        @Override
        public String guardKey() {
            return key;
        }

        @Override
        public boolean isSatisfiedBy(Request request) {
            evaluations++;
            return verdict;
        }
    }

    /** Definición mínima INICIAL → SIGUIENTE condicionada por la clave dada. */
    // --- Canal público: la compuerta que decide qué trámites se diligencian sin sesión ---

    private static final java.util.UUID PUBLIC_DEFINITION_ID =
            java.util.UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Test
    @DisplayName("PUBLIC_CAPTURE_ENABLED no interpretable: es configuración rota, nunca un canal cerrado en silencio")
    void nonBooleanPublicCaptureFlagIsInvalidConfiguration() {
        stubPublicDefinition("sí");

        // Leerlo como false dejaría un trámite declarado como público rechazando todo
        // con un 404 y culpando de la configuración rota a quien diligencia el formato.
        // Leerlo como true es peor: abriría a envíos anónimos un trámite que nadie habilitó.
        assertThatExceptionOfType(IncompleteConfigurationException.class)
                .isThrownBy(() -> service.registerFromPublicChannel("ADICION_CREDITOS", publicBody()));

        verify(requestRepo, never()).save(any());
    }

    @Test
    @DisplayName("PUBLIC_CAPTURE_ENABLED en false explícito: el canal queda cerrado, no abierto")
    void explicitFalsePublicCaptureFlagKeepsTheChannelClosed() {
        stubPublicDefinition("false");

        // El trámite declara que NO tiene canal público. Abrirlo sería aceptar envíos
        // anónimos en un trámite que nadie habilitó: el mismo fail-open que vigila el
        // caso no interpretable, por la otra puerta de la misma compuerta.
        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.registerFromPublicChannel("ADICION_CREDITOS", publicBody()));

        verify(requestRepo, never()).save(any());
    }

    private void stubPublicDefinition(String flagValue) {
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .id(PUBLIC_DEFINITION_ID)
                .code("ADICION_CREDITOS")
                .version(1)
                .name("Adición de créditos")
                .states(List.of(initial, next))
                .build();
        when(definitionRepo.findTopByCodeOrderByVersionDesc("ADICION_CREDITOS"))
                .thenReturn(Optional.of(definition));
        when(parameterRepo.findByDefinitionIdAndKey(PUBLIC_DEFINITION_ID, "PUBLIC_CAPTURE_ENABLED"))
                .thenReturn(Optional.of(WorkflowParameter.builder()
                        .key("PUBLIC_CAPTURE_ENABLED")
                        .value(flagValue)
                        .build()));
    }

    /** Cuerpo válido: la compuerta se evalúa antes de mirarlo, así que su contenido no influye. */
    private PublicRequestBody publicBody() {
        return new PublicRequestBody("Ana Pérez", "1234567890", "ana@uniremington.edu.co",
                "3001234567", null, "Ingeniería de Sistemas", "Cali", "Ingeniería",
                "Distancia", "8", "Necesito la asignatura para graduarme", "data:image/png;base64,AAAA");
    }

    /**
     * Definición mínima con una transición guardada. Cierra en FINAL a propósito: hasta la
     * 007 dejaba SIGUIENTE sin salida, y el motor rechaza ahora ese callejón (FR-014) — con
     * razón. Un fixture no puede ser la excepción de la regla que el sistema afirma.
     */
    private WorkflowDefinition definitionGuardedBy(String code, String guardKey) {
        return WorkflowDefinition.builder()
                .code(code)
                .version(1)
                .name("Trámite con guarda")
                .states(List.of(initial, next, terminal))
                .transitions(List.of(
                        WorkflowTransition.builder()
                                .fromState(initial).toState(next)
                                .responsible("COORDINACION").requiresNote(false)
                                .guardKey(guardKey).build(),
                        WorkflowTransition.builder()
                                .fromState(next).toState(terminal)
                                .responsible("COORDINACION").requiresNote(false).build()))
                .build();
    }

    @Test
    @DisplayName("guarda satisfecha: la transición procede como cualquier otra (US4-1)")
    void advanceWithSatisfiedGuardProceeds() {
        TestGuard guard = new TestGuard(GUARD_KEY, true);
        Request request = requestAt(initial, definitionGuardedBy("CON_GUARDA", GUARD_KEY));

        RequestResponse response = serviceWith(guard)
                .advance(REQUEST_ID, new AdvanceRequestBody("SIGUIENTE", null), EMAIL);

        assertThat(response.currentState().code()).isEqualTo("SIGUIENTE");
        assertThat(request.getCurrentState()).isSameAs(next);
        assertThat(guard.evaluations).isEqualTo(1);
        verify(logRepo).save(any());
    }

    @Test
    @DisplayName("guarda no satisfecha: 409, sin efectos, y el detalle nombra la regla (US4-2)")
    void advanceWithUnsatisfiedGuardBlocksWithoutSideEffects() {
        TestGuard guard = new TestGuard(GUARD_KEY, false);
        Request request = requestAt(initial, definitionGuardedBy("CON_GUARDA", GUARD_KEY));

        // El tipo es la afirmación: GuardRejectedException, no su padre. Si el motor
        // dejara de resolver la guarda y cayera en el "transición no definida" de
        // arriba, lanzaría IllegalTransitionException y este caso fallaría.
        assertThatExceptionOfType(GuardRejectedException.class)
                .isThrownBy(() -> serviceWith(guard)
                        .advance(REQUEST_ID, new AdvanceRequestBody("SIGUIENTE", null), EMAIL))
                .withMessageContaining(GUARD_KEY);

        assertThat(request.getCurrentState()).isSameAs(initial);
        verify(logRepo, never()).save(any());
    }

    @Test
    @DisplayName("transición sin guarda: se comporta como en la 002 y no consulta regla alguna (US4-3, FR-017)")
    void advanceWithoutGuardKeySkipsEvaluationEntirely() {
        // La definición de siempre: todas sus transiciones tienen guard_key nulo,
        // como las que V2.1.0 dejó sembradas. La guarda registrada NIEGA, así que si
        // el motor la consultara este avance fallaría en vez de proceder.
        TestGuard guard = new TestGuard(GUARD_KEY, false);
        Request request = requestAt(initial);

        RequestResponse response = serviceWith(guard)
                .advance(REQUEST_ID, new AdvanceRequestBody("SIGUIENTE", null), EMAIL);

        assertThat(response.currentState().code()).isEqualTo("SIGUIENTE");
        assertThat(request.getCurrentState()).isSameAs(next);
        assertThat(guard.evaluations).isZero();
    }

    @Test
    @DisplayName("dos definiciones con la misma clave: ambas la evalúan sin que el motor conozca el trámite (US4-4, FR-018)")
    void advanceResolvesSameGuardKeyAcrossDifferentDefinitions() {
        TestGuard guard = new TestGuard(GUARD_KEY, true);
        RequestServiceImpl guarded = serviceWith(guard);

        requestAt(initial, definitionGuardedBy("ADICION_PRUEBA", GUARD_KEY));
        assertThat(guarded.advance(REQUEST_ID, new AdvanceRequestBody("SIGUIENTE", null), EMAIL)
                .currentState().code()).isEqualTo("SIGUIENTE");

        requestAt(initial, definitionGuardedBy("NOVEDAD_PRUEBA", GUARD_KEY));
        assertThat(guarded.advance(REQUEST_ID, new AdvanceRequestBody("SIGUIENTE", null), EMAIL)
                .currentState().code()).isEqualTo("SIGUIENTE");

        assertThat(guard.evaluations).isEqualTo(2);
    }

    @Test
    @DisplayName("clave sin implementación registrada: 500 de configuración y la transición NO se ejecuta (US4-5, FR-019)")
    void advanceWithUnknownGuardKeyFailsClosed() {
        Request request =
                requestAt(initial, definitionGuardedBy("CON_GUARDA", "REGLA_INEXISTENTE"));

        // Sin guardas registradas la clave no resuelve. Falla cerrado: omitirla
        // ejecutaría una transición cuya condición nadie llegó a evaluar.
        assertThatExceptionOfType(IncompleteConfigurationException.class)
                .isThrownBy(() -> serviceWith()
                        .advance(REQUEST_ID, new AdvanceRequestBody("SIGUIENTE", null), EMAIL));

        assertThat(request.getCurrentState()).isSameAs(initial);
        verify(logRepo, never()).save(any());
    }

    // --- helpers -------------------------------------------------------------------------

    private static final java.util.UUID REQUEST_ID =
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");

    // --- 007 US2: desde cuándo espera cada entrada de la bandeja ---------------------
    // El criterio de selección vive en la consulta y se prueba en el IT (RequestControllerIT);
    // acá se prueba lo que SÍ es lógica del servicio: combinar el timeline con la
    // radicación, convertir a la hora de la sede y ordenar (research.md D3, D4, D5).

    private static final java.util.UUID SECOND_ID =
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final java.util.UUID THIRD_ID =
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("bandeja: waitingSince es el occurredAt de la ÚLTIMA transición, no la radicación (D3)")
    void inboxWaitingSinceIsTheLastTransitionNotTheRegistration() {
        // Radicada hace dos meses, movida dos veces: espera desde el ÚLTIMO movimiento.
        LocalDateTime registered = LocalDateTime.of(2026, 7, 1, 12, 0);
        LocalDateTime firstMove = registered.plusDays(10);
        LocalDateTime lastMove = registered.plusDays(60);
        Request request = pendingRequest(REQUEST_ID, registered);
        when(requestRepo.findPendingFor(any(), any(Limit.class))).thenReturn(List.of(request));
        when(logRepo.findTimelinesOf(any())).thenReturn(List.of(
                entry(request, null, initial, registered),
                entry(request, initial, next, firstMove),
                entry(request, next, initial, lastMove)));

        List<InboxEntryResponse> inbox = service.getInbox("EXTERNO", 50);

        assertThat(inbox).singleElement().satisfies(entry -> {
            assertThat(entry.waitingSince()).isEqualTo(CampusTime.toCampus(lastMove));
            // Confundir los dos campos es el error que el frontend comete hoy: se fija
            // que son instantes DISTINTOS, no solo que uno tiene el valor esperado.
            assertThat(entry.waitingSince().toInstant()).isNotEqualTo(entry.createdAt().toInstant());
            // createdAt también sale con el offset de la sede (review M4). El offset se
            // afirma aparte: AssertJ compara OffsetDateTime por instante, y UTC crudo y
            // Bogotá son el mismo instante con distinto marcador.
            assertThat(entry.createdAt()).isEqualTo(CampusTime.toCampus(registered));
            assertThat(entry.createdAt().getOffset())
                    .isEqualTo(CampusTime.toCampus(registered).getOffset());
        });
    }

    @Test
    @DisplayName("bandeja: sin entradas de timeline, waitingSince es la radicación — respaldo defensivo, no un caso real")
    void inboxWaitingSinceFallsBackToRegistrationWhenThereIsNoTimeline() {
        // En producción no ocurre: toda solicitud nace con una entrada (register, también
        // por el canal público). El test fija que el respaldo existe, no que el caso sea real.
        LocalDateTime registered = LocalDateTime.of(2026, 7, 1, 12, 0);
        Request request = pendingRequest(REQUEST_ID, registered);
        when(requestRepo.findPendingFor(any(), any(Limit.class))).thenReturn(List.of(request));
        when(logRepo.findTimelinesOf(any())).thenReturn(List.of());

        List<InboxEntryResponse> inbox = service.getInbox("EXTERNO", 50);

        assertThat(inbox).singleElement()
                .extracting(InboxEntryResponse::waitingSince)
                .isEqualTo(CampusTime.toCampus(registered));
    }

    @Test
    @DisplayName("bandeja: ordenada por waitingSince ascendente — primero la que más lleva esperando (D5)")
    void inboxIsOrderedByWaitingSinceAscending() {
        // El repositorio las devuelve por radicación (corte bajo la cota, D8); la espera
        // las reordena: la radicada ÚLTIMA es la que más lleva detenida.
        LocalDateTime base = LocalDateTime.of(2026, 7, 1, 12, 0);
        Request first = pendingRequest(REQUEST_ID, base);
        Request second = pendingRequest(SECOND_ID, base.plusDays(1));
        Request third = pendingRequest(THIRD_ID, base.plusDays(2));
        when(requestRepo.findPendingFor(any(), any(Limit.class)))
                .thenReturn(List.of(first, second, third));
        when(logRepo.findTimelinesOf(any())).thenReturn(List.of(
                entry(first, null, initial, base),
                entry(first, initial, next, base.plusDays(30)),   // movida hace poco
                entry(second, null, initial, base.plusDays(1)),
                entry(second, initial, next, base.plusDays(15)),  // en el medio
                entry(third, null, initial, base.plusDays(2))));  // quieta desde que nació

        List<InboxEntryResponse> inbox = service.getInbox("EXTERNO", 50);

        assertThat(inbox).extracting(InboxEntryResponse::id)
                .containsExactly(THIRD_ID, SECOND_ID, REQUEST_ID);
        assertThat(inbox).extracting(InboxEntryResponse::waitingSince).isSorted();
    }

    // --- 007 FR-014: el motor no deja una solicitud detenida sin responsable posible ------
    // Un estado no final sin transiciones de salida es un callejón: la solicitud que entrara
    // no aparecería en ninguna bandeja y nadie sería responsable de ella. La base no lo
    // impide (V2.2.0) y el invariante de WorkflowGenericityIT solo mira lo sembrado al
    // correr: la guarda del motor es la que vale para una definición cargada en caliente.

    /** Definición con un callejón: INICIAL → LIMBO, y LIMBO no es final ni tiene salidas. */
    private final WorkflowState limbo =
            WorkflowState.builder().code("LIMBO").name("Limbo").build();
    private final WorkflowDefinition deadEndDefinition = WorkflowDefinition.builder()
            .code("TRAMITE_CALLEJON")
            .version(1)
            .name("Trámite con callejón")
            .states(List.of(initial, limbo, terminal))
            .transitions(List.of(
                    WorkflowTransition.builder()
                            .fromState(initial).toState(limbo)
                            .responsible("EXTERNO").requiresNote(false).build(),
                    WorkflowTransition.builder()
                            .fromState(initial).toState(terminal)
                            .responsible("EXTERNO").requiresNote(false).build()))
            .build();

    @Test
    @DisplayName("avanzar hacia un estado no final sin salidas: 500 de configuración, sin efectos (FR-014)")
    void advanceIntoADeadEndStateFailsClosed() {
        Request request = requestAt(initial, deadEndDefinition);

        assertThatExceptionOfType(IncompleteConfigurationException.class)
                .isThrownBy(() -> service.advance(
                        REQUEST_ID, new AdvanceRequestBody("LIMBO", null), EMAIL))
                .withMessageContaining("LIMBO")
                .withMessageContaining("TRAMITE_CALLEJON");

        // La guarda rechaza el destino, no la solicitud: sigue donde estaba, sin rastro
        assertThat(request.getCurrentState()).isSameAs(initial);
        verify(logRepo, never()).save(any());
    }

    @Test
    @DisplayName("registrar en una definición cuyo inicial no tiene salidas: 500 de configuración, nada se persiste (FR-014)")
    void registerIntoAnInitialStateWithoutExitsFailsClosed() {
        WorkflowDefinition lonely = WorkflowDefinition.builder()
                .code("TRAMITE_SIN_SALIDA")
                .version(1)
                .name("Trámite sin salida")
                .states(List.of(initial))
                .transitions(List.of())
                .build();
        when(definitionRepo.findTopByCodeOrderByVersionDesc("TRAMITE_SIN_SALIDA"))
                .thenReturn(Optional.of(lonely));

        assertThatExceptionOfType(IncompleteConfigurationException.class)
                .isThrownBy(() -> service.register(
                        new CreateRequestBody("TRAMITE_SIN_SALIDA", "Ana María Pérez", "DOC-PRUEBA-001"),
                        EMAIL))
                .withMessageContaining("INICIAL")
                .withMessageContaining("TRAMITE_SIN_SALIDA");

        verify(requestRepo, never()).save(any());
        verify(logRepo, never()).save(any());
    }

    @Test
    @DisplayName("bandeja: sin entrada de nacimiento, origin es null — anomalía de datos declarada en el contrato, no un tercer origen (review M3)")
    void inboxOriginIsNullWhenTheBirthEntryIsMissing() {
        // Un timeline con movimientos pero sin la entrada from NULL: no ocurre por register,
        // que la escribe siempre. Si ocurre, el contrato declara null; nadie lo miraba.
        LocalDateTime registered = LocalDateTime.of(2026, 7, 1, 12, 0);
        Request request = pendingRequest(REQUEST_ID, registered);
        when(requestRepo.findPendingFor(any(), any(Limit.class))).thenReturn(List.of(request));
        when(logRepo.findTimelinesOf(any())).thenReturn(List.of(
                entry(request, initial, next, registered.plusDays(1))));

        List<InboxEntryResponse> inbox = service.getInbox("EXTERNO", 50);

        assertThat(inbox).singleElement().extracting(InboxEntryResponse::origin).isNull();
    }

    /** Solicitud del trámite de prueba, pendiente en el estado inicial, con id y radicación fijos. */
    private Request pendingRequest(java.util.UUID id, LocalDateTime createdAt) {
        return Request.builder()
                .id(id)
                .definition(definition)
                .currentState(initial)
                .studentName("Ana María Pérez")
                .studentDocument("DOC-PRUEBA-001")
                .createdAt(createdAt)
                .build();
    }

    /** Entrada de timeline con instante fijo: lo que @PrePersist pondría en la base. */
    private RequestTransitionLog entry(Request request, WorkflowState from, WorkflowState to,
            LocalDateTime occurredAt) {
        return RequestTransitionLog.builder()
                .request(request)
                .fromState(from)
                .toState(to)
                .actor(actor)
                .occurredAt(occurredAt)
                .build();
    }

    /** Motor con las guardas dadas registradas; sin argumentos, ninguna. */
    private RequestServiceImpl serviceWith(IWorkflowGuard... guards) {
        return new RequestServiceImpl(
                definitionRepo, requestRepo, logRepo, userRepo, businessRules, parameterRepo,
                List.of(guards));
    }

    /** Solicitud del trámite de prueba parada en el estado dado, con stubs de I/O listos. */
    private Request requestAt(WorkflowState state) {
        return requestAt(state, definition);
    }

    /** Igual, para una definición distinta de la de siempre (US4). */
    private Request requestAt(WorkflowState state, WorkflowDefinition definition) {
        Request request = Request.builder()
                .definition(definition)
                .currentState(state)
                .studentName("Ana María Pérez")
                .studentDocument("DOC-PRUEBA-001")
                .build();
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(actor));
        when(requestRepo.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(RequestTransitionLog.class))).thenAnswer(inv -> inv.getArgument(0));
        return request;
    }

    private void stubHappyPath() {
        when(definitionRepo.findTopByCodeOrderByVersionDesc("TRAMITE_PRUEBA"))
                .thenReturn(Optional.of(definition));
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(actor));
        when(requestRepo.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(RequestTransitionLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}
