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
import java.util.Optional;
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

    private static final String CAPTURES_CREDITS = "CAPTURES_CREDITS";
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

        if (!capturesCredits(definition)) {
            // El formato oficial de novedad de notas no tiene columna de créditos
            // (material-coord/2026-06-03-coord-formato-novedad-notas.docx): recibirlos
            // es un dato de más del cliente, no una configuración rota del servidor.
            if (!declared.isEmpty()) {
                throw new UnprocessableRequestException(
                        "Este trámite no captura créditos: sus asignaturas no deben declararlos");
            }
            return;
        }

        // La configuración se valida antes que el dato del cliente: si el trámite declara
        // capturar créditos pero le falta su tope, el defecto es del servidor y no puede
        // presentarse como un error de quien envía la solicitud.
        int maximum = requirePositiveInteger(definition, MAX_CREDITS);

        // Omitir el dato NO puede ser la forma de esquivar el tope: sin esta guarda, una
        // solicitud sin créditos se registraba con 201 y la validación no llegaba a correr.
        if (declared.size() != subjects.size()) {
            throw new UnprocessableRequestException(
                    "Este trámite exige declarar los créditos de cada asignatura");
        }

        int requested = declared.stream().mapToInt(Integer::intValue).sum();
        if (requested > maximum) {
            throw new UnprocessableRequestException(
                    "La solicitud suma %d créditos y el máximo configurado para este trámite es %d"
                            .formatted(requested, maximum));
        }
    }

    /**
     * Un trámite captura créditos solo si lo declara. La ausencia del parámetro es el
     * caso por defecto —no captura— y por eso NO es configuración incompleta: obligar a
     * declararlo encarecería crear un trámite nuevo, que es lo que el motor abarata.
     *
     * Un valor que no sea true ni false SÍ es configuración rota: leerlo como false
     * dejaría a un trámite que sí captura créditos rechazando toda solicitud con un 422,
     * culpando al usuario de un error que no cometió.
     */
    private boolean capturesCredits(WorkflowDefinition definition) {
        Optional<String> configured = parameterRepo
                .findByDefinitionIdAndKey(definition.getId(), CAPTURES_CREDITS)
                .map(WorkflowParameter::getValue);
        if (configured.isEmpty()) {
            return false;
        }
        String value = configured.get().trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IncompleteConfigurationException(
                "El parámetro %s de la definición %s tiene un valor no interpretable"
                        .formatted(CAPTURES_CREDITS, definition.getId()));
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
