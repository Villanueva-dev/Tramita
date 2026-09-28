package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.AcademicProgram;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * El catálogo de programas de pregrado (research.md D1).
 *
 * {@code existsByName} respalda la pertenencia al catálogo que exige FR-004
 * (RequestBusinessRulesImpl); {@code findAllByOrderByNameAsc} respalda el listado
 * público de FR-001.
 */
public interface IAcademicProgramRepo extends JpaRepository<AcademicProgram, UUID> {

    /** Pertenencia exacta al catálogo, sin normalizar (FR-004, research.md D8). */
    boolean existsByName(String name);

    /**
     * El orden es el de la intercalación de la base de datos —{@code en_US.utf8} en
     * los entornos conocidos—, no un orden aplicado en Java (research.md D5).
     */
    List<AcademicProgram> findAllByOrderByNameAsc();
}
