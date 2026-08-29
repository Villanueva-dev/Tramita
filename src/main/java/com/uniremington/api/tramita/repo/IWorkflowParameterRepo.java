package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.WorkflowParameter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowParameterRepo extends JpaRepository<WorkflowParameter, UUID> {

    Optional<WorkflowParameter> findByDefinitionIdAndKey(UUID definitionId, String key);
}