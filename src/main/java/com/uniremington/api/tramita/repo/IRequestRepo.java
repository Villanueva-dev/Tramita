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
     * La bandeja de trabajo (007, FR-001, research.md D1): las solicitudes cuyo estado
     * actual ofrece, en la definición con la que nacieron, alguna transición cuyo
     * {@code responsible} es el pedido. No hay columna que lo almacene: se deriva de
     * la configuración, de modo que incorporar un área nueva no toca este código
     * (§VI). El responsable llega por parámetro y nunca como literal (D2).
     *
     * {@code distinct} porque un estado puede tener varias salidas con el mismo
     * responsable —EN_COORDINACION tiene dos: avanzar y devolver— y el join las
     * multiplicaría. Un estado final no tiene salidas, así que un trámite cerrado
     * queda fuera sin filtrarlo aparte; eso lo garantiza el invariante de
     * configuración que prueba WorkflowGenericityIT, no la base.
     *
     * Orden por radicación ascendente: es el corte bajo la cota (D8). El orden por
     * espera (D5) lo aplica el servicio sobre el resultado a partir de la US2.
     *
     * El {@link Limit} sigue siendo obligatorio en la firma, por la misma razón que
     * en la consulta de la 004 a la que reemplaza: quien llama decide cuánto pide;
     * una consulta sin cota se vuelve un volcado el día que el volumen crezca. Lo que
     * evita el volcado de datos personales no es negarse a listar, sino que
     * {@code InboxEntryResponse} no lleve documento (§III).
     */
    @Query("""
            select distinct r from Request r
            join r.definition d
            join d.transitions t
            where t.fromState = r.currentState
              and t.responsible = :responsible
            order by r.createdAt asc
            """)
    List<Request> findPendingFor(@Param("responsible") String responsible, Limit limit);
}
