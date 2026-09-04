---

description: "Task list — Formularios validados y reglas de negocio por trámite (003)"
---

# Tasks: Formularios validados y reglas de negocio por trámite

**Input**: Design documents from `/specs/003-request-form-rules/`

**Prerequisites**: plan.md, spec.md, research.md (D1–D11), data-model.md, contracts/openapi.yaml

**Tests**: incluidos y **obligatorios**. El proyecto trabaja en TDD estricto (constitución §V).

**Organization**: agrupadas por user story. Cada historia es entregable y verificable por
separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivo distinto, sin dependencia pendiente)
- **[Story]**: a qué user story pertenece (US1..US4)
- Toda tarea lleva su ruta de archivo exacta

## Reglas de ejecución de esta feature

1. **El RED se evidencia siempre con `clean`.** `./mvnw clean test` o
   `./mvnw clean test-compile`. El incremental de Maven ya produjo un `BUILD SUCCESS` falso en
   este repositorio: sin `clean`, un RED puede no existir.
2. **Un test cuyo nombre afirma una garantía debe tener la aserción que la verifica.** Antes de
   pasar a verde, atacá el test con un mutante que ataque exactamente lo que su nombre afirma.
   Si el mutante sobrevive, el test no vale.
3. **Portar no es copiar.** Cada tarea que toma código de `origin/router-ia` (`82ece40`) dice
   qué se porta y qué se corrige al portarlo. Leer con
   `git show origin/router-ia:<ruta>`; **no hacer checkout ni merge de la rama**.
4. **Los commits de fase son manuales.** El auto-commit de Spec Kit está apagado en
   `.specify/extensions/git/git-config.yml`. Ninguna tarea asume commit automático.

---

## Phase 1: Setup

**Purpose**: confirmar que se parte de un baseline verde.

- [x] T001 Levantar la base y correr la suite completa desde limpio para fijar el baseline: `docker start tramita-postgres && ./mvnw clean verify`. Anotar el número de tests que pasan; ese es el piso que ninguna tarea posterior puede bajar.
- [x] T002 [P] Leer las 9 piezas del prototipo con `git show origin/router-ia:<ruta>` y tener a mano el review de referencia. No hacer `git checkout` ni `git merge` de `origin/router-ia` en ningún momento.

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: esquema y manejo de errores que las cuatro historias necesitan. Flyway posee el
schema, así que las migraciones van antes que cualquier entidad: mapear una columna que no
existe rompe el arranque con `ddl-auto: validate`.

- [x] T003 [P] Crear la migración `src/main/resources/db/migration/V2.3.0__Persist_request_form_data.sql` con las 4 columnas opcionales de `request` (`student_code`, `program`, `semester`, `reason VARCHAR(2000)`) y la tabla `request_subject` según data-model.md, con `CHECK (credits > 0)`, notas `NUMERIC(3,2)` e índice sobre `request_id`. **Portado de `V2.3.0` del prototipo, corrigiendo tres cosas**: se elimina la columna `student_email` (FR-020), se elimina la columna `priority` (fuera de alcance), y las notas dejan de ser `VARCHAR(20)` para ser numéricas (D3).
- [x] T004 [P] Crear la migración `src/main/resources/db/migration/V3.0.0__Configure_business_rules.sql` con la tabla `workflow_parameter` (`UNIQUE(definition_id, parameter_key)`), la columna `workflow_transition.guard_key VARCHAR(50)` nullable, y el seed de `MAX_CREDITS`, `MIN_GRADE` y `MAX_GRADE` atado a `code + version`. **Portado de `V3.0.0` del prototipo, corrigiendo**: el comentario del seed NO dice «confirmado en entrevistas» — dice **provisional y no auditado**, porque el respaldo es derivado y el reglamento estudiantil no se obtuvo (constitución §IV). Agrega además `guard_key`, que el prototipo no trae.
- [x] T005 Crear `src/main/java/com/uniremington/api/tramita/shared/exception/IncompleteConfigurationException.java` como `RuntimeException` de dominio, con el racional de D2 en el javadoc: representa una falla de configuración del sistema, no un error del usuario.
- [x] T006 Escribir en `src/test/java/com/uniremington/api/tramita/shared/exception/GlobalExceptionHandlerTest.java` el test que afirma que `IncompleteConfigurationException` produce `500` con título fijo y **sin detalle interno** (regla dura: nunca `ex.getMessage()` al cliente). Verificar RED con `./mvnw clean test`.
- [x] T007 Agregar el `@ExceptionHandler(IncompleteConfigurationException.class)` en `src/main/java/com/uniremington/api/tramita/shared/exception/GlobalExceptionHandler.java` usando `ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)` con `setTitle("Configuración del trámite incompleta")`. Verde en T006.
- [x] T008 Correr `docker start tramita-postgres && ./mvnw clean verify` para confirmar que las dos migraciones aplican sobre una base con datos y que Hibernate valida el schema. Es la comprobación de que `V2.3.0` no rompe las solicitudes creadas por `V2.0.0`.

**Checkpoint**: el esquema está listo y el error de configuración tiene forma. Commit manual sugerido: `feat(003): prepara el esquema del formulario y las reglas configurables`.

---

## Phase 3: User Story 1 — Registrar una solicitud con el contenido real del formulario (P1) 🎯 MVP

**Goal**: llevar al sistema el contenido del formulario —datos académicos y asignaturas— que hoy
vive en el Word adjunto.

**Independent Test**: registrar una solicitud de adición con dos asignaturas y una de novedad de
notas con su nota propuesta; ambas se consultan y devuelven íntegro lo capturado.

### Tests primero (RED)

- [x] T009 [P] [US1] En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`, agregar el test de US1-1: registrar con formulario completo y dos asignaturas devuelve `201` y las dos asignaturas con todos sus datos.
- [x] T010 [P] [US1] En el mismo IT, agregar el test de US1-4 (**compatibilidad, FR-006**): una petición con solo `definitionCode`, `studentName` y `studentDocument` sigue devolviendo `201`, con lista de asignaturas vacía. Este test debe pasar sin modificar ningún test existente de la 002.
- [x] T011 [P] [US1] En el mismo IT, agregar el test de US1-6 (**FR-020**): la respuesta de una solicitud **no** contiene ningún campo de correo, y `rg -n 'student_email' src/main/resources/db/migration/` no devuelve nada.
- [x] T012 [P] [US1] En el mismo IT, agregar el test de US1-7 (**FR-021**): sin sesión, registrar y consultar devuelven `401` sin filtrar contenido de la solicitud.
- [x] T013 [US1] Verificar RED con `./mvnw clean test -Dtest=RequestControllerIT`. Los cuatro tests nuevos deben fallar; los de la 002 deben seguir pasando.

### Implementación

- [x] T014 [P] [US1] Crear `src/main/java/com/uniremington/api/tramita/model/RequestSubject.java` con el patrón Lombok de `main` (`@NoArgsConstructor(PROTECTED)` + `@AllArgsConstructor(PRIVATE)` + `@Builder` + `@Getter`; **nunca `@Data` ni `@Setter`**), `updatable = false` en todo el estado, `group` mapeado a `subject_group`, y las notas como `BigDecimal`. Portado del prototipo, cambiando el tipo de las notas (D3).
- [x] T015 [US1] Ampliar `src/main/java/com/uniremington/api/tramita/model/Request.java` con `studentCode`, `program`, `semester`, `reason` (todos `updatable = false`) y el `@OneToMany(mappedBy = "request", cascade = ALL, orphanRemoval = true, fetch = LAZY)` con `@Builder.Default`. **Sin `studentEmail` ni `priority`.** Usar imports explícitos: no dejar `jakarta.persistence.CascadeType.ALL` totalmente cualificado inline como hace el prototipo.
- [x] T016 [P] [US1] Crear `src/main/java/com/uniremington/api/tramita/dto/SubjectRequestBody.java` con `@NotBlank @Size` en `code` y `name`, `@Min(1) @Max(30)` en `credits` (**FR-009** — el prototipo no tiene ninguna cota y por eso admite negativos), y las notas como `BigDecimal`. Documentar en el javadoc que la cota superior es de sanidad de entrada, sin respaldo normativo (D8).
- [x] T017 [P] [US1] Crear `src/main/java/com/uniremington/api/tramita/dto/SubjectResponse.java` como record de salida.
- [x] T018 [US1] Ampliar `src/main/java/com/uniremington/api/tramita/dto/CreateRequestBody.java` con los campos del formulario, `@Size(max = 2000)` en `reason` (**FR-004**), `@Valid List<SubjectRequestBody> subjects`, y **el constructor de compatibilidad de 3 argumentos** que delega con valores por defecto. Es la mejor decisión del prototipo y se porta tal cual: es lo que hace que ningún test de la 002 se toque.
- [x] T019 [US1] Ampliar `src/main/java/com/uniremington/api/tramita/dto/RequestResponse.java` con los datos del formulario y la lista de `SubjectResponse`. **Sin campo de correo.**
- [x] T020 [US1] Cablear en `src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java` la construcción de las asignaturas, seteando `.request(request)` en cada una para que la FK no quede nula, y el mapeo a `SubjectResponse` en las consultas. Usar imports explícitos, no nombres totalmente cualificados inline.
- [x] T021 [US1] Verde: `./mvnw clean verify`. Los cuatro tests de T009–T012 pasan y la suite de la 002 sigue intacta.

**Checkpoint**: US1 entregable. El formulario vive en el sistema. Commit manual sugerido: `feat(003): captura el formulario y las asignaturas de cada trámite`.

---

## Phase 4: User Story 2 — Impedir que una solicitud viole el límite del trámite (P1)

**Goal**: aplicar las reglas de negocio en la captura, leyéndolas de configuración.

**Independent Test**: con un tope configurado, una solicitud que lo excede se rechaza y una que
lo alcanza exactamente se registra.

### Tests primero (RED)

- [x] T022 [P] [US2] Crear `src/test/java/com/uniremington/api/tramita/service/impl/RequestBusinessRulesImplTest.java` con los casos de US2-1 y US2-2: 22 créditos sobre un tope de 21 lanza la excepción de negocio indicando el límite; 21 exactos no lanza. Portados del prototipo.
- [x] T023 [P] [US2] En el mismo test, agregar **el caso que hoy no existe** (US2-4, **FR-010**): con el repositorio devolviendo `Optional.empty()`, validar **lanza `IncompleteConfigurationException`**. ⚠️ El prototipo no tiene este test, y peor: su `rejectsGradesOutsideRange` no stubea el repositorio y pasa por accidente porque su fixture lleva `credits = null`. Al escribir este caso, revisá que ningún otro test del archivo dependa del stub ausente.
- [x] T024 [P] [US2] En el mismo test, agregar US2-6 (**FR-011**): un parámetro con valor `"veintiuno"` y otro con `"0"` lanzan `IncompleteConfigurationException`, **no** una excepción de negocio contra el usuario y **no** un límite cero.
- [x] T025 [P] [US2] En el mismo test, agregar US2-5 (**FR-012**): una nota fuera del rango configurado lanza la excepción de negocio indicando el rango; el rango se lee de configuración, no de una constante.
- [x] T026 [P] [US2] En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`, agregar US2-3 (**FR-009**): un cuerpo con `credits: 30` y `credits: -20` —que sumarían 10 y pasarían el tope— devuelve `400` y no crea ninguna fila.
- [x] T027 [P] [US2] En el mismo IT, agregar US2-7 (**FR-004**): un `reason` que excede la longitud máxima devuelve `400` y no registra la solicitud truncada.
- [x] T028 [US2] Verificar RED con `./mvnw clean test`. **Atacar cada test con un mutante que ataque lo que su nombre afirma** antes de seguir: si al cambiar `ifPresent` por `orElseThrow` el test de T023 no cambia de resultado, ese test no está verificando nada.

### Implementación

- [x] T029 [P] [US2] Crear `src/main/java/com/uniremington/api/tramita/model/WorkflowParameter.java` con el patrón Lombok de `main`, `key` mapeado a `parameter_key` con `updatable = false` y `value` a `parameter_value` sin `updatable = false` (ajustar un parámetro es el caso de uso de SC-005). Portado del prototipo tal cual.
- [x] T030 [P] [US2] Crear `src/main/java/com/uniremington/api/tramita/repo/IWorkflowParameterRepo.java` con `Optional<WorkflowParameter> findByDefinitionIdAndKey(UUID, String)`. Query derivada; no necesita `@Query`. Portado tal cual.
- [x] T031 [US2] Crear el contrato `src/main/java/com/uniremington/api/tramita/service/IRequestBusinessRules.java`. **Corrige al prototipo**, que dejó la clase como `@Component` suelto en `impl/` sin interfaz, rompiendo la estructura por capas de la constitución §II.
- [x] T032 [US2] Crear `src/main/java/com/uniremington/api/tramita/service/impl/RequestBusinessRulesImpl.java`. **Portado de `RequestBusinessRules` del prototipo con cuatro correcciones**: (a) la lectura del parámetro pasa de `ifPresent` a `orElseThrow(IncompleteConfigurationException::new)` — FR-010; (b) el parseo inválido lanza también `IncompleteConfigurationException` en vez de `IllegalStateException` — FR-011; (c) el rango de notas se lee de `MIN_GRADE`/`MAX_GRADE` en vez de estar fijo en `0.0`–`5.0` — FR-012; (d) imports explícitos, sin `java.util.Objects::nonNull` cualificado inline.
- [x] T033 [US2] Inyectar `IRequestBusinessRules` por la interfaz en `RequestServiceImpl` e invocar la validación antes de persistir la solicitud.
- [x] T034 [US2] Verde: `./mvnw clean verify`.

**Checkpoint**: US2 entregable. Las reglas se aplican y ninguna falla en silencio. Commit manual sugerido: `feat(003): valida las solicitudes contra las reglas configuradas del trámite`.

---

## Phase 5: User Story 3 — Ajustar una regla sin desplegar (P2)

**Goal**: demostrar SC-005 de forma verificable.

**Independent Test**: cambiar el valor del parámetro y comprobar que una solicitud antes
rechazada pasa a aceptarse, sin recompilar ni redesplegar.

### Tests primero (RED)

- [x] T035 [P] [US3] En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`, agregar US3-1 (**SC-005**): con el tope en un valor, una solicitud se rechaza; tras actualizar la fila de `workflow_parameter`, la misma solicitud se acepta — todo dentro del mismo test, sin reiniciar el contexto.
- [x] T036 [P] [US3] Agregar US3-2 (**FR-014**): dos definiciones con topes distintos validan cada una contra el suyo.
- [x] T037 [P] [US3] Agregar US3-3 (**FR-013**): con dos versiones de la misma definición y topes distintos, una solicitud registrada contra la v1 sigue validándose con el tope de la v1 aunque la v2 tenga otro. Es la garantía de versionado que la 002 estableció en D2.
- [x] T038 [US3] Verificar RED con `./mvnw clean test -Dtest=RequestControllerIT`.

### Implementación

- [x] T039 [US3] Ajustar lo que haga falta para que los tres tests pasen. Si el diseño de la Phase 4 quedó correcto, esta historia **no requiere código nuevo**: la FK de `workflow_parameter` apunta a la versión de la definición, y con eso FR-013 se cumple sin lógica adicional (data-model.md). Si hiciera falta código, es señal de que el parámetro se está resolviendo por `code` en vez de por la definición concreta de la solicitud — corregirlo ahí.
- [x] T040 [US3] Verde: `./mvnw clean verify`.

**Checkpoint**: US3 entregable. SC-005 queda demostrado con un test, no con una afirmación.

---

## Phase 6: User Story 4 — Condicionar un paso del trámite a una regla (P3)

**Goal**: entregar el mecanismo de guardas que D3 de la 002 difirió a esta feature.

**Independent Test**: una transición con regla asociada procede cuando se cumple y se bloquea
cuando no, sin que el motor conozca ningún trámite.

### Tests primero (RED)

- [ ] T041 [P] [US4] Crear el test del motor con una **guarda de prueba** (implementación de `IWorkflowGuard` declarada solo en el árbol de tests): US4-1 la transición procede cuando la regla se cumple; US4-2 se bloquea sin alterar el estado cuando no se cumple, informando qué regla falló.
- [ ] T042 [P] [US4] Agregar US4-3 (**FR-017**): una transición con `guard_key` nulo se comporta exactamente como en la 002, sin validación adicional. Todas las transiciones ya sembradas están en este caso.
- [ ] T043 [P] [US4] Agregar US4-4 (**FR-018**): dos definiciones distintas que declaran la misma clave de guarda la evalúan correctamente, sin que el motor incorpore conocimiento de ninguna.
- [ ] T044 [P] [US4] Agregar US4-5 (**FR-019**): una transición con una `guard_key` sin implementación registrada se bloquea con `IncompleteConfigurationException` y **no** se ejecuta omitiendo la guarda.
- [ ] T045 [US4] Verificar RED con `./mvnw clean test`.

### Implementación

- [ ] T046 [P] [US4] Crear el contrato `src/main/java/com/uniremington/api/tramita/service/IWorkflowGuard.java` con el método que declara la clave que atiende y el método de evaluación. Javadoc con el racional de D5: se resuelve por registro de beans, **no** por enum (recompilaría el motor con cada guarda) ni por reflection (rompería en runtime sin aviso del compilador).
- [ ] T047 [US4] Ampliar `src/main/java/com/uniremington/api/tramita/model/WorkflowTransition.java` con `guardKey` (`@Column(name = "guard_key")`, nullable). El comentario que hoy dice «las guardas llegan en la feature 003» se reemplaza por la documentación real del campo.
- [ ] T048 [US4] Cablear en `RequestServiceImpl.advance()` la resolución de la guarda: recibir la colección de `IWorkflowGuard` inyectada por Spring, resolver por clave, lanzar `IncompleteConfigurationException` si la clave no tiene implementación (FR-019), y evaluar antes de ejecutar la transición.
- [ ] T049 [US4] Verde: `./mvnw clean verify`.

**Checkpoint**: US4 entregable. Se entrega el mecanismo **sin sembrar ninguna guarda en producción** — la tensión con el Principio I que Complexity Tracking declaró. La guarda de prueba vive solo en el árbol de tests.

---

## Phase 7: Polish & cross-cutting

- [ ] T050 [P] Reemplazar el identificador con forma de cédula por un valor inequívocamente sintético en las **6 ocurrencias de 4 archivos** heredadas de `main` (**FR-022**, constitución §III): `specs/002-workflow-engine/contracts/openapi.yaml:201`, `specs/002-workflow-engine/quickstart.md:67` y `:91`, `src/test/java/com/uniremington/api/tramita/service/impl/RequestServiceImplTest.java:92` y `:235`, `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java:61`. Confirmar con `rg -n '1144099888' --hidden -g '!.git'` → sin resultados. **No** reescribir el historial de `main` (D11).
- [ ] T051 [P] Revisar que ninguna clase nueva use `@Data`, `@Setter` ni imports comodín de Lombok en entidades, y que ningún servicio se inyecte por su implementación en vez de por su interfaz.
- [ ] T052 Ejecutar el `quickstart.md` completo contra la instancia local, incluidos los pasos 4, 5 y 6 —los que reproducen los fallos del prototipo— y el paso 8, que comprueba con `\d request_subject` que las notas son `numeric(3,2)` y no `character varying`.
- [ ] T053 Correr `docker start tramita-postgres && ./mvnw clean verify` una última vez y confirmar que el número de tests es mayor que el baseline anotado en T001.
- [ ] T054 Marcar las tareas cumplidas en este archivo y cerrar el issue #9 desde el cuerpo de la PR con `Closes #9`, para que el milestone del Sprint 1 avance solo.

---

## Dependencias

```text
Phase 1 (Setup)
   └─> Phase 2 (Foundational: migraciones + error de configuración)
          ├─> Phase 3 (US1) ──────────┐
          ├─> Phase 4 (US2) ──> Phase 5 (US3)
          └─> Phase 6 (US4)           │
                                      └─> Phase 7 (Polish)
```

- **US1** solo depende de `V2.3.0`.
- **US2** depende de `V3.0.0` y del manejador de error de configuración; además consume los DTOs
  de US1, así que en la práctica se implementa después.
- **US3** depende de US2: no valida nada nuevo, valida que lo de US2 sea configurable.
- **US4** depende de `V3.0.0` (por `guard_key`) pero **no** de US1 ni US2: puede desarrollarse en
  paralelo con la Phase 4.
- **Phase 7** al final, salvo T050, que puede correrse en cualquier momento.

## Oportunidades de paralelismo

- T003 y T004: las dos migraciones son archivos distintos.
- T009–T012: cuatro tests en el mismo archivo — **no** son paralelos entre sí pese al `[P]` de
  archivo distinto respecto a otras fases; coordinar si se reparten.
- T014, T016, T017: entidad y DTOs nuevos, archivos independientes.
- T022–T027 y T029–T030: tests y modelos de US2 en archivos separados.
- Phase 4 y Phase 6 pueden avanzar en paralelo si hay dos personas.

## Estrategia de entrega

**MVP = Phase 1 + Phase 2 + Phase 3 (US1).** Con eso el formulario ya vive en el sistema y la
solicitud deja de depender del Word adjunto, que es el salto de valor de SP2.

**Incremento 2 = Phase 4 + Phase 5 (US2 + US3).** Cierra el issue #9 y con él el Sprint 1: las
reglas se aplican y son configurables.

**Incremento 3 = Phase 6 (US4).** Habilita las guardas que SP3 y SP4 van a consumir en el
Sprint 2.

## Trazabilidad tarea → requisito

| Requisito | Tareas |
|---|---|
| FR-001, FR-002, FR-003 | T003, T014, T015, T016, T018, T020 |
| FR-004 motivo acotado | T003, T018, T027 |
| FR-005 inmutabilidad | T014, T015 |
| FR-006 compatibilidad | T010, T018 |
| FR-007, FR-014 parámetros configurables | T004, T029, T030, T036 |
| FR-008 tope de créditos | T022, T032 |
| FR-009 créditos positivos | T003, T016, T026 |
| FR-010 configuración ausente | T005, T006, T007, T023, T032 |
| FR-011 configuración inválida | T024, T032 |
| FR-012 rango de notas configurable | T004, T025, T032 |
| FR-013 versionado de parámetros | T037 |
| FR-015..FR-018 guardas | T041, T042, T043, T046, T047, T048 |
| FR-019 guarda desconocida | T044, T048 |
| FR-020 sin correo | T003, T011, T015, T019 |
| FR-021 solo autenticada | T012 |
| FR-022 datos sintéticos | T050 |
