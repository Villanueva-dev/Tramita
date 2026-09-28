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
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * El anexo que un trámite exige a un programa concreto, en la versión con la
 * que nació la solicitud (FR-007, FR-008, FR-009).
 *
 * La FK a {@link WorkflowDefinition} apunta a la VERSIÓN concreta, con el
 * mismo criterio de {@link WorkflowParameter} (research.md D2): una solicitud
 * se rige por la versión con la que nació. Las dos claves son
 * {@code updatable = false} porque una regla no se «mueve» de programa: se
 * borra y se crea otra. La FK a {@link AcademicProgram} es por {@code id},
 * así que renombrar un programa arrastra su regla; el {@code ON DELETE
 * RESTRICT} de la tabla garantiza en la base, no por disciplina del código,
 * que la regla nunca quede huérfana (FR-008).
 */
@Entity
@Table(name = "workflow_annex_rule", uniqueConstraints = @UniqueConstraint(
        name = "uq_workflow_annex_rule_definition_program",
        columnNames = {"definition_id", "program_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class WorkflowAnnexRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false, updatable = false)
    private WorkflowDefinition definition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private AcademicProgram program;

    /** Sin updatable = false: corregir el texto del documento es el caso de uso. */
    @Column(name = "document_name", nullable = false, length = 120)
    private String documentName;

    /** Sin updatable = false: corregir la indicación de dónde obtenerlo es el caso de uso. */
    @Column(name = "source_hint", nullable = false, length = 255)
    private String sourceHint;
}
