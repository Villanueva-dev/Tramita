package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.Request;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IRequestRepo extends JpaRepository<Request, UUID> {

    /**
     * Localización por los dos datos con los que la Coordinación identifica un
     * trámite (FR-011): cédula por igualdad exacta o fragmento del nombre
     * case-insensitive. Un solo parámetro para ambos criterios (contrato).
     *
     * El patrón llega ya escapado desde el servicio y la consulta declara
     * {@code escape '\'}: sin eso, buscar «%» hace match con TODAS las filas y
     * el endpoint devuelve el padrón completo —nombre y cédula de cada
     * estudiante— cuando su contrato es localizar UN trámite. La feature
     * persiste datos personales bajo el principio de minimización (Ley 1581 de
     * 2012), así que un volcado total no es solo un bug de búsqueda.
     */
    @Query("""
            select r from Request r
            where r.studentDocument = :q
               or lower(r.studentName) like lower(concat('%', :pattern, '%')) escape '\\'
            order by r.createdAt desc
            """)
    List<Request> search(@Param("q") String q, @Param("pattern") String escapedPattern);

    /**
     * Las solicitudes más recientes, sin criterio (004, FR-012/FR-013). Es la
     * consulta que la búsqueda de arriba deliberadamente NO ofrece: allá un patrón
     * sin escapar devolvería el padrón completo, y acá el listado total es el
     * contrato — lo que evita el volcado de datos personales no es negarse a
     * listar, sino que {@code InboxEntryResponse} no lleve documento (D8).
     *
     * El {@link Limit} es obligatorio en la firma y no un default del repositorio:
     * una consulta sin cota podría convertirse en un volcado el día que el volumen
     * crezca, y quien la llame debe decidir explícitamente cuánto pide.
     *
     * ⚠️ REEMPLAZADA por la feature 007 (research.md D7): la bandeja deja de ser
     * «las más recientes» y pasa a ser «las que esperan a un responsable». Esta
     * consulta se conserva hasta que su único llamador cambie; si queda sin
     * llamadores, se elimina en esa misma entrega. El contrato de la 004 ya
     * declara la enmienda como no aditiva.
     */
    List<Request> findAllByOrderByCreatedAtDesc(Limit limit);
}
