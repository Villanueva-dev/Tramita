package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.AdvanceRequestBody;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.DocumentApprovalRequest;
import com.uniremington.api.tramita.dto.DocumentApprovalResponse;
import com.uniremington.api.tramita.dto.DocumentResponse;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.dto.RequestSummaryResponse;
import com.uniremington.api.tramita.dto.TimelineEntryResponse;
import com.uniremington.api.tramita.service.IRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final com.uniremington.api.tramita.service.impl.PdfDocumentService pdfDocumentService;
    private final com.uniremington.api.tramita.service.impl.RequestDocumentService documentService;

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
            @RequestParam(required = false) @Size(min = 2) String search) {
        return search == null || search.isBlank()
                ? requestService.findAll()
                : requestService.search(search);
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

    /** Descarga la constancia PDF real solo para solicitudes finalizadas. */
    @GetMapping(value = "/{id}/document", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> document(@PathVariable UUID id) {
        byte[] pdf = pdfDocumentService.generate(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=constancia_%s.pdf".formatted(id))
                .body(pdf);
    }

    /** Carga un PDF y conserva sus metadatos y hash para auditoría documental. */
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse uploadDocument(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return documentService.upload(id, file);
    }

    /** Lista los adjuntos sin exponer la ruta física de almacenamiento. */
    @GetMapping("/{id}/documents")
    public List<DocumentResponse> listDocuments(@PathVariable UUID id) {
        return documentService.list(id);
    }

    /** Descarga un adjunto perteneciente a la solicitud indicada. */
    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        Resource resource = documentService.download(id, documentId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(resource);
    }

    /** Registra una firma externa sobre un adjunto con sello UTC y hash aprobado. */
    @PostMapping("/{id}/documents/{documentId}/approvals")
    public DocumentApprovalResponse registerDocumentApproval(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentApprovalRequest body,
            Authentication authentication) {
        return documentService.registerApproval(id, documentId, body, authentication.getName());
    }

    /** Lista la traza append-only de aprobaciones sobre el documento adjunto. */
    @GetMapping("/{id}/documents/{documentId}/approvals")
    public List<DocumentApprovalResponse> listDocumentApprovals(@PathVariable UUID id, @PathVariable UUID documentId) {
        return documentService.listApprovals(id, documentId);
    }
}
