package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.dto.RequestMetricsResponse;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestTransitionLog;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowState;
import com.uniremington.api.tramita.model.WorkflowTransition;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestMetricsServiceImplTest {

    private final IRequestRepo requestRepo = mock(IRequestRepo.class);
    private final IRequestTransitionLogRepo logRepo = mock(IRequestTransitionLogRepo.class);
    private final RequestMetricsServiceImpl service = new RequestMetricsServiceImpl(requestRepo, logRepo);

    @Test
    @DisplayName("agrega definiciones, estados, cierres y devoluciones configuradas")
    void aggregatesMetricsForCompletedRequestWithConfiguredReturn() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 10, 8, 0);
        WorkflowState underReview = state("REVISION", false);
        WorkflowState returned = state("DEVUELTO", false);
        WorkflowState completed = state("FINALIZADO", true);
        WorkflowTransition returnTransition = WorkflowTransition.builder()
                .fromState(underReview).toState(returned).requiresNote(true).responsible("COORDINACION").build();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .code("ADICION_CREDITOS").version(1).name("Adición de créditos")
                .transitions(List.of(returnTransition)).build();
        UUID requestId = UUID.randomUUID();
        Request request = Request.builder()
                .id(requestId).definition(definition).currentState(completed)
                .studentName("Estudiante").studentDocument("123").createdAt(createdAt).build();
        List<RequestTransitionLog> timeline = List.of(
                log(null, underReview, createdAt.plusMinutes(5)),
                log(underReview, returned, createdAt.plusMinutes(30)),
                log(returned, completed, createdAt.plusHours(2)));
        when(requestRepo.findAll()).thenReturn(List.of(request));
        when(logRepo.findByRequestIdOrderByOccurredAtAscIdAsc(requestId)).thenReturn(timeline);

        RequestMetricsResponse result = service.getRequestMetrics();

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.byDefinition()).containsExactlyEntriesOf(java.util.Map.of("ADICION_CREDITOS", 1L));
        assertThat(result.byCurrentState()).containsExactlyEntriesOf(java.util.Map.of("FINALIZADO", 1L));
        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.averageCycleHours()).isEqualTo(120.0);
        assertThat(result.returnCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("no calcula promedio sin ciclos finalizados con fecha válida")
    void returnsNullAverageWhenNoCompletedCycleHasValidDates() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 10, 8, 0);
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .code("NOVEDAD_NOTAS").version(1).name("Novedad de notas").build();
        UUID openRequestId = UUID.randomUUID();
        UUID invalidCompletedRequestId = UUID.randomUUID();
        Request openRequest = Request.builder()
                .id(openRequestId).definition(definition).currentState(state("REVISION", false))
                .studentName("Estudiante").studentDocument("123").createdAt(createdAt).build();
        Request invalidCompletedRequest = Request.builder()
                .id(invalidCompletedRequestId).definition(definition).currentState(state("FINALIZADO", true))
                .studentName("Estudiante").studentDocument("456").createdAt(createdAt).build();
        when(requestRepo.findAll()).thenReturn(List.of(openRequest, invalidCompletedRequest));
        when(logRepo.findByRequestIdOrderByOccurredAtAscIdAsc(openRequestId)).thenReturn(List.of());
        when(logRepo.findByRequestIdOrderByOccurredAtAscIdAsc(invalidCompletedRequestId))
                .thenReturn(List.of(log(null, state("FINALIZADO", true), createdAt.minusMinutes(1))));

        RequestMetricsResponse result = service.getRequestMetrics();

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.averageCycleHours()).isNull();
        assertThat(result.returnCount()).isZero();
    }

    private WorkflowState state(String code, boolean finalState) {
        return WorkflowState.builder().code(code).name(code).finalState(finalState).build();
    }

    private RequestTransitionLog log(WorkflowState from, WorkflowState to, LocalDateTime occurredAt) {
        return RequestTransitionLog.builder().fromState(from).toState(to).occurredAt(occurredAt).build();
    }
}