package com.uniremington.api.tramita.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Definición versionada de un trámite (data-model.md). La versión es parte de la
 * identidad — UNIQUE(code, version): editar un trámite es insertar la versión
 * siguiente, nunca mutar filas usadas (research.md D2). Por eso la entidad no
 * expone setters: es configuración de solo lectura para la aplicación (la única
 * escritura del alcance es la semilla SQL). El builder existe para construir
 * definiciones en memoria en los tests del motor.
 */
@Entity
@Table(name = "workflow_definition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class WorkflowDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Identificador estable del trámite entre versiones (los códigos concretos viven en la semilla). */
    @Column(nullable = false)
    private String code;

    /**
     * Versión de la definición. NO es un {@code @Version} de JPA: es identidad de
     * negocio — la vigente para solicitudes nuevas es la mayor por code.
     */
    @Column(nullable = false)
    private int version;

    /** Nombre para mostrar ('Adición de créditos'). */
    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "definition", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @Builder.Default
    private List<WorkflowState> states = new ArrayList<>();

    @OneToMany(mappedBy = "definition", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @Builder.Default
    private List<WorkflowTransition> transitions = new ArrayList<>();

    // UTC explícito, convención del chasis (001, JD3-012).
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
