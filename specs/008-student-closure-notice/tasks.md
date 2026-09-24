---
description: "Lista de tareas de la feature 008 — aviso de cierre al estudiante"
---

# Tasks: aviso de cierre al estudiante

**Input**: documentos de diseño en `specs/008-student-closure-notice/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md` (D1–D9), `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`

**Tests**: **obligatorios**. El proyecto tiene TDD estricto: el rojo se observa antes de implementar. Cada tarea marcada `(RED)` debe fallar **por la razón esperada** antes de escribir la que la sigue, y cada tarea `(MUTANTE)` comprueba que la aserción afirma lo que su nombre dice. Las tareas `(GUARDA)` **no** son RED: están verdes antes de escribirlas y fijan algo que ya se cumple para que no se pierda; etiquetarlas RED diluiría la regla.

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

⚠️ **Los mutantes se revierten con `cp` desde una copia**, nunca con `git checkout` mientras haya trabajo sin commitear.

**Línea base al empezar** (`cb85fd6`, la punta de la 007 que mergeó como `412a5e0`): **163 unitarios + 112 IT** en verde. Ese número se re-mide en T001, no se cita de memoria.

**Sin migración**: esta feature no agrega ninguna. Si alguna tarea parece necesitar una, es señal de que se salió del diseño — ver `research.md` D6 y `data-model.md`.

**Cada fase cierra con un commit de unidad de trabajo**: Conventional Commits en español con la plantilla `.gitmessage` (activarla con `git config commit.template .gitmessage` si no está), cuerpo con el **porqué** y línea `Verificado:` con el comando y su resultado. Sin atribución de IA.

---

## Phase 1: Preparación

**Propósito**: confirmar el punto de partida y medir los dos hechos externos de los que depende la entrega: el estado del front (condición de despliegue D9) y la tesis del §VI.

- [x] T001 Levantar la base y confirmar la línea base en verde: `docker start tramita-postgres && ./mvnw clean verify`, anotando en este archivo el conteo real de unitarios e IT. Si difiere de 163 + 112, se escribe el medido y se busca por qué antes de seguir — ✅ **Medido el 2026-09-24 sobre `32a6187`: 163 unitarios + 112 IT, `BUILD SUCCESS`, 41,9 s.** Coincide con la línea base
- [x] T002 [P] Medir el estado del front para la condición de despliegue (research D9). **No es un stop**: es lo que la PR del back lleva escrito en su cuerpo (T041). Comandos y resultado esperado, medidos el 2026-09-24 sobre `origin/main` = `7b9e2bf`: (a) `git -C ../tramita-frontend fetch -q origin && git -C ../tramita-frontend grep -n 'studentPhone' origin/main -- app lib components` → el formulario público lo captura en `components/do-fr-100/sections.tsx:96` (`TextField` con `maxLength`, **sin filtro de dígitos**) y `lib/api.ts` lo manda crudo en `submitPublicRequest`; (b) `git -C ../tramita-frontend grep -nE 'inputMode|replace\(/\\D' origin/main -- app lib components` → solo `app/requests/new/page.tsx:253` (el formulario interno, otro campo) y `components/pdf-document.tsx:8`: **ninguna en el formulario público**; (c) `git -C ../tramita-frontend show origin/main:lib/store.tsx | grep -c studentPhone` → `0`: el formulario interno **no manda** `studentPhone`, así que hoy ningún cliente envía `""` (research D3). Si (b) ya muestra el filtro en `app/solicitud/` o `components/do-fr-100/`, anotarlo: la condición de D9 quedó satisfecha del lado del front — ✅ **Medido el 2026-09-24 sobre `7b9e2bf`**: (a) 13 líneas, el formulario público lo captura en `components/do-fr-100/sections.tsx:96` con el tope de `lib/public-request-limits.ts:21` (`studentPhone: 30`) y lo manda crudo desde `lib/api.ts:310,325`; (b) `inputMode` solo en `app/requests/new/page.tsx:253` y `replace(/\D` solo en `components/pdf-document.tsx:8` → **sin filtro en el formulario público**: la condición de D9 sigue pendiente del lado del front; (c) `0` → nadie manda `""`. Dato para el brief a Codex: sus fixtures usan `'000 000 0000'` (`app/solicitud/creditos-adicionales/page.test.tsx:23`, `lib/api.test.ts:264`) y el tope de 30 pasa a 10
- [x] T003 [P] Línea base de la tesis del §VI: `git grep -nE '"(FINALIZADA|DEVUELTA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' HEAD -- 'src/main/java/*.java'` devuelve **una sola línea**, `src/main/java/com/uniremington/api/tramita/service/impl/DoFr100Renderer.java:166` (el rótulo impreso del papel). Se vuelve a medir en T035; entre las dos mediciones nada puede haber sumado un literal — ✅ **Medido el 2026-09-24 sobre `32a6187`: una línea, `DoFr100Renderer.java:166`**

