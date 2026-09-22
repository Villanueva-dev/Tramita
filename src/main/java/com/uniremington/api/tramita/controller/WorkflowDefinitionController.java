package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.WorkflowDefinitionDetailResponse;
import com.uniremington.api.tramita.service.IWorkflowDefinitionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de trámites configurados (contracts/openapi.yaml). Protegido por la
 * sesión de 001 como todo /api/** (FR-012). Desde la 007 cada definición trae sus estados
 * con las marcas de inicial y final (FR-011a, issue #22); el cambio es aditivo (FR-011c).
 */
@RestController
@RequestMapping("/api/workflow-definitions")
@RequiredArgsConstructor
public class WorkflowDefinitionController {

    private final IWorkflowDefinitionService definitionService;

    @GetMapping
    public List<WorkflowDefinitionDetailResponse> listCurrent() {
        return definitionService.findAllCurrent();
    }
}
