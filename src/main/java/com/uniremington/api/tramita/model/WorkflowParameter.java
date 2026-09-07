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
 * Un parámetro de negocio configurable por definición de trámite (FR-007).
 *
 * Es el mecanismo que hace que las reglas sean dato y no código: cambiar el tope
 * de créditos es un UPDATE, no un despliegue (SC-005).
 *
 * La FK apunta a la VERSIÓN concreta de la definición, que es la identidad en la
 * feature 002. Con eso FR-013 sale gratis: una solicitud se rige por la versión
 * con la que nació y sus parámetros viajan con ella, así que publicar una v2 con
 * otro tope no altera las solicitudes en curso.
 */
@Entity
@Table(name = "workflow_parameter", uniqueConstraints = @UniqueConstraint(
        name = "uq_workflow_parameter_definition_key",
        columnNames = {"definition_id", "parameter_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class WorkflowParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false, updatable = false)
    private WorkflowDefinition definition;

    @Column(name = "parameter_key", nullable = false, updatable = false, length = 50)
    private String key;

    /** Sin updatable = false: ajustar el valor ES el caso de uso (SC-005). */
    @Column(name = "parameter_value", nullable = false, length = 100)
    private String value;
}
