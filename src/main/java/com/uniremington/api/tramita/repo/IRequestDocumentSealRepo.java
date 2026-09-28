package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.RequestDocumentSeal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sellos de emisión (FR-001, FR-006, FR-008). Solo lectura y {@code save}: no existe
 * operación de edición ni borrado en la aplicación, y el trigger de la base lo garantiza
 * aunque alguien agregue una por error (V4.1.0, §VII).
 */
public interface IRequestDocumentSealRepo extends JpaRepository<RequestDocumentSeal, UUID> {

    /** La consulta del canal público: se entra por el código impreso en el papel (FR-006). */
    Optional<RequestDocumentSeal> findByVerificationCode(String verificationCode);

    /** El historial de emisiones de una solicitud, en orden, con desempate estable (FR-008). */
    List<RequestDocumentSeal> findByRequestIdOrderByIssuedAtAscIdAsc(UUID requestId);
}
