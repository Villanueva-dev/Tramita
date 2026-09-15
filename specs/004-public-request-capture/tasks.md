---

description: "Task list — Captura pública del formato DO-FR-100 (004)"
---

# Tasks: Captura pública del formato DO-FR-100

**Input**: Design documents from `/specs/004-public-request-capture/`

**Prerequisites**: plan.md, spec.md, research.md (D1–D9), data-model.md, contracts/openapi.yaml

**Tests**: incluidos y **obligatorios**. El proyecto trabaja en TDD estricto (constitución §V).

**Organization**: agrupadas por user story. Cada historia es entregable y verificable por
separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivo distinto, sin dependencia pendiente)
- **[Story]**: a qué user story pertenece (US1..US3)
- Toda tarea lleva su ruta de archivo exacta

## Reglas de ejecución de esta feature

1. **El RED se evidencia siempre con `clean`.** `./mvnw clean test` o
   `./mvnw clean verify`. El incremental de Maven ya produjo un `BUILD SUCCESS` falso en este
   repositorio: sin `clean`, un RED puede no existir.
2. **Un test cuyo nombre afirma una garantía debe tener la aserción que la verifica.** Antes
   de pasar a verde, atacá el test con un mutante que ataque exactamente lo que su nombre
   afirma. Si el mutante sobrevive, el test no vale.
3. **Los IT nuevos copian el encabezado de `AuthControllerIT`** — las mismas properties
   literales, para compartir el contexto de Spring cacheado. Un bloque distinto levanta un
   contexto nuevo y multiplica el tiempo de la suite.
4. **Cada test de límites usa un origen o un dato propio del escenario.** Los IT comparten
   contexto: un test que agota el contador de una clave contamina a los demás.
5. **Ningún dato personal real** en tests, fixtures ni comandos del quickstart. El
   repositorio es público (§III).
6. **El filtro nuevo NO lleva estereotipo.** Es la excepción documentada del §II: un filtro
   anotado se auto-registra además en la cadena del servlet y corre dos veces por petición.

---

## Phase 1: Setup

**Propósito**: partir de un baseline confiable.

- [ ] T001 Confirmar baseline verde en la rama antes de tocar nada: `docker start tramita-postgres && ./mvnw clean verify`. Anotar el conteo de tests de partida (surefire y failsafe por separado, desde `target/surefire-reports/` y `target/failsafe-reports/`), para poder afirmar al cierre cuántos se agregaron sin citar un número de memoria.

---

## Phase 2: Foundational (Prerrequisitos bloqueantes)

**No hay tareas en esta fase, y es deliberado.** Esta feature es **aditiva sobre un motor ya
construido**: no crea entidades, no agrega dependencias y no altera ningún contrato existente.
Las tres historias tocan archivos distintos y ninguna necesita infraestructura que la otra
tenga que construir primero. Declararlo explícitamente evita que alguien invente una fase de
andamiaje que el diseño no necesita (§I).

---

## Phase 3: User Story 1 — El estudiante entrega el formato firmado sin tener cuenta (P1) 🎯 MVP

**Objetivo**: un visitante sin sesión envía el formato DO-FR-100 firmado y queda registrado
como solicitud del motor, con el histórico nombrando al portal como responsable del tramo
inicial.

**Test independiente**: `curl` sin cookie de sesión y sin cabecera CSRF contra el endpoint
público devuelve `201`, y la solicitud aparece al buscarla por nombre con la sesión de la
Coordinación.

### RED — el canal no existe

