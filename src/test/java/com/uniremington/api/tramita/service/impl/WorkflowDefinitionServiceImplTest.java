package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.dto.WorkflowDefinitionResponse;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.repo.IWorkflowDefinitionRepo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionServiceImplTest {

    private final IWorkflowDefinitionRepo definitionRepo = mock(IWorkflowDefinitionRepo.class);
    private final WorkflowDefinitionServiceImpl service = new WorkflowDefinitionServiceImpl(definitionRepo);

    @Test
    @DisplayName("proyecta las definiciones vigentes al contrato publico")
    void mapsCurrentDefinitionsToResponses() {
        WorkflowDefinition credits = WorkflowDefinition.builder()
                .code("ADICION_CREDITOS").version(2).name("Adición de créditos").build();
        WorkflowDefinition grades = WorkflowDefinition.builder()
                .code("NOVEDAD_NOTAS").version(1).name("Novedad de notas").build();
        when(definitionRepo.findAllCurrent()).thenReturn(List.of(credits, grades));

        List<WorkflowDefinitionResponse> result = service.findAllCurrent();

        assertThat(result).containsExactly(
                new WorkflowDefinitionResponse("ADICION_CREDITOS", "Adición de créditos", 2),
                new WorkflowDefinitionResponse("NOVEDAD_NOTAS", "Novedad de notas", 1));
    }
}