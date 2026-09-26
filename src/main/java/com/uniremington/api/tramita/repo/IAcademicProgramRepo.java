package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.AcademicProgram;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sin consultas propias todavía: {@code existsByName} y
 * {@code findAllByOrderByNameAsc} entran en T020 y T025 (research.md D1).
 */
public interface IAcademicProgramRepo extends JpaRepository<AcademicProgram, UUID> {
}
