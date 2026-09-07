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
 * Todo el estado es {@code updatable = false}: los datos de captura son inmutables
 * (FR-005), coherente con el diseño de la 002 — corregir un dato capturado es
 * registrar una devolución, no editar el registro.
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

    @Column(nullable = false, updatable = false, length = 30)
    private String code;

    @Column(nullable = false, updatable = false, length = 150)
    private String name;

    /** Nulo en novedad de notas, que no captura créditos. La base exige que, si viene, sea positivo. */
    @Column(updatable = false)
    private Integer credits;

    /** group es palabra reservada en SQL; la columna se llama subject_group. */
    @Column(name = "subject_group", updatable = false, length = 30)
    private String group;

    @Column(name = "current_grade", updatable = false, precision = 3, scale = 2)
    private BigDecimal currentGrade;

    @Column(name = "proposed_grade", updatable = false, precision = 3, scale = 2)
    private BigDecimal proposedGrade;
}
