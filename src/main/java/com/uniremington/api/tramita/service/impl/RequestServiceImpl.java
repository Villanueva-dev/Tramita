package com.uniremington.api.tramita.service.impl;

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
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import com.uniremington.api.tramita.repo.IUserRepo;
import com.uniremington.api.tramita.repo.IWorkflowDefinitionRepo;
import com.uniremington.api.tramita.service.IRequestService;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
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
