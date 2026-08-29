package com.uniremington.api.tramita.dto;

import java.util.Map;

/** Indicadores agregados de la bandeja operativa; no contiene datos personales. */
public record RequestMetricsResponse(
        long total,
        Map<String, Long> byDefinition,
        Map<String, Long> byCurrentState,
        long completed,
        Double averageCycleHours,
        long returnCount) {
}
