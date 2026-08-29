package com.uniremington.api.tramita.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Fuente documental versionada; solo las oficiales validadas sirven para respuestas normativas. */
@Entity
@Table(name = "knowledge_source")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class KnowledgeSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_id", nullable = false, length = 120, updatable = false)
    private String sourceId;

    @Column(name = "source_class", nullable = false, length = 40, updatable = false)
    private String sourceClass;

    @Column(nullable = false, length = 255, updatable = false)
    private String title;

    @Column(name = "document_type", nullable = false, length = 80, updatable = false)
    private String documentType;

    @Column(name = "version_label", nullable = false, length = 80, updatable = false)
    private String versionLabel;

    @Column(length = 255, updatable = false)
    private String issuer;

    @Column(name = "validated_at", updatable = false)
    private LocalDateTime validatedAt;

    @Column(nullable = false, length = 20, updatable = false)
    private String status;

    @Column(name = "content_sha256", length = 64, updatable = false)
    private String contentSha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "retired_at", updatable = false)
    private LocalDateTime retiredAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
