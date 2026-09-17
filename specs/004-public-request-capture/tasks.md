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

- [x] T001 Confirmar baseline verde en la rama antes de tocar nada: `docker start tramita-postgres && ./mvnw clean verify`. Anotar el conteo de tests de partida (surefire y failsafe por separado, desde `target/surefire-reports/` y `target/failsafe-reports/`), para poder afirmar al cierre cuántos se agregaron sin citar un número de memoria.

  **Baseline medido el 2026-09-16 sobre `6e4ac1a`** — `docker start tramita-postgres && ./mvnw clean verify` → `BUILD SUCCESS` en 31,9 s:

  | Suite | Clases | Tests | Fallos | Errores | Omitidos |
  |---|---|---|---|---|---|
  | `target/surefire-reports/` (unitarios) | 7 | **57** | 0 | 0 | 0 |
  | `target/failsafe-reports/` (IT) | 5 | **50** | 0 | 0 | 0 |

  Al cierre de la feature, la cantidad de tests agregados se afirma restando contra estos dos números, no contra un recuerdo.

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

- [x] T002 [US1] Crear `src/test/java/com/uniremington/api/tramita/controller/PublicRequestControllerIT.java` con el encabezado de `AuthControllerIT` (mismas properties literales, `@AutoConfigureMockMvc`, `@Import(TestcontainersConfiguration.class)`) y un test `publicSubmissionWithoutSessionOrCsrfIsAccepted` que hace `POST /api/public/requests/ADICION_CREDITOS` **sin `.session(...)` y sin `.with(csrf())`** y espera `201`. Debe fallar: la ruta no existe.
- [x] T003 [P] [US1] En el mismo IT, `publicReceiptCarriesNoIdentifierNorState`: el cuerpo del `201` no contiene `id`, `currentState` ni `definition`, y la respuesta **no trae cabecera `Location`** (FR-008, D5).
- [x] T004 [P] [US1] En el mismo IT, `submissionWithoutSignatureIsRejected`: envío sin `signature` → `422` en `application/problem+json`, y `requestRepo.count()` no cambia (FR-003).
- [x] T004a [P] [US1] En el mismo IT, `submissionWithAnyBlankMandatoryFieldIsRejected`: **un caso por cada uno de los once campos obligatorios**, cada uno enviado como cadena vacía y como cadena de solo espacios → `422`, y `requestRepo.count()` no cambia. Es la aserción que hace cumplir «ningún campo puede quedar vacío» (FR-003, D10); sin ella, `@NotNull` pasaría por buena una cadena vacía.
- [x] T005 [P] [US1] En el mismo IT, `submissionWithoutEmailIsRejected` y `submissionWithoutCommitmentsIsRejected` → `422` cada uno, sin registrar (FR-003, FR-005).
- [x] T006 [P] [US1] En el mismo IT, `tradeWithoutPublicCaptureReturnsNotFound`: `POST /api/public/requests/NOVEDAD_NOTAS` → `404` (FR-002, D1).
- [x] T007 [P] [US1] En el mismo IT, `bodyCannotOverrideTheTradeFromThePath`: un cuerpo que incluye `definitionCode` con otro trámite se registra igualmente bajo el de la ruta (FR-002a).
- [x] T008 [P] [US1] En el mismo IT, `publicSubmissionTimelineNamesThePortalActor`: el tramo inicial del histórico tiene `fromState` nulo y `actorEmail` igual a `portal-publico@tramita.local` (FR-009, FR-010, §VII).
- [x] T009 [P] [US1] En `src/test/java/com/uniremington/api/tramita/controller/AuthControllerIT.java`, agregar `portalAccountCannotAuthenticate`: intentar iniciar sesión con `portal-publico@tramita.local` devuelve `401` (FR-011, D4).
- [x] T010 [US1] Ejecutar `./mvnw clean verify` y **dejar constancia del RED**: qué tests fallan y con qué error. Cada fallo debe deberse a la causa esperada (ruta inexistente, columna inexistente), no a otra.

  **RED registrado el 2026-09-16** — `./mvnw clean verify` → `BUILD FAILURE`, `Tests run: 60, Failures: 9`.

  Los **nueve** tests de `PublicRequestControllerIT` fallan, y los nueve con **el mismo error**:

  ```
  java.lang.AssertionError: Status expected:<201> but was:<403>   (T002, T003, T007, T008)
  java.lang.AssertionError: Status expected:<422> but was:<403>   (T004, T004a, T005 ×2)
  java.lang.AssertionError: Status expected:<404> but was:<403>   (T006)
  ```

  **La causa es la esperada**: el `403` lo produce el filtro CSRF rechazando un `POST` sin token, porque la ruta `/api/public/requests/**` todavía no está permitida ni exceptuada en `SecurityConfig`. Ninguno falla por una causa distinta —columna ausente, error de deserialización o `NullPointerException`—, que es lo que esta tarea existe para descartar.

  **T009 (`portalAccountCannotAuthenticate`) pasa en VERDE desde ya**, y es correcto: hoy la garantía se cumple por ausencia —la fila del portal no existe, y un email desconocido ya da `401` genérico anti-enumeración—. Su valor es de regresión: **debe seguir en verde después de T011**, que es cuando la fila pasa a existir. Si se pusiera en rojo ahí, la fila sintética sería una cuenta usable.

  Conteo de partida de la suite de IT: 50 → 60 (nueve del canal público más T009).

