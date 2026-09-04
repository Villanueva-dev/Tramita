package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.SubjectRequestBody;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowParameter;
import com.uniremington.api.tramita.repo.IWorkflowParameterRepo;
import com.uniremington.api.tramita.service.IRequestBusinessRules;
import com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Reglas de captura leídas de configuración (research.md D1, D2, D4).
 *
 * Ninguna validación se saltea en silencio: si el parámetro que necesita no está
 * cargado, o está con un valor no interpretable, se lanza
 * {@link IncompleteConfigurationException} y la solicitud NO se registra. Fallar
 * abierto haría que la validación existiera en el código sin ocurrir en los hechos
 * (FR-010, FR-011).
 *
 * En cambio, un parámetro solo se exige cuando la validación que lo usa aplica: un
 * trámite que no captura créditos no necesita tener MAX_CREDITS cargado.
 */
@Service
@RequiredArgsConstructor
public class RequestBusinessRulesImpl implements IRequestBusinessRules {

    private static final String MAX_CREDITS = "MAX_CREDITS";
    private static final String MIN_GRADE = "MIN_GRADE";
    private static final String MAX_GRADE = "MAX_GRADE";

    private final IWorkflowParameterRepo parameterRepo;

    @Override
    public void validate(WorkflowDefinition definition, CreateRequestBody body) {
        List<SubjectRequestBody> subjects = body.subjects();
        validateCredits(definition, subjects);
        validateGrades(definition, subjects);
    }

    private void validateCredits(WorkflowDefinition definition, List<SubjectRequestBody> subjects) {
        List<Integer> declared = subjects.stream()
                .map(SubjectRequestBody::credits)
                .filter(Objects::nonNull)
                .toList();
        // Sin créditos declarados, la regla no aplica y su parámetro no se exige.
        if (declared.isEmpty()) {
            return;
        }

        int maximum = requirePositiveInteger(definition, MAX_CREDITS);
        int requested = declared.stream().mapToInt(Integer::intValue).sum();
        if (requested > maximum) {
            throw new UnprocessableRequestException(
                    "La solicitud suma %d créditos y el máximo configurado para este trámite es %d"
                            .formatted(requested, maximum));
        }
    }

    private void validateGrades(WorkflowDefinition definition, List<SubjectRequestBody> subjects) {
        List<BigDecimal> declared = subjects.stream()
                .flatMap(subject -> java.util.stream.Stream.of(
                        subject.currentGrade(), subject.proposedGrade()))
                .filter(Objects::nonNull)
                .toList();
        if (declared.isEmpty()) {
            return;
        }

        BigDecimal minimum = requireDecimal(definition, MIN_GRADE);
        BigDecimal maximum = requireDecimal(definition, MAX_GRADE);
        if (minimum.compareTo(maximum) >= 0) {
            throw new IncompleteConfigurationException(
                    "%s (%s) no es menor que %s (%s) en la definición %s"
                            .formatted(MIN_GRADE, minimum, MAX_GRADE, maximum, definition.getId()));
        }

        for (BigDecimal grade : declared) {
            if (grade.compareTo(minimum) < 0 || grade.compareTo(maximum) > 0) {
                throw new UnprocessableRequestException(
                        "Las notas deben estar entre %s y %s".formatted(
                                minimum.toPlainString(), maximum.toPlainString()));
            }
        }
    }

    /** El valor debe existir y ser un entero positivo; cualquier otra cosa es configuración rota. */
    private int requirePositiveInteger(WorkflowDefinition definition, String key) {
        String raw = requireValue(definition, key);
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            // Un 0 o un texto NO se interpretan como "límite cero": eso rechazaría
            // toda solicitud con un 422, culpando al usuario de la configuración.
            throw new IncompleteConfigurationException(
                    "El parámetro %s de la definición %s tiene un valor no interpretable"
                            .formatted(key, definition.getId()));
        }
    }

    private BigDecimal requireDecimal(WorkflowDefinition definition, String key) {
        String raw = requireValue(definition, key);
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IncompleteConfigurationException(
                    "El parámetro %s de la definición %s tiene un valor no interpretable"
                            .formatted(key, definition.getId()));
        }
    }

    private String requireValue(WorkflowDefinition definition, String key) {
        UUID definitionId = definition.getId();
        return parameterRepo.findByDefinitionIdAndKey(definitionId, key)
                .map(WorkflowParameter::getValue)
                .orElseThrow(() -> new IncompleteConfigurationException(
                        "Falta el parámetro %s para la definición %s"
                                .formatted(key, definitionId)));
    }
}
