package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.KnowledgeChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IKnowledgeChunkRepo extends JpaRepository<KnowledgeChunk, UUID> {

    List<KnowledgeChunk> findBySourceIdOrderByChunkOrderAsc(UUID sourceId);

    /** Recuperación léxica inicial: solo fuentes institucionales validadas. */
    @Query("""
            select chunk from KnowledgeChunk chunk
            join fetch chunk.source source
            where source.status = 'VALIDATED'
              and source.sourceClass = 'OFFICIAL_INSTITUTIONAL'
              and lower(chunk.content) like lower(concat('%', :term, '%')) escape '\\'
            order by source.title asc, chunk.chunkOrder asc
            """)
    List<KnowledgeChunk> searchValidated(@Param("term") String term);
}
