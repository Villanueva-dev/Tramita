package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.RequestMetricsResponse;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestTransitionLog;
import com.uniremington.api.tramita.model.WorkflowTransition;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import com.uniremington.api.tramita.service.IRequestMetricsService;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestMetricsServiceImpl implements IRequestMetricsService {

    private final IRequestRepo requestRepo;
    private final IRequestTransitionLogRepo logRepo;

    @Override
    @Transactional(readOnly = true)
    public RequestMetricsResponse getRequestMetrics() {
        List<Request> requests = requestRepo.findAll();
        Map<String, Long> byDefinition = new LinkedHashMap<>();
        Map<String, Long> byCurrentState = new LinkedHashMap<>();
        long completed = 0;
        long returnCount = 0;
        long completedCycleHours = 0;
        long completedWithCycle = 0;

        for (Request request : requests) {
            increment(byDefinition, request.getDefinition().getCode());
            increment(byCurrentState, request.getCurrentState().getCode());
            List<RequestTransitionLog> timeline = logRepo.findByRequestIdOrderByOccurredAtAscIdAsc(request.getId());
            returnCount += timeline.stream().filter(log -> isReturn(request, log)).count();

            if (request.getCurrentState().isFinalState()) {
                completed++;
                var completedAt = timeline.stream()
                        .map(RequestTransitionLog::getOccurredAt)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
                // El ciclo se mide con fechas UTC del request y del timeline append-only.
                if (completedAt != null) {
                    long hours = Duration.between(request.getCreatedAt(), completedAt).toMinutes();
                    if (hours >= 0) {
                        completedCycleHours += hours;
                        completedWithCycle++;
                    }
                }
            }
        }

        Double averageCycleHours = completedWithCycle == 0
                ? null
                : (double) completedCycleHours / completedWithCycle;
        return new RequestMetricsResponse(
                requests.size(), byDefinition, byCurrentState, completed, averageCycleHours, returnCount);
    }

    private boolean isReturn(Request request, RequestTransitionLog log) {
        if (log.getFromState() == null) return false;
        return request.getDefinition().getTransitions().stream()
                .filter(WorkflowTransition::isRequiresNote)
                .anyMatch(transition -> transition.getFromState().getCode().equals(log.getFromState().getCode())
                        && transition.getToState().getCode().equals(log.getToState().getCode()));
    }

    private void increment(Map<String, Long> values, String key) {
        values.merge(key, 1L, Long::sum);
    }
}
