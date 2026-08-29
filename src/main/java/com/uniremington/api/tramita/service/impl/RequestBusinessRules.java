package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.SubjectRequestBody;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.repo.IWorkflowParameterRepo;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reglas autoritativas de captura; los límites configurables vienen de BD. */
@Component
@RequiredArgsConstructor
public class RequestBusinessRules {

    private static final String MAX_CREDITS = "MAX_CREDITS";
    private final IWorkflowParameterRepo parameterRepo;

    public void validate(WorkflowDefinition definition, CreateRequestBody body) {
        List<SubjectRequestBody> subjects = body.subjects() == null ? List.of() : body.subjects();
        validateCredits(definition, subjects);
        validateGrades(subjects);
    }

    private void validateCredits(WorkflowDefinition definition, List<SubjectRequestBody> subjects) {
        parameterRepo.findByDefinitionIdAndKey(definition.getId(), MAX_CREDITS).ifPresent(parameter -> {
            int maximum = parsePositiveInteger(parameter.getValue());
            int requested = subjects.stream().map(SubjectRequestBody::credits)
                    .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
            if (requested > maximum) {
                throw new UnprocessableRequestException(
                        "La solicitud supera el máximo configurado de %d créditos".formatted(maximum));
            }
        });
    }

    private void validateGrades(List<SubjectRequestBody> subjects) {
        subjects.stream().flatMap(subject -> java.util.stream.Stream.of(
                subject.currentGrade(), subject.proposedGrade()))
                .filter(grade -> grade != null && !grade.isBlank())
                .forEach(this::validateGrade);
    }

    private void validateGrade(String rawGrade) {
        try {
            BigDecimal grade = new BigDecimal(rawGrade.trim());
            if (grade.compareTo(BigDecimal.ZERO) < 0 || grade.compareTo(BigDecimal.valueOf(5)) > 0) {
                throw new UnprocessableRequestException("Las notas deben estar entre 0.0 y 5.0");
            }
        } catch (NumberFormatException exception) {
            throw new UnprocessableRequestException("Las notas deben ser valores numéricos entre 0.0 y 5.0");
        }
    }

    private int parsePositiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("El parámetro MAX_CREDITS tiene una configuración inválida");
        }
    }
}