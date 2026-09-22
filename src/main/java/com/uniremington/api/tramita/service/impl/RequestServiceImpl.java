package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.AdvanceRequestBody;
import com.uniremington.api.tramita.dto.AvailableTransitionResponse;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.InboxEntryResponse;
import com.uniremington.api.tramita.dto.PublicRequestBody;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.dto.RequestSummaryResponse;
import com.uniremington.api.tramita.dto.SubjectResponse;
import com.uniremington.api.tramita.dto.TimelineEntryResponse;
import com.uniremington.api.tramita.dto.WorkflowDefinitionResponse;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestSubject;
import com.uniremington.api.tramita.model.RequestTransitionLog;
import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowState;
import com.uniremington.api.tramita.model.WorkflowParameter;
import com.uniremington.api.tramita.model.WorkflowTransition;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import com.uniremington.api.tramita.repo.IUserRepo;
import com.uniremington.api.tramita.repo.IWorkflowParameterRepo;
import com.uniremington.api.tramita.repo.IWorkflowDefinitionRepo;
import com.uniremington.api.tramita.service.IRequestBusinessRules;
import com.uniremington.api.tramita.service.IRequestService;
import com.uniremington.api.tramita.service.IWorkflowGuard;
import com.uniremington.api.tramita.shared.exception.GuardRejectedException;
import com.uniremington.api.tramita.shared.exception.IllegalTransitionException;
import com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import com.uniremington.api.tramita.util.CampusTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
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

    /** Habilita el canal público de un trámite (research.md D1). */
    private static final String PUBLIC_CAPTURE_ENABLED = "PUBLIC_CAPTURE_ENABLED";

    /**
     * Responsable del tramo inicial de una solicitud pública (research.md D4). La
     * fila la siembra V3.3.0 y está inactiva: nombra al portal en el histórico sin
     * ser una cuenta.
     */
    private static final String PORTAL_ACTOR_EMAIL = "portal-publico@tramita.local";

    /**
     * Un único mensaje para «el trámite no existe» y «el trámite no admite captura
     * pública». La distinción no le sirve a quien envía legítimamente y sí a quien
     * sondea qué trámites existen (FR-002).
     */
    private static final String NO_PUBLIC_CHANNEL =
            "No hay captura pública disponible para ese trámite";

    private final IWorkflowDefinitionRepo definitionRepo;
    private final IRequestRepo requestRepo;
    private final IRequestTransitionLogRepo logRepo;
    private final IUserRepo userRepo;
    private final IRequestBusinessRules businessRules;
    private final IWorkflowParameterRepo parameterRepo;

    /**
     * Todas las guardas registradas como bean (research.md D5). Se recorre por
     * clave en vez de indexarse en un Map porque su tamaño es del orden de las
     * transiciones condicionadas del sistema —hoy cero— y un Map en el
     * constructor solo agregaría un modo de fallo al arranque.
     */
    private final List<IWorkflowGuard> guards;

    @Override
    @Transactional
    public RequestResponse register(CreateRequestBody body, String actorEmail) {
        // Tipo inexistente en la configuración → 422 con el motivo (US1, edge case)
        WorkflowDefinition definition = definitionRepo
                .findTopByCodeOrderByVersionDesc(body.definitionCode())
                .orElseThrow(() -> new UnprocessableRequestException(
                        "El tipo de trámite '%s' no existe en la configuración"
                                .formatted(body.definitionCode())));

        // Configuración rota = error del sistema (500), no del usuario. El
        // esquema garantiza como máximo un inicial por definición (índice
        // parcial de V2.2.0); acá se cubre el caso de CERO, que ningún
        // constraint puede exigir sin bloquear la carga incremental de una
        // definición. El mensaje nombra la definición: quien cargó el trámite
        // por SQL necesita saber cuál quedó a medias.
        List<WorkflowState> initialStates = definition.getStates().stream()
                .filter(WorkflowState::isInitial)
                .toList();
        if (initialStates.size() != 1) {
            throw new IllegalStateException(
                    "La definición %s v%d declara %d estados iniciales; debe declarar exactamente 1"
                            .formatted(definition.getCode(), definition.getVersion(),
                                    initialStates.size()));
        }
        WorkflowState initial = initialStates.getFirst();
        // Y el inicial tiene que tener salida (FR-014 de la 007): la configuración se
        // comprueba antes que el formulario, porque una definición rota no es culpa de
        // quien envía y no debe llegar a evaluarle nada.
        requireAnExitOrClosure(definition, initial);

        // Las reglas del trámite se aplican ANTES de persistir: una solicitud que
        // las incumple no debe existir ni siquiera un instante (US2).
        businessRules.validate(definition, body);

        Request request = requestRepo.save(Request.builder()
                .definition(definition)
                .currentState(initial)
                .studentName(body.studentName())
                .studentDocument(body.studentDocument())
                .studentCode(body.studentCode())
                .program(body.program())
                .semester(body.semester())
                .reason(body.reason())
                .studentEmail(body.studentEmail())
                .studentPhone(body.studentPhone())
                .campus(body.campus())
                .faculty(body.faculty())
                .modality(body.modality())
                .studentSignature(body.signature())
                .build());

        // Las asignaturas se persisten por cascada desde la solicitud. El lado
        // dueño de la relación es RequestSubject, así que hay que setearlo: sin
        // .request(request) la FK saldría nula y la inserción fallaría.
        request.getSubjects().addAll(body.subjects().stream()
                .map(subject -> RequestSubject.builder()
                        .request(request)
                        .code(subject.code())
                        .name(subject.name())
                        .credits(subject.credits())
                        .group(subject.group())
                        .currentGrade(subject.currentGrade())
                        .proposedGrade(subject.proposedGrade())
                        .build())
                .toList());

        // Entrada de nacimiento del timeline (research.md D7): from NULL
        logRepo.save(RequestTransitionLog.builder()
                .request(request)
                .fromState(null)
                .toState(initial)
                .actor(resolveActor(actorEmail))
                .build());

        return toResponse(request);
    }

    @Override
    @Transactional
    public void registerFromPublicChannel(String definitionCode, PublicRequestBody body) {
        // Se resuelve y se comprueba ANTES de mapear nada: un trámite sin canal
        // público no debe llegar siquiera a construir una solicitud.
        WorkflowDefinition definition = definitionRepo
                .findTopByCodeOrderByVersionDesc(definitionCode)
                .orElseThrow(() -> new ResourceNotFoundException(NO_PUBLIC_CHANNEL));

        if (!allowsPublicCapture(definition)) {
            throw new ResourceNotFoundException(NO_PUBLIC_CHANNEL);
        }

        // El código de la RUTA es el que viaja al motor: el cuerpo público no tiene
        // definitionCode, así que no hay nada que un envío manipulado pueda pisar
        // (FR-002a). Se delega en register() para no tener dos caminos de alta: las
        // reglas de negocio, el estado inicial y la entrada de nacimiento del
        // histórico son exactamente los mismos que por el canal autenticado.
        //
        // La llamada es interna, así que no pasa por el proxy de @Transactional; es
        // lo correcto acá: ambos métodos deben compartir UNA transacción, no abrir dos.
        register(toCreateBody(definitionCode, body), PORTAL_ACTOR_EMAIL);
    }

    /**
     * Misma semántica que {@code capturesCredits} de RequestBusinessRulesImpl, y por
     * la misma razón (research.md D1): el parámetro ausente significa «este trámite
     * no tiene canal público» —el caso por defecto, no una configuración incompleta—,
     * y un valor que no es true ni false es configuración ROTA, nunca un false
     * silencioso. Leerlo como negativo dejaría un canal declarado como público
     * rechazando todo y culpando de ello a quien envía.
     */
    private boolean allowsPublicCapture(WorkflowDefinition definition) {
        Optional<String> configured = parameterRepo
                .findByDefinitionIdAndKey(definition.getId(), PUBLIC_CAPTURE_ENABLED)
                .map(WorkflowParameter::getValue);
        if (configured.isEmpty()) {
            return false;
        }
        String value = configured.get().trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IncompleteConfigurationException(
                "El parámetro %s de la definición %s tiene un valor no interpretable"
                        .formatted(PUBLIC_CAPTURE_ENABLED, definition.getId()));
    }

    /**
     * El formato público como cuerpo del motor. Sin asignaturas: el DO-FR-100 no
     * tiene tabla de materias —la que el estudiante necesita viaja en prosa dentro
     * de los compromisos adquiridos—, de modo que las reglas de créditos no tienen
     * qué validar y no rechazan el envío.
     */
    private CreateRequestBody toCreateBody(String definitionCode, PublicRequestBody body) {
        return new CreateRequestBody(
                definitionCode,
                body.studentName(),
                body.studentDocument(),
                body.studentCode(),
                body.program(),
                body.semester(),
                body.reason(),
                List.of(),
                body.studentEmail(),
                body.studentPhone(),
                body.campus(),
                body.faculty(),
                body.modality(),
                body.signature());
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
        Request request = loadRequest(requestId);

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

        // El destino tiene que tener salida o ser un cierre (FR-014 de la 007). Va después
        // de resolver la transición —un destino inexistente sigue siendo 409— y antes de
        // la nota: la configuración rota es del operador, no de quien envía.
        requireAnExitOrClosure(request.getDefinition(), transition.getToState());

        // La obligatoriedad de la nota es dato de la definición (FR-014): el
        // motor solo la hace cumplir — la "devolución" es concepto de la config
        String note = body.note() == null || body.note().isBlank() ? null : body.note();
        if (transition.isRequiresNote() && note == null) {
            throw new UnprocessableRequestException(
                    "Esta transición exige una observación con el motivo");
        }

        // Antes de tocar el timeline: una entrada de una transición que no llegó a
        // ocurrir sería una mentira en el historial, y la trazabilidad es la tesis
        // del sistema (FR-016)
        evaluateGuard(transition, request);

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

    /**
     * Resuelve por nombre la guarda que la transición declara y la evalúa
     * (FR-016, research.md D5). El motor no sabe qué evalúa: solo que la
     * definición nombró una regla y que alguien registrada la atiende.
     *
     * Sin guard_key no hay nada que resolver y el paso se comporta como en la
     * feature 002 (FR-017) — el caso de todas las transiciones sembradas hoy.
     *
     * Una clave sin implementación NO se omite: bloquea (FR-019). Omitirla
     * ejecutaría una transición cuya condición nadie verificó, que es el fallo
     * abierto que esta feature vino a cerrar. Y es 500, no 422, por lo mismo que
     * un parámetro faltante: la petición está bien, la configuración no.
     */
    private void evaluateGuard(WorkflowTransition transition, Request request) {
        String guardKey = transition.getGuardKey();
        if (guardKey == null) {
            return;
        }

        IWorkflowGuard guard = guards.stream()
                .filter(candidate -> guardKey.equals(candidate.guardKey()))
                .findFirst()
                .orElseThrow(() -> new IncompleteConfigurationException(
                        "La transición %s → %s declara la guarda '%s', que no tiene implementación registrada"
                                .formatted(
                                        transition.getFromState().getCode(),
                                        transition.getToState().getCode(),
                                        guardKey)));

        if (!guard.isSatisfiedBy(request)) {
            throw new GuardRejectedException(
                    "La regla '%s' no se cumple para esta solicitud".formatted(guardKey));
        }
    }

    /**
     * FR-014 de la 007: una solicitud nunca queda detenida en un estado del que no se
     * puede salir. Un estado no final sin transiciones de salida es un callejón: la
     * solicitud que entrara no aparecería en la bandeja de nadie —la bandeja lee las
     * transiciones de salida, research.md D1— y nadie sería responsable de ella. Eso es
     * configuración rota, no un error de quien envía, así que se rechaza con el 500 de
     * configuración antes de persistir nada.
     *
     * Es la capa de RUNTIME de FR-014. La base no lo impide (V2.2.0 solo restringe los
     * iniciales y las transiciones a sí mismo) y el invariante de configuración de
     * WorkflowGenericityIT solo cubre lo sembrado cuando corre: para una definición
     * cargada por SQL en caliente —la vía que SC-005 promueve— esta guarda es la que vale.
     * El motor sigue sin conocer trámites: compara estados por código, como el resto.
     */
    private void requireAnExitOrClosure(WorkflowDefinition definition, WorkflowState state) {
        if (state.isFinalState()) {
            return;
        }
        boolean hasExit = definition.getTransitions().stream()
                .anyMatch(t -> t.getFromState().getCode().equals(state.getCode()));
        if (!hasExit) {
            throw new IncompleteConfigurationException(
                    "El estado %s de la definición %s v%d no es final y no tiene transiciones de salida: una solicitud quedaría detenida sin responsable posible"
                            .formatted(state.getCode(), definition.getCode(), definition.getVersion()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RequestResponse getById(UUID requestId) {
        return toResponse(loadRequest(requestId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestSummaryResponse> search(String query) {
        return requestRepo.search(query, escapeLikeWildcards(query)).stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * La bandeja (007, US1). El criterio vive en la consulta —quién espera a quién es
     * un hecho de la configuración, research.md D1— y el responsable llega por
     * parámetro: este método no conoce ningún rótulo de área (D2). La cota la trae
     * quien llama (D8).
     */
    @Override
    @Transactional(readOnly = true)
    public List<InboxEntryResponse> getInbox(String responsible, int limit) {
        List<Request> pending = requestRepo.findPendingFor(responsible, Limit.of(limit));
        Map<UUID, List<RequestTransitionLog>> timelines = timelinesOf(pending);
        // El repositorio corta por radicación (D8); acá manda la espera (D5): primero la
        // que más lleva detenida. El sort es estable, así que los empates conservan la
        // radicación.
        return pending.stream()
                .map(request -> toInboxEntry(request, responsible,
                        timelines.getOrDefault(request.getId(), List.of())))
                .sorted(Comparator.comparing(InboxEntryResponse::waitingSince))
                .toList();
    }

    /**
     * El timeline del lote entero, agrupado por solicitud: UNA consulta, no una por
     * fila. De él salen las dos cosas que la bandeja deriva por entrada: el origen
     * (FR-007) y desde cuándo espera (D3). Traer el lote y derivar en memoria es una
     * consulta menos que una agregada aparte para el máximo, y a este volumen las
     * filas de más no pesan (javadoc de {@code findTimelinesOf}).
     */
    private Map<UUID, List<RequestTransitionLog>> timelinesOf(List<Request> requests) {
        if (requests.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = requests.stream().map(Request::getId).toList();
        return logRepo.findTimelinesOf(ids).stream()
                .collect(Collectors.groupingBy(entry -> entry.getRequest().getId()));
    }

    /**
     * Desde cuándo espera (D3): el instante de su ÚLTIMA transición, no el de su
     * radicación. Una solicitud radicada hace dos meses y devuelta ayer lleva un día
     * esperando; medir desde {@code createdAt} la pondría primera y desplazaría a la que
     * de verdad lleva semanas detenida. La radicación es solo el respaldo para una
     * solicitud sin timeline, que en producción no existe: {@code register} escribe
     * la entrada de nacimiento siempre. Se convierte a la hora de la sede en el borde
     * de salida (D4/FR-006): lo persistido sigue en UTC.
     */
    private OffsetDateTime waitingSince(Request request, List<RequestTransitionLog> timeline) {
        LocalDateTime since = timeline.stream()
                .map(RequestTransitionLog::getOccurredAt)
                .max(Comparator.naturalOrder())
                .orElse(request.getCreatedAt());
        return CampusTime.toCampus(since);
    }

    /**
     * El origen sale del actor de la entrada de nacimiento (FR-007): el portal público
     * escribe con su propia cuenta desde la 004, así que no hay nada nuevo que
     * persistir. Sin entrada de nacimiento no hay origen que afirmar: null, no un
     * valor inventado.
     */
    private InboxEntryResponse.Origin originOf(List<RequestTransitionLog> timeline) {
        return timeline.stream()
                .filter(entry -> entry.getFromState() == null)
                .findFirst()
                .map(entry -> PORTAL_ACTOR_EMAIL.equals(entry.getActor().getEmail())
                        ? InboxEntryResponse.Origin.PUBLIC_LINK
                        : InboxEntryResponse.Origin.COORDINATION)
                .orElse(null);
    }

    /**
     * El timeline es un solo SELECT ordenado (research.md D7/D11). El "en nombre
     * de" (FR-006) se deriva por join contra la definición de nacimiento — es
     * estable porque las definiciones versionadas son inmutables (research.md D5).
     */
    @Override
    @Transactional(readOnly = true)
    public List<TimelineEntryResponse> getTimeline(UUID requestId) {
        Request request = loadRequest(requestId);
        List<WorkflowTransition> transitions = request.getDefinition().getTransitions();
        return logRepo.findByRequestIdOrderByOccurredAtAscIdAsc(request.getId()).stream()
                .map(entry -> toTimelineEntry(entry, transitions))
                .toList();
    }

    // --- helpers -------------------------------------------------------------------------

    /**
     * Neutraliza los comodines de LIKE para que el fragmento se busque literal.
     * El backslash va primero: escaparlo después duplicaría los que la propia
     * función acaba de introducir.
     */
    private String escapeLikeWildcards(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Request loadRequest(UUID requestId) {
        return requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La solicitud %s no existe".formatted(requestId)));
    }

    private RequestSummaryResponse toSummary(Request request) {
        WorkflowDefinition definition = request.getDefinition();
        return new RequestSummaryResponse(
                request.getId(),
                new WorkflowDefinitionResponse(
                        definition.getCode(), definition.getName(), definition.getVersion()),
                request.getStudentName(),
                request.getStudentDocument(),
                StateResponseMapper.toResponse(request.getCurrentState()),
                request.getCreatedAt());
    }

    /**
     * Mapeo propio y no una variante de {@link #toSummary}: que este método NO tenga
     * una línea para el documento de identidad es la garantía de FR-014, y conviene
     * que se vea al leerlo.
     */
    private InboxEntryResponse toInboxEntry(Request request, String pendingResponsible,
            List<RequestTransitionLog> timeline) {
        WorkflowDefinition definition = request.getDefinition();
        return new InboxEntryResponse(
                request.getId(),
                new WorkflowDefinitionResponse(
                        definition.getCode(), definition.getName(), definition.getVersion()),
                request.getStudentName(),
                StateResponseMapper.toResponse(request.getCurrentState()),
                request.getCreatedAt(),
                waitingSince(request, timeline),
                pendingResponsible,
                originOf(timeline));
    }

    private TimelineEntryResponse toTimelineEntry(
            RequestTransitionLog entry, List<WorkflowTransition> transitions) {
        // Nacimiento (from NULL): no hay paso de la definición que lo respalde
        String responsible = entry.getFromState() == null ? null
                : transitions.stream()
                        .filter(t -> t.getFromState().getCode().equals(entry.getFromState().getCode())
                                && t.getToState().getCode().equals(entry.getToState().getCode()))
                        .map(WorkflowTransition::getResponsible)
                        .findFirst()
                        .orElse(null);
        return new TimelineEntryResponse(
                entry.getId(),
                entry.getFromState() == null ? null : StateResponseMapper.toResponse(entry.getFromState()),
                StateResponseMapper.toResponse(entry.getToState()),
                entry.getActor().getEmail(),
                responsible,
                entry.getNote(),
                entry.getOccurredAt());
    }

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
                        StateResponseMapper.toResponse(t.getToState()), t.getResponsible(), t.isRequiresNote()))
                .toList();
        return new RequestResponse(
                request.getId(),
                new WorkflowDefinitionResponse(
                        definition.getCode(), definition.getName(), definition.getVersion()),
                request.getStudentName(),
                request.getStudentDocument(),
                request.getStudentCode(),
                request.getProgram(),
                request.getSemester(),
                request.getReason(),
                toSubjectResponses(request),
                StateResponseMapper.toResponse(current),
                available,
                request.getCreatedAt());
    }

    private List<SubjectResponse> toSubjectResponses(Request request) {
        return request.getSubjects().stream()
                .map(subject -> new SubjectResponse(
                        subject.getCode(), subject.getName(), subject.getCredits(),
                        subject.getGroup(), subject.getCurrentGrade(), subject.getProposedGrade()))
                .toList();
    }
}
