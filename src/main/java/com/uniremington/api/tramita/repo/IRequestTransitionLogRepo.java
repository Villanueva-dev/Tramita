package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.RequestTransitionLog;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Timeline de una solicitud (FR-008). Solo lectura + save: no existe operación
 * de edición ni borrado en la aplicación (FR-007) — y el trigger de BD lo
 * garantiza aunque alguien la agregue por error (research.md D8).
 */
public interface IRequestTransitionLogRepo extends JpaRepository<RequestTransitionLog, Long> {

    /** Orden cronológico con desempate por id monótono (research.md D11). */
    List<RequestTransitionLog> findByRequestIdOrderByOccurredAtAscIdAsc(UUID requestId);

    /**
     * Las entradas de nacimiento —{@code fromState} NULL, research.md D7 de la 002— de
     * un lote de solicitudes, con su actor ya cargado. Sirve para derivar el origen de
     * cada entrada de la bandeja (007, FR-007) en UNA consulta, no una por fila.
     */
    @Query("""
            select l from RequestTransitionLog l
            join fetch l.actor
            where l.request.id in :requestIds
              and l.fromState is null
            """)
    List<RequestTransitionLog> findBirthEntries(@Param("requestIds") Collection<UUID> requestIds);
}
