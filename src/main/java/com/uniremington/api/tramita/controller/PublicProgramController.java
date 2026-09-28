package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.ProgramResponse;
import com.uniremington.api.tramita.service.IAcademicProgramService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo público de programas de pregrado (009, FR-001, SC-004). CUARTO endpoint
 * abierto del sistema, después del login, la captura pública (004) y la consulta del
 * sello (006).
 *
 * SIN EXCLUSIÓN DE CSRF: es un {@code GET} y CSRF no protege operaciones que no
 * cambian estado (mismo razonamiento que {@code PUBLIC_SEAL_LOOKUP} en
 * SecurityConfig). TAMPOCO tiene límite de tasa propio: el filtro de envíos públicos
 * solo intercepta {@code POST /api/public/requests/*} (research.md D5).
 */
@RestController
@RequestMapping("/api/public/programs")
@RequiredArgsConstructor
public class PublicProgramController {

    private final IAcademicProgramService programService;

    @GetMapping
    public List<ProgramResponse> list() {
        return programService.listPrograms();
    }
}
