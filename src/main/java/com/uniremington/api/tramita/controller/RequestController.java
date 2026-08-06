package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.AdvanceRequestBody;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.service.IRequestService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ciclo de vida de solicitudes (contracts/openapi.yaml). El actor de cada
 * operación es el usuario de la sesión (FR-012): el controller lo toma del
 * Authentication y lo baja al servicio — la capa web conoce la sesión, el motor
 * no.
 */
@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final IRequestService requestService;

    /** US1: 201 + Location del recurso creado (semántica REST de creación). */
    @PostMapping
    public ResponseEntity<RequestResponse> register(
            @Valid @RequestBody CreateRequestBody body, Authentication authentication) {
        RequestResponse response = requestService.register(body, authentication.getName());
        return ResponseEntity
                .created(URI.create("/api/requests/" + response.id()))
                .body(response);
    }

    /**
     * US2/US5: aplica una transición definida (avance o devolución — el motor no
     * distingue, FR-013) y devuelve la solicitud actualizada.
     */
    @PostMapping("/{id}/transitions")
    public RequestResponse advance(
            @PathVariable UUID id,
            @Valid @RequestBody AdvanceRequestBody body,
            Authentication authentication) {
        return requestService.advance(id, body, authentication.getName());
    }
}
