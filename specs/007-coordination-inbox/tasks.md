---
description: "Lista de tareas de la feature 007 — bandeja de trabajo de la coordinación"
---

# Tasks: bandeja de trabajo de la coordinación

**Input**: documentos de diseño en `specs/007-coordination-inbox/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`

**Tests**: **obligatorios**. El proyecto tiene TDD estricto: el rojo se observa antes de implementar. Cada tarea marcada `(RED)` debe fallar **por la razón esperada** antes de escribir la que la sigue, y cada tarea `(MUTANTE)` comprueba que la aserción afirma lo que su nombre dice.

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
- [ ] T002 Confirmar que **ningún cliente consume** `GET /requests/inbox`, que es lo que autoriza la enmienda no aditiva de D7: `git -C ../tramita-frontend grep -rn 'requests/inbox' origin/main` debe devolver **0 resultados**. Si devuelve alguno, **detenerse**: la enmienda rompería un cliente real y hay que reabrir D7

---

## Phase 2: Foundational (prerrequisito de todas las historias)

**Propósito**: declarar por escrito el cambio de contrato **antes** de cambiarlo, que es lo que exige el §IV.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta que esta fase esté completa.

- [ ] T003 Enmendar `specs/004-public-request-capture/contracts/openapi.yaml`: la descripción de `GET /requests/inbox` deja de prometer «las solicitudes más recientes, sin criterio» y remite a `specs/007-coordination-inbox/contracts/openapi.yaml`. Dejar escrito que el cambio **no es aditivo** y por qué se hizo igual (nadie lo consume, T002)
- [ ] T004 Actualizar el javadoc de `findAllByOrderByCreatedAtDesc` en `src/main/java/com/uniremington/api/tramita/repo/IRequestRepo.java` si el método deja de usarse, o marcarlo como reemplazado. **No borrarlo en esta tarea**: su eliminación se decide en T031 según quede o no sin llamadores

**Checkpoint**: el contrato viejo ya no promete algo que el código dejará de cumplir.

---

## Phase 3: User Story 1 — Ver qué espera mi acción (P1) 🎯 MVP

**Goal**: la Coordinación obtiene la lista de solicitudes detenidas esperando **su** acción, y solo esas.

**Independent Test**: registrar solicitudes en distintos puntos del flujo y comprobar que la consulta devuelve exactamente las que esperan al responsable pedido. Entrega valor sin US2 ni US3.

### Tests para US1 ⚠️ primero el rojo

