package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.AdvanceRequestBody;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.InboxEntryResponse;
import com.uniremington.api.tramita.dto.PublicRequestBody;
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
     * Registra una solicitud llegada por el canal público, sin sesión (004, US1).
     *
     * El trámite lo fija el código de la RUTA y nunca el cuerpo (FR-002a), y solo
     * responde para trámites que declaran {@code PUBLIC_CAPTURE_ENABLED}: para
     * cualquier otro es un recurso inexistente, sin distinguir «no existe» de «no
     * admite captura pública» (FR-002, research.md D1).
     *
     * No devuelve nada. El recibo del canal público es deliberadamente pobre —sin
     * identificador ni estado (FR-008, D5)—, de modo que no hay qué devolver desde
     * el motor: lo que ve el estudiante es una confirmación, no un recurso.
     */
    void registerFromPublicChannel(String definitionCode, PublicRequestBody body);

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
     * Las solicitudes registradas más recientemente, sin criterio de búsqueda (004,
     * US2, FR-012/FR-013). Existe porque no se puede buscar a alguien de cuya
     * solicitud nadie se enteró: con la captura pública, una solicitud puede llegar
     * sin que nadie de la Coordinación sepa que existe.
     *
     * Devuelve {@link InboxEntryResponse} y no el resumen de la 002 porque esta
     * vista lista SIN filtro y no puede exponer documentos de identidad (FR-014).
     */
    List<InboxEntryResponse> getInbox();

    /**
     * Timeline completo en orden cronológico (US3, FR-008): cada entrada con su
     * autor real y el responsable del paso según la definición (FR-006).
     */
    List<TimelineEntryResponse> getTimeline(UUID requestId);
}
