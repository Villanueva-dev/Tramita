package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.WorkflowParameter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowParameterRepo extends JpaRepository<WorkflowParameter, UUID> {

    /**
     * Query derivada de {@code definition.id} + {@code key}: la búsqueda es por la
     * definición CONCRETA de la solicitud, no por su código, para que cada versión
     * conserve sus propios parámetros (FR-013).
     */
    Optional<WorkflowParameter> findByDefinitionIdAndKey(UUID definitionId, String key);
}
