package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.AssistantRequest;
import com.uniremington.api.tramita.dto.AssistantResponse;
import com.uniremington.api.tramita.dto.AssistantSource;
import com.uniremington.api.tramita.dto.KnowledgeSearchResult;
import com.uniremington.api.tramita.service.IAssistantService;
import com.uniremington.api.tramita.service.IKnowledgeSearchService;
import com.uniremington.api.tramita.service.IOpenRouterClient;
import com.uniremington.api.tramita.shared.config.AiProperties;
import com.uniremington.api.tramita.shared.exception.AiUnavailableException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssistantServiceImpl implements IAssistantService {

    private static final String DISCLAIMER = "Orientación informativa; la decisión corresponde a la institución.";
    private static final String NO_CONTEXT = "No encontré respaldo suficiente en las fuentes institucionales validadas.";

    private final IKnowledgeSearchService searchService;
    private final IOpenRouterClient openRouterClient;
    private final AiProperties properties;

    @Override
    @Transactional(readOnly = true)
    public AssistantResponse answer(AssistantRequest request) {
        List<KnowledgeSearchResult> results = searchService.search(request.question());
        if (results.isEmpty()) {
            // Sin evidencia validada no se consume el proveedor ni se fabrica una respuesta.
            return new AssistantResponse(NO_CONTEXT, false, List.of(),
                    "Comuníquese con la Coordinación Académica de la Sede Cali.");
        }
        if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiUnavailableException("El asistente de IA no está habilitado");
        }

        List<String> context = results.stream().map(this::contextEntry).toList();
        String answer = openRouterClient.answer(request.question(), context);
        List<AssistantSource> sources = results.stream().map(this::toSource).toList();
        return new AssistantResponse(answer, true, sources, DISCLAIMER);
    }

    private String contextEntry(KnowledgeSearchResult result) {
        return "FUENTE: %s | VERSION: %s | UBICACION: %s\nCONTENIDO:\n%s"
                .formatted(result.title(), result.version(), result.locator(), result.content());
    }

    private AssistantSource toSource(KnowledgeSearchResult result) {
        return new AssistantSource(result.sourceId(), result.title(), result.version(),
                result.locator(), result.section(), result.page());
    }
}
