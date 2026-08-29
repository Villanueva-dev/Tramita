package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.Request;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IRequestRepo extends JpaRepository<Request, UUID> {

    List<Request> findAllByOrderByCreatedAtDesc();

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
}
