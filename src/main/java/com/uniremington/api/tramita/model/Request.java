package com.uniremington.api.tramita.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Solicitud de un trámite para un estudiante concreto (data-model.md). Queda
 * atada a la VERSIÓN de la definición con la que nació (FR-009): los cambios de
 * configuración posteriores no la afectan. Datos personales minimizados a
 * nombre + cédula (Ley 1581 de 2012, supuesto de la spec). Esta entity nunca se
 * expone en la API: los DTOs mapean a mano solo los campos permitidos.
 */
@Entity
@Table(name = "request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** La definición (y versión) con la que nació — inmutable (FR-009). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", updatable = false)
    private WorkflowDefinition definition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_state_id")
    private WorkflowState currentState;

    @Column(name = "student_name", nullable = false, updatable = false)
    private String studentName;

    @Column(name = "student_document", nullable = false, updatable = false)
    private String studentDocument;

        // Datos estructurados del formulario; se separan las asignaturas para conservar su cardinalidad.
        @Column(name = "student_code", updatable = false)
        private String studentCode;

        @Column(name = "student_email", updatable = false)
        private String studentEmail;

        @Column(updatable = false)
        private String program;

        @Column(updatable = false)
        private String semester;

        @Column(updatable = false)
        private String reason;

        @Column(nullable = false, updatable = false)
        @Builder.Default
        private String priority = "normal";

        @OneToMany(mappedBy = "request", cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
        @Builder.Default
        private List<RequestSubject> subjects = new ArrayList<>();

    /**
     * Locking optimista (research.md D6): ante dos avances casi simultáneos solo
     * prospera la transacción que vio el estado vigente; la otra recibe 409.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Única mutación permitida: el motor mueve la solicitud a un estado de su
     * definición tras validar la transición. Método de dominio en lugar de
     * setter para que la intención quede en la firma.
     */
    public void moveTo(WorkflowState target) {
        this.currentState = target;
    }

    // UTC explícito, convención del chasis (001, JD3-012).
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
