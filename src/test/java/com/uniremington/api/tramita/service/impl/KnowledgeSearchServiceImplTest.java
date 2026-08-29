package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.model.KnowledgeChunk;
import com.uniremington.api.tramita.repo.IKnowledgeChunkRepo;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeSearchServiceImplTest {

    private final IKnowledgeChunkRepo chunkRepo = mock(IKnowledgeChunkRepo.class);
    private final KnowledgeSearchServiceImpl service = new KnowledgeSearchServiceImpl(chunkRepo);

    @Test
    void rejectsMissingOrTooShortTerm() {
        assertThatExceptionOfType(UnprocessableRequestException.class)
                .isThrownBy(() -> service.search(" "));
        assertThatExceptionOfType(UnprocessableRequestException.class)
                .isThrownBy(() -> service.search(null));
    }

    @Test
    void rejectsTermLongerThanLimit() {
        assertThatExceptionOfType(UnprocessableRequestException.class)
                .isThrownBy(() -> service.search("a".repeat(201)));
    }

    @Test
    void escapesLikeWildcardsAndLimitsResults() {
        when(chunkRepo.searchValidated(anyString())).thenReturn(List.of());

        assertThat(service.search(" Reglamento_% ")).isEmpty();

        verify(chunkRepo).searchValidated("reglamento\\_\\%");
    }
}