---

## Phase 2: Foundational (prerrequisito de todas las historias)

**Propósito**: sacar el enum de origen del DTO de la bandeja **antes** de que un segundo DTO lo necesite (research D1). Es un refactor: el JSON no cambia y ningún test se toca.

**⚠️ CRÍTICO**: ninguna historia puede empezar hasta que esta fase esté completa. US1 necesita el tipo `RequestOrigin`.

- [x] T004 Crear `src/main/java/com/uniremington/api/tramita/dto/RequestOrigin.java`: enum `{COORDINATION, PUBLIC_LINK}`, con el javadoc que hoy acompaña al enum anidado en `src/main/java/com/uniremington/api/tramita/dto/InboxEntryResponse.java:64-67` —en particular, que `COORDINATION` significa «cualquier cuenta autenticada», no un rol— más una línea que diga por qué es de primer nivel: lo usan dos DTO (research D1)
- [x] T005 En `src/main/java/com/uniremington/api/tramita/dto/InboxEntryResponse.java`: el componente `origin` pasa a ser de tipo `RequestOrigin` y el enum anidado `Origin` **se elimina**. Nada más cambia en el record
- [x] T006 En `src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java`: `originOf` (`:439`) pasa a devolver `RequestOrigin`, y sus dos literales (`:444`, `:445`) pasan a `RequestOrigin.PUBLIC_LINK` / `RequestOrigin.COORDINATION`. El llamador de `:510` no cambia. Verificado hoy: `grep -n 'InboxEntryResponse.Origin' src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java` → exactamente esas tres líneas
- [x] T007 Verificar el refactor y cerrar la fase: `grep -rn 'InboxEntryResponse.Origin' src/` → **0 líneas**; `./mvnw clean verify` en verde con el mismo conteo de T001; y `git diff --stat` **no toca ningún test**: el IT que afirma `"PUBLIC_LINK"` sobre el JSON de la bandeja (`src/test/java/com/uniremington/api/tramita/controller/PublicRequestControllerIT.java:257-278`) sigue verde sin editarse, que es la prueba de que el contrato de la 007 no se movió. Commit `refactor(008): el origen de la solicitud pasa a un enum de primer nivel`, con `Verificado:` los dos comandos — ✅ **2026-09-24**: `grep -rn 'InboxEntryResponse.Origin' src/` → 0 líneas; `./mvnw clean verify` → 163 unitarios + 112 IT, `BUILD SUCCESS` (40,2 s); `git diff --stat -- src/` → solo `InboxEntryResponse.java` y `RequestServiceImpl.java`, ningún test

**Checkpoint**: `RequestOrigin` existe, la bandeja lo usa y nada observable cambió.

---

## Phase 3: User Story 1 — Avisarle por correo que su trámite terminó (P1) 🎯 MVP

**Goal**: cada acción que devuelve el detalle de una solicitud (registrar, mover, consultar) trae su **origen**, el **correo** y el **teléfono** declarados. Con `currentState.isFinal`, que ya viaja, el cliente tiene todo para ofrecer el correo prellenado sin una segunda consulta (FR-008, spec US1 escenario 2). El sistema no envía ni registra nada (FR-006, FR-007).

**Independent Test**: una solicitud del enlace público llevada a un estado final devuelve, en la respuesta de esa misma transición, los tres hechos; una registrada por la Coordinación devuelve `origin = COORDINATION`; la búsqueda y la bandeja no devuelven contacto. Entrega valor sin US2 ni US3.

### Tests para US1 ⚠️ primero el rojo

⚠️ **Los tests son de integración, no unitarios.** El origen se deriva del actor de la entrada de nacimiento, y eso lo produce el flujo real (`register` con la cuenta del portal); `RequestServiceImplTest` mockea el repositorio y afirmaría el origen por construcción (research D8). Los de solicitudes **públicas** van en `PublicRequestControllerIT`, que ya tiene los helpers `filledForm` (`:387`), `publicSubmission` (`:433`), `login` (`:467`) y `findIdByName` (`:480`) —el `201` público no devuelve identificador, y así lo resuelve el test de origen de la 007 en `:257-278`—. Los de solicitudes **internas** van en `RequestControllerIT`, con `createRequestWithForm` (`:1273`), `registerAndGetId` (`:1230`), `advanceRequest` (`:1239`) y `login` (`:1250`).

