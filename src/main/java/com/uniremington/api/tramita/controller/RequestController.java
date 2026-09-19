package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.AdvanceRequestBody;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.InboxEntryResponse;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.dto.RequestSummaryResponse;
import com.uniremington.api.tramita.dto.SealEntryResponse;
import com.uniremington.api.tramita.dto.TimelineEntryResponse;
import com.uniremington.api.tramita.service.IDocumentSealService;
import com.uniremington.api.tramita.service.IDocumentService;
import com.uniremington.api.tramita.service.IRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final IDocumentService documentService;
    private final IDocumentSealService sealService;

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

    /**
     * US2 de la 004/FR-012: las solicitudes recientes, sin criterio de búsqueda.
     *
     * DECLARADO ANTES de {@code @GetMapping("/{id}")} a propósito. Spring resuelve
     * por especificidad del patrón —un segmento literal gana sobre una variable—, de
     * modo que el orden del archivo no es lo que lo hace funcionar; pero la colisión
     * es real y ya se midió: antes de que este método existiera,
     * {@code GET /api/requests/inbox} entraba por {@code /{id}}, fallaba al convertir
     * «inbox» a UUID y devolvía 400. Dejarlo contiguo es lo que hace evidente al
     * siguiente lector que estas dos rutas compiten.
     *
     * Devuelve InboxEntryResponse, SIN documento de identidad (FR-014).
     */
    @GetMapping("/inbox")
    public List<InboxEntryResponse> getInbox() {
        return requestService.getInbox();
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

    /**
     * US3 de la 006/FR-008: el historial de EMISIONES del documento, de la más antigua a la
     * más reciente — no el recorrido del trámite, que ya existe en {@link #getTimeline}.
     *
     * Una solicitud sin emisiones devuelve lista vacía, no 404: existe, simplemente nadie
     * pidió el documento todavía. Solo si la solicitud misma no existe la respuesta es 404,
     * y ese criterio lo resuelve {@code DocumentSealServiceImpl#history}.
     */
    @GetMapping("/{id}/seals")
    public List<SealEntryResponse> getSeals(@PathVariable UUID id) {
        return sealService.history(id);
    }

    /**
     * SP3: el formato oficial del trámite, diligenciado con los datos de la solicitud.
     *
     * SE GENERA BAJO DEMANDA Y NO SE GUARDA. El DO-FR-100 es el documento que circula PARA
     * ser firmado, así que se emite en cualquier momento de la vida de la solicitud: si
     * solo saliera al cerrar el trámite, no serviría para aquello por lo que existe. Que
     * una solicitud en revisión pueda imprimir su formato no la vuelve aprobada, y el
     * propio documento lo muestra: el bloque «Firma de la Facultad» va vacío.
     *
     * Congelar el documento y sellarlo es SP4 (issue #11), no esto.
     *
     * NO LO PUEDE PEDIR EL ESTUDIANTE, por dos vías independientes: la ruta exige sesión
     * —`anyRequest().authenticated()` en SecurityConfig— y además el recibo del canal
     * público no devuelve identificador (FR-008), así que quien envía el formato no tiene
     * con qué construir esta URL.
     */
    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> getDocument(
            @PathVariable UUID id, Authentication authentication) {
        byte[] document = documentService.generateFor(id, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // El nombre lleva el id de la solicitud y NUNCA la cédula ni el nombre del
                // estudiante: el archivo se descarga, se reenvía y queda en carpetas
                // compartidas, y el nombre viaja con él (§III, minimización).
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"DO-FR-100-%s.pdf\"".formatted(id))
                .body(document);
    }
}
