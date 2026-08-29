package com.uniremington.api.tramita.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Evidencia append-only de una aprobación externa registrada sobre un adjunto. */
@Entity
@Table(name = "request_document_approval")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class RequestDocumentApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private RequestDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Column(name = "signer_name", nullable = false, length = 120, updatable = false)
    private String signerName;

    @Column(name = "signer_role", nullable = false, length = 80, updatable = false)
    private String signerRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_type", nullable = false, length = 20, updatable = false)
    private SignatureType signatureType;

    @Column(name = "document_sha256", nullable = false, length = 64, updatable = false)
    private String documentSha256;

    @Column(updatable = false, length = 1000)
    private String note;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private LocalDateTime signedAt;

    @Column(name = "timestamped_at", nullable = false, updatable = false)
    private LocalDateTime timestampedAt;

    @PrePersist
    void onCreate() {
        timestampedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}