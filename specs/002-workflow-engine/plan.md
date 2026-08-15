# Implementation Plan: Motor de workflow configurable con timeline de auditoría

**Branch**: `002-workflow-engine` | **Date**: 2026-08-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-workflow-engine/spec.md`

## Summary

Motor genérico de workflow para trámites académicos (SP1) con timeline de auditoría
inmutable como subproducto (SP6). La definición de cada trámite —estados, transiciones,
responsable de cada paso— vive en **base de datos como configuración versionada**: agregar
o modificar un trámite es un `INSERT`, no un deploy (FR-001, SC-005). Cada solicitud queda
atada a la versión de la definición con la que nació (FR-009). El motor expone cuatro
operaciones sobre solicitudes (registrar, avanzar, localizar, consultar timeline) detrás de
la sesión autenticada de la feature `001` (FR-012).

El diseño técnico se cerró el 2026-08-05 (registro en engram #866) tras evaluar y descartar
enums en código y un motor BPMN embebido; este plan lo baja a esquema, contratos y
estructura de código. **Esta feature no evalúa reglas de negocio** (SP2 → feature `003`):
el motor valida legalidad de transiciones contra la definición, no decisiones de negocio.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: las del chasis existente — Spring Boot 4.0.7
(`spring-boot-starter-webmvc`, `-security`, `-data-jpa`, `-validation`), Flyway
(`flyway-database-postgresql`), driver PostgreSQL, Lombok. **Esta feature no agrega
ninguna dependencia nueva**: el motor es JPA + SQL + lógica propia (esa es la tesis — OE2
dice *desarrollar* un motor, no integrar uno). springdoc-openapi sigue diferido por
incompatibilidad con Boot 4 (misma razón que en `001`).

**Storage**: PostgreSQL (schema por Flyway, Hibernate `validate`). Dos migraciones nuevas:
`V2.0.0__Create_workflow_tables.sql` (5 tablas + trigger de inmutabilidad del log) y
`V2.1.0__Seed_workflow_definitions.sql` (semilla de los dos trámites). Las definiciones se
leen de BD en cada operación, **sin caché** — hace demostrable SC-005 en vivo (cargar un
trámite nuevo y operarlo sin reiniciar).

**Testing**: JUnit 5, `spring-security-test`, Testcontainers (módulo `postgresql`). Unit
para el algoritmo del motor (transición legal/ilegal/estado final/nota obligatoria);
integración para el flujo completo de ambos trámites, la inmutabilidad del timeline y los
escenarios 401 de FR-012.

**Target Platform**: Servidor Linux (JAR ejecutable), mismo perfil que `001`.

**Project Type**: Aplicación web (backend; el SPA consume el contrato REST desde otro origen).

**Performance Goals**: No aplica presión de performance: SC-001 es escala humana (<1 min
con timeline en pantalla). Sin caché ni optimizaciones especulativas.

**Constraints**: toda operación exige la sesión autenticada de `001` (FR-012) y CSRF en las
mutaciones (patrón `XSRF-TOKEN` ya operativo); errores RFC 9457 vía `GlobalExceptionHandler`
existente; timeline solo-INSERT reforzado a nivel de BD (FR-007/SC-002); concurrencia sobre
`request` resuelta con locking optimista `@Version` → 409 (edge case de avances simultáneos).

**Scale/Scope**: 1 actor autenticado, 2 definiciones en la semilla, decenas de solicitudes
por semestre. 5 tablas nuevas, 2 controllers, 2 services.

## Constitution Check

*GATE: debe pasar antes de Phase 0. Re-evaluado tras Phase 1 (design).*

| Principio | Cumplimiento en este plan |
|-----------|---------------------------|
| **I. Simplicidad (KISS+YAGNI)** | ✅ 5 tablas, no 6: `workflow_parameter` y `guard_key` (diseño #866) se difieren a `003` porque en `002` no existe regla de negocio que los consuma — criterio de corte del propio diseño: *"YAGNI aplica a lo barato de agregar después"*; entrarán con una migración trivial cuando SP2 exista. Sin caché, sin N-roles, sin N-guardas, sin acciones on-enter. El versionado de definiciones sí entra día uno: retrofitearlo es caro (research D2). |
| **II. Arquitectura por capas** | ✅ Las piezas nuevas caen en las capas existentes: `controller/`, `dto/`, `model/`, `repo/`, `service/` + `service/impl/`, `shared/exception/`. Interfaces con prefijo `I`, servicios expuestos por interface. Ningún paquete nuevo. |
| **III. Seguridad por defecto** | ✅ Todos los endpoints detrás de la sesión de `001` (FR-012, escenarios 401 en la spec); CSRF activo en mutaciones; DTOs en la frontera (nunca entities); validación autoritativa en backend; datos personales minimizados a nombre + cédula (Ley 1581, supuesto de la spec). |
| **IV. Decisiones defendibles y trazables** | ✅ Cada decisión en `research.md` con trade-off y fuente (docs oficiales verificadas vía Context7 donde aplica); el diseño de fondo trae su registro de alternativas descartadas (engram #866). Trazabilidad requisito → tabla → endpoint en `data-model.md` y `contracts/`. |
| **V. Testing del comportamiento sensible** | ✅ Se testea el motor (validación de transiciones, nota obligatoria, estado final), la inmutabilidad del log y la frontera de autenticación. No se testean getters ni CRUD trivial. |
| **Restricciones tecnológicas** | ✅ Stack fijo sin dependencias nuevas; Flyway gestiona el schema; RFC 9457; servicios por interface; Class/QF siguen siendo cajas negras (el motor registra decisiones externas, no las ejecuta). |

**Resultado**: PASS. Sin violaciones → *Complexity Tracking* vacío.

**Re-evaluación post-Phase 1**: PASS — el diseño final mantiene 5 tablas, cero dependencias
nuevas y ningún paquete fuera de la estructura por capas.

## Project Structure

### Documentation (this feature)

```text
specs/002-workflow-engine/
├── plan.md              # Este archivo
├── research.md          # Phase 0 — decisiones + fuentes
├── data-model.md        # Phase 1 — 5 tablas, entidades, semilla de los dos trámites
├── quickstart.md        # Phase 1 — cómo correr y demostrar el motor (incl. SC-005 en vivo)
├── contracts/
│   └── openapi.yaml      # Phase 1 — contrato REST de /api/workflow-definitions y /api/requests
├── checklists/
│   └── requirements.md   # (pre-existente, gate 16/16 cerrado)
└── tasks.md             # Phase 2 — lo genera /speckit-tasks (NO este comando)
```

### Source Code (repository root)

Se agregan clases a la estructura package-by-layer existente (constitución v2.1.0 §II);
no se crea ningún paquete nuevo de primer nivel.

```text
src/main/java/com/uniremington/api/tramita/
├── controller/
│   ├── WorkflowDefinitionController.java  # GET /api/workflow-definitions
│   └── RequestController.java             # POST/GET /api/requests, POST .../transitions, GET .../timeline
├── dto/
│   ├── CreateRequestBody.java             # entrada: definitionCode, studentName, studentDocument
│   ├── AdvanceRequestBody.java            # entrada: targetStateCode, note (condicional)
│   ├── RequestResponse.java               # detalle: estado actual + availableTransitions
│   ├── RequestSummaryResponse.java        # resultado de localización (FR-011)
│   ├── TimelineEntryResponse.java         # entrada del timeline (FR-008)
│   └── WorkflowDefinitionResponse.java    # code, name, version vigente
├── model/
│   ├── WorkflowDefinition.java            # (code, version) UNIQUE — la versión es la identidad
│   ├── WorkflowState.java
│   ├── WorkflowTransition.java
│   ├── Request.java                       # @Version (locking optimista)
│   └── RequestTransitionLog.java          # solo INSERT — timeline SP6
├── repo/
│   ├── IWorkflowDefinitionRepo.java
│   ├── IRequestRepo.java
│   └── IRequestTransitionLogRepo.java
├── service/
│   ├── IWorkflowDefinitionService.java
│   └── IRequestService.java               # register / advance / search / getTimeline
├── service/impl/
│   ├── WorkflowDefinitionServiceImpl.java
│   └── RequestServiceImpl.java            # EL MOTOR: advance() = buscar transición → validar
│                                          #   → exigir nota si aplica → log → mover estado.
│                                          #   No conoce "adición" ni "créditos" (research D1).
└── shared/exception/
    ├── ResourceNotFoundException.java     # 404 problem+json
    ├── IllegalTransitionException.java    # 409 — transición no definida / estado final
    └── (GlobalExceptionHandler ampliado: 404, 409, ObjectOptimisticLockingFailureException → 409)

src/main/resources/db/migration/
├── V2.0.0__Create_workflow_tables.sql     # 5 tablas + trigger de inmutabilidad del log
└── V2.1.0__Seed_workflow_definitions.sql  # adición (cerrada) + novedad (provisional, data-model)

src/test/java/com/uniremington/api/tramita/
├── service/impl/RequestServiceImplTest.java   # unit — algoritmo del motor
└── controller/RequestControllerIT.java        # IT Testcontainers — flujo completo, 401, inmutabilidad
```

**Structure Decision**: mismas capas y mismo módulo Maven que `001`; la feature entera son
clases nuevas dentro de la estructura vigente. La correspondencia con los contenedores C4
se documenta en los diagramas (trade-off aceptado en la constitución §II).

## Complexity Tracking

> Sin violaciones a la constitución. Tabla intencionalmente vacía.
