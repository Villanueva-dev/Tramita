package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.RequestMetricsResponse;
import com.uniremington.api.tramita.service.IRequestMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Indicadores agregados de la bandeja operativa (P7). */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class RequestMetricsController {

    private final IRequestMetricsService metricsService;

    @GetMapping("/requests")
    public RequestMetricsResponse getRequestMetrics() {
        return metricsService.getRequestMetrics();
    }
}
