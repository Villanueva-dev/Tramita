package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.dto.AssistantRequest;
import com.uniremington.api.tramita.dto.KnowledgeSearchResult;
import com.uniremington.api.tramita.dto.RequestMetricsResponse;
import com.uniremington.api.tramita.service.IKnowledgeSearchService;
import com.uniremington.api.tramita.service.IOpenRouterClient;
import com.uniremington.api.tramita.service.IRequestMetricsService;
import com.uniremington.api.tramita.shared.config.AiProperties;
import com.uniremington.api.tramita.shared.exception.AiUnavailableException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistantServiceImplTest {

        private final IKnowledgeSearchService searchService = mock(IKnowledgeSearchService.class);
    private final IOpenRouterClient openRouterClient = mock(IOpenRouterClient.class);
    private final IRequestMetricsService requestMetricsService = mock(IRequestMetricsService.class);
    private final AssistantRequest request = new AssistantRequest("¿Qué documentos necesito?");

    private AssistantResponseCache cache(AiProperties properties) {
        return new AssistantResponseCache(properties, Clock.systemUTC());
    }

    @Test
    void abstainsWithoutValidatedContext() {
        when(searchService.search(request.question())).thenReturn(List.of());
        AiProperties properties = new AiProperties(false, "", "http://localhost", "model", 500, 20, 10, 60, "", 0);
        AssistantServiceImpl service = new AssistantServiceImpl(
                searchService, openRouterClient, properties, requestMetricsService, cache(properties));

        var response = service.answer(request);

        assertThat(response.grounded()).isFalse();
        assertThat(response.sources()).isEmpty();
        verify(openRouterClient, never()).answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsEnabledProviderWithoutApiKey() {
        KnowledgeSearchResult result = new KnowledgeSearchResult(
                UUID.randomUUID(), "Requisitos documentales", "reglamento", "Reglamento", "2026", "p. 1", "Sección 1", 1);
        when(searchService.search(request.question())).thenReturn(List.of(result));
        AiProperties properties = new AiProperties(true, "", "http://localhost", "model", 500, 20, 10, 60, "", 0);
        AssistantServiceImpl service = new AssistantServiceImpl(
                searchService, openRouterClient, properties, requestMetricsService, cache(properties));

        assertThatExceptionOfType(AiUnavailableException.class).isThrownBy(() -> service.answer(request));
        verify(openRouterClient, never()).answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void returnsGroundedAnswerAndCitations() {
        KnowledgeSearchResult result = new KnowledgeSearchResult(
                UUID.randomUUID(), "Requisitos documentales", "reglamento", "Reglamento", "2026", "p. 1", "Sección 1", 1);
        when(searchService.search(request.question())).thenReturn(List.of(result));
        when(openRouterClient.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("Debe presentar los documentos indicados en la fuente.");
        AiProperties properties = new AiProperties(true, "secret-no-real", "http://localhost", "model", 500, 20, 10, 60, "", 0);
        AssistantServiceImpl service = new AssistantServiceImpl(
                searchService, openRouterClient, properties, requestMetricsService, cache(properties));

        var response = service.answer(request);

        assertThat(response.grounded()).isTrue();
        assertThat(response.answer()).contains("documentos");
        assertThat(response.sources()).singleElement().satisfies(source ->
                assertThat(source.sourceId()).isEqualTo("reglamento"));
    }

    @Test
    void answersOperationalQuestionsWithoutDocumentaryEvidence() {
        AssistantRequest operationalRequest = new AssistantRequest("¿Cuántas solicitudes están pendientes?");
        when(searchService.search(operationalRequest.question())).thenReturn(List.of());
        when(requestMetricsService.getRequestMetrics()).thenReturn(new RequestMetricsResponse(
                5, Map.of("ADICION_CREDITOS", 5L), Map.of("EN_FACULTAD", 3L), 1, 24.0, 1));
        when(openRouterClient.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("Hay 3 solicitudes en revisión de la facultad.");
        AiProperties properties = new AiProperties(true, "secret-no-real", "http://localhost", "model", 500, 20, 10, 60, "", 0);
        AssistantServiceImpl service = new AssistantServiceImpl(
                searchService, openRouterClient, properties, requestMetricsService, cache(properties));

        var response = service.answer(operationalRequest);

        assertThat(response.grounded()).isTrue();
        assertThat(response.sources()).singleElement().satisfies(source ->
                assertThat(source.sourceId()).isEqualTo("metricas-operativas-tramita"));
    }

    @Test
    void reusesCachedAnswerForRepeatedQuestion() {
        KnowledgeSearchResult result = new KnowledgeSearchResult(
                UUID.randomUUID(), "Requisitos documentales", "reglamento", "Reglamento", "2026", "p. 1", "Sección 1", 1);
        when(searchService.search(request.question())).thenReturn(List.of(result));
        when(openRouterClient.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("Debe presentar los documentos indicados en la fuente.");
        AiProperties properties = new AiProperties(true, "secret-no-real", "http://localhost", "model", 500, 20, 10, 60, "", 120);
        AssistantResponseCache sharedCache = cache(properties);
        AssistantServiceImpl service = new AssistantServiceImpl(
                searchService, openRouterClient, properties, requestMetricsService, sharedCache);

        service.answer(request);
        service.answer(request);

        verify(openRouterClient, org.mockito.Mockito.times(1))
                .answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
    }
}