- [x] T008 [US1] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/PublicRequestControllerIT.java`: radicar con `publicSubmission(..., PUBLIC_TRADE, filledForm(nombre, "SIN-DATO-REAL-3xx"))`, ubicar el id con `findIdByName`, y comprobar que `GET /api/requests/{id}` con sesión responde `$.origin == "PUBLIC_LINK"`, `$.studentEmail` y `$.studentPhone` **iguales a lo que mandó el formulario** (leerlos del propio `Map` de `filledForm`, no de literales repetidos), y `$.currentState.isFinal == false` (spec US1 escenario 1: en un estado intermedio los hechos ya viajan, el aviso no). Debe fallar porque los tres campos todavía no existen en `RequestResponse` — ✅ RED observado: `No value at JSON path "$.origin"` (`PublicRequestControllerIT:402`); verde tras T017
- [x] T009 [P] [US1] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/PublicRequestControllerIT.java`: la misma solicitud pública se lleva a un estado final con dos `POST /api/requests/{id}/transitions` —`EN_FACULTAD` y después `RECHAZADA`, el camino más corto del seed: `V2.1.0__Seed_workflow_definitions.sql:44` (`REGISTRADA → EN_FACULTAD`, renombrado a `EN_COORDINACION` por `V3.2.0:33`) y `:53` (`EN_FACULTAD → RECHAZADA`)— y **la respuesta del segundo POST** ya trae `$.origin == "PUBLIC_LINK"`, `$.studentEmail`, `$.studentPhone`, `$.currentState.isFinal == true` y `$.availableTransitions` vacío. Es el escenario 2 de US1: el aviso se ofrece con la respuesta de la acción, sin otra consulta. `PublicRequestControllerIT` no tiene helper de avance: construir el POST como `RequestControllerIT.advanceRequest` (`:1239-1248`: `.with(csrf())`, JSON `{"targetStateCode": ...}`) — ✅ RED observado: `No value at JSON path "$.origin"` (`:427`), con `isFinal == true` y `availableTransitions` vacío ya verdes; verde tras T017. Se agregó el helper `advance(id, target)`
- [x] T010 [US1] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`: **invertir a conciencia** `registerPersistsStudentEmailButNeverReturnsIt` (`:252`, aserción `:279`). Se renombra —por ejemplo `registerPersistsAndReturnsStudentEmail`—, su `@DisplayName` deja de decir «NUNCA sale» y pasa a citar FR-008 de la 008, afirma `$.studentEmail` igual al enviado, y **conserva la segunda mitad**, la que verifica que la fila lo guarda. Reescribir también su comentario de cabecera (`:253-266`): hoy explica por qué el correo no salía; debe explicar por qué ahora sale (research D4: el consumidor es esta feature) sin borrar la historia de la 004. Hoy está verde afirmando lo contrario: es el RED de FR-008 en el canal interno — ✅ Invertido: ahora `registerPersistsAndReturnsStudentEmail`; RED observado: `No value at JSON path "$.studentEmail"` (`:278`); verde tras T016–T017. La mitad de la fila se conservó
- [x] T011 [P] [US1] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`: `POST /api/requests` con `studentEmail` **y** `studentPhone` (`"3001234567"`) via `createRequestWithForm` devuelve los dos bajo su clave y `$.origin == "COORDINATION"`. Y `GET /api/requests/{id}` de esa misma solicitud devuelve lo mismo (FR-008: las tres acciones). Falla hoy por los tres campos — ✅ RED observado: `No value at JSON path "$.origin"` (`:1243`); verde tras T017. Fixture `3000000001` (M4)
- [x] T012 [P] [US1] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`: `POST /api/requests` con el cuerpo mínimo de la 002 (sin correo ni teléfono, `registerAndGetId`) devuelve `$.origin == "COORDINATION"` y el cuerpo **crudo** no contiene `"studentEmail"` ni `"studentPhone"` (`content().string(not(containsString("\"studentEmail\"")))`, como hace `:280-282` con el valor). ⚠️ No basta `jsonPath("$.studentEmail").doesNotExist()`: sobre una clave presente con valor `null` esa aserción pasa, y no detectaría el mutante de T020 (confianza media en ese detalle de Spring; T020 lo confirma). Es RED por `origin`; la mitad del contacto está verde hoy y es lo que T020 vigila — ✅ RED observado por `origin`: `No value at JSON path "$.origin"` (`:1264`); la aserción sobre el cuerpo crudo es la que mata a T020(a)
- [x] T013 [P] [US1] **(GUARDA)** En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`: ni `GET /api/requests?search=` ni `GET /api/requests/inbox?responsible=COORDINACION&limit=200` devuelven `studentEmail` ni `studentPhone`, con una solicitud registrada **con** los dos datos en la base. Las dos listas son arreglos de raíz (`$[*]`, ver `:603-607` y `:1094-1098`). Afirmar `jsonPath("$[*].studentEmail").doesNotExist()` **y** que el cuerpo crudo no contiene `"studentEmail"` ni `"studentPhone"`, con el precedente y el comentario de `inboxNeverExposesStudentDocument` (`:1086-1098`): sobre el JSON servido, no sobre el DTO. Está verde desde antes —los DTO no llevan el campo— y por eso no es RED: fija la minimización del §III para que T016 no la rompa por accidente. Su mutante es el (b) de T020 — ✅ GUARDA: verde al escribirse (búsqueda y bandeja sin `studentEmail`/`studentPhone`, JSON crudo incluido). Fixture `3000000002` (M4)
- [x] T014 [P] [US1] **(GUARDA)** En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`, SC-006: registrar, avanzar una vez, leer `GET /api/requests/{id}/timeline` (`$.length()`, forma en `:576-578`), consultar `GET /api/requests/{id}` **dos veces**, y volver a leer el timeline: mismo tamaño. Es la única garantía del back sobre «avisar no deja registro»: consultar los hechos no escribe. Verde hoy; se fija porque T017 empieza a leer el timeline desde `toResponse`, y una lectura que escribiera sería exactamente el error que este test detecta — ✅ GUARDA: verde al escribirse (timeline en 2 antes y después de consultar el detalle dos veces)
- [x] T015 [US1] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/WorkflowGenericityIT.java`, SC-005: `insertDemoV1()` (`:376`, `ABIERTO → CERRADO`), `registerAndGetId(session, "DEMO", ...)` (`:126`), avanzar a `CERRADO`, y la respuesta de la transición trae `$.currentState.isFinal == true`, `$.origin == "COORDINATION"` y `$.availableTransitions` vacío, sin que el código conozca DEMO. Es RED por `origin`. Se afirma `COORDINATION` y no `PUBLIC_LINK` a propósito: `insertDefinition` (`:393`) no siembra `workflow_parameter`, y el canal público exige `PUBLIC_CAPTURE_ENABLED` (`RequestServiceImpl:178-203`); sembrarlo en el test sumaría un camino que SC-005 no necesita —lo que demuestra es que «final» sale de la configuración— — ✅ RED observado: `No value at JSON path "$.origin"` (`WorkflowGenericityIT:228`), con `isFinal` y `availableTransitions` ya verdes; verde tras T017. ⚠️ Desviación: el test no avanza «a CERRADO» sino por la única transición disponible hasta que la configuración diga final, para no depender de si DEMO está en v1 o v2 al correr (los tests de @Order(1)/(2) siembran las dos). Un primer intento usó `toState` y cayó por `PathNotFound`: la propiedad es `targetState`; corregido antes de dar el RED por válido

### Implementación de US1

- [x] T016 [US1] En `src/main/java/com/uniremington/api/tramita/dto/RequestResponse.java`: agregar al final del record `RequestOrigin origin`, `String studentEmail`, `String studentPhone` (aditivo; el `@JsonInclude(NON_NULL)` ya existe). **Reescribir el javadoc** de `:16`: deja de afirmar «NO expone ningún dato de contacto (FR-020…)» —una cita que sobrevivió a su base— y dice por qué los expone ahora: el consumidor existe y es esta feature (FR-008, FR-008a; research D4), y por qué la búsqueda y la bandeja siguen sin llevarlos (§III) — ✅ Aplicado: tres componentes al final con javadoc por campo; javadoc del record reescrito (FR-008a)
- [x] T017 [US1] En `src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java`, `toResponse`: cargar el timeline con `logRepo.findByRequestIdOrderByOccurredAtAscIdAsc(request.getId())` (`src/main/java/com/uniremington/api/tramita/repo/IRequestTransitionLogRepo.java:19`, el mismo método de `getTimeline`), derivar `originOf(timeline)` y pasar `request.getStudentEmail()` y `request.getStudentPhone()` **sin transformarlos** (FR-011). Javadoc con el costo declarado: un SELECT más por cada respuesta de detalle, en las tres acciones, acotado por el largo del timeline; y la optimización anotada y no construida (research D2). No tocar `originOf`: es la regla de la 007 y de ella depende que «origen» signifique lo mismo en la bandeja y en el detalle — ✅ Aplicado: `toResponse` carga el timeline con `findByRequestIdOrderByOccurredAtAscIdAsc` y pasa `originOf(timeline)`, correo y teléfono sin transformar; javadoc con el costo (D2)

### Verificación de US1

