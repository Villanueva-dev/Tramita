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
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestTransitionLog;
import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowState;
import com.uniremington.api.tramita.model.WorkflowTransition;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import com.uniremington.api.tramita.repo.IUserRepo;
import com.uniremington.api.tramita.repo.IWorkflowDefinitionRepo;
import com.uniremington.api.tramita.shared.exception.IllegalTransitionException;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    private final RequestServiceImpl service =
            new RequestServiceImpl(definitionRepo, requestRepo, logRepo, userRepo);

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
                new CreateRequestBody("TRAMITE_PRUEBA", "Ana María Pérez", "1144099888"), EMAIL);

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

    // --- helpers -------------------------------------------------------------------------

    private static final java.util.UUID REQUEST_ID =
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Solicitud del trámite de prueba parada en el estado dado, con stubs de I/O listos. */
    private Request requestAt(WorkflowState state) {
        Request request = Request.builder()
                .definition(definition)
                .currentState(state)
                .studentName("Ana María Pérez")
                .studentDocument("1144099888")
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
