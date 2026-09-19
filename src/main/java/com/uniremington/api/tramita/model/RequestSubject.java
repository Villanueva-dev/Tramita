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
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una asignatura involucrada en una solicitud (FR-002). Pertenece a una única
 * solicitud y no existe fuera de ella.
 *
 * La captura queda editable únicamente cuando el trámite fue devuelto o rechazado;
 * la operación se autoriza en el servicio y no modifica el timeline de transiciones.
 *
 * Las notas son BigDecimal contra columnas NUMERIC(3,2) (research.md D3): una nota
 * es un número, y con VARCHAR la base aceptaría 'abc'.
 */
@Entity
@Table(name = "request_subject")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class RequestSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, updatable = false)
    private Request request;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    /** Nulo en novedad de notas, que no captura créditos. La base exige que, si viene, sea positivo. */
    @Column
    private Integer credits;

    /** group es palabra reservada en SQL; la columna se llama subject_group. */
    @Column(name = "subject_group", length = 30)
    private String group;

    @Column(name = "current_grade", precision = 3, scale = 2)
    private BigDecimal currentGrade;

    @Column(name = "proposed_grade", precision = 3, scale = 2)
    private BigDecimal proposedGrade;
}
