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
 * Estado de una definición de trámite (data-model.md). El code es único por
 * definición — el motor compara estados por code, no por id: es estable entre
 * la BD y las definiciones construidas en memoria por los tests. Un estado
 * final cierra el trámite sea satisfactorio (FINALIZADA) o negativo (RECHAZADA):
 * FR-015 emerge de que la definición tenga o no ese estado, sin lógica especial.
 */
@Entity
@Table(name = "workflow_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class WorkflowState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id")
    private WorkflowDefinition definition;

    /** Único por definición ('REGISTRADA', 'EN_FACULTAD', …). */
    @Column(nullable = false)
    private String code;

    /** Nombre para mostrar ('En facultad'). */
    @Column(nullable = false)
    private String name;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    @Column(name = "is_final", nullable = false)
    private boolean finalState;
}
