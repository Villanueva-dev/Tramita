package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.KnowledgeSearchResult;
import java.util.List;

public interface IKnowledgeSearchService {

    /** Recupera fragmentos citables solo de fuentes institucionales validadas. */
    List<KnowledgeSearchResult> search(String term);
}
