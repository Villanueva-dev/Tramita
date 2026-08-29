package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.KnowledgeSearchResult;
import com.uniremington.api.tramita.service.IKnowledgeSearchService;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Recuperador documental protegido para pruebas y para la futura capa RAG. */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Validated
public class KnowledgeController {

    private final IKnowledgeSearchService searchService;

    @GetMapping("/search")
    public List<KnowledgeSearchResult> search(
            @RequestParam @Size(min = 2, max = 200) String term) {
        return searchService.search(term);
    }
}
