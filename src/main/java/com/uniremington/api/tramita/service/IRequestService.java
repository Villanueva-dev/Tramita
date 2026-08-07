package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.AdvanceRequestBody;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.dto.RequestSummaryResponse;
import com.uniremington.api.tramita.dto.TimelineEntryResponse;
import java.util.List;
import java.util.UUID;

public interface IRequestService {

    /**
     * Registra una solicitud (US1, FR-002): nace en el estado inicial de la
     * definición VIGENTE de su trámite y escribe la primera entrada del timeline
     * (research.md D7). El actor es el usuario autenticado de la sesión (FR-012).
     */
    RequestResponse register(CreateRequestBody body, String actorEmail);

    /**
     * Avanza (o devuelve) la solicitud por una transición de SU definición (US2,
     * FR-003/FR-004): solo las transiciones definidas desde el estado actual son
     * legales; la nota es obligatoria cuando la transición la exige (FR-014).
     * Toda transición efectuada queda en el timeline con autor y fecha (FR-005).
     */
    RequestResponse advance(UUID requestId, AdvanceRequestBody body, String actorEmail);

    /** Detalle de una solicitud con sus transiciones disponibles (US3). */
    RequestResponse getById(UUID requestId);

    /**
     * Localiza solicitudes por cédula (igualdad exacta) o fragmento del nombre
     * (case-insensitive) — los dos datos con los que la Coordinación identifica
     * un trámite al recibir la consulta (US3, FR-011).
     */
    List<RequestSummaryResponse> search(String query);

    /**
     * Timeline completo en orden cronológico (US3, FR-008): cada entrada con su
     * autor real y el responsable del paso según la definición (FR-006).
     */
    List<TimelineEntryResponse> getTimeline(UUID requestId);
}
