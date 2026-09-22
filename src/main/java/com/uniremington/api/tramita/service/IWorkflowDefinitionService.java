package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.WorkflowDefinitionDetailResponse;
import java.util.List;

public interface IWorkflowDefinitionService {

    /**
     * Definiciones vigentes (mayor version por code) — insumo del formulario de registro (002,
     * US1) y, desde la 007, con los estados de cada una para que el cliente no reconozca
     * códigos (FR-011a, issue #22).
     */
    List<WorkflowDefinitionDetailResponse> findAllCurrent();
}
