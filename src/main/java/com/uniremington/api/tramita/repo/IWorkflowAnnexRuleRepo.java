package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.WorkflowAnnexRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sin consultas propias todavía: {@code findByDefinitionIdAndProgramName}
 * entra en T036 (research.md D2).
 */
public interface IWorkflowAnnexRuleRepo extends JpaRepository<WorkflowAnnexRule, UUID> {
}
