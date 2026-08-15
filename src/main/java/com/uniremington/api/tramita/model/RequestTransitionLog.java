package com.uniremington.api.tramita.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Entrada del timeline de auditoría de SP6 (data-model.md). Solo se agrega:
 * sin setters, se construye completa y se persiste una vez (FR-007); la
 * garantía fuerte vive en el trigger de BD (SC-002, research.md D8). fromState
 * NULL = entrada de registro, el nacimiento de la solicitud (research.md D7).
 * PK BIGSERIAL: identidad monótona como desempate del orden cronológico
 * (research.md D11).
 */
@Entity
@Table(name = "request_transition_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class RequestTransitionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", updatable = false)
    private Request request;

    /** NULL solo en la entrada de registro (research.md D7). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_state_id", updatable = false)
    private WorkflowState fromState;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_state_id", updatable = false)
    private WorkflowState toState;

    /** Quién registró la entrada — siempre un usuario autenticado de 001 (FR-005). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    /** Observación; obligatoria cuando la transición la exige (FR-014). */
    @Column(updatable = false)
    private String note;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    // UTC explícito, convención del chasis (001, JD3-012).
    @PrePersist
    void onCreate() {
        occurredAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