### GREEN — abrir el canal

- [x] T011 [US1] Crear `src/main/resources/db/migration/V3.3.0__Enable_public_capture.sql`: `ALTER TABLE request` con las **seis** columnas —`student_email VARCHAR(255)`, `student_signature TEXT`, `student_phone VARCHAR(30)`, `campus VARCHAR(120)`, `faculty VARCHAR(120)`, `modality VARCHAR(50)`—, todas opcionales como las de `V2.3.0`; `INSERT` de `PUBLIC_CAPTURE_ENABLED='true'` en `workflow_parameter` solo para `ADICION_CREDITOS` v1; `INSERT` de la fila de identidad en `users` con `active = FALSE` y un `password_hash` sin prefijo de algoritmo. Escribir en la migración **por qué entran ahora** los campos que `V2.3.0` dejó fuera: el consumidor es el PDF formal del SP3 (`Tramita#10`), ver D10. Y **por qué quedan nullable** pese a ser obligatorias en el canal público: la migración corre sobre filas existentes que no las tienen.
- [x] T012 [US1] Agregar a `src/main/java/com/uniremington/api/tramita/model/Request.java` los seis campos —`studentEmail`, `studentSignature`, `studentPhone`, `campus`, `faculty`, `modality`—, todos `updatable = false` como el resto de los datos de captura.
- [x] T013 [P] [US1] Ampliar `src/main/java/com/uniremington/api/tramita/dto/CreateRequestBody.java` con los seis campos **opcionales**. El constructor de compatibilidad existente no se toca (FR-006 de la 002): que sean opcionales acá y obligatorios en el canal público es deliberado.
- [x] T014 [P] [US1] Crear `src/main/java/com/uniremington/api/tramita/dto/PublicRequestBody.java` **sin `definitionCode`**, con las validaciones del contrato: **once campos obligatorios** —nombre, documento, correo (formato de email), contacto, programa, sede, facultad, modalidad, semestre, compromisos y firma—, todos con su longitud máxima. `studentCode` es el único opcional. Usar `@NotBlank` y no `@NotNull`: un valor de solo espacios no cuenta como diligenciado (FR-003).
- [x] T015 [P] [US1] Crear `src/main/java/com/uniremington/api/tramita/dto/PublicReceiptResponse.java` con un único campo de mensaje.
- [x] T016 [US1] Agregar a `src/main/java/com/uniremington/api/tramita/service/IRequestService.java` la operación de registro público, y su implementación en `service/impl/RequestServiceImpl.java`: resolver la definición por código, **verificar `PUBLIC_CAPTURE_ENABLED`** con la misma semántica que `capturesCredits` (ausente = no habilitado; valor no interpretable = configuración rota, 500), mapear a `CreateRequestBody` y **delegar en el `register()` existente** con el correo del portal como actor.
- [x] T017 [US1] Crear `src/main/java/com/uniremington/api/tramita/controller/PublicRequestController.java` con `POST /api/public/requests/{definitionCode}` devolviendo `201` **sin cabecera `Location`**.
- [x] T018 [US1] En `src/main/java/com/uniremington/api/tramita/shared/config/SecurityConfig.java`: `permitAll` para ese método y ruta, y excluirla de CSRF. Documentar en el propio código la justificación de D2 — sin sesión no hay identidad que suplantar — y que la exclusión está acotada a esa ruta y no es precedente para ninguna otra.
- [x] T019 [US1] Ejecutar `./mvnw clean verify` hasta que T002–T009 pasen. Ningún test preexistente puede quedar en rojo.

  **GREEN el 2026-09-16** — `./mvnw clean verify` → `BUILD SUCCESS`: **57 unitarios + 60 IT, 0 fallos**. Los nueve del canal público pasan y ningún preexistente quedó en rojo (`RequestControllerIT` 33/33, `AuthControllerIT` 9/9).

  **Tres cosas costaron un ciclo rojo cada una, y las tres están escritas donde se repetirían**:

  1. **El hash sin prefijo de algoritmo NO devuelve 401: lanza `IllegalArgumentException` → 500.** `research.md` D4 lo describía como «defensa en profundidad» suponiendo que la cuenta inactiva cortaba antes; en Spring Security 7 el orden es `performPreCheck → additionalAuthenticationChecks`, o sea que **la contraseña se evalúa ANTES que el estado de la cuenta** (`AbstractUserDetailsAuthenticationProvider:159→191`). Corregido a `{bcrypt}` + contenido no-BCrypt, que sí devuelve `false` limpio. Queda documentado en la propia migración.
  2. **Una constraint en el `@PathVariable` desviaba el 422 a un 400.** Basta una anotación de validación en cualquier parámetro para que Spring valide el handler por método: el fallo del cuerpo deja de ser `MethodArgumentNotValidException` y pasa a `HandlerMethodValidationException`, que el advice no atiende. Cuerpo observado: `{"detail":"Validation failure","status":400}`. Documentado en el propio controller.
  3. **Agregar un campo a `RequestServiceImpl` cambió el orden del constructor de Lombok** y rompió la construcción manual de `RequestServiceImplTest`. Sin consecuencia de diseño, pero explica el commit del test.

