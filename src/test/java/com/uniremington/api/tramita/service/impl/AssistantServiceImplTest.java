package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.dto.AssistantRequest;
import com.uniremington.api.tramita.dto.KnowledgeSearchResult;
import com.uniremington.api.tramita.service.IKnowledgeSearchService;
import com.uniremington.api.tramita.service.IOpenRouterClient;
import com.uniremington.api.tramita.shared.config.AiProperties;
import com.uniremington.api.tramita.shared.exception.AiUnavailableException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistantServiceImplTest {

        private final IKnowledgeSearchService searchService = mock(IKnowledgeSearchService.class);
    private final IOpenRouterClient openRouterClient = mock(IOpenRouterClient.class);
    private final AssistantRequest request = new AssistantRequest("¿Qué documentos necesito?");

    @Test
    void abstainsWithoutValidatedContext() {
        when(searchService.search(request.question())).thenReturn(List.of());
        AssistantServiceImpl service = new AssistantServiceImpl(
                searchService, openRouterClient, new AiProperties(false, "", "http://localhost", "model", 500, 20));

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
        AssistantServiceImpl service = new AssistantServiceImpl(
                searchService, openRouterClient, new AiProperties(true, "", "http://localhost", "model", 500, 20));

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
        AssistantServiceImpl service = new AssistantServiceImpl(
                searchService, openRouterClient, new AiProperties(true, "secret-no-real", "http://localhost", "model", 500, 20));

        var response = service.answer(request);

        assertThat(response.grounded()).isTrue();
        assertThat(response.answer()).contains("documentos");
        assertThat(response.sources()).singleElement().satisfies(source ->
                assertThat(source.sourceId()).isEqualTo("reglamento"));
    }
}
