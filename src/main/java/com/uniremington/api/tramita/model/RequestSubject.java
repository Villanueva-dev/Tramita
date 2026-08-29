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

/** Información de una asignatura asociada a una solicitud. */
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

    @Column(updatable = false)
    private Integer credits;

    @Column(name = "subject_group", updatable = false, length = 30)
    private String group;

    @Column(name = "current_grade", updatable = false, length = 20)
    private String currentGrade;

    @Column(name = "proposed_grade", updatable = false, length = 20)
    private String proposedGrade;
}