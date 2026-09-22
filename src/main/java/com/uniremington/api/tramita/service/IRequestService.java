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
     * La bandeja de trabajo (007, US1, FR-001): las solicitudes que esperan la acción
     * del responsable pedido, según lo que la configuración de cada trámite declara
     * para el siguiente movimiento (research.md D1). El responsable viaja como
     * parámetro y nunca como literal (D2). NO es control de acceso (FR-003a): filtra,
     * no impide.
     *
     * Enmienda no aditiva de la 004 (D7): antes listaba «las más recientes, sin
     * criterio». Sigue existiendo por la misma razón de entonces —con la captura
     * pública, una solicitud puede llegar sin que nadie de la Coordinación sepa que
     * existe— y sigue devolviendo {@link InboxEntryResponse}, sin documento de
     * identidad (§III).
     *
     * @param limit cota explícita del resultado; la decide quien llama (D8).
     */
    List<InboxEntryResponse> getInbox(String responsible, int limit);

    /**
     * Timeline completo en orden cronológico (US3, FR-008): cada entrada con su
     * autor real y el responsable del paso según la definición (FR-006).
     */
    List<TimelineEntryResponse> getTimeline(UUID requestId);
}
