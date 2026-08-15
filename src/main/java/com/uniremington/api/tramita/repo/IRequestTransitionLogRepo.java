package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.RequestTransitionLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Timeline de una solicitud (FR-008). Solo lectura + save: no existe operación
 * de edición ni borrado en la aplicación (FR-007) — y el trigger de BD lo
 * garantiza aunque alguien la agregue por error (research.md D8).
 */
public interface IRequestTransitionLogRepo extends JpaRepository<RequestTransitionLog, Long> {

    /** Orden cronológico con desempate por id monótono (research.md D11). */
    List<RequestTransitionLog> findByRequestIdOrderByOccurredAtAscIdAsc(UUID requestId);
}
