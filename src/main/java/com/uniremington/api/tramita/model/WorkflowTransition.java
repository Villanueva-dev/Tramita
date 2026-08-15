package com.uniremington.api.tramita.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Paso permitido entre dos estados de una definición (data-model.md). Avances y
 * devoluciones son la misma cosa para el motor (FR-013): una devolución es una
 * transición hacia un estado anterior que la configuración marca con
 * requiresNote — el motivo obligatorio de FR-014 (research.md D4). Sin
 * guard_key: las guardas de reglas de negocio llegan en la feature 003 con SP2
 * (research.md D3).
 */
@Entity
@Table(name = "workflow_transition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class WorkflowTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id")
    private WorkflowDefinition definition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_state_id")
    private WorkflowState fromState;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_state_id")
    private WorkflowState toState;

    /**
     * Etiqueta de configuración del responsable del paso ('COORDINACION',
     * 'FACULTAD', …) — FR-001. El timeline la muestra junto al actor real para
     * dejar constancia del "en nombre de" (FR-006, research.md D5).
     */
    @Column(nullable = false)
    private String responsible;

    /** true = el motor exige observación al recorrerla (FR-014). */
    @Column(name = "requires_note", nullable = false)
    private boolean requiresNote;
}
