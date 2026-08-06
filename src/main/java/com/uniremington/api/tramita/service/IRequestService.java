package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.RequestResponse;

public interface IRequestService {

    /**
     * Registra una solicitud (US1, FR-002): nace en el estado inicial de la
     * definición VIGENTE de su trámite y escribe la primera entrada del timeline
     * (research.md D7). El actor es el usuario autenticado de la sesión (FR-012).
     */
    RequestResponse register(CreateRequestBody body, String actorEmail);
}
