package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.ProgramResponse;
import java.util.List;

/**
 * Catálogo de programas de pregrado, expuesto al canal público (009, FR-001).
 *
 * Se expone por interface según la constitución §II: los servicios se inyectan por
 * su contrato, no por su implementación.
 */
public interface IAcademicProgramService {

    /**
     * El catálogo completo, en el orden de la intercalación de la base de datos
     * (research.md D5). Sin caché: un programa agregado por SQL aparece de
     * inmediato, sin reiniciar (FR-006).
     */
    List<ProgramResponse> listPrograms();
}
