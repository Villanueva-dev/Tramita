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
     * El timeline completo de un lote de solicitudes en UNA consulta (007, US2). La
     * bandeja deriva de él dos cosas por solicitud: el origen (la entrada de nacimiento,
     * FR-007) y desde cuándo espera (el {@code occurredAt} más reciente, research.md D3).
     *
     * Se trae el lote entero y se deriva en memoria en vez de una consulta agregada
     * {@code max(occurredAt) … group by}: es una consulta menos por bandeja, no exige un
     * tipo de proyección, y a 30–40 solicitudes por semestre las filas de más no pesan.
     * Si el volumen creciera, el candidato es la agregada, no una consulta por fila.
     *
     * Sin orden: el consumidor calcula máximos y busca {@code fromState} NULL, y el
     * orden no le sirve de nada.
     */
    @Query("""
            select l from RequestTransitionLog l
            join fetch l.actor
            where l.request.id in :requestIds
            """)
    List<RequestTransitionLog> findTimelinesOf(@Param("requestIds") Collection<UUID> requestIds);
}
