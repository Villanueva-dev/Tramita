---
description: "Lista de tareas de la feature 007 — bandeja de trabajo de la coordinación"
---

# Tasks: bandeja de trabajo de la coordinación

**Input**: documentos de diseño en `specs/007-coordination-inbox/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`

**Tests**: **obligatorios**. El proyecto tiene TDD estricto: el rojo se observa antes de implementar. Cada tarea marcada `(RED)` debe fallar **por la razón esperada** antes de escribir la que la sigue, y cada tarea `(MUTANTE)` comprueba que la aserción afirma lo que su nombre dice. Las tareas `(GUARDA)` e `(INVARIANTE)` **no** son RED: están verdes antes de escribirlas y fijan algo que ya se cumple para que no se pierda; etiquetarlas RED diluiría la regla.

## Formato: `[ID] [P?] [Story] Descripción`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: a qué historia pertenece (US1, US2, US3)
- Toda tarea lleva la ruta exacta del archivo

## Convención de verificación

```bash
docker start tramita-postgres && ./mvnw clean verify
```

⚠️ **`clean` no es opcional al medir en rojo.** La compilación incremental de Maven puede dar un rojo falso o esconder uno verdadero.

⚠️ **Nunca `-q` junto a `-Dtest=`**: no imprime la línea «Tests run», y entonces un exit 0 **no prueba que los tests corrieran**. El separador de `-Dtest=` es la **coma**, no el `+`.

**Línea base al empezar** (commit `0483796`): **157 unitarios + 96 IT** en verde. Ese número se re-mide, no se cita de memoria.

**Sin migración**: esta feature no agrega ninguna. Si alguna tarea parece necesitar una, es señal de que se salió del diseño — ver `research.md` D1 y D3.

---

## Phase 1: Preparación

**Propósito**: confirmar el punto de partida y el hecho que sostiene la decisión D7.

- [ ] T001 Levantar la base y confirmar la línea base en verde: `docker start tramita-postgres && ./mvnw clean verify`, anotando el conteo real de unitarios e IT
- [ ] T002 Confirmar que **ningún cliente consume** `GET /requests/inbox`, que es lo que autoriza la enmienda no aditiva de D7: `git -C ../tramita-frontend grep -rn 'requests/inbox' origin/main -- app lib components` debe devolver **0 resultados** (medido así el 2026-09-21). Se acota a código a propósito: sin acotar aparecen 2 líneas en `openspec/changes/archive/…/proposal.md`, documentación de la 004 que cita el endpoint, no un consumidor. Si el comando acotado devuelve alguno, **detenerse**: la enmienda rompería un cliente real y hay que reabrir D7

---

## Phase 2: Foundational (prerrequisito de todas las historias)

**Propósito**: declarar por escrito el cambio de contrato **antes** de cambiarlo, que es lo que exige el §IV.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta que esta fase esté completa.

- [x] T003 Enmendar `specs/004-public-request-capture/contracts/openapi.yaml`: la descripción de `GET /requests/inbox` deja de prometer «las solicitudes más recientes, sin criterio» y remite a `specs/007-coordination-inbox/contracts/openapi.yaml`. Dejar escrito que el cambio **no es aditivo** y por qué se hizo igual (nadie lo consume, T002)
- [x] T004 Actualizar el javadoc de `findAllByOrderByCreatedAtDesc` en `src/main/java/com/uniremington/api/tramita/repo/IRequestRepo.java` si el método deja de usarse, o marcarlo como reemplazado. **No borrarlo en esta tarea**: su eliminación se decide en T014, que reemplaza su única llamada: si queda sin llamadores, se borra ahí

**Checkpoint**: el contrato viejo ya no promete algo que el código dejará de cumplir.

---

## Phase 3: User Story 1 — Ver qué espera mi acción (P1) 🎯 MVP

**Goal**: la Coordinación obtiene la lista de solicitudes detenidas esperando **su** acción, y solo esas.

**Independent Test**: registrar solicitudes en distintos puntos del flujo y comprobar que la consulta devuelve exactamente las que esperan al responsable pedido. Entrega valor sin US2 ni US3.

### Tests para US1 ⚠️ primero el rojo

⚠️ **Los tests del criterio (T005–T007) son de integración, no unitarios.** El criterio vive en la consulta de T010, y `RequestServiceImplTest` mockea `IRequestRepo` (línea 52): un test con el repositorio mockeado devuelve lo que el mock diga, sin importar qué consulta tenga el método real, y el mutante de T017 no lo tocaría. Van en `RequestControllerIT`, contra Postgres real, con los helpers que ya registran y avanzan solicitudes por la API.

- [x] T005 [US1] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`: una solicitud cuyo estado actual tiene una transición con `responsible = X` **aparece** en `GET /api/requests/inbox?responsible=X`, y la lista tiene **exactamente el tamaño esperado** (`$.length()`), no solo «contiene»: sin `distinct` en T010, una solicitud en `EN_COORDINACION` saldría dos veces, porque ese estado tiene dos transiciones de salida con el mismo responsable. Cubrir también una solicitud **devuelta** (`DEVUELTA`): aparece en la bandeja de `COORDINACION`, porque es ella quien registra el reingreso (research D1, spec US1 escenario 6). Debe fallar porque el parámetro todavía no existe y la lista viene sin criterio
- [x] T006 [P] [US1] **(RED)** En el mismo IT: una solicitud cuyo estado actual **no** ofrece ninguna transición de X (por ejemplo, avanzada a `EN_FACULTAD`, cuyas salidas son todas de `FACULTAD`) **no aparece** en la bandeja de X
- [x] T007 [P] [US1] **(RED)** En el mismo IT: una solicitud llevada por el motor a un **estado final** (`RECHAZADA` está a dos avances del inicial) no aparece en la bandeja de **ningún** responsable sembrado. El nombre del test debe afirmar que sale por construcción —de un estado final no salen transiciones—, no que hay un filtro de cerradas. Lo que hace verdadero «por construcción» es el invariante de T018, porque la base no lo impide
- [x] T008 [US1] **(RED)** En el mismo IT, el contrato HTTP de `GET /api/requests/inbox`: con sesión y `responsible` responde 200 (el contenido lo afirman T005–T007); sin sesión responde 401; sin el parámetro `responsible` responde 400 en `application/problem+json`
- [x] T009 [P] [US1] **(GUARDA)** En el mismo IT: la respuesta **no contiene** `studentDocument`. Está verde desde antes de escribirlo —el DTO ya no lleva el campo— y por eso no es un RED: es el invariante del DTO desde la 004 (§III) y se fija acá para que no se pierda al ampliarlo en T011. Su mutante es T019

### Implementación de US1

- [x] T010 [US1] Agregar a `src/main/java/com/uniremington/api/tramita/repo/IRequestRepo.java` la consulta que devuelve las solicitudes cuyo estado actual ofrece una transición con el `responsible` dado, con `select distinct` (un estado puede tener varias salidas con el mismo responsable), ordenada por `createdAt` ascendente y con `Limit` explícito. El orden por espera se aplica después, en memoria (T026): bajo la cota, el corte es por radicación, y así lo declara research D8. **Sin literales de responsables** en la consulta: el valor llega por parámetro (research D2)
- [x] T011 [US1] Ampliar `src/main/java/com/uniremington/api/tramita/dto/InboxEntryResponse.java` con `pendingResponsible` y `origin`. **No agregar `studentDocument`**
- [x] T012 [US1] Derivar `origin` en `src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java` a partir del actor de la **primera** entrada de timeline: el canal público actúa con cuenta propia desde la 004, así que el dato ya está registrado
- [x] T013 [US1] Cambiar la firma de la bandeja en `src/main/java/com/uniremington/api/tramita/service/IRequestService.java` para que reciba el responsable y la cota
- [x] T014 [US1] Implementarla en `RequestServiceImpl.java`, reemplazando la llamada a `findAllByOrderByCreatedAtDesc`
- [x] T015 [US1] Exponer el parámetro `responsible` (obligatorio) y `limit` (opcional, con tope) en `src/main/java/com/uniremington/api/tramita/controller/RequestController.java`, con validación que produzca 400 vía RFC 9457
- [x] T016 [US1] Comprobar que un `responsible` inexistente responde **200 con lista vacía**, no 404: un 404 filtraría qué etiquetas existen (contrato de la 007)

### Verificación de US1

- [x] T017 [US1] **(MUTANTE)** Invertir el criterio de la consulta de T010 —devolver las que **no** esperan a ese responsable— y comprobar que T005 y T006 se ponen **rojos**. Revertir con `cp` desde backup, **nunca** `git checkout` si hay trabajo sin commitear
- [x] T018 [US1] **(INVARIANTE)** En `src/test/java/com/uniremington/api/tramita/controller/WorkflowGenericityIT.java` (ya tiene `JdbcTemplate` y carga la definición DEMO): para **toda** definición presente en la base, **ningún estado final tiene transiciones de salida** y **todo estado no final tiene al menos una**. No es un RED —el seed lo cumple hoy— y reemplaza al mutante que había acá: no existe un «filtro de cerradas» que quitar, porque el cierre queda fuera por construcción (T007), y la base no lo garantiza (`V2.2.0` solo agrega `uq_workflow_state_one_initial_per_definition` y `ck_workflow_transition_not_self`). La segunda mitad es la cobertura de **FR-014**: una solicitud detenida sin responsable posible solo puede existir si la configuración tiene un estado así, y este test lo hace visible antes de que exista la solicitud
- [x] T019 [US1] **(MUTANTE)** Agregar `studentDocument` al DTO y comprobar que T009 se pone **rojo**. Revertir

**Checkpoint**: US1 funciona sola. La Coordinación ya puede reemplazar la revisión manual del correo.

---

## Phase 4: User Story 2 — Saber qué lleva más tiempo esperando (P2)

**Goal**: cada entrada dice desde cuándo espera, y la lista viene ordenada por eso.

**Independent Test**: con la bandeja funcionando, crear solicitudes con distintas antigüedades de espera y comprobar el orden y el instante expuesto.

### Tests para US2 ⚠️ primero el rojo

- [x] T020 [US2] **(RED)** En `RequestServiceImplTest.java`: el `waitingSince` de una solicitud **con transiciones** es el `occurredAt` de la **última**, no su `createdAt`. El test debe usar una solicitud cuyo `createdAt` sea claramente anterior, de modo que confundir ambos campos lo ponga rojo
- [x] T021 [P] [US2] **(RED)** En el mismo archivo: el `waitingSince` de una solicitud **sin entradas de timeline** es su `createdAt`. Es un respaldo **defensivo**: en producción no ocurre, porque toda solicitud nace con una entrada (`RequestServiceImpl.register`, también por el canal público, que delega en él). El test fija que el respaldo existe, no que el caso sea real
- [x] T022 [P] [US2] **(RED)** En el mismo archivo: la bandeja viene ordenada por `waitingSince` **ascendente** — primero la que más lleva esperando
- [x] T023 [US2] **(RED)** En `RequestControllerIT.java`: una solicitud **antigua que fue devuelta** aparece con `waitingSince` reciente y `createdAt` antiguo, y queda **después** de otra que lleva más tiempo detenida. Es el escenario que distingue esta feature de ordenar por fecha de radicación

### Implementación de US2

- [x] T024 [US2] Agregar a `src/main/java/com/uniremington/api/tramita/repo/IRequestTransitionLogRepo.java` la consulta que devuelve el `occurredAt` **más reciente por solicitud** para un conjunto de ids, **en una sola consulta**. No una por solicitud
- [x] T025 [US2] Ampliar `InboxEntryResponse.java` con `waitingSince` de tipo **`OffsetDateTime`**, construido con `CampusTime.toCampus(...)` (`util/CampusTime.java`, la 006): es lo que hace verdadero «instante con offset» (research D4) y resuelve FR-006 sin cálculo propio. **`createdAt` se deja como está** (`LocalDateTime`, contrato de la 004): decidido el 2026-09-21, ver data-model. **No agregar días ni duración**: se expone el instante
- [x] T026 [US2] Resolver `waitingSince` en `RequestServiceImpl.java` combinando el resultado de T024 con `createdAt` como respaldo, convirtiéndolo con `CampusTime.toCampus`, y ordenar el resultado ascendente

### Verificación de US2

- [x] T027 [US2] **(MUTANTE)** Reemplazar `waitingSince` por `createdAt` y comprobar que T020 y T023 se ponen **rojos**. Es el mutante más importante de la feature: es el error que el frontend ya comete hoy
- [x] T028 [US2] **(MUTANTE)** Invertir el orden a descendente y comprobar que T022 se pone **rojo**
- [x] T029 [US2] Comprobar que no hay N+1: contar las consultas emitidas para una bandeja con varias solicitudes —habilitando el log SQL de Hibernate en el test o con un contador— y verificar que el número **no crece** con la cantidad de resultados

**Checkpoint**: US1 y US2 funcionan, y de forma independiente entre sí.

---

## Phase 5: User Story 3 — Responder en qué área está el trámite (P3)

**Goal**: el catálogo expone los estados de cada trámite con sus marcas, para que el cliente deje de reconocer códigos. Resuelve el issue #22.

**Independent Test**: consultar el catálogo y comprobar que cada definición trae sus estados, y que el estado inicial es **distinto** en cada trámite. No depende de US1 ni de US2.

### Tests para US3 ⚠️ primero el rojo

- [ ] T030 [P] [US3] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/WorkflowDefinitionControllerIT.java`: cada definición del catálogo trae su lista de `states`, y cada estado trae `isInitial` e `isFinal`
- [ ] T031 [P] [US3] **(RED)** En el mismo IT: el estado con `isInitial = true` es **distinto** entre `ADICION_CREDITOS` y `NOVEDAD_NOTAS`. Es lo que demuestra por qué el cliente no puede usar una constante global — y lo que se rompió en silencio cuando `V3.2.0` renombró uno solo de los dos
- [ ] T032 [P] [US3] **(GUARDA)** En el mismo IT: los campos `code`, `name` y `version` siguen presentes con su significado anterior (FR-011c, el cambio es aditivo). Está verde desde antes —los tres campos ya existen— y por eso no es un RED: fija la aditividad para que T034–T036 no la rompan

### Implementación de US3

- [ ] T033 [US3] Agregar `isInitial` a `src/main/java/com/uniremington/api/tramita/dto/StateResponse.java` y actualizar su único constructor, en `RequestServiceImpl.toStateResponse`
- [ ] T034 [US3] Crear `src/main/java/com/uniremington/api/tramita/dto/WorkflowDefinitionDetailResponse.java` con `code`, `name`, `version` y `states`. ⛔ **No ampliar `WorkflowDefinitionResponse`**: se anida en cada respuesta de solicitud (research D6)
- [ ] T035 [US3] Cambiar el tipo de retorno en `src/main/java/com/uniremington/api/tramita/service/IWorkflowDefinitionService.java` y mapear los estados en `src/main/java/com/uniremington/api/tramita/service/impl/WorkflowDefinitionServiceImpl.java`
- [ ] T036 [US3] Ajustar el tipo de retorno en `src/main/java/com/uniremington/api/tramita/controller/WorkflowDefinitionController.java`

### Verificación de US3

- [ ] T037 [US3] **(MUTANTE)** Devolver `isInitial` siempre `false` y comprobar que T030 y T031 se ponen **rojos**
- [ ] T038 [US3] Comprobar que `git diff` sobre `src/main/java/com/uniremington/api/tramita/dto/WorkflowDefinitionResponse.java` **no muestra cambios**: es la garantía de que el DTO anidado quedó intacto

**Checkpoint**: las tres historias funcionan, cada una verificable por separado.

---

## Phase 6: Cierre y verificación transversal

- [ ] T039 **La tesis sigue en pie**: `git grep -nE '"(FINALIZADA|DEVUELTA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' -- 'src/main/java/*.java'` devuelve **una sola línea**, la del rótulo impreso del papel. Y `git grep -nE '"(COORDINACION|FACULTAD|REGISTRO_CALI|REGISTRO_NACIONAL|SEDE|FINANCIERA)"' -- 'src/main/java/*.java'` devuelve **cero**: el responsable nunca es un literal (research D2)
- [ ] T040 **SC-005 / §VI**: en `src/test/java/com/uniremington/api/tramita/controller/WorkflowGenericityIT.java`, sembrar una definición nueva por SQL en el test y comprobar que sus solicitudes aparecen en la bandeja **sin desplegar código ni tocar el motor**
- [ ] T041 Comprobar que la última migración del repositorio **sigue siendo `V4.1.0`**: `ls src/main/resources/db/migration/ | sort | tail -1`. Si aparece una nueva, se salió del diseño
- [ ] T042 Suite completa: `docker start tramita-postgres && ./mvnw clean verify`. Anotar el conteo y compararlo con la línea base de T001
- [ ] T043 Recorrer `specs/007-coordination-inbox/quickstart.md` **contra el servidor arriba**, los siete pasos. Es lo que ningún test con mocks cubre: que el filtro esté cableado en la cadena real y que el responsable salga de la configuración y no de un supuesto
- [ ] T044 Revisar que `spec.md`, `plan.md` y `research.md` no hayan quedado desmentidos por la implementación. Si algo cambió, **se corrige el documento**, no se deja la afirmación vieja

