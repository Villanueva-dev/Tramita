package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.WorkflowDefinitionDetailResponse;
import com.uniremington.api.tramita.repo.IWorkflowDefinitionRepo;
import com.uniremington.api.tramita.service.IWorkflowDefinitionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de definiciones vigentes. Lee de BD en cada llamada, sin caché
 * (research.md D10 de la 002): una definición cargada por SQL queda operable de inmediato
 * — así se demuestra SC-005 en vivo.
 *
 * Desde la 007 cada definición sale con sus estados (FR-011a). La relación {@code states} es
 * perezosa y se recorre dentro de la transacción de solo lectura de este método; los estados
 * se mapean con {@link StateResponseMapper}, el mismo sitio que usan las respuestas de
 * solicitud, para no tener dos constructores del mismo contrato.
 */
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionServiceImpl implements IWorkflowDefinitionService {

    private final IWorkflowDefinitionRepo definitionRepo;

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowDefinitionDetailResponse> findAllCurrent() {
        return definitionRepo.findAllCurrent().stream()
                .map(d -> new WorkflowDefinitionDetailResponse(
                        d.getCode(), d.getName(), d.getVersion(),
                        d.getStates().stream().map(StateResponseMapper::toResponse).toList()))
                .toList();
    }
}
