package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.RequestDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IRequestDocumentRepo extends JpaRepository<RequestDocument, UUID> {

    List<RequestDocument> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    Optional<RequestDocument> findByIdAndRequestId(UUID id, UUID requestId);
}