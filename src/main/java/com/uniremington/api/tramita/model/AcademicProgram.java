package com.uniremington.api.tramita.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un programa de pregrado del catálogo de la Sede Cali (FR-001, FR-006).
 *
 * Es dato, no código: incorporar, renombrar o quitar un programa es una fila,
 * no un despliegue (research.md D1). La solicitud guarda su {@code name}
 * publicado, no este {@code id} (research.md D3); la regla de anexo lo
 * referencia por {@code id} y se resuelve por {@code name} al leer el detalle
 * (research.md D2).
 */
@Entity
@Table(name = "academic_program", uniqueConstraints = @UniqueConstraint(
        name = "uq_academic_program_name",
        columnNames = "name"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class AcademicProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Sin updatable = false: renombrar es un caso de uso (research.md D1). */
    @Column(nullable = false, length = 120)
    private String name;
}
