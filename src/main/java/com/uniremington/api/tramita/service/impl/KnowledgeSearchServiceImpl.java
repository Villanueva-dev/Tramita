package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.KnowledgeSearchResult;
import com.uniremington.api.tramita.model.KnowledgeChunk;
import com.uniremington.api.tramita.repo.IKnowledgeChunkRepo;
import com.uniremington.api.tramita.service.IKnowledgeSearchService;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeSearchServiceImpl implements IKnowledgeSearchService {

    private static final int MAX_RESULTS = 8;
    private static final int MAX_TERM_LENGTH = 200;
        private static final List<String> STOP_WORDS = List.of(
            "como", "cómo", "cual", "cuál", "cuales", "cuáles", "de", "del", "el", "en", "es", "la", "las", "lo", "los", "para", "por", "que", "qué", "se", "una", "un", "y");

    private final IKnowledgeChunkRepo chunkRepo;

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeSearchResult> search(String term) {
        String normalizedTerm = normalize(term);
        if (normalizedTerm.length() < 2) {
            throw new UnprocessableRequestException("El término debe tener al menos 2 caracteres");
        }
        if (normalizedTerm.length() > MAX_TERM_LENGTH) {
            throw new UnprocessableRequestException("El término no puede superar 200 caracteres");
        }

        // El escape evita que %, _ o la barra inversa alteren el patrón LIKE del repositorio.
        Map<java.util.UUID, KnowledgeChunk> chunksById = new LinkedHashMap<>();
        significantTerms(normalizedTerm).forEach(searchTerm -> chunkRepo.searchValidated(escape(searchTerm))
            .forEach(chunk -> chunksById.putIfAbsent(chunk.getId(), chunk)));
        return chunksById.values().stream()
                .limit(MAX_RESULTS)
                .map(this::toResult)
                .toList();
    }

        private List<String> significantTerms(String normalizedTerm) {
        List<String> terms = Arrays.stream(normalizedTerm.split("[^\\p{L}\\p{N}_%\\\\]+"))
            .filter(term -> term.length() >= 3)
            .filter(term -> !STOP_WORDS.contains(term))
            .distinct()
            .toList();
        return terms.isEmpty() ? List.of(normalizedTerm) : terms;
        }

        private String escape(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }

    private String normalize(String term) {
        return term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
    }

    private KnowledgeSearchResult toResult(KnowledgeChunk chunk) {
        var source = chunk.getSource();
        return new KnowledgeSearchResult(
                chunk.getId(),
                chunk.getContent(),
                source.getSourceId(),
                source.getTitle(),
                source.getVersionLabel(),
                chunk.getLocator(),
                chunk.getSectionLabel(),
                chunk.getPageNumber());
    }
}