- [x] T018 [US1] `./mvnw clean verify` en verde; anotar el conteo en `specs/008-student-closure-notice/tasks.md` (T008–T015 suman IT; ninguno unitario) y comprobar que T008, T009, T010, T011, T012 y T015 pasaron de rojo a verde **por la implementación**, no por editar la aserción — ✅ 2026-09-24: `./mvnw clean verify` → 163 unitarios + 119 IT, `BUILD SUCCESS` (40,7 s); +7 IT sobre T001; los seis RED pasaron a verde por T016–T017 sin tocar aserciones
- [x] T019 [US1] **(MUTANTE)** Invertir la condición de `originOf` en `src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java` (`PUBLIC_LINK` ↔ `COORDINATION`) y comprobar que T008, T009 (por `PUBLIC_LINK`) y T010, T011, T012, T015 (por `COORDINATION`) se ponen **rojos**. Verificar de paso que el IT de la bandeja de la 007 (`PublicRequestControllerIT:257`) también cae: es la prueba de que el detalle y la bandeja comparten la regla. Revertir con `cp` — ✅ Mutante muerto por 7 tests: T008 (`:402`), T009 (`:427`), T011 (`:1243`), T012 (`:1264`), T015 (`:228`) y **dos de la 007**: `publicSubmissionShowsUpInTheInboxWithPublicLinkOrigin:276` e `inboxListsExactlyWhatWaitsForTheResponsible:905` (mensaje típico: `expected:<PUBLIC_LINK> but was:<COORDINATION>`). T010 no cae porque no afirma `origin`. Restaurado con `cp`, `diff -q` idéntico
- [x] T020 [US1] **(MUTANTE)** Dos mutantes sobre la minimización: (a) quitar `@JsonInclude(JsonInclude.Include.NON_NULL)` de `src/main/java/com/uniremington/api/tramita/dto/RequestResponse.java` → T012 se pone **rojo** (las claves aparecen con `null`); si T012 sigue verde, su aserción sobre el cuerpo crudo está mal escrita, no el mutante. (b) agregar `String studentEmail` a `src/main/java/com/uniremington/api/tramita/dto/RequestSummaryResponse.java` y poblarlo → T013 se pone **rojo**. Revertir los dos con `cp` — ✅ (a) muerto por T012 en `:1271`, la aserción sobre el cuerpo crudo (las `doesNotExist` de `:1269-1270` pasaron con las claves en `null`: confirmado lo que T012 anticipaba); (b) muerto por T013 en `:1301`: `Expected no value at JSON path "$[*].studentEmail" but found: ["listado.sin.contacto@ejemplo.test"]`. Ambos restaurados con `cp`, `diff -q` idéntico
- [x] T021 [US1] Commit `feat(008): el detalle expone el origen y el contacto del estudiante`. El cuerpo explica por qué salen ahora (research D4, D5) y nombra el test invertido de T010 con su razón; pie `Refs: #13`, `Refs: specs/008-student-closure-notice/research.md (D2, D4, D5)`, `Verificado:` el conteo de T018 y los dos mutantes de T019–T020 — ✅ 2026-09-24, es el commit que contiene estas marcas (`git log --oneline -1`)

**Checkpoint**: US1 funciona sola. Con este commit el front ya puede armar el `mailto:` desde el detalle (plan.md, «Reparto con el frontend», punto 3).

---

## Phase 4: User Story 2 — Avisarle por WhatsApp (P2)

**Goal**: el back garantiza lo único que la P2 le pide: el teléfono sale **tal como se guardó**, sin reescribirlo (FR-011), para que el cliente decida si es un móvil (`^3[0-9]{9}$`, regla que vive **solo** allá por FR-002/FR-012). Todo lo demás de US2 es del front (research D5).

**Independent Test**: una solicitud cuyo teléfono almacenado no cumple el formato de la 008 lo devuelve verbatim en el detalle y su fila no cambia (spec US2 escenario 3: sin WhatsApp, con correo, sin modificar).

### Tests para US2