### Verificación con mutantes

- [x] T020 [US1] Atacar T006 y T008 con un mutante cada uno y confirmar que cae el test cuyo nombre lo afirma, y **ningún otro**: (a) poner `PUBLIC_CAPTURE_ENABLED='false'` en adición de créditos debe romper el `201` y no el `404`; (b) usar la cuenta de la Coordinación como actor del tramo inicial debe romper solo `publicSubmissionTimelineNamesThePortalActor`. Registrar el resultado de cada mutante.

  **Resultado de los dos mutantes, medido el 2026-09-16.** Los dos matan lo que su test afirma y **nada más**, que es la condición para que estos tests valgan:

  | Mutante | Qué cayó | Qué sobrevivió |
  |---|---|---|
  | (a) `PUBLIC_CAPTURE_ENABLED='false'` en adición de créditos | Los **cuatro** que esperan `201`, todos con `Status expected:<201> but was:<404>` | ✅ `tradeWithoutPublicCaptureReturnsNotFound` **siguió verde** — su `404` no depende de que el otro trámite esté habilitado, que es justo lo que este mutante venía a descartar |
  | (b) La cuenta de la Coordinación como actor del tramo inicial | **Uno solo**: `publicSubmissionTimelineNamesThePortalActor`, con `JSON path "$[0].actorEmail" expected:<portal-publico@tramita.local> but was:<coordinacion.cali@uniremington.edu.co>` | Los otros ocho |

  Ambos mutantes revertidos; `./mvnw clean verify` → `BUILD SUCCESS` (57 unitarios + 60 IT) con el código en su estado final.

---

## Phase 4: User Story 2 — La Coordinación se entera de que llegó algo nuevo (P2)

**Objetivo**: ver las solicitudes recientes sin escribir ningún criterio, y sin exponer
documentos de identidad.

**Test independiente**: con sesión, `GET /api/requests/inbox` devuelve las solicitudes de la
más nueva a la más vieja, y ningún objeto del arreglo tiene `studentDocument`.

### RED

