package com.uniremington.api.tramita.dto;

import java.util.List;

/**
 * Definición vigente CON sus estados: la respuesta del catálogo desde la 007 (FR-011a,
 * issue #22, contracts/openapi.yaml).
 *
 * Es un esquema propio y NO una ampliación de {@link WorkflowDefinitionResponse}, que se anida
 * en cada respuesta de solicitud: ampliar aquél metería el listado completo de estados dentro
 * de cada solicitud devuelta y dispararía la carga perezosa de {@code states} en cada una
 * (research.md D6). Aditivo para los clientes del catálogo: {@code code}, {@code name} y
 * {@code version} conservan su forma y su significado (FR-011c).
 *
 * {@code states} no lleva orden significativo: el flujo admite devoluciones y rechazos, así
 * que no es una secuencia, y un «paso N de M» afirmaría una linealidad que la configuración
 * no tiene (FR-011b).
 */
public record WorkflowDefinitionDetailResponse(
        String code,
        String name,
        int version,
        List<StateResponse> states) {
}