---

## Dependencias

```text
Phase 1 (T001–T002)
  └─► Phase 2 (T003–T004)     ← bloquea a todas las historias
        ├─► US1 (T005–T019)   🎯 MVP
        │     └─► US2 (T020–T029)   depende de US1: necesita la bandeja donde mostrarse
        └─► US3 (T030–T038)   INDEPENDIENTE de US1 y US2
              └─► Phase 6 (T039–T044)
```

- **US2 depende de US1**: sin la lista no hay dónde poner el indicador.
- **US3 no depende de nada** salvo la fase 2. Puede hacerse en paralelo con US1 por otra persona, o antes si se quiere cerrar el issue #22 primero.
- **T039–T041 son verificaciones, no implementación**: si fallan, el error está en una tarea anterior.

## Oportunidades de paralelismo

- T006, T007 y T009 son casos independientes dentro del mismo IT: `[P]`.
- T020–T022 pueden escribirse juntos antes de implementar.
- T030–T032 son todos en el mismo IT pero independientes entre sí.
- **US3 completa** puede ir en paralelo con US1 si hay dos personas: no comparten archivos salvo `RequestServiceImpl.toStateResponse` (T033), que hay que coordinar.

## Estrategia de entrega

**MVP = US1.** Con las tareas T001–T019 la Coordinación ya tiene una lista correcta de lo que espera su acción, que es el 100 % del valor que justifica la feature. US2 la vuelve priorizable y US3 cierra el issue #22.

Si el tiempo aprieta, el orden de sacrificio es el inverso al de prioridad: primero se difiere US3 —que tiene su propio issue y puede entregarse aparte—, después US2. **US1 no se difiere**: sin ella la feature no existe.