- [x] T021 [US2] En `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java`, agregar `inboxListsRecentRequestsNewestFirst`: con sesión, `GET /api/requests/inbox` **sin parámetros** devuelve `200` y las solicitudes ordenadas por fecha de creación descendente (FR-012, FR-013).
- [x] T022 [P] [US2] En el mismo IT, `inboxNeverExposesStudentDocument`: **aserción sobre el JSON de la respuesta**, no sobre el DTO — ningún elemento del arreglo tiene la clave `studentDocument` (FR-014, D8).
- [x] T023 [P] [US2] En el mismo IT, `inboxRequiresAnAuthenticatedSession`: sin sesión → `401` (FR-015).
- [x] T024 [US2] Ejecutar `./mvnw clean verify` y dejar constancia del RED.

  **RED el 2026-09-16** — `Tests run: 63, Failures: 2`. Caen `inboxListsRecentRequestsNewestFirst` e `inboxNeverExposesStudentDocument`, ambos con `Status expected:<200> but was:<400>`.

  🔑 **La causa es exactamente la colisión que T028 anticipa**, y quedó medida antes de escribir el endpoint: `GET /api/requests/inbox` entraba por `@GetMapping("/{id}")`, Spring intentaba convertir `«inbox»` a `UUID` y fallaba con 400. No es un 404 de ruta inexistente.

  `inboxRequiresAnAuthenticatedSession` (T023) **pasa en verde desde el RED**, y es correcto: el filter chain resuelve el 401 antes de llegar a cualquier controller, así que la garantía se cumple por ausencia. Su valor es de regresión — debe seguir verde después de T028, que es cuando la ruta empieza a existir. Mismo caso que `portalAccountCannotAuthenticate` en la US1.

### GREEN

- [x] T025 [P] [US2] Crear `src/main/java/com/uniremington/api/tramita/dto/InboxEntryResponse.java`: igual a `RequestSummaryResponse` **menos** `studentDocument`. Documentar en el javadoc por qué no es duplicación (dos contratos con reglas de exposición distintas) y citar la razón que ya está escrita en `IRequestRepo`.
- [x] T026 [US2] Agregar a `src/main/java/com/uniremington/api/tramita/repo/IRequestRepo.java` una consulta de recientes acotada por `Limit`, ordenada por fecha de creación descendente.
- [x] T027 [US2] Agregar la operación a `service/IRequestService.java` e implementarla en `service/impl/RequestServiceImpl.java`, mapeando a `InboxEntryResponse`.
- [x] T028 [US2] Agregar `GET /api/requests/inbox` a `src/main/java/com/uniremington/api/tramita/controller/RequestController.java`. **Declararlo antes de `@GetMapping("/{id}")`** o verificar que el patrón de ruta no colisione: `inbox` no debe resolverse como un identificador.
- [x] T029 [US2] Ejecutar `./mvnw clean verify` hasta que T021–T023 pasen.

  **GREEN el 2026-09-16** — `BUILD SUCCESS`: **57 unitarios + 63 IT**, 0 fallos. Ningún test preexistente quedó en rojo.

  **Decisión tomada acá porque ni el spec ni D8 la fijaban**: la bandeja trae **50 solicitudes** (`INBOX_SIZE`). Con las 30-40 por semestre que reporta la Coordinación (`material-coord/2026-06-04-entrevista3-sintesis-analitica.md:213`), 50 cubre más de un semestre completo: en la práctica se ve todo lo que llegó, sin paginar. El tope es cota de sanidad contra un volcado futuro, no paginación — y el `Limit` va en la firma del repositorio, obligando a quien llame a decidir cuánto pide.

  **Sobre el orden de declaración (T028)**: Spring resuelve por especificidad del patrón —un segmento literal gana sobre una variable—, así que el orden en el archivo no es lo que hace funcionar `/inbox`. Se declaró contiguo a `/{id}` igualmente, para que la competencia entre ambas rutas sea visible al leer.

### Verificación con mutantes

- [x] T030 [US2] Atacar T022 con un mutante: agregar `studentDocument` al DTO de la bandeja. Debe caer `inboxNeverExposesStudentDocument` y **ningún otro test**. Si sobrevive, la aserción está mirando el DTO y no el JSON.

  **Resultado del mutante, medido el 2026-09-16**: se agregó `studentDocument` al record `InboxEntryResponse` y su línea correspondiente en `toInboxEntry`. Cayó **exactamente un test** —`inboxNeverExposesStudentDocument`— y **ningún otro** de los 36 de la clase. La aserción mira el JSON servido y el valor concreto del documento, no la forma del DTO. Mutante revertido; `./mvnw clean verify` → `BUILD SUCCESS` (57 + 63).

