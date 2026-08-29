package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.DocumentApprovalRequest;
import com.uniremington.api.tramita.dto.DocumentApprovalResponse;
import com.uniremington.api.tramita.dto.DocumentResponse;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestDocument;
import com.uniremington.api.tramita.model.RequestDocumentApproval;
import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.repo.IRequestDocumentApprovalRepo;
import com.uniremington.api.tramita.repo.IRequestDocumentRepo;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IUserRepo;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Almacena adjuntos fuera de la BD y conserva sus metadatos y hash. */
@Service
@RequiredArgsConstructor
public class RequestDocumentService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private final IRequestRepo requestRepo;
    private final IRequestDocumentRepo documentRepo;
    private final IRequestDocumentApprovalRepo approvalRepo;
    private final IUserRepo userRepo;

    @Value("${app.documents.storage-path:./data/documents}")
    private String storagePath;

    @Transactional
    public DocumentResponse upload(UUID requestId, MultipartFile file) {
        Request request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("La solicitud %s no existe".formatted(requestId)));
        validate(file);
        // Nombre físico independiente del id de la entidad: un id asignado a mano en
        // un @GeneratedValue hace que Spring Data haga merge() en vez de persist().
        String storedName = UUID.randomUUID() + ".pdf";
        Path target = Path.of(storagePath).toAbsolutePath().normalize().resolve(storedName);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            RequestDocument document = documentRepo.save(RequestDocument.builder()
                    .request(request).originalName(safeName(file.getOriginalFilename()))
                    .storedName(storedName).contentType("application/pdf").size(file.getSize())
                    .sha256(hash(target)).build());
            return toResponse(document);
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible almacenar el documento", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID requestId) {
        ensureRequest(requestId);
        return documentRepo.findByRequestIdOrderByCreatedAtAsc(requestId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Resource download(UUID requestId, UUID documentId) {
        RequestDocument document = documentRepo.findByIdAndRequestId(documentId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("El documento no existe"));
        Path path = Path.of(storagePath).toAbsolutePath().normalize().resolve(document.getStoredName());
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) throw new ResourceNotFoundException("El archivo no existe en almacenamiento");
            return resource;
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible leer el documento", exception);
        }
    }

    @Transactional
    public DocumentApprovalResponse registerApproval(
            UUID requestId, UUID documentId, DocumentApprovalRequest body, String actorEmail) {
        RequestDocument document = loadDocument(requestId, documentId);
        validateApproval(body);
        RequestDocumentApproval approval = approvalRepo.save(RequestDocumentApproval.builder()
                .document(document)
                .actor(resolveActor(actorEmail))
                .signerName(body.signerName().trim())
                .signerRole(body.signerRole().trim())
                .signatureType(body.signatureType())
                // El hash se copia al aprobar para congelar la evidencia del PDF exacto.
                .documentSha256(document.getSha256())
                .note(normalizeOptional(body.note()))
                .signedAt(body.signedAt())
                .build());
        return toApprovalResponse(approval);
    }

    @Transactional(readOnly = true)
    public List<DocumentApprovalResponse> listApprovals(UUID requestId, UUID documentId) {
        loadDocument(requestId, documentId);
        return approvalRepo.findByDocumentIdAndDocumentRequestIdOrderByTimestampedAtAscIdAsc(documentId, requestId)
                .stream()
                .map(this::toApprovalResponse)
                .toList();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new UnprocessableRequestException("El archivo no puede estar vacío");
        if (file.getSize() > MAX_FILE_SIZE) throw new UnprocessableRequestException("El archivo no puede superar 5 MB");
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new UnprocessableRequestException("Solo se permiten archivos PDF");
        }
    }

    private void validateApproval(DocumentApprovalRequest body) {
        if (body.signedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new UnprocessableRequestException("La fecha de firma no puede estar en el futuro");
        }
    }

    private Request ensureRequest(UUID requestId) {
        return requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("La solicitud %s no existe".formatted(requestId)));
    }

    private RequestDocument loadDocument(UUID requestId, UUID documentId) {
        ensureRequest(requestId);
        return documentRepo.findByIdAndRequestId(documentId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("El documento no existe"));
    }

    private String safeName(String originalName) {
        String name = originalName == null ? "documento.pdf" : Path.of(originalName).getFileName().toString();
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private User resolveActor(String actorEmail) {
        return userRepo.findByEmail(actorEmail).orElseThrow(
                () -> new IllegalStateException("La sesión referencia un usuario inexistente"));
    }

    private String hash(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    private DocumentResponse toResponse(RequestDocument document) {
        return new DocumentResponse(document.getId(), document.getOriginalName(), document.getContentType(),
                document.getSize(), document.getSha256(), document.getCreatedAt());
    }

    private DocumentApprovalResponse toApprovalResponse(RequestDocumentApproval approval) {
        return new DocumentApprovalResponse(
                approval.getId(),
                approval.getSignerName(),
                approval.getSignerRole(),
                approval.getSignatureType(),
                approval.getDocumentSha256(),
                approval.getActor().getEmail(),
                approval.getNote(),
                approval.getSignedAt(),
                approval.getTimestampedAt());
    }
}