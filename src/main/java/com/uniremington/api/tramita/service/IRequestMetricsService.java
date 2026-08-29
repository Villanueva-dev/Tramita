package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.RequestMetricsResponse;

public interface IRequestMetricsService {

    /** Métricas agregadas para la bandeja operativa, sin datos personales. */
    RequestMetricsResponse getRequestMetrics();
}