- [ ] T002 [US1] Crear `src/test/java/com/uniremington/api/tramita/controller/PublicRequestControllerIT.java` con el encabezado de `AuthControllerIT` (mismas properties literales, `@AutoConfigureMockMvc`, `@Import(TestcontainersConfiguration.class)`) y un test `publicSubmissionWithoutSessionOrCsrfIsAccepted` que hace `POST /api/public/requests/ADICION_CREDITOS` **sin `.session(...)` y sin `.with(csrf())`** y espera `201`. Debe fallar: la ruta no existe.
- [ ] T003 [P] [US1] En el mismo IT, `publicReceiptCarriesNoIdentifierNorState`: el cuerpo del `201` no contiene `id`, `currentState` ni `definition`, y la respuesta **no trae cabecera `Location`** (FR-008, D5).
- [ ] T004 [P] [US1] En el mismo IT, `submissionWithoutSignatureIsRejected`: envío sin `signature` → `422` en `application/problem+json`, y `requestRepo.count()` no cambia (FR-003).
- [ ] T005 [P] [US1] En el mismo IT, `submissionWithoutEmailIsRejected` y `submissionWithoutCommitmentsIsRejected` → `422` cada uno, sin registrar (FR-003, FR-005).
- [ ] T006 [P] [US1] En el mismo IT, `tradeWithoutPublicCaptureReturnsNotFound`: `POST /api/public/requests/NOVEDAD_NOTAS` → `404` (FR-002, D1).
- [ ] T007 [P] [US1] En el mismo IT, `bodyCannotOverrideTheTradeFromThePath`: un cuerpo que incluye `definitionCode` con otro trámite se registra igualmente bajo el de la ruta (FR-002a).
- [ ] T008 [P] [US1] En el mismo IT, `publicSubmissionTimelineNamesThePortalActor`: el tramo inicial del histórico tiene `fromState` nulo y `actorEmail` igual a `portal-publico@tramita.local` (FR-009, FR-010, §VII).
- [ ] T009 [P] [US1] En `src/test/java/com/uniremington/api/tramita/controller/AuthControllerIT.java`, agregar `portalAccountCannotAuthenticate`: intentar iniciar sesión con `portal-publico@tramita.local` devuelve `401` (FR-011, D4).
- [ ] T010 [US1] Ejecutar `./mvnw clean verify` y **dejar constancia del RED**: qué tests fallan y con qué error. Cada fallo debe deberse a la causa esperada (ruta inexistente, columna inexistente), no a otra.

### GREEN — abrir el canal

- [ ] T011 [US1] Crear `src/main/resources/db/migration/V3.3.0__Enable_public_capture.sql`: `ALTER TABLE request` con `student_email VARCHAR(255)` y `student_signature TEXT` (ambas opcionales, como las de `V2.3.0`); `INSERT` de `PUBLIC_CAPTURE_ENABLED='true'` en `workflow_parameter` solo para `ADICION_CREDITOS` v1; `INSERT` de la fila de identidad en `users` con `active = FALSE` y un `password_hash` sin prefijo de algoritmo. Escribir en la migración **por qué** entra el correo ahora (el consumidor existe) y **por qué no entra el teléfono** (no lo tiene), citando el precedente de `V2.3.0`.
- [ ] T012 [US1] Agregar a `src/main/java/com/uniremington/api/tramita/model/Request.java` los campos `studentEmail` y `studentSignature`, ambos `updatable = false` como el resto de los datos de captura.
- [ ] T013 [P] [US1] Ampliar `src/main/java/com/uniremington/api/tramita/dto/CreateRequestBody.java` con `studentEmail` y `signature` opcionales. El constructor de compatibilidad existente no se toca (FR-006 de la 002).
- [ ] T014 [P] [US1] Crear `src/main/java/com/uniremington/api/tramita/dto/PublicRequestBody.java` **sin `definitionCode`**, con las validaciones del contrato: nombre, documento, correo (formato de email), compromisos y firma obligatorios.
- [ ] T015 [P] [US1] Crear `src/main/java/com/uniremington/api/tramita/dto/PublicReceiptResponse.java` con un único campo de mensaje.
- [ ] T016 [US1] Agregar a `src/main/java/com/uniremington/api/tramita/service/IRequestService.java` la operación de registro público, y su implementación en `service/impl/RequestServiceImpl.java`: resolver la definición por código, **verificar `PUBLIC_CAPTURE_ENABLED`** con la misma semántica que `capturesCredits` (ausente = no habilitado; valor no interpretable = configuración rota, 500), mapear a `CreateRequestBody` y **delegar en el `register()` existente** con el correo del portal como actor.
- [ ] T017 [US1] Crear `src/main/java/com/uniremington/api/tramita/controller/PublicRequestController.java` con `POST /api/public/requests/{definitionCode}` devolviendo `201` **sin cabecera `Location`**.
- [ ] T018 [US1] En `src/main/java/com/uniremington/api/tramita/shared/config/SecurityConfig.java`: `permitAll` para ese método y ruta, y excluirla de CSRF. Documentar en el propio código la justificación de D2 — sin sesión no hay identidad que suplantar — y que la exclusión está acotada a esa ruta y no es precedente para ninguna otra.
- [ ] T019 [US1] Ejecutar `./mvnw clean verify` hasta que T002–T009 pasen. Ningún test preexistente puede quedar en rojo.

### Verificación con mutantes

- [ ] T020 [US1] Atacar T006 y T008 con un mutante cada uno y confirmar que cae el test cuyo nombre lo afirma, y **ningún otro**: (a) poner `PUBLIC_CAPTURE_ENABLED='false'` en adición de créditos debe romper el `201` y no el `404`; (b) usar la cuenta de la Coordinación como actor del tramo inicial debe romper solo `publicSubmissionTimelineNamesThePortalActor`. Registrar el resultado de cada mutante.

