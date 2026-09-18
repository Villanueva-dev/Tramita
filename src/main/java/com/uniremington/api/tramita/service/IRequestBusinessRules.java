package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.model.WorkflowDefinition;

/**
 * Reglas de negocio de la captura de una solicitud (US2). Los límites se leen de
 * la configuración del trámite, nunca de constantes en código (FR-007, FR-012).
 *
 * Se expone por interface según la constitución §II: los servicios se inyectan por
 * su contrato, no por su implementación.
 */
public interface IRequestBusinessRules {

    /**
     * Valida el cuerpo contra los parámetros configurados para esa definición.
     *
     * @throws com.uniremington.api.tramita.shared.exception.UnprocessableRequestException
     *         si el contenido incumple una regla del trámite (422)
     * @throws com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException
     *         si un parámetro que la validación necesita no está configurado o su
     *         valor no es interpretable (500) — FR-010, FR-011
     */
    void validate(WorkflowDefinition definition, CreateRequestBody body);
}
