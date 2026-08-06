package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.AdvanceRequestBody;
import com.uniremington.api.tramita.dto.AvailableTransitionResponse;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.dto.StateResponse;
import com.uniremington.api.tramita.dto.WorkflowDefinitionResponse;
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
import com.uniremington.api.tramita.service.IRequestService;
import com.uniremington.api.tramita.shared.exception.IllegalTransitionException;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El motor de workflow (research.md, data-model.md). No conoce ningún trámite
 * concreto: valida y mueve solicitudes contra la definición con la que nacieron
 * (FR-009). Los estados se comparan por code — único por definición y estable
 * entre BD y definiciones en memoria de los tests.
 */
@Service
@RequiredArgsConstructor
public class RequestServiceImpl implements IRequestService {

    private final IWorkflowDefinitionRepo definitionRepo;
    private final IRequestRepo requestRepo;
    private final IRequestTransitionLogRepo logRepo;
    private final IUserRepo userRepo;

    @Override
    @Transactional
    public RequestResponse register(CreateRequestBody body, String actorEmail) {
        // Tipo inexistente en la configuración → 422 con el motivo (US1, edge case)
        WorkflowDefinition definition = definitionRepo
                .findTopByCodeOrderByVersionDesc(body.definitionCode())
                .orElseThrow(() -> new UnprocessableRequestException(
                        "El tipo de trámite '%s' no existe en la configuración"
                                .formatted(body.definitionCode())));

        WorkflowState initial = definition.getStates().stream()
                .filter(WorkflowState::isInitial)
                .findFirst()
                // Configuración rota = error del sistema (500), no del usuario
                .orElseThrow(() -> new IllegalStateException(
                        "La definición %s v%d no declara estado inicial"
                                .formatted(definition.getCode(), definition.getVersion())));

        Request request = requestRepo.save(Request.builder()
                .definition(definition)
                .currentState(initial)
                .studentName(body.studentName())
                .studentDocument(body.studentDocument())
                .build());

        // Entrada de nacimiento del timeline (research.md D7): from NULL
        logRepo.save(RequestTransitionLog.builder()
                .request(request)
                .fromState(null)
                .toState(initial)
                .actor(resolveActor(actorEmail))
                .build());

        return toResponse(request);
    }

    /**
     * El corazón del motor (data-model.md): valida contra la definición de la
     * solicitud, exige la nota si la transición la declara, registra en el
     * timeline y mueve el estado. Sin literales de negocio: no sabe qué trámite
     * corre (US4). El conflicto de dos avances simultáneos lo resuelve el
     * {@code @Version} de Request al hacer commit (research.md D6).
     */
    @Override
    @Transactional
    public RequestResponse advance(UUID requestId, AdvanceRequestBody body, String actorEmail) {
        Request request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La solicitud %s no existe".formatted(requestId)));

        // La definición que rige es la de nacimiento (FR-009), y de un estado
        // final no hay salida: el trámite está cerrado (US2-4)
        WorkflowState current = request.getCurrentState();
        if (current.isFinalState()) {
            throw new IllegalTransitionException(
                    "El trámite está cerrado en '%s' y no admite más transiciones"
                            .formatted(current.getName()));
        }

        WorkflowTransition transition = request.getDefinition().getTransitions().stream()
                .filter(t -> t.getFromState().getCode().equals(current.getCode())
                        && t.getToState().getCode().equals(body.targetStateCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalTransitionException(
                        "La transición %s → %s no está definida para este trámite"
                                .formatted(current.getCode(), body.targetStateCode())));

        // La obligatoriedad de la nota es dato de la definición (FR-014): el
        // motor solo la hace cumplir — la "devolución" es concepto de la config
        String note = body.note() == null || body.note().isBlank() ? null : body.note();
        if (transition.isRequiresNote() && note == null) {
            throw new UnprocessableRequestException(
                    "Esta transición exige una observación con el motivo");
        }

        logRepo.save(RequestTransitionLog.builder()
                .request(request)
                .fromState(current)
                .toState(transition.getToState())
                .actor(resolveActor(actorEmail))
                .note(note)
                .build());
        request.moveTo(transition.getToState());

        return toResponse(requestRepo.save(request));
    }

    // --- helpers -------------------------------------------------------------------------

    private User resolveActor(String actorEmail) {
        // Con sesión válida el usuario existe; si no, es un estado imposible (500 honesto)
        return userRepo.findByEmail(actorEmail).orElseThrow(
                () -> new IllegalStateException("La sesión referencia un usuario inexistente"));
    }

    /** Mapeo a mano (convención de 001): la entity nunca cruza la frontera de la API. */
    private RequestResponse toResponse(Request request) {
        WorkflowDefinition definition = request.getDefinition();
        WorkflowState current = request.getCurrentState();
        // De un estado final no sale ninguna transición: lista vacía = trámite cerrado
        var available = definition.getTransitions().stream()
                .filter(t -> t.getFromState().getCode().equals(current.getCode()))
                .map(t -> new AvailableTransitionResponse(
                        toStateResponse(t.getToState()), t.getResponsible(), t.isRequiresNote()))
                .toList();
        return new RequestResponse(
                request.getId(),
                new WorkflowDefinitionResponse(
                        definition.getCode(), definition.getName(), definition.getVersion()),
                request.getStudentName(),
                request.getStudentDocument(),
                toStateResponse(current),
                available,
                request.getCreatedAt());
    }

    private StateResponse toStateResponse(WorkflowState state) {
        return new StateResponse(state.getCode(), state.getName(), state.isFinalState());
    }
}