---

## Phase 4: User Story 2 — La Coordinación se entera de que llegó algo nuevo (P2)

**Objetivo**: ver las solicitudes recientes sin escribir ningún criterio, y sin exponer
documentos de identidad.

**Test independiente**: con sesión, `GET /api/requests/inbox` devuelve las solicitudes de la
más nueva a la más vieja, y ningún objeto del arreglo tiene `studentDocument`.

### RED

- [ ] T021 [US2] En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`, agregar `inboxListsRecentRequestsNewestFirst`: con sesión, `GET /api/requests/inbox` **sin parámetros** devuelve `200` y las solicitudes ordenadas por fecha de creación descendente (FR-012, FR-013).
- [ ] T022 [P] [US2] En el mismo IT, `inboxNeverExposesStudentDocument`: **aserción sobre el JSON de la respuesta**, no sobre el DTO — ningún elemento del arreglo tiene la clave `studentDocument` (FR-014, D8).
- [ ] T023 [P] [US2] En el mismo IT, `inboxRequiresAnAuthenticatedSession`: sin sesión → `401` (FR-015).
- [ ] T024 [US2] Ejecutar `./mvnw clean verify` y dejar constancia del RED.

### GREEN

- [ ] T025 [P] [US2] Crear `src/main/java/com/uniremington/api/tramita/dto/InboxEntryResponse.java`: igual a `RequestSummaryResponse` **menos** `studentDocument`. Documentar en el javadoc por qué no es duplicación (dos contratos con reglas de exposición distintas) y citar la razón que ya está escrita en `IRequestRepo`.
- [ ] T026 [US2] Agregar a `src/main/java/com/uniremington/api/tramita/repo/IRequestRepo.java` una consulta de recientes acotada por `Limit`, ordenada por fecha de creación descendente.
- [ ] T027 [US2] Agregar la operación a `service/IRequestService.java` e implementarla en `service/impl/RequestServiceImpl.java`, mapeando a `InboxEntryResponse`.
- [ ] T028 [US2] Agregar `GET /api/requests/inbox` a `src/main/java/com/uniremington/api/tramita/controller/RequestController.java`. **Declararlo antes de `@GetMapping("/{id}")`** o verificar que el patrón de ruta no colisione: `inbox` no debe resolverse como un identificador.
- [ ] T029 [US2] Ejecutar `./mvnw clean verify` hasta que T021–T023 pasen.

### Verificación con mutantes

- [ ] T030 [US2] Atacar T022 con un mutante: agregar `studentDocument` al DTO de la bandeja. Debe caer `inboxNeverExposesStudentDocument` y **ningún otro test**. Si sobrevive, la aserción está mirando el DTO y no el JSON.

---

## Phase 5: User Story 3 — El canal abierto resiste el abuso (P3)

**Objetivo**: limitar envíos por origen y tamaño del cuerpo, con error tipado y bloqueo que
expira solo.

**Test independiente**: superar el umbral desde un origen devuelve `429` con `Retry-After`;
un cuerpo desmesurado devuelve `413`; otros orígenes siguen funcionando.

**Depende de**: US1 — protege el endpoint que aquella crea.

### RED

- [ ] T031 [US3] Crear `src/test/java/com/uniremington/api/tramita/security/PublicSubmissionThrottlingFilterTest.java` siguiendo el patrón de `LoginThrottlingFilterTest` (mocks de servlet de Spring Test, sin contexto de Spring): un cuerpo por encima del tope devuelve `413` y **no llega a la cadena**.
- [ ] T032 [P] [US3] En `PublicRequestControllerIT`, `oversizedSubmissionIsRejected`: cuerpo mayor al tope → `413` (FR-017).
- [ ] T033 [P] [US3] En `PublicRequestControllerIT`, `tooManySubmissionsFromSameOriginAreThrottled`: superar el umbral → `429`, con cabecera `Retry-After` y `application/problem+json` (FR-016, FR-018).
- [ ] T034 [P] [US3] En `src/test/java/com/uniremington/api/tramita/service/impl/SlidingWindowCounterTest.java`, cubrir la expiración con un reloj mutable como el de `LoginAttemptServiceTest`: pasada la ventana, la clave deja de estar bloqueada sin intervención (FR-019).
- [ ] T035 [US3] Ejecutar `./mvnw clean verify` y dejar constancia del RED.

### GREEN

- [ ] T036 [US3] Crear `src/main/java/com/uniremington/api/tramita/service/impl/SlidingWindowCounter.java` con la mecánica de ventana deslizante extraída de `LoginAttemptService` (registro, consulta de bloqueo, segundos restantes, purga), con nombres neutros.
- [ ] T037 [US3] Hacer que `src/main/java/com/uniremington/api/tramita/service/impl/LoginAttemptService.java` delegue en el contador, **conservando su API pública intacta**. Los 14 puntos de uso en 5 archivos no se tocan; las 3 suites que lo cubren deben seguir verdes sin editarlas.
- [ ] T038 [US3] Crear `src/main/java/com/uniremington/api/tramita/security/PublicSubmissionThrottlingFilter.java` calcado de `LoginThrottlingFilter`: `OncePerRequestFilter`, matcher por método y ruta, tope de 256 KB leyendo un byte de más, `429` con `Retry-After` y `413` en `problem+json`. **Sin estereotipo** (regla 6).
- [ ] T039 [US3] Registrarlo en `SecurityConfig` con `new` y `addFilterBefore`, antes del punto donde se resuelve la petición pública.
- [ ] T040 [US3] Ejecutar `./mvnw clean verify` hasta que T031–T034 pasen, y confirmar que las suites de throttling del login siguen verdes **sin haberlas editado**.

### Verificación con mutantes

- [ ] T041 [US3] Atacar con dos mutantes: (a) anotar el filtro nuevo con `@Component` — debe hacerse evidente el doble conteo (el `429` llega a la mitad de los envíos configurados); (b) subir el tope a un valor enorme debe romper solo el test del `413`. Registrar ambos resultados.

---

## Phase 6: Polish

- [ ] T042 [P] Recorrer `specs/004-public-request-capture/quickstart.md` de punta a punta contra la instancia local y corregir cualquier comando que no devuelva lo que el documento afirma. Un comando citado como prueba debe poder re-ejecutarse y dar el mismo resultado.
- [ ] T043 [P] Verificar que `contracts/openapi.yaml` describe lo que quedó implementado, en particular los códigos de error de cada endpoint. Si algo divergió, gana el código y se corrige el contrato.
- [ ] T044 Verificar la ausencia de datos personales reales en todo lo agregado: `git diff main --stat` y revisión de los tests y del quickstart (§III).
- [ ] T045 Medir el conteo final de tests desde `target/surefire-reports/` y `target/failsafe-reports/` y compararlo con el baseline de T001. Usar el número **medido**, no uno recordado, en el cuerpo del commit y en la PR.

---

## Dependencias

```
Setup (T001)
   │
   ├─► US1  (T002–T020)   MVP — entregable sola
   │      │
   │      └─► US3  (T031–T041)   protege el endpoint de US1
   │
   └─► US2  (T021–T030)   independiente de US1 y de US3
            
