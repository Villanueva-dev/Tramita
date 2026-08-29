package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.RequestDocumentApproval;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IRequestDocumentApprovalRepo extends JpaRepository<RequestDocumentApproval, Long> {

    List<RequestDocumentApproval> findByDocumentIdAndDocumentRequestIdOrderByTimestampedAtAscIdAsc(
            UUID documentId, UUID requestId);
}