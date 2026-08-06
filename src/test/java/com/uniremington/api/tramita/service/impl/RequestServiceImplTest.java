package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
     * Definición mínima en memoria: INICIAL → SIGUIENTE (responsable EXTERNO) y
     * SIGUIENTE → FINAL. Códigos genéricos a propósito: el motor no conoce
     * trámites (US4) y este test tampoco debería.
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
                            .responsible("COORDINACION").requiresNote(false).build()))
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

    // --- helpers -------------------------------------------------------------------------

    private void stubHappyPath() {
        when(definitionRepo.findTopByCodeOrderByVersionDesc("TRAMITE_PRUEBA"))
                .thenReturn(Optional.of(definition));
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(actor));
        when(requestRepo.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepo.save(any(RequestTransitionLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}