---

## Phase 5: User Story 3 — El canal abierto resiste el abuso (P3)

**Objetivo**: limitar envíos por origen y tamaño del cuerpo, con error tipado y bloqueo que
expira solo.

⚠️ **Los números ya están decididos y NO se re-deliberan acá**: `20 envíos por IP cada 15
minutos` y `256 KB`, en `application.yml` bajo `app.public-capture` — nunca en
`workflow_parameter`, porque el rate limit es propiedad del canal HTTP y no del trámite. El
razonamiento completo, la calibración contra las 30-40 solicitudes por semestre y la
precondición de despliegue detrás de proxy están en **research.md D3-bis**. Leerlo antes de
escribir la primera línea de esta fase.

**Test independiente**: superar el umbral desde un origen devuelve `429` con `Retry-After`;
un cuerpo desmesurado devuelve `413`; otros orígenes siguen funcionando.

**Depende de**: US1 — protege el endpoint que aquella crea.

### RED

- [x] T031 [US3] Crear `src/test/java/com/uniremington/api/tramita/security/PublicSubmissionThrottlingFilterTest.java` siguiendo el patrón de `LoginThrottlingFilterTest` (mocks de servlet de Spring Test, sin contexto de Spring): un cuerpo por encima del tope devuelve `413` y **no llega a la cadena**.
- [x] T032 [P] [US3] En `PublicRequestControllerIT`, `oversizedSubmissionIsRejected`: cuerpo mayor al tope → `413` (FR-017).
- [x] T033 [P] [US3] En `PublicRequestControllerIT`, `tooManySubmissionsFromSameOriginAreThrottled`: superar el umbral → `429`, con cabecera `Retry-After` y `application/problem+json` (FR-016, FR-018).
- [x] T034 [P] [US3] En `src/test/java/com/uniremington/api/tramita/service/impl/SlidingWindowCounterTest.java`, cubrir la expiración con un reloj mutable como el de `LoginAttemptServiceTest`: pasada la ventana, la clave deja de estar bloqueada sin intervención (FR-019).
- [x] T035 [US3] Ejecutar `./mvnw clean verify` y dejar constancia del RED.

  **RED el 2026-09-16** — falla la **compilación de los tests**: `cannot find symbol` para `SlidingWindowCounter`, `PublicCaptureProperties` y `PublicSubmissionThrottlingFilter`. Es el RED legítimo de una fase que estrena clases: los tests nombran lo que todavía no existe.

### GREEN

