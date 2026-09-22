package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.StateResponse;
import com.uniremington.api.tramita.model.WorkflowState;

/**
 * Único sitio donde se construye {@link StateResponse} (007, T033). Antes vivía como método
 * privado de {@code RequestServiceImpl}; el catálogo de definiciones también necesita mapear
 * estados desde la 007, y había tres formas de dárselo:
 *
 * <ul>
 *   <li>Una factoría estática en el propio record: descartada porque metería una dependencia
 *       de {@code dto/} hacia {@code model/}, y la capa de contratos no debe conocer entidades.</li>
 *   <li>Duplicar la línea en {@code WorkflowDefinitionServiceImpl} (lo que hizo el spike de la
 *       planificación): descartada porque rompe la garantía de «un solo sitio» que data-model.md
 *       declara, y el mutante T037 —{@code isInitial} siempre false— solo alcanzaría a uno de
 *       los dos caminos.</li>
 *   <li>Este mapeador, en la capa de implementación de servicios, compartido por ambos.</li>
 * </ul>
 *
 * Mapeo a mano, como el resto del proyecto (convención de 001): la entidad nunca cruza la
 * frontera de la API.
 */
final class StateResponseMapper {

    private StateResponseMapper() {
    }

    static StateResponse toResponse(WorkflowState state) {
        return new StateResponse(
                state.getCode(), state.getName(), state.isInitial(), state.isFinalState());
    }
}
