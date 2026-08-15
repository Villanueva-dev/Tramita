package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.WorkflowDefinitionResponse;
import com.uniremington.api.tramita.repo.IWorkflowDefinitionRepo;
import com.uniremington.api.tramita.service.IWorkflowDefinitionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de definiciones vigentes. Lee de BD en cada llamada, sin caché
 * (research.md D10): una definición cargada por SQL queda operable de inmediato
 * — así se demuestra SC-005 en vivo.
 */
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionServiceImpl implements IWorkflowDefinitionService {

    private final IWorkflowDefinitionRepo definitionRepo;

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponse> findAllCurrent() {
        return definitionRepo.findAllCurrent().stream()
                .map(d -> new WorkflowDefinitionResponse(d.getCode(), d.getName(), d.getVersion()))
                .toList();
    }
}
