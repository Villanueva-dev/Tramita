package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.WorkflowDefinitionResponse;
import java.util.List;

public interface IWorkflowDefinitionService {

    /** Definiciones vigentes (mayor version por code) — insumo del formulario de registro (US1). */
    List<WorkflowDefinitionResponse> findAllCurrent();
}