- [ ] T022 [US2] **(GUARDA)** En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java` (ya inyecta `JdbcTemplate`, `:54`): registrar una solicitud con `registerAndGetId`, escribirle por SQL un teléfono anterior a la feature —`jdbcTemplate.update("UPDATE request SET student_phone = ? WHERE id = ?::uuid", "300 123 4567", id)`— y comprobar que `GET /api/requests/{id}` devuelve `$.studentPhone == "300 123 4567"` **exacto**, dos veces seguidas. Se escribe por SQL y no por la API a propósito: es **estable en cualquier orden de ejecución** (antes de US3 el endpoint lo aceptaría; después lo rechaza), y representa el caso real, las filas radicadas antes de la feature. Es legal: `request` no tiene trigger de inmutabilidad (`grep -n 'CREATE TRIGGER' src/main/resources/db/migration/*.sql` → solo `request_transition_log` y `request_document_seal`) ni `CHECK` sobre la columna (`grep -n -i 'student_phone' src/main/resources/db/migration/*.sql` → solo el `ADD COLUMN … VARCHAR(30)` de `V3.3.0:31`); `updatable = false` es una promesa de JPA, no de la base. Verde al escribirse: su valor es T023

### Verificación de US2

- [ ] T023 [US2] **(MUTANTE)** En `toResponse` de `src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java`, pasar `request.getStudentPhone().replaceAll("\\D", "")` (con guarda de null) en vez del valor crudo → T022 se pone **rojo**. Es el error que FR-011 prohíbe: normalizar en el servidor. Revertir con `cp`
- [ ] T024 [US2] Commit `test(008): el teléfono sale tal como se guardó, sin normalizar` sobre `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`. Cuerpo: por qué la guarda existe (FR-011, US2-3, filas anteriores a la feature) y por qué es SQL y no API. `Verificado:` la suite y el mutante de T023

**Checkpoint**: US1 y US2 funcionan; el back entrega los hechos de los dos canales.

---

## Phase 5: User Story 3 — Recibir un teléfono utilizable desde la captura (P3)

**Goal**: los dos canales de captura exigen **exactamente diez dígitos** en `studentPhone` (research D3): obligatorio en el público, opcional en el interno. Es la única enmienda **no aditiva** de la feature (FR-013) y la que tiene un consumidor real: el formulario público del front (T002).

**Independent Test**: enviar teléfonos con distintos formatos por los dos canales y comprobar cuáles se aceptan, que el rechazo nombra el campo y no repite el valor, y que un blanco se reporta como ausente y no como inválido.

### Tests para US3 ⚠️ primero el rojo

- [ ] T025 [US3] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/PublicRequestControllerIT.java`, con el patrón de `:161-176` (`$.title`, `$.invalidFields[0]`, `$.missingFields.length()`): (a) cada uno de `"300 123 4567"`, `"+57 3001234567"`, `"300123456"`, `"30012345678"` y `"abcdefghij"` responde **422**, título «Formato inválido», `invalidFields == ["studentPhone"]`, `missingFields` vacío, y el cuerpo **no contiene el valor enviado**; (b) `"   "` responde 422, título «Formato incompleto», `missingFields == ["studentPhone"]`, `invalidFields` vacío —una sola lista, porque la ausencia domina (`ValidationFields`)—; (c) `"3001234567"` y `"6025551234"` responden **201** (un fijo es un contacto válido, FR-012). **En la misma tarea**, el fixture de `filledForm` (`:392`, `"000 000 0000"`) pasa a `"3001234567"`: si no, toda la clase queda roja por la razón equivocada al llegar T028. Hoy `"300 123 4567"` → 201: es el RED de FR-009
- [ ] T026 [P] [US3] **(RED)** En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`, con `createRequestWithForm`: (a) sin `studentPhone` → 201 (FR-010: opcional); (b) `"300 123 4567"` → **400**, título «Petición inválida», `invalidFields == ["studentPhone"]`; (c) `"3001234567"` → 201 y devuelto bajo su clave. Hoy (b) → 201: es el RED de FR-010
- [ ] T027 [P] [US3] En `src/test/java/com/uniremington/api/tramita/shared/exception/PublicCaptureExceptionHandlerTest.java:156`: el `FormBuilder` pasa de `"000 000 0000"` a `"3001234567"`. Ese test valida el record real (`VALIDATOR.validate(body, errors)`, `:117`) y sus cuatro casos afirman listas exactas: sin este cambio, T028 los pone rojos por un teléfono que no es lo que prueban. No es RED ni guarda: es el fixture que T028 obliga a corregir, y se hace antes para que el único rojo de T028 sea el esperado

### Implementación de US3

- [ ] T028 [US3] En `src/main/java/com/uniremington/api/tramita/dto/PublicRequestBody.java:31`: `studentPhone` pasa de `@NotBlank @Size(max = 30)` a `@NotBlank @Pattern(regexp = "[0-9]{10}")`. Importar `jakarta.validation.constraints.Pattern`. Actualizar el javadoc del record donde enumera el campo: diez dígitos en formato nacional colombiano, sin espacios ni prefijo; el sistema no verifica la nacionalidad del número (FR-009; research D3 cita por qué `[0-9]` y no `\d`, y por qué se quita el `@Size`)
- [ ] T029 [P] [US3] En `src/main/java/com/uniremington/api/tramita/dto/CreateRequestBody.java:39`: `studentPhone` pasa de `@Size(max = 30)` a `@Pattern(regexp = "[0-9]{10}")`. Sigue opcional: `@Pattern` considera válido `null`. Dejar escrito en el javadoc que `""` cuenta como «vino e inválido» (400) y que para no declarar teléfono se omite el campo (FR-010, research D3)

### Verificación de US3

- [ ] T030 [US3] `./mvnw clean verify` en verde; anotar el conteo. `git diff --stat` **no toca** `DoFr100LayoutCanaryTest`, `DoFr100RendererTest`, `PdfDeterminismProbeTest` ni `DoFr100FontIsolationTest`: construyen la entidad sin pasar por Bean Validation y uno es un canario con SHA-256 literal (`DoFr100LayoutCanaryTest:60-68`, `KNOWN_DIGEST`) que cambiar el teléfono rompería sin motivo. `grep -rln '000 000 0000' src/test/java` → **exactamente esos cuatro archivos** (hoy son seis)
- [ ] T031 [US3] **(MUTANTE)** `[0-9]{10}` → `[0-9]{9,10}` en `src/main/java/com/uniremington/api/tramita/dto/PublicRequestBody.java` → el caso `"300123456"` de T025 se pone **rojo**. Revertir con `cp`
- [ ] T032 [US3] **(MUTANTE)** Quitar el `@Pattern` de `src/main/java/com/uniremington/api/tramita/dto/CreateRequestBody.java` → el caso (b) de T026 se pone **rojo**. Revertir con `cp`
- [ ] T033 [US3] Commit `feat(008): la captura exige un teléfono de diez dígitos` sobre `src/main/java/com/uniremington/api/tramita/dto/` y los dos IT. El cuerpo **declara la enmienda no aditiva** de los contratos de la 004 y del interno, con el consumidor real medido en T002 y la condición de despliegue (research D9). Precedente medido: el repositorio **no usa** el pie `BREAKING CHANGE:` (`git log --oneline --grep='BREAKING'` → 0 commits) y el commit que reusó la bandeja en la 007 (`b70980d`) tampoco lo declaró en el cuerpo —la declaración vivió en `plan.md` y en el contrato—. Acá se escribe en el cuerpo además del plan **y se usa por primera vez el pie `BREAKING CHANGE:`** que `.gitmessage` prevé para esto (qué se rompe: `300 123 4567` pasa de 201 a 422/400; cómo adaptarse: mandar diez dígitos, el filtro del front), porque esta vez hay un cliente que se rompe. La 007 no lo necesitó: nadie consumía el endpoint. `Verificado:` la suite de T030 y los mutantes de T031–T032

**Checkpoint**: las tres historias funcionan, cada una verificable por separado.

---

## Phase 6: Cierre y verificación transversal

- [ ] T034 Comprobar que la última migración del repositorio **sigue siendo `V4.1.0`**: `ls src/main/resources/db/migration/ | sort | tail -1`. Si aparece una nueva, se salió del diseño (research D6)
- [ ] T035 **La tesis sigue en pie**: el comando de T003 devuelve **la misma única línea** (`DoFr100Renderer.java:166`). Y el de los responsables, `git grep -nE '"(COORDINACION|FACULTAD|REGISTRO_CALI|REGISTRO_NACIONAL)"' HEAD -- 'src/main/java/*.java'`, sigue en **cero**: nada de esta feature reconoce un estado ni un responsable por su código (research D7)
- [ ] T036 Suite completa: `docker start tramita-postgres && ./mvnw clean verify`. Anotar el conteo final y compararlo con la línea base de T001: la diferencia son los IT de T008–T015, T022, T025 y T026
- [ ] T037 Recorrer `specs/008-student-closure-notice/quickstart.md` **contra el servidor arriba** (perfil `dev`), los diez pasos, incluido el `jq` del paso 4 que arma el `mailto:` y el `wa.me` con lo que devolvió el API. Es lo que ningún test cubre: que el cliente real tenga todo lo que necesita con una sola llamada. Corregir en el documento cualquier divergencia y anotar la fecha de la corrida
- [ ] T038 [P] Enmendar `docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md` para que deje de describir el aviso como correo automático con chat de respaldo (spec, *Assumptions*, «El árbol de problemas queda desactualizado»; precedente PR #44). Re-ubicar primero las líneas con `grep -n 'SP7\|puerto de notificación\|adaptador email' docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md`; hoy son cuatro: la fila SP7 (`:134`, «Adaptador canónico: correo institucional automático + acción opcional de chat con plantilla»), la decisión E3-p2 (`:164`, «El adaptador de chat con plantilla … es decisión de ingeniería del equipo»), la nota de riesgo (`:181`, «El adaptador email es el canónico del puerto de notificación») y la fila de riesgos (`:216`, «Si SMTP no está disponible a tiempo, el puerto de notificación queda sin implementar»). Lo que dicen ahora: dos acciones manuales de la Coordinación —correo prellenado, lo que pidió (Q22–Q23), y WhatsApp, decisión del equipo—, sin envío automático ni puerto; la dependencia de SMTP (Q34) deja de ser un riesgo del MVP porque el sistema no envía. No borrar el registro de lo que se creía: enmendar con fecha, como hizo la PR #44 con el plazo
- [ ] T039 [P] Reconciliar los artefactos con lo implementado (precedente `223f256`): `specs/008-student-closure-notice/spec.md:7` pasa a «Implementada»; `plan.md`, `research.md`, `data-model.md` y `contracts/openapi.yaml` se corrigen si algo cambió al implementar (número de línea, nombre de test, conteo). Si algo cambió, **se corrige el documento**, no se deja la afirmación vieja
- [ ] T040 Review con agente limpio sobre el rango de la feature (`git log --oneline 412a5e0..HEAD`), con la skill `review-agente-limpio` (`.claude/skills/review-agente-limpio/SKILL.md`) y su reglamento en `docs/workflow/code-review-agente-limpio.md`. **El informe no se aplica, se confirma primero**: cada hallazgo se reproduce (mutante o comando) antes de tocar código; los confirmados entran en un commit propio, como `cb85fd6` en la 007
- [ ] T041 Preparar el cuerpo de la PR **sin abrirla ni pushear hasta el OK del usuario** (la rama es local; `main` está protegida y todo va por PR con el check `build`). Contiene: `Closes #13` en **texto plano, nunca entre backticks** (una PR de este repo ya lo puso con backticks y el issue sobrevivió abierto); la **condición de despliegue** de D9 con el resultado de T002 (el filtro del front entra antes o a la vez; sin él, el formulario público recibe 422 por `300 123 4567`); la **enmienda de los criterios de cierre del #13** («puerto como interfaz» y «solo en FINALIZADO», spec *Assumptions*); la decisión sobre `#38` y `#39` —describen el puerto de `router-ia`, que no se construye; **se decide con el usuario**, no acá—; y el brief para Codex, que es la sección «Reparto con el frontend» de `plan.md`. **Sin `--milestone`**: el milestone vive en el issue y la PR llega al tablero por el `Closes`

---

## Dependencias

```text
Phase 1 (T001–T003)
  └─► Phase 2 (T004–T007)     ← bloquea a todas las historias: US1 necesita RequestOrigin
        ├─► US1 (T008–T021)   🎯 MVP
        │     └─► US2 (T022–T024)   depende de US1: afirma sobre el studentPhone que T016 expone
        └─► US3 (T025–T033)   INDEPENDIENTE de US1 y US2 en sus tests; T025 y T026 no dependen de
              │               RequestResponse. Comparte RequestControllerIT con US1: coordinar
              └─► Phase 6 (T034–T041)
```

- **US2 depende de US1**: sin `studentPhone` en el detalle no hay nada que afirmar verbatim.
- **US3 no depende de US1 ni de US2**: sus tests atacan los cuerpos de entrada, no la respuesta. El único punto de contacto es (c) de T026, que afirma «devuelto bajo su clave», y eso sí necesita T016; si US3 se hace primero, esa mitad se escribe después.
- **T034–T036 son verificaciones, no implementación**: si fallan, el error está en una tarea anterior.
- **T040 va antes de T041**: la PR se abre con las correcciones del review ya commiteadas, como en la 007.

## Oportunidades de paralelismo

- T002 y T003 son mediciones independientes: `[P]`.
- T009 se puede escribir junto con T008 (misma clase, casos distintos); T011, T012, T013 y T014 son casos independientes dentro de `RequestControllerIT`: `[P]` entre sí, pero **una sola persona edita ese archivo a la vez**.
- T026 y T027 no tocan los archivos de T025.
- T029 no toca el archivo de T028.
- T038 y T039 son documentos distintos y pueden ir en paralelo con T037.
- **US3 completa** puede hacerse antes o en paralelo con US1 si hay dos personas: comparten `RequestControllerIT` (coordinar) y nada más.

## Estrategia de entrega

**MVP = Phase 1–3 (US1).** Con T001–T021 el detalle ya trae los hechos que el front necesita para el correo prellenado, que es el canal que pidió la Coordinación y el 100 % del valor que justifica la feature (spec, US1: «Sin esta historia no hay aviso»). US2 fija que el teléfono no se toca y US3 hace que el teléfono nuevo sirva.

**Una sola PR, no tres.** Las tres historias cambian los mismos dos archivos de producción (`RequestResponse`, `RequestServiceImpl`) y los mismos dos IT, y la enmienda del teléfono (US3) es lo que vuelve útil el `studentPhone` que US1 expone. Partirla en PRs dejaría entre una y otra un detalle que expone teléfonos que el API todavía acepta sin forma. Los commits de unidad de trabajo (T007, T021, T024, T033, más los de la fase 6) conservan la trazabilidad dentro de la PR; `squash` está deshabilitado en el repo justamente para eso.

Si el tiempo aprieta, el orden de sacrificio es el inverso al de prioridad: primero se difiere US3 —queda como issue propio, con la enmienda del contrato ya declarada en el plan—, después US2, que es una guarda y un mutante. **US1 no se difiere**: sin ella la feature no existe.
