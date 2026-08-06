package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.Request;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IRequestRepo extends JpaRepository<Request, UUID> {

    /**
     * Localización por los dos datos con los que la Coordinación identifica un
     * trámite (FR-011): cédula por igualdad exacta o fragmento del nombre
     * case-insensitive. Un solo parámetro para ambos criterios (contrato).
     */
    @Query("""
            select r from Request r
            where r.studentDocument = :q
               or lower(r.studentName) like lower(concat('%', :q, '%'))
            order by r.createdAt desc
            """)
    List<Request> search(@Param("q") String q);
}
