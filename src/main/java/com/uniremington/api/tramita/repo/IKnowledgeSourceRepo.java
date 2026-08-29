package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.KnowledgeSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IKnowledgeSourceRepo extends JpaRepository<KnowledgeSource, UUID> {

    Optional<KnowledgeSource> findBySourceIdAndVersionLabel(String sourceId, String versionLabel);

    List<KnowledgeSource> findByStatusAndSourceClassOrderByTitleAscVersionLabelDesc(
            String status, String sourceClass);
}
