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
 * Paso permitido entre dos estados de una definición (data-model.md). Avances y devoluciones
 * son transiciones para el motor (FR-013). La definición marca explícitamente los retornos
 * con {@code returnForCorrection}; {@code requiresNote} es una propiedad independiente que
 * exige el motivo de FR-014 (research.md D4). Desde la feature 003 una transición también
 * puede condicionarse a una regla de negocio nombrada (guardKey, FR-015).
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

    /** True cuando esta transición devuelve el trámite a corrección (SP2, FR-013). */
    @Column(name = "return_for_correction", nullable = false)
    private boolean returnForCorrection;

    /**
     * Nombre de la regla de negocio que condiciona el paso (FR-015). Solo el
     * nombre: el motor lo resuelve contra las implementaciones de IWorkflowGuard
     * registradas y nunca conoce el trámite (FR-018, research.md D5).
     *
     * NULL = sin guarda, comportamiento idéntico al de la feature 002 (FR-017).
     * Es el caso de todas las transiciones sembradas por V2.1.0.
     */
    @Column(name = "guard_key")
    private String guardKey;
}