- [x] T036 [US3] Crear `src/main/java/com/uniremington/api/tramita/service/impl/SlidingWindowCounter.java` con la mecánica de ventana deslizante extraída de `LoginAttemptService` (registro, consulta de bloqueo, segundos restantes, purga), con nombres neutros.
- [x] T037 [US3] Hacer que `src/main/java/com/uniremington/api/tramita/service/impl/LoginAttemptService.java` delegue en el contador, **conservando su API pública intacta**. Los 14 puntos de uso en 5 archivos no se tocan; las 3 suites que lo cubren deben seguir verdes sin editarlas.
- [x] T037a [US3] Crear `src/main/java/com/uniremington/api/tramita/shared/config/PublicCaptureProperties.java`, record `@ConfigurationProperties(prefix = "app.public-capture")` con `maxSubmissions`, `window` y `maxBodySize`, validando en el constructor compacto como hace `CorsProperties` (fail-fast al arranque). Declarar los valores en `application.yml`: **20 envíos / 15m / 256KB**, con el porqué de que sean holgados (research.md **D3-bis**). Registrarlo en el `@EnableConfigurationProperties` de `SecurityConfig`.
- [x] T038 [US3] Crear `src/main/java/com/uniremington/api/tramita/security/PublicSubmissionThrottlingFilter.java` calcado de `LoginThrottlingFilter`: `OncePerRequestFilter`, matcher por método y ruta, tope leyendo un byte de más, `429` con `Retry-After` y `413` en `problem+json`. Los tres números salen de `PublicCaptureProperties`, **nunca de constantes** (D3-bis). **Sin estereotipo** (regla 6). Debe **registrar en WARN cada bloqueo con la IP que usó como clave**: es el único diagnóstico que delata en producción que falta `server.forward-headers-strategy` y que se está contando contra la IP del proxy (D3-bis, «Precondición de despliegue»).
- [x] T039 [US3] Registrarlo en `SecurityConfig` con `new` y `addFilterBefore`, antes del punto donde se resuelve la petición pública.
- [x] T040 [US3] Ejecutar `./mvnw clean verify` hasta que T031–T034 pasen, y confirmar que las suites de throttling del login siguen verdes **sin haberlas editado**.

  **GREEN el 2026-09-16** — `BUILD SUCCESS`: **73 unitarios + 66 IT**, 0 fallos. `LoginAttemptServiceTest` (10) y `LoginThrottlingFilterTest` (4) **siguen verdes sin una sola edición**, que es la prueba de que la extracción no cambió comportamiento.

  🔑 **EL FILTRO NUEVO DESTAPÓ UN DEFECTO EN LOS TESTS DE LA US1.** Al conectarlo, tres tests del canal público empezaron a devolver `429`: todos usaban el `127.0.0.1` que MockMvc pone por omisión, o sea **compartían la clave del contador**, y `submissionWithAnyBlankMandatoryFieldIsRejected` —que hace 11 campos × 2 variantes = **22 envíos**, más que los 20 de la ventana— agotaba el cupo para los demás. Es exactamente la regla 4 de esta feature, que se había aplicado a los tests de la US3 pero no a los de la US1, escritos cuando el filtro no existía.

  **Corrección**: el helper `publicSubmission` ahora **exige el origen en su firma**, sin valor por omisión, y cada escenario nombra el suyo dentro del rango `203.0.113.x` que la RFC 5737 reserva para documentación. El test de los once campos usa **un origen por caso**, porque un solo origen no alcanza para 22 envíos.

  **Se descartó subir el umbral para que el test entrara**: el número está decidido y justificado en D3-bis, y adaptarlo a la comodidad de un test sería invertir la relación entre la prueba y lo probado.

### Verificación con mutantes

- [x] T041 [US3] Atacar con dos mutantes: (a) anotar el filtro nuevo con `@Component` — debe hacerse evidente el doble conteo (el `429` llega a la mitad de los envíos configurados); (b) subir el tope a un valor enorme debe romper solo el test del `413`. Registrar ambos resultados.

  **Resultado de los mutantes, medido el 2026-09-16. Uno de los dos contradijo la predicción de esta tarea.**

  **(a) Doble conteo.** La tarea decía «anotar el filtro nuevo con `@Component`». Se hizo, y **NO produce doble conteo: el contexto de Spring no arranca**, con `NoSuchBeanDefinitionException` sobre `SlidingWindowCounter`, y los 12 tests de la clase mueren en error. Es un resultado **mejor** que el previsto: a diferencia de `LoginThrottlingFilter`, este filtro es inmune al descuido porque sus dependencias no son beans, así que el fallo es ruidoso e inmediato en vez de silencioso.

  Para verificar igualmente la propiedad que la tarea perseguía, se construyó el mutante equivalente: **dos instancias con nombre de filtro propio que comparten el contador**. Resultado: cae **un solo test**, `tooManySubmissionsFromSameOriginAreThrottled`, y su mensaje nombra el número exacto — *«el envío 11 está dentro del límite»*, es decir **el 429 llegó a la mitad de los 20 configurados**, tal como la tarea anticipaba.

  ⚠️ **Dos intentos previos de este mutante SOBREVIVIERON, y la razón importa**: (1) registrar dos veces la *misma instancia* no hace nada, porque `OncePerRequestFilter` deduplica por un atributo del request derivado del nombre del filtro; (2) dos instancias con nombres distintos pero **contador propio cada una** tampoco, porque ningún contador ve el envío dos veces. **Un mutante que no ataca lo que dice atacar no prueba nada** — la misma lección que un test con la aserción incompleta.

  **(b) Tope de tamaño.** Subir `app.public-capture.max-body-size` de `256KB` a `500MB` hace caer **exactamente uno**, `oversizedSubmissionIsRejected`, con `Status expected:<413> but was:<201>`.

  **(c) Mutante no planeado, y salió gratis.** Al restaurar mal `SecurityConfig` durante el trabajo, el filtro quedó sin registrar. Los tests lo detectaron de inmediato: `413 → 201` y `429 → 422`. Vale como evidencia de que **la suite nota la ausencia del filtro**, no solo su mala configuración.

  Todos los mutantes revertidos; `./mvnw clean verify` → `BUILD SUCCESS` (73 + 66).

