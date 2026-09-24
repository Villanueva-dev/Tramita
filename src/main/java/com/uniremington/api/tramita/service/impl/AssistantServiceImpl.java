package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.AssistantRequest;
import com.uniremington.api.tramita.dto.AssistantResponse;
import com.uniremington.api.tramita.dto.AssistantSource;
import com.uniremington.api.tramita.dto.KnowledgeSearchResult;
import com.uniremington.api.tramita.dto.RequestMetricsResponse;
import com.uniremington.api.tramita.service.IAssistantService;
import com.uniremington.api.tramita.service.IKnowledgeSearchService;
import com.uniremington.api.tramita.service.IOpenRouterClient;
import com.uniremington.api.tramita.service.IRequestMetricsService;
import com.uniremington.api.tramita.shared.config.AiProperties;
import com.uniremington.api.tramita.shared.exception.AiUnavailableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssistantServiceImpl implements IAssistantService {

    private static final String DISCLAIMER = "Orientación informativa basada en fuentes institucionales o evidencia operativa validada; la decisión corresponde a la institución.";
    private static final String NO_CONTEXT = "No encontré respaldo suficiente en las fuentes validadas disponibles.";
    private static final String OPERATIONAL_SOURCE_ID = "metricas-operativas-tramita";

    /**
     * Palabras que delatan una pregunta sobre el estado agregado de la bandeja, no sobre un
     * reglamento. Heurística simple y explícita a propósito (KISS): un clasificador de intención
     * es una inversión que el volumen de este MVP no justifica; el costo del trade-off es que
     * una pregunta operativa con vocabulario distinto puede abstenerse igual que antes.
     */
    private static final List<String> OPERATIONAL_KEYWORDS = List.of(
            "solicitud", "tramite", "trámite", "proceso", "pendiente", "estado", "metrica",
            "métrica", "cuanta", "cuánta", "cuanto", "cuánto", "promedio", "devuelta", "devuelto",
            "avance", "bandeja", "completad");

    /** Traduce los códigos crudos de workflow_state a lenguaje natural para el modelo. */
    private static final Map<String, String> STATE_GLOSSARY = Map.ofEntries(
            Map.entry("REGISTRADA", "registrada (recién creada)"),
            Map.entry("EN_FACULTAD", "en revisión de la facultad"),
            Map.entry("APROBADA_FACULTAD", "aprobada por la facultad"),
            Map.entry("EN_REGISTRO_CALI", "en registro Cali (carga en QF)"),
            Map.entry("EN_REGISTRO_NACIONAL", "en registro nacional"),
            Map.entry("EN_PREPARACION", "en preparación (carpeta y firmas)"),
            Map.entry("EN_REVISION_FINANCIERA", "en revisión financiera"),
            Map.entry("EN_REGISTRO_CONTROL", "en registro y control"),
            Map.entry("FINALIZADA", "finalizada"),
            Map.entry("DEVUELTA", "devuelta para corrección"),
            Map.entry("RECHAZADA", "rechazada"));

    private final IKnowledgeSearchService searchService;
    private final IOpenRouterClient openRouterClient;
    private final AiProperties properties;
    private final IRequestMetricsService requestMetricsService;
    private final AssistantResponseCache responseCache;

    @Override
    @Transactional(readOnly = true)
    public AssistantResponse answer(AssistantRequest request) {
        List<KnowledgeSearchResult> results = searchService.search(request.question());
        boolean operationalQuestion = isOperationalQuestion(request.question());
        if (results.isEmpty() && !operationalQuestion) {
            // Sin evidencia validada ni pregunta operativa reconocible: no se consume el proveedor.
            return new AssistantResponse(NO_CONTEXT, false, List.of(),
                    "Comuníquese con la Coordinación Académica de la Sede Cali.");
        }
        if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiUnavailableException("El asistente de IA no está habilitado");
        }

        List<String> context = new ArrayList<>(results.stream().map(this::contextEntry).toList());
        List<AssistantSource> sources = new ArrayList<>(results.stream().map(this::toSource).toList());
        Instant now = Instant.now();
        if (operationalQuestion) {
            RequestMetricsResponse metrics = requestMetricsService.getRequestMetrics();
            context.add(operationalContextEntry(metrics));
            sources.add(operationalSource(now));
        }

        String cacheKey = cacheKey(request.question(), sources);
        String answer = responseCache.get(cacheKey)
                .orElseGet(() -> {
                    String generated = openRouterClient.answer(request.question(), context);
                    responseCache.put(cacheKey, generated);
                    return generated;
                });
        return new AssistantResponse(answer, true, sources, DISCLAIMER);
    }

    private boolean isOperationalQuestion(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return OPERATIONAL_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private String operationalContextEntry(RequestMetricsResponse metrics) {
        StringBuilder byState = new StringBuilder();
        metrics.byCurrentState().forEach((code, count) -> byState
                .append("- ").append(STATE_GLOSSARY.getOrDefault(code, code)).append(": ").append(count).append('\n'));
        String averageCycle = metrics.averageCycleHours() == null
                ? "sin datos suficientes"
                : "%.1f horas".formatted(metrics.averageCycleHours());
        return """
                FUENTE: Métricas operativas en vivo del sistema (agregadas, sin datos personales)
                Total de trámites: %d
                Finalizados: %d
                Devueltos en algún punto del flujo: %d
                Tiempo promedio de ciclo (finalizados): %s
                Distribución por estado actual:
                %s"""
                .formatted(metrics.total(), metrics.completed(), metrics.returnCount(), averageCycle, byState);
    }

    private AssistantSource operationalSource(Instant now) {
        return new AssistantSource(OPERATIONAL_SOURCE_ID, "Métricas operativas de Trámita", now.toString(),
                "/api/requests/metrics", null, null);
    }

    private String cacheKey(String question, List<AssistantSource> sources) {
        String sourceFingerprint = sources.stream()
                .map(source -> source.sourceId() + ":" + source.locator())
                .sorted()
                .reduce("", (a, b) -> a + "|" + b);
        return question.trim().toLowerCase(Locale.ROOT) + "::" + sourceFingerprint;
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