Polish (T042–T045)  ← después de las historias que se entreguen
```

- **US1 y US2 son independientes entre sí**: tocan archivos distintos y pueden hacerse en
  cualquier orden, o en paralelo si hubiera dos personas.
- **US3 depende de US1**: no tiene sentido proteger un endpoint que no existe.

## Paralelismo dentro de cada historia

- **US1**: T003–T009 son tests en archivos distintos o métodos independientes → `[P]`.
  T013–T015 son tres archivos nuevos sin dependencia entre sí → `[P]`.
- **US2**: T022 y T023 son métodos independientes; T025 es un archivo nuevo.
- **US3**: T032–T034 tocan tres archivos distintos.

## Estrategia de entrega

**MVP = US1.** Entrega valor sola: el estudiante entrega el formato y deja de existir el paso
de transcripción. Aun sin la bandeja, la Coordinación encuentra la solicitud con la búsqueda
que ya tiene.

Sugerencia de división en PR, por el presupuesto de revisión de ~400 líneas:

| PR | Contenido | Por qué corta ahí |
|---|---|---|
| 1 | US1 completa (T002–T020) | Es el MVP y se demuestra solo. La migración y el endpoint van juntos: por separado ninguno es verificable. |
| 2 | US2 completa (T021–T030) | Independiente. Se puede revisar sin conocer la US1. |
| 3 | US3 completa (T031–T041) + Polish | El refactor del contador entra con su consumidor, no antes: extraer una clase sin quien la use es difícil de justificar en revisión. |

## Criterios de cierre

No son tareas —no llevan identificador ni se ejecutan por separado— sino las condiciones que
deben cumplirse para dar la feature por entregada:

1. `./mvnw clean verify` en verde, con el conteo de tests **medido** al cerrar, no recordado.
2. Los seis mutantes de T020, T030 y T041 ejecutados, cada uno cayendo en el test cuyo nombre
   lo afirma y en ningún otro.
3. El quickstart recorrido entero contra la instancia local.
4. Cero datos personales reales en el diff.
5. Ningún test preexistente editado para que pase.