---

## Phase 6: Polish

- [x] T042 [P] Recorrer `specs/004-public-request-capture/quickstart.md` de punta a punta contra la instancia local y corregir cualquier comando que no devuelva lo que el documento afirma. Un comando citado como prueba debe poder re-ejecutarse y dar el mismo resultado.
- [x] T043 [P] Verificar que `contracts/openapi.yaml` describe lo que quedó implementado, en particular los códigos de error de cada endpoint. Si algo divergió, gana el código y se corrige el contrato.
- [x] T044 Verificar la ausencia de datos personales reales en todo lo agregado: `git diff main --stat` y revisión de los tests y del quickstart (§III).
- [x] T045 Medir el conteo final de tests desde `target/surefire-reports/` y `target/failsafe-reports/` y compararlo con el baseline de T001. Usar el número **medido**, no uno recordado, en el cuerpo del commit y en la PR.

---

**Resultado del polish, medido el 2026-09-16 contra la instancia local.**

**T042 — el quickstart tenía tres afirmaciones falsas**, todas por haberse escrito antes de que D10 sumara cuatro campos obligatorios:
1. El paso 1 afirmaba `201` y la instancia devolvía **`422`** (faltaban `campus`, `faculty`, `modality`, `studentPhone`).
2. El paso 2 afirmaba `404` y devolvía **`422`**: la validación del cuerpo corre **antes** de que el servicio resuelva el trámite, así que el `404` solo aparece con el cuerpo completo. No es defecto — quien sondea manda un cuerpo válido, y ahí «no existe» y «no habilitado» responden idéntico, verificado con `TRAMITE_QUE_NO_EXISTE`.
3. El `INSERT` de habilitación usaba la columna `value`, que **no existe**: es `parameter_value`.

Además, su explicación del paso 8 decía que «ningún codificador reconoce su valor de contraseña», lo que esconde la trampa del prefijo `{bcrypt}` (sin él no hay `401` sino `500`). Corregido.

Los ocho pasos quedaron recorridos y **todos los resultados del documento son ahora valores medidos**: `201` sin `Location`, `404` idéntico para inexistente y no habilitado, `413` con 294 KB, `429` al envío 21 con `Retry-After: 874`, `jq 'map(has("studentDocument")) | any'` → `false`, timeline con `fromState: null` y el portal como actor, y `401` genérico para la cuenta del portal.

🔑 **Hallazgo no previsto**: el origen que el filtro contó fue **`0:0:0:0:0:0:0:1`** (loopback IPv6), no `127.0.0.1`. Un cliente con doble pila tiene dos claves y el doble de cupo. Se decidió **no normalizar** —va a favor del objetivo de disponibilidad y no debilita el corte contra un script— y queda documentado en D3-bis. Lo delató el WARN de diagnóstico, que así demostró su utilidad antes de llegar a producción.

**T043** — el contrato declara 7 códigos para el canal y los 7 tienen origen en el código. Se corrigió la descripción del `400`, que seguía describiendo lo que ahora hace el `422`; queda explícita la asimetría con el resto del API y por qué se acepta.

**T044** — cero datos personales reales en las 29 archivos del diff. Los únicos correos son la cuenta institucional que ya estaba en `main` y dos sintéticos (`@ejemplo.test`, `@tramita.local`); los documentos llevan todos el prefijo `SIN-DATO-REAL`, y las IP son del rango `203.0.113.x` que la RFC 5737 reserva para documentación.

**T045 — conteo final medido**, no recordado:

| Suite | Baseline T001 | Final | Agregados |
|---|---|---|---|
| surefire (unitarios) | 57 (7 clases) | **73 (9 clases)** | **+16** |
| failsafe (IT) | 50 (5 clases) | **66 (6 clases)** | **+16** |

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
