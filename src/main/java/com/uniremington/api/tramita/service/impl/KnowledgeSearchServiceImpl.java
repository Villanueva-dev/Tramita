package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.dto.KnowledgeSearchResult;
import com.uniremington.api.tramita.model.KnowledgeChunk;
import com.uniremington.api.tramita.repo.IKnowledgeChunkRepo;
import com.uniremington.api.tramita.service.IKnowledgeSearchService;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeSearchServiceImpl implements IKnowledgeSearchService {

    private static final int MAX_RESULTS = 8;
    private static final int MAX_TERM_LENGTH = 200;

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
        String escapedTerm = normalizedTerm
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return chunkRepo.searchValidated(escapedTerm).stream()
                .limit(MAX_RESULTS)
                .map(this::toResult)
                .toList();
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
