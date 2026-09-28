package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.WorkflowAnnexRule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IWorkflowAnnexRuleRepo extends JpaRepository<WorkflowAnnexRule, UUID> {

    /**
     * Query derivada de {@code definition.id} + {@code program.name}: la búsqueda es
     * por la VERSIÓN concreta de la definición con que nació la solicitud, no por su
     * código, con el mismo criterio que {@link IWorkflowParameterRepo#findByDefinitionIdAndKey}
     * (research.md D2). Devuelve a lo sumo una fila: las dos unicidades de
     * {@code academic_program.name} y {@code workflow_annex_rule(definition_id, program_id)}
     * lo garantizan en el esquema, no por disciplina del código.
     */
    Optional<WorkflowAnnexRule> findByDefinitionIdAndProgramName(UUID definitionId, String programName);
}
