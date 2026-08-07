package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.AdvanceRequestBody;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.dto.RequestSummaryResponse;
import com.uniremington.api.tramita.dto.TimelineEntryResponse;
import com.uniremington.api.tramita.service.IRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** US3/FR-011: localización por cédula exacta o fragmento del nombre. */
    @GetMapping
    public List<RequestSummaryResponse> search(
            @RequestParam @NotBlank @Size(min = 2) String search) {
        return requestService.search(search);
    }

    /** US3: detalle con las transiciones disponibles desde el estado actual. */
    @GetMapping("/{id}")
    public RequestResponse getById(@PathVariable UUID id) {
        return requestService.getById(id);
    }

    /** US3/FR-008: el timeline completo, en orden cronológico. */
    @GetMapping("/{id}/timeline")
    public List<TimelineEntryResponse> getTimeline(@PathVariable UUID id) {
        return requestService.getTimeline(id);
    }
}
