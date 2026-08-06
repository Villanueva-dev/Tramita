package com.uniremington.api.tramita.repo;

import com.uniremington.api.tramita.model.WorkflowDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Lectura de definiciones. Sin caché a propósito (research.md D10): cada
 * operación lee la configuración vigente de BD — cargar un trámite nuevo por
 * SQL lo hace operable de inmediato, sin reiniciar (SC-005).
 */
public interface IWorkflowDefinitionRepo extends JpaRepository<WorkflowDefinition, UUID> {

    /** La definición VIGENTE de un trámite = la de mayor version por code (research.md D2). */
    Optional<WorkflowDefinition> findTopByCodeOrderByVersionDesc(String code);

    /** Las vigentes de todos los trámites — insumo del formulario de registro (US1). */
    @Query("""
            select d from WorkflowDefinition d
            where d.version = (select max(d2.version) from WorkflowDefinition d2 where d2.code = d.code)
            order by d.name
            """)
    List<WorkflowDefinition> findAllCurrent();
}
