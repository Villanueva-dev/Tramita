package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.SubjectRequestBody;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowParameter;
import com.uniremington.api.tramita.repo.IWorkflowParameterRepo;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestBusinessRulesTest {

    private static final UUID DEFINITION_ID = UUID.randomUUID();
    private final IWorkflowParameterRepo parameterRepo = mock(IWorkflowParameterRepo.class);
    private final RequestBusinessRules rules = new RequestBusinessRules(parameterRepo);
    private final WorkflowDefinition definition = WorkflowDefinition.builder()
            .id(DEFINITION_ID).code("ADICION_CREDITOS").version(1).name("Adición").build();

    @Test
    @DisplayName("rechaza créditos por encima del parámetro configurado")
    void rejectsCreditsAboveConfiguredLimit() {
        when(parameterRepo.findByDefinitionIdAndKey(DEFINITION_ID, "MAX_CREDITS"))
                .thenReturn(Optional.of(parameter("21")));

        assertThatThrownBy(() -> rules.validate(definition, bodyWithSubject(22, null)))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("21");
    }

    @Test
    @DisplayName("acepta exactamente el límite de créditos configurado")
    void acceptsConfiguredCreditLimit() {
        when(parameterRepo.findByDefinitionIdAndKey(DEFINITION_ID, "MAX_CREDITS"))
                .thenReturn(Optional.of(parameter("21")));

        assertThatCode(() -> rules.validate(definition, bodyWithSubject(21, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rechaza notas fuera del rango institucional de 0.0 a 5.0")
    void rejectsGradesOutsideRange() {
        assertThatThrownBy(() -> rules.validate(definition, bodyWithSubject(null, "5.1")))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("0.0 y 5.0");
    }

    private WorkflowParameter parameter(String value) {
        return WorkflowParameter.builder().definition(definition)
                .key("MAX_CREDITS").value(value).build();
    }

    private CreateRequestBody bodyWithSubject(Integer credits, String grade) {
        return new CreateRequestBody("ADICION_CREDITOS", "Estudiante", "123456",
                null, null, null, null, null, "normal",
                List.of(new SubjectRequestBody("MAT-01", "Materia", credits, null, grade, null)));
    }
}