package com.uniremington.api.tramita.model;

import jakarta.persistence.CascadeType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    /**
     * Datos académicos del formulario (FR-001). Opcionales: una solicitud creada
     * con el cuerpo mínimo de la 002 sigue siendo válida (FR-006).
     *
     * NO existe un campo de correo del estudiante: su único consumidor previsto
     * era la notificación (SP7), fuera del alcance de este sprint. El dato entra
     * cuando exista quien lo use (FR-020, constitución §III).
     */
    @Column(name = "student_code", updatable = false, length = 30)
    private String studentCode;

    @Column(updatable = false, length = 120)
    private String program;

    @Column(updatable = false, length = 50)
    private String semester;

    @Column(updatable = false, length = 2000)
    private String reason;

    /**
     * Asignaturas del trámite (FR-002). Se separan en su propia tabla para
     * conservar la cardinalidad: un trámite involucra N asignaturas, cada una con
     * sus propios datos.
     */
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL,
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
