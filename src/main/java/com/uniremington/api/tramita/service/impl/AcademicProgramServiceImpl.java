package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.ProgramResponse;
import com.uniremington.api.tramita.repo.IAcademicProgramRepo;
import com.uniremington.api.tramita.service.IAcademicProgramService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de programas leído en vivo de la base (009, FR-001, FR-006).
 *
 * SIN CACHÉ, a propósito: un programa agregado por SQL —el catálogo es dato, no
 * código (research.md D1)— tiene que aparecer sin reiniciar el servidor, mismo
 * criterio que las definiciones de trámite (precedente
 * {@code WorkflowGenericityIT.java:119}).
 *
 * El orden es el de la intercalación de la base de datos, {@code en_US.utf8} en los
 * entornos conocidos (research.md D5): no se reordena en Java.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicProgramServiceImpl implements IAcademicProgramService {

    private final IAcademicProgramRepo programRepo;

    @Override
    public List<ProgramResponse> listPrograms() {
        return programRepo.findAllByOrderByNameAsc().stream()
                // Sin transformar el nombre: se muestra tal como está guardado (FR-001).
                .map(program -> new ProgramResponse(program.getName()))
                .toList();
    }
}