- [ ] T005 [US1] **(RED)** En `src/test/java/com/uniremington/api/tramita/service/impl/RequestServiceImplTest.java`: una solicitud cuyo estado actual tiene una transición con `responsible = X` **aparece** en la bandeja de X. Debe fallar porque el método todavía no existe
- [ ] T006 [P] [US1] **(RED)** En el mismo archivo: una solicitud cuyo estado actual **no** ofrece ninguna transición de X **no aparece** en la bandeja de X
- [ ] T007 [P] [US1] **(RED)** En el mismo archivo: una solicitud en **estado final** no aparece en la bandeja de **ningún** responsable. El nombre del test debe afirmar que sale por construcción —de un estado final no salen transiciones—, no que hay un filtro de cerradas
- [ ] T008 [US1] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`: `GET /api/requests/inbox?responsible=COORDINACION` responde 200 con las pendientes de esa área; sin sesión responde 401; sin el parámetro `responsible` responde 400
- [ ] T009 [P] [US1] **(RED)** En el mismo IT: la respuesta **no contiene** `studentDocument`. Es el invariante del DTO desde la 004 (§III) y se fija acá para que no se pierda al ampliarlo

### Implementación de US1

- [ ] T010 [US1] Agregar a `src/main/java/com/uniremington/api/tramita/repo/IRequestRepo.java` la consulta que devuelve las solicitudes cuyo estado actual ofrece una transición con el `responsible` dado, con `Limit` explícito. **Sin literales de responsables** en la consulta: el valor llega por parámetro (research D2)
- [ ] T011 [US1] Ampliar `src/main/java/com/uniremington/api/tramita/dto/InboxEntryResponse.java` con `pendingResponsible` y `origin`. **No agregar `studentDocument`**
- [ ] T012 [US1] Derivar `origin` en `src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java` a partir del actor de la **primera** entrada de timeline: el canal público actúa con cuenta propia desde la 004, así que el dato ya está registrado
- [ ] T013 [US1] Cambiar la firma de la bandeja en `src/main/java/com/uniremington/api/tramita/service/IRequestService.java` para que reciba el responsable y la cota
- [ ] T014 [US1] Implementarla en `RequestServiceImpl.java`, reemplazando la llamada a `findAllByOrderByCreatedAtDesc`
- [ ] T015 [US1] Exponer el parámetro `responsible` (obligatorio) y `limit` (opcional, con tope) en `src/main/java/com/uniremington/api/tramita/controller/RequestController.java`, con validación que produzca 400 vía RFC 9457
- [ ] T016 [US1] Comprobar que un `responsible` inexistente responde **200 con lista vacía**, no 404: un 404 filtraría qué etiquetas existen (contrato de la 007)

### Verificación de US1

- [ ] T017 [US1] **(MUTANTE)** Invertir el criterio de la consulta de T010 —devolver las que **no** esperan a ese responsable— y comprobar que T005 y T006 se ponen **rojos**. Revertir con `cp` desde backup, **nunca** `git checkout` si hay trabajo sin commitear
- [ ] T018 [US1] **(MUTANTE)** Quitar el filtro por estado final implícito —forzar que incluya cerradas— y comprobar que T007 se pone **rojo**
- [ ] T019 [US1] **(MUTANTE)** Agregar `studentDocument` al DTO y comprobar que T009 se pone **rojo**. Revertir

**Checkpoint**: US1 funciona sola. La Coordinación ya puede reemplazar la revisión manual del correo.

---

## Phase 4: User Story 2 — Saber qué lleva más tiempo esperando (P2)

**Goal**: cada entrada dice desde cuándo espera, y la lista viene ordenada por eso.

**Independent Test**: con la bandeja funcionando, crear solicitudes con distintas antigüedades de espera y comprobar el orden y el instante expuesto.

### Tests para US2 ⚠️ primero el rojo

- [ ] T020 [US2] **(RED)** En `RequestServiceImplTest.java`: el `waitingSince` de una solicitud **con transiciones** es el `occurredAt` de la **última**, no su `createdAt`. El test debe usar una solicitud cuyo `createdAt` sea claramente anterior, de modo que confundir ambos campos lo ponga rojo
- [ ] T021 [P] [US2] **(RED)** En el mismo archivo: el `waitingSince` de una solicitud **sin transiciones** es su `createdAt`
- [ ] T022 [P] [US2] **(RED)** En el mismo archivo: la bandeja viene ordenada por `waitingSince` **ascendente** — primero la que más lleva esperando
- [ ] T023 [US2] **(RED)** En `RequestControllerIT.java`: una solicitud **antigua que fue devuelta** aparece con `waitingSince` reciente y `createdAt` antiguo, y queda **después** de otra que lleva más tiempo detenida. Es el escenario que distingue esta feature de ordenar por fecha de radicación

### Implementación de US2

- [ ] T024 [US2] Agregar a `src/main/java/com/uniremington/api/tramita/repo/IRequestTransitionLogRepo.java` la consulta que devuelve el `occurredAt` **más reciente por solicitud** para un conjunto de ids, **en una sola consulta**. No una por solicitud
- [ ] T025 [US2] Ampliar `InboxEntryResponse.java` con `waitingSince`. **No agregar días ni duración**: se expone el instante (research D4)
- [ ] T026 [US2] Resolver `waitingSince` en `RequestServiceImpl.java` combinando el resultado de T024 con `createdAt` como respaldo, y ordenar el resultado ascendente

### Verificación de US2

- [ ] T027 [US2] **(MUTANTE)** Reemplazar `waitingSince` por `createdAt` y comprobar que T020 y T023 se ponen **rojos**. Es el mutante más importante de la feature: es el error que el frontend ya comete hoy
- [ ] T028 [US2] **(MUTANTE)** Invertir el orden a descendente y comprobar que T022 se pone **rojo**
- [ ] T029 [US2] Comprobar que no hay N+1: contar las consultas emitidas para una bandeja con varias solicitudes —habilitando el log SQL de Hibernate en el test o con un contador— y verificar que el número **no crece** con la cantidad de resultados

**Checkpoint**: US1 y US2 funcionan, y de forma independiente entre sí.

---

## Phase 5: User Story 3 — Responder en qué área está el trámite (P3)

**Goal**: el catálogo expone los estados de cada trámite con sus marcas, para que el cliente deje de reconocer códigos. Resuelve el issue #22.

**Independent Test**: consultar el catálogo y comprobar que cada definición trae sus estados, y que el estado inicial es **distinto** en cada trámite. No depende de US1 ni de US2.

### Tests para US3 ⚠️ primero el rojo

- [ ] T030 [P] [US3] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/WorkflowDefinitionControllerIT.java`: cada definición del catálogo trae su lista de `states`, y cada estado trae `isInitial` e `isFinal`
- [ ] T031 [P] [US3] **(RED)** En el mismo IT: el estado con `isInitial = true` es **distinto** entre `ADICION_CREDITOS` y `NOVEDAD_NOTAS`. Es lo que demuestra por qué el cliente no puede usar una constante global — y lo que se rompió en silencio cuando `V3.2.0` renombró uno solo de los dos
- [ ] T032 [P] [US3] **(RED)** En el mismo IT: los campos `code`, `name` y `version` siguen presentes con su significado anterior (FR-011c, el cambio es aditivo)

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

- T006, T007 y T009 son archivos de test distintos o casos independientes: `[P]`.
- T020–T022 pueden escribirse juntos antes de implementar.
- T030–T032 son todos en el mismo IT pero independientes entre sí.
- **US3 completa** puede ir en paralelo con US1 si hay dos personas: no comparten archivos salvo `RequestServiceImpl.toStateResponse` (T033), que hay que coordinar.

## Estrategia de entrega

**MVP = US1.** Con las tareas T001–T019 la Coordinación ya tiene una lista correcta de lo que espera su acción, que es el 100 % del valor que justifica la feature. US2 la vuelve priorizable y US3 cierra el issue #22.

Si el tiempo aprieta, el orden de sacrificio es el inverso al de prioridad: primero se difiere US3 —que tiene su propio issue y puede entregarse aparte—, después US2. **US1 no se difiere**: sin ella la feature no existe.
