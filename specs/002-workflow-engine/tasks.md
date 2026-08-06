# Tasks: Motor de workflow configurable con timeline de auditoría

**Input**: Design documents from `/specs/002-workflow-engine/`

**Prerequisites**: plan.md, spec.md, research.md (D1–D11), data-model.md, contracts/openapi.yaml, quickstart.md

**Tests**: SÍ — TDD sobre el comportamiento sensible (constitución, Principio V): el algoritmo
del motor, la inmutabilidad del timeline y la frontera de autenticación (FR-012). No se
testea CRUD trivial ni getters. **Regla de RED** (memoria del proyecto): evidenciar el rojo
con `./mvnw clean test-compile` / `./mvnw clean test` — el build incremental de Maven puede
dar un BUILD SUCCESS falso («Nothing to compile»).

**Organization**: por user story (US1–US5 de la spec), en orden de prioridad P1 → P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizable (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1–US5 — solo en las fases de user story
- Rutas bajo `src/main/java/com/uniremington/api/tramita/` abreviadas como `…/tramita/`

---

## Phase 1: Setup

**Purpose**: línea base verificable antes de tocar nada. La feature no agrega dependencias
ni configuración nueva (plan.md) — el chasis ya está.

- [X] T001 Establecer línea base: `docker start tramita-postgres && ./mvnw clean verify` en verde (suite de `001` intacta antes de empezar)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: esquema, entidades, repos y manejo de errores que TODAS las stories necesitan.

**⚠️ CRITICAL**: ninguna user story arranca antes de cerrar esta fase.

- [X] T002 Migración `V2.0.0__Create_workflow_tables.sql` en `src/main/resources/db/migration/`: 5 tablas según data-model.md (`workflow_definition` con `UNIQUE(code,version)`, `workflow_state`, `workflow_transition` con `responsible`+`requires_note`, `request` con `version` de locking, `request_transition_log` con PK `BIGSERIAL`), índices de búsqueda (`student_document`, `lower(student_name)`, `(request_id, occurred_at, id)`) y trigger `trg_timeline_immutable` (BEFORE UPDATE OR DELETE → RAISE EXCEPTION)
- [X] T003 Migración `V2.1.0__Seed_workflow_definitions.sql`: semilla de adición de créditos v1 (CERRADA — estados y transiciones exactos de data-model.md, devoluciones con `requires_note=true`) y novedad de notas v1 (**PROVISIONAL** — comentario SQL que remita a la reunión con la Coordinación; si cambia antes de correr en ambientes compartidos, se edita esta migración, si no, se carga v2)
- [X] T004 [P] Entidad `WorkflowDefinition` (con `@OneToMany` states/transitions LAZY, agregado de solo lectura) en `…/tramita/model/WorkflowDefinition.java`
- [X] T005 [P] Entidad `WorkflowState` en `…/tramita/model/WorkflowState.java`
- [X] T006 [P] Entidad `WorkflowTransition` en `…/tramita/model/WorkflowTransition.java`
- [X] T007 [P] Entidad `Request` con `@Version private long version;` en `…/tramita/model/Request.java`
- [X] T008 [P] Entidad `RequestTransitionLog` (sin setters de negocio; se construye completa y se persiste una vez) en `…/tramita/model/RequestTransitionLog.java`
- [X] T009 [P] Repos `IWorkflowDefinitionRepo`, `IRequestRepo`, `IRequestTransitionLogRepo` en `…/tramita/repo/`
- [X] T010 [P] Excepciones `ResourceNotFoundException` (404) e `IllegalTransitionException` (409) en `…/tramita/shared/exception/`
- [X] T011 Ampliar `GlobalExceptionHandler` en `…/tramita/shared/exception/GlobalExceptionHandler.java`: 404, 409 (`IllegalTransitionException` y `ObjectOptimisticLockingFailureException` — research D6) en `application/problem+json`
- [X] T012 Checkpoint foundational: `./mvnw clean verify` — Flyway aplica V2.0.0/V2.1.0 en Testcontainers e Hibernate (`ddl-auto: validate`) valida el schema contra las entidades

**Checkpoint**: base de datos y modelo listos — las user stories pueden arrancar.

---

## Phase 3: User Story 1 — Registrar una solicitud de trámite (Priority: P1) 🎯 MVP

**Goal**: la Coordinación registra solicitudes con nombre + cédula; nacen en el estado
inicial de la definición **vigente** de su trámite, con la primera entrada del timeline.

**Independent Test**: registrar una solicitud de cada trámite y verificar que cada una nace
en el estado inicial de su propia definición (spec, US1).

### Tests for User Story 1 (RED primero)

- [X] T013 [P] [US1] Unit RED en `src/test/java/…/tramita/service/impl/RequestServiceImplTest.java`: `register()` crea en el estado inicial de SU definición y escribe la entrada inicial del log (`from_state` NULL, autor, fecha — research D7); tipo de trámite inexistente → `UnprocessableRequestException` (422); con dos versiones de una definición usa la VIGENTE (mayor `version`)
- [X] T014 [P] [US1] IT RED en `src/test/java/…/tramita/controller/RequestControllerIT.java`: `POST /api/requests` → 201 + `Location` + body con `currentState` inicial y `availableTransitions`; sin sesión → 401 sin registrar nada (FR-012, escenario US1-4); `definitionCode` inexistente → 422 problem+json
- [X] T015 [P] [US1] IT RED en `src/test/java/…/tramita/controller/WorkflowDefinitionControllerIT.java`: `GET /api/workflow-definitions` → 200 con los dos trámites vigentes de la semilla; sin sesión → 401

### Implementation for User Story 1

- [X] T016 [P] [US1] DTOs en `…/tramita/dto/`: `CreateRequestBody` (validación Bean Validation: campos obligatorios, longitudes del contrato), `WorkflowDefinitionResponse`, `RequestResponse` (con `State` y `AvailableTransition` anidados según openapi.yaml)
- [X] T017 [US1] `IWorkflowDefinitionService` + `WorkflowDefinitionServiceImpl` (vigentes = mayor `version` por `code`; **sin caché** — research D10) en `…/tramita/service/` y `…/tramita/service/impl/`
- [X] T018 [US1] `IRequestService.register()` + `RequestServiceImpl` en `…/tramita/service/impl/RequestServiceImpl.java`: crea la solicitud atada a la definición vigente (FR-009), inserta la entrada inicial del log, mapea `RequestResponse` con `availableTransitions` derivadas de la definición
- [X] T019 [US1] `WorkflowDefinitionController` (`GET /api/workflow-definitions`) en `…/tramita/controller/WorkflowDefinitionController.java`
- [X] T020 [US1] `RequestController` (`POST /api/requests` → 201 + `Location`) en `…/tramita/controller/RequestController.java`
- [X] T021 [US1] GREEN: `./mvnw clean verify` — T013–T015 en verde sin tocar los tests

**Checkpoint**: US1 funcional e independientemente testeable — se pueden registrar
solicitudes de ambos trámites.

---

## Phase 4: User Story 2 — Avanzar una solicitud por sus estados (Priority: P1)

**Goal**: el motor — `advance()` valida contra la definición de la solicitud, registra en
el timeline y mueve el estado. Sin literales de negocio en el código.

**Independent Test**: con una solicitud registrada, una transición definida cambia el
estado; una no definida se bloquea con el estado intacto (spec, US2).

### Tests for User Story 2 (RED primero)

> El algoritmo del motor es UNA unidad: sus cuatro validaciones (transición definida,
> estado no final, nota obligatoria, existencia) se testean juntas aquí. FR-014
> (`requires_note`) se ejercita a nivel de negocio en US5 con la semilla real.

- [X] T022 [P] [US2] Unit RED en `src/test/java/…/tramita/service/impl/RequestServiceImplTest.java`: transición legal → estado cambia y el log registra autor+fecha (FR-005); transición no definida → `IllegalTransitionException`, estado intacto, **cero** entradas nuevas en el log (FR-004 + edge case de la spec); estado final → `IllegalTransitionException` (US2-4); transición con `requires_note` sin nota → `UnprocessableRequestException` 422 (FR-014); con nota → aplica y la nota queda en el log; solicitud inexistente → `ResourceNotFoundException`
- [X] T023 [P] [US2] IT RED en `src/test/java/…/tramita/controller/RequestControllerIT.java`: `POST /api/requests/{id}/transitions` recorre la semilla real de adición (REGISTRADA → EN_FACULTAD → …); transición ilegal → 409 problem+json y el estado no cambia; sin sesión → 401 y **sin** entrada de timeline (FR-012, escenario US2-5); `ObjectOptimisticLockingFailureException` mapeada a 409 por el handler (research D6 — se testea el mapping, no la carrera)

### Implementation for User Story 2

- [X] T024 [P] [US2] DTO `AdvanceRequestBody` (`targetStateCode` obligatorio, `note` opcional) en `…/tramita/dto/AdvanceRequestBody.java`
- [X] T025 [US2] El motor: `IRequestService.advance()` en `…/tramita/service/impl/RequestServiceImpl.java` — (1) carga la solicitud y SU definición (FR-009), (2) busca la transición desde el estado actual en esa definición o falla 409, (3) exige nota si `requires_note` (FR-014), (4) inserta la entrada del log, (5) mueve `current_state_id`. **Prohibido**: mencionar trámites, estados o valores concretos (la verificación es T035)
- [X] T026 [US2] `RequestController`: `POST /api/requests/{id}/transitions` → 200 con la solicitud actualizada, en `…/tramita/controller/RequestController.java`
- [X] T027 [US2] GREEN: `./mvnw clean verify` — T022–T023 en verde sin tocar los tests

**Checkpoint**: el corazón de SP1 late — registrar y avanzar funcionan sobre configuración.

---

## Phase 5: User Story 3 — Consultar el timeline de auditoría (Priority: P1)

**Goal**: SP6 — localizar una solicitud por nombre/cédula y leer su historia completa en
orden cronológico, con autor, responsable del paso y observaciones. Inmutable de verdad.

**Independent Test**: sobre una solicitud avanzada varias veces, el timeline muestra orden,
fechas y autores; no existe operación de edición/borrado y el trigger rechaza mutación
directa (spec US3 + SC-002).

### Tests for User Story 3 (RED primero)

- [ ] T028 [P] [US3] IT RED en `src/test/java/…/tramita/controller/RequestControllerIT.java`: `GET /api/requests/{id}/timeline` → orden cronológico con autor y `responsible` derivado de la definición (FR-006, entrada de nacimiento sin `responsible`); `GET /api/requests?search=` localiza por cédula exacta y por fragmento de nombre case-insensitive (FR-011); `GET /api/requests/{id}` → detalle con `availableTransitions`; las tres sin sesión → 401 (FR-012, escenario US3-4)
- [ ] T029 [P] [US3] IT RED de inmutabilidad en `src/test/java/…/tramita/repo/TimelineImmutabilityIT.java`: `UPDATE` y `DELETE` directos por `JdbcTemplate` sobre `request_transition_log` → excepción del trigger (SC-002: la garantía sobrevive al acceso directo a BD)

### Implementation for User Story 3

- [ ] T030 [P] [US3] DTOs `TimelineEntryResponse` y `RequestSummaryResponse` según openapi.yaml en `…/tramita/dto/`
- [ ] T031 [US3] `IRequestService`: `getTimeline()` (un SELECT ordenado por `occurred_at, id`; `responsible` por join estable contra la definición — research D5) y `search()` en `…/tramita/service/impl/RequestServiceImpl.java`
- [ ] T032 [US3] `RequestController`: `GET /api/requests/{id}`, `GET /api/requests?search=`, `GET /api/requests/{id}/timeline` en `…/tramita/controller/RequestController.java`
- [ ] T033 [US3] GREEN: `./mvnw clean verify` — T028–T029 en verde sin tocar los tests

**Checkpoint**: P1 completo — SP1 + SP6 operativos de punta a punta para adición de créditos.

---

## Phase 6: User Story 4 — Dos trámites de profundidad distinta, un motor (Priority: P2)

**Goal**: demostrar la tesis — la diferencia entre adición y novedad es configuración.
**No hay código de producción nuevo en esta fase**: si aparece un `if` por trámite, US4 falló.

**Independent Test**: operar los dos trámites con sus definiciones y un trámite DEMO cargado
solo con SQL, sin cambios en el sistema (spec, US4).

- [ ] T034 [US4] IT en `src/test/java/…/tramita/controller/WorkflowGenericityIT.java`: recorrido completo de novedad de notas con la semilla (REGISTRADA → EN_PREPARACION → … → FINALIZADA); devolución de novedad retorna a EN_PREPARACION; el cierre por rechazo NO existe en novedad → 409 (US4-3, FR-015) y SÍ en adición → RECHAZADA
- [ ] T035 [US4] IT SC-005 en `src/test/java/…/tramita/controller/WorkflowGenericityIT.java`: insertar por `JdbcTemplate` una definición `DEMO` v1 en runtime (sin migración, sin restart) y registrar + avanzar + cerrar una solicitud DEMO por la API — el motor la orquesta sin ningún cambio de código (US4-2)
- [ ] T036 [US4] Verificación de genericidad: `rg -n 'ADICION|NOVEDAD|DEVUELTA|RECHAZADA|EN_FACULTAD' src/main/java/` → **cero** ocurrencias (los literales solo viven en `V2.1.0` y en los tests); registrar comando y resultado en la línea `Verificado:` del commit

**Checkpoint**: la tesis del motor genérico queda demostrada y automatizada.

---

## Phase 7: User Story 5 — Devolución con motivo y cierre por rechazo (Priority: P2)

**Goal**: los caminos de excepción reales del negocio (re-trabajo E2 del árbol de
problemas) sobre el motor ya construido — el mecanismo (`requires_note`, estados finales)
se implementó en US2; aquí se valida contra la configuración real y de punta a punta.

**Independent Test**: devolución con motivo vuelve al estado de corrección conservando el
timeline; rechazo cierra en el trámite que lo admite y se bloquea en el que no (spec, US5).

- [ ] T037 [US5] IT en `src/test/java/…/tramita/controller/RequestControllerIT.java`: los 5 escenarios de aceptación de US5 contra la semilla — devolución con motivo → estado de corrección + entrada con `note` (US5-1); sin motivo → 422 (US5-2); rechazo definitivo en adición → `RECHAZADA` final sin más transiciones (US5-3); intento de rechazo en novedad → 409 (US5-4); tras corregir y reavanzar, el timeline conserva tramo previo + devolución + nuevo avance sin sobrescribir (US5-5, SC-007: las devoluciones son contables con su motivo)
- [ ] T038 [US5] GREEN: `./mvnw clean verify` — si algún escenario nace en verde (el motor ya lo cubre), sabotearlo momentáneamente para evidenciar que el test discrimina (tests honestos, no amañados), luego restaurar

**Checkpoint**: todas las user stories funcionales — la spec completa está implementada.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T039 [P] Actualizar `README.md`: sección del motor de workflow (endpoints, semilla, cómo demostrar SC-005 en vivo)
- [ ] T040 Ejecutar el guion completo de `quickstart.md` contra la app corriendo (perfil dev) — validación manual de SC-001…SC-007, incluida la demo del trámite DEMO por `psql` y el rechazo del trigger
- [ ] T041 Suite final: `docker start tramita-postgres && ./mvnw clean verify` + revisión de que el comportamiento sensible quedó cubierto (motor, inmutabilidad, FR-012) sin perseguir cobertura nominal (Principio V)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (P1)**: sin dependencias
- **Foundational (P2)**: tras Setup — **BLOQUEA todas las stories** (T002 → T003; T004–T010 en paralelo tras T002; T011 tras T010; T012 al final)
- **US1 (P3)**: tras Foundational — sin dependencias de otras stories
- **US2 (P4)**: tras US1 (necesita solicitudes registradas y `RequestController`)
- **US3 (P5)**: tras US2 (el timeline con varios tramos exige avances previos)
- **US4 (P6)**: tras US3 (demuestra sobre el motor completo) — solo tests
- **US5 (P7)**: tras US2 (usa `advance()` completo); independiente de US4
- **Polish (P8)**: tras todas las stories

### Within Each User Story

- Tests RED → implementación → GREEN (`./mvnw clean verify`, nunca el incremental)
- DTOs [P] → services → controllers
- Checkpoint antes de pasar a la siguiente story

### Parallel Opportunities

- T004–T010 (entidades, repos, excepciones): 7 tareas en paralelo tras T002
- T013–T015 (tests RED de US1): en paralelo — archivos distintos
- T022–T023, T028–T029: pares RED en paralelo dentro de su story
- US4 y US5 podrían repartirse entre dos personas tras US3 (archivos de test distintos)

---

## Implementation Strategy

**MVP = P1 completo (Fases 1–5)**: US1 sola registra pero no avanza — el valor demostrable
ante la tutora es registrar + avanzar + timeline (SP1+SP6). US4 y US5 (P2) son la
demostración de la tesis y los caminos de excepción; pueden entrar en una segunda pasada.

1. Fases 1–2 → base verificada (`clean verify` en verde)
2. Fases 3–5 (US1→US2→US3) → **demo mínima ante la tutora posible aquí**
3. Fase 6 (US4) → tesis del motor genérico automatizada
4. Fase 7 (US5) → re-trabajo medible (SC-007)
5. Fase 8 → quickstart validado de punta a punta

**Nota de semilla**: si la reunión del martes con la Coordinación cambia la cadena de
novedad de notas, el impacto es SOLO `V2.1.0` (editarla si aún no corrió en ambientes
compartidos) + los recorridos de T034. La estructura y el motor no se tocan — ese
aislamiento es la feature funcionando.

## Notes

- Commit por tarea o grupo lógico (Conventional Commits en español, `.gitmessage`, línea `Verificado:` en cambios de comportamiento)
- RED siempre con `./mvnw clean test` / `clean test-compile` — el incremental miente
- Los IT corren con failsafe: la suite completa es `./mvnw clean verify`, no `test`
- 41 tareas: Setup 1 · Foundational 11 · US1 9 · US2 6 · US3 6 · US4 3 · US5 2 · Polish 3
