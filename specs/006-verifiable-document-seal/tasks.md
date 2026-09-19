---
description: "Lista de tareas de la feature 006 — sello verificable y registro de emisiones"
---

# Tasks: sello verificable y registro de emisiones del documento formal

**Input**: documentos de diseño en `specs/006-verifiable-document-seal/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`

**Tests**: **obligatorios**. El proyecto tiene TDD estricto: el rojo se observa antes de implementar. Cada tarea marcada `(RED)` debe fallar por la razón esperada antes de escribir la que la sigue.

## Formato: `[ID] [P?] [Story] Descripción`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: a qué historia pertenece (US1, US2, US3)
- Toda tarea lleva la ruta exacta del archivo

## Convención de verificación

```bash
docker start tramita-postgres && ./mvnw clean verify
```

⚠️ **`clean` no es opcional al medir en rojo.** La compilación incremental de Maven puede dar
un rojo falso o esconder uno verdadero.

**Línea base al empezar** (commit `a402500`): 112 unitarios + 76 IT en verde. Ese número se
re-mide, no se cita de memoria.

---

## Phase 1: Preparación

**Propósito**: confirmar el punto de partida antes de tocar nada.

- [x] T001 Levantar la base y confirmar la línea base en verde: `docker start tramita-postgres && ./mvnw clean verify`, anotando el conteo real de unitarios e IT
- [x] T002 Comprobar que la sonda `src/test/java/com/uniremington/api/tramita/service/impl/PdfDeterminismProbeTest.java` está presente y corre (ya commiteada en `eadad66`; **no crearla de nuevo**)

---

## Phase 2: Fundacional — bloqueante para las tres historias

**Propósito**: el render reproducible y el almacenamiento del sello. **Ninguna historia puede empezar sin esto**: sellar antes de que el render sea reproducible produce sellos que nunca verifican, y la tabla es de solo anexado, así que no se corrigen después.

### Tramo 1 — determinismo del render (FR-004, FR-005)

- [x] T003 (RED) Reescribir `src/test/java/com/uniremington/api/tramita/service/impl/PdfDeterminismProbeTest.java` como test de comportamiento: renderizar dos veces la misma solicitud y afirmar `assertArrayEquals`. Debe fallar hoy — el `/ID` del trailer se sortea en cada `save()`
  - ⚠️ **El test compara dos reconstrucciones entre sí, NUNCA contra una huella dorada literal.** Una constante hexadecimal se rompería en T016, al imprimirse el código en el pie, y actualizarla —la reacción natural— desactiva la única barrera de D5 contra olvidar bumpear la versión del formato. Dejarlo escrito en el javadoc del test
- [x] T004 (GREEN) Fijar el `/ID` en `src/main/java/com/uniremington/api/tramita/service/impl/DoFr100Renderer.java` con `document.getDocument().setDocumentID(COSArray)`: primera cadena derivada del identificador de la solicitud, segunda derivada del identificador **más la revisión** (research.md D1)
- [x] T005 [P] Agregar a `PdfDeterminismProbeTest` el caso de que **dos solicitudes distintas no comparten `/ID`** (FR-005)
- [x] T006 Verificar el tramo: `./mvnw clean test -Dtest=PdfDeterminismProbeTest` en verde, y `DoFr100RendererTest` sin regresiones

### Tramo 2 — migración, entidad y repositorio (FR-001, FR-002, FR-008)

- [x] T007 (RED) Crear `src/test/java/com/uniremington/api/tramita/repo/DocumentSealImmutabilityIT.java` imitando `TimelineImmutabilityIT`: afirmar que `UPDATE` y `DELETE` sobre `request_document_seal` fallan **por acceso directo al motor**, y que `INSERT` sí funciona
  - ⚠️ El caso del `INSERT` no es de relleno: si el trigger llegara a cubrir `INSERT`, con fail-closed **apagaría la emisión de documentos por completo**. Es el modo de fallo más caro que esta feature puede causarse a sí misma
- [x] T008 (GREEN) Escribir `src/main/resources/db/migration/V4.1.0__Register_document_seals.sql` en este orden exacto: (a) **sanear** las calificaciones con más de un decimal —hay **1 fila medida**, `04f93f4d-a718-470e-9e0c-f8fde6f9e33f`, `proposed_grade = 3.46`—, (b) la restricción `ck_request_subject_grades_one_decimal`, (c) la tabla `request_document_seal`, (d) los dos índices, (e) la función y el trigger `BEFORE UPDATE OR DELETE`
  - ⚠️ El saneamiento va **antes** de la restricción. Invertirlo hace que Flyway falle al aplicar la migración y deja el arranque roto
- [x] T009 [P] Crear la entidad `src/main/java/com/uniremington/api/tramita/model/RequestDocumentSeal.java` con las columnas de `data-model.md`, todas `updatable = false`, y **sin setters** (es de solo anexado)
- [x] T010 [P] Crear `src/main/java/com/uniremington/api/tramita/repo/IRequestDocumentSealRepo.java` con la búsqueda por `verificationCode` y el listado por solicitud ordenado por `issuedAt`
- [x] T011 Verificar el tramo: `./mvnw clean verify` con el IT de inmutabilidad en verde y Flyway aplicando `V4.1.0` sin error

**Checkpoint**: el documento es reproducible y el sello tiene dónde vivir. Recién ahora pueden empezar las historias.

---

## Phase 3: User Story 1 — toda emisión queda sellada y con rastro (P1) 🎯 MVP

**Meta**: emitir el documento deja un registro permanente e imprime la cara legible del sello.

**Prueba independiente**: emitir el documento de una solicitud y comprobar que (a) quedó un sello nuevo, (b) el documento muestra la marca legible, (c) el registro no admite modificación.

- [x] T012 [P] [US1] (RED) Crear `src/test/java/com/uniremington/api/tramita/util/VerificationCodeGeneratorTest.java`: el código no se repite entre invocaciones y tiene a lo sumo 13 caracteres alfanuméricos
- [x] T013 [P] [US1] (GREEN) Crear `src/main/java/com/uniremington/api/tramita/util/VerificationCodeGenerator.java`: 64 bits de `SecureRandom` con `Long.toUnsignedString(valor, 36)` (research.md D3). **Sin dependencias nuevas**
- [x] T014 [US1] (RED) Agregar a `src/test/java/com/uniremington/api/tramita/service/impl/DoFr100RendererTest.java` el caso de que el pie imprime el código, la fecha de emisión, el estado y la revisión (FR-003)
  - ⚠️ **Este tramo rompe DOS tests existentes de ese mismo archivo, y el mecanismo no es obvio**: `lowestTextBaseline` (`:376-394`) descarta las líneas del pie filtrando por el literal `"Generado por Trámita"`, no por su posición, y dos tests asertan que el resto no baja de `BOTTOM = 70` (`DoFr100Renderer:78`). Extender ese filtro es **parte de esta tarea**, no una sorpresa a descubrir en rojo
- [x] T015 [US1] (GREEN) Extender `IDocumentRenderer` en `src/main/java/com/uniremington/api/tramita/service/IDocumentRenderer.java` para que cada formato declare su `formatVersion()`, y `render` reciba el código de verificación
- [x] T016 [US1] (GREEN) Implementar en `DoFr100Renderer` la versión del formato —constante del renderer combinada con la huella del logo del classpath (research.md D5)— y el pie con las cuatro líneas legibles
- [x] T017 [US1] Actualizar el test de determinismo para pasar **el mismo código** en las dos reconstrucciones. Si el criterio de T003 se respetó, es el único cambio necesario y el test sigue afirmando lo mismo
- [x] T018 [US1] (RED) Crear `src/test/java/com/uniremington/api/tramita/service/impl/DocumentSealServiceImplTest.java` con el caso de emisión: se registra un sello con huella, código, versión de formato, revisión, estado (**código y nombre**) y actor
- [x] T019 [US1] (GREEN) Crear `src/main/java/com/uniremington/api/tramita/service/IDocumentSealService.java` y `src/main/java/com/uniremington/api/tramita/service/impl/DocumentSealServiceImpl.java` con la emisión del sello
- [x] T020 [US1] (RED) Agregar a `src/test/java/com/uniremington/api/tramita/service/impl/DocumentServiceImplTest.java` el caso **fail-closed**: si el guardado del sello falla, no se devuelve documento y no queda sello a medias (FR-012)
- [x] T021 [US1] (GREEN) Modificar `src/main/java/com/uniremington/api/tramita/service/impl/DocumentServiceImpl.java`: quitar `readOnly = true` de `generateFor()` (`:69`), generar el código, renderizar con él, calcular la huella y registrar el sello **en la misma transacción**
  - ⚠️ Anotar en el javadoc de `generateFor()` que quitar `readOnly` **reactiva el dirty checking**: una modificación accidental de `Request` durante el renderizado se persistiría. Hoy el renderer solo lee, y conviene que el próximo que lo toque lo sepa
  - ⛔ **Sin política de reintento, sin transacción aparte, sin manejo de error propio.** Los tres se rechazaron explícitamente como sobreingeniería: reintentar es volver a pedir el documento, y el `GlobalExceptionHandler` ya devuelve RFC 9457
- [x] T022 [US1] Verificar la historia: `./mvnw clean verify` en verde y el paso 1 del quickstart ejecutado a mano

### Agregado durante US1, no estaba planificado

- [x] T022a Aislar las fuentes por documento en `DoFr100Renderer`: compartirlas hacía que emitir un documento de 2000 caracteres cambiara los bytes de los anteriores, y con el renderer como `@Service` singleton eso significaba reportar documentos legítimos como alterados. Vigilado por `DoFr100FontIsolationTest`
- [x] T022b Sumar la versión de PDFBox a `formatVersion()`, leída en tiempo de ejecución: el pom la fija para poder actualizarla ante vulnerabilidades, y sin esto un parche de seguridad marcaría los sellos previos como alterados en vez de no verificables
- [x] T022c `DoFr100LayoutCanaryTest`: el plan daba por hecho (D5) que el test de determinismo avisaría ante un cambio de maquetación. **Se midió que no avisa** —compara dos reconstrucciones entre sí y sobrevive a cualquier cambio de trazado—, así que la barrera que D5 describe no existía

**Checkpoint**: US1 entregable por sí sola. Ya responde «cuántas veces se emitió y quién lo pidió», que hoy es imposible.

---

## Phase 4: User Story 2 — verificar si un documento es auténtico (P2)

**Meta**: los tres veredictos, por los dos canales.

**Prueba independiente**: emitir → verificar da **íntegro**; alterar un byte → **alterado**; cambiar el formato o la revisión → **no verificable**, jamás «alterado».

- [ ] T023 [US2] (RED) Agregar a `DocumentSealServiceImplTest` los tres veredictos y **los dos motivos** de `NOT_VERIFIABLE` — son los casos que distinguen esta feature de una ingenua:
  - `INTACT` — el documento tal como se emitió
  - `TAMPERED` — la huella no coincide y el sistema **sí** podía comparar
  - `NOT_VERIFIABLE` / `DATA_CHANGED` — emitido sobre una revisión anterior de la solicitud, **no** `TAMPERED`
  - `NOT_VERIFIABLE` / `FORMAT_CHANGED` — emitido con una versión del formato que ya no es la vigente, **no** `TAMPERED`
  - 🔑 El segundo motivo no es simetría de catálogo: es **SC-003** y el edge case de `spec.md:81`, el único que el spec describe como «a la vez y en silencio». `research.md` D6 (`:180-183`) enumera los dos motivos y el contrato los declara (`openapi.yaml:257`)
- [ ] T024 [US2] (GREEN) Implementar la verificación en `DocumentSealServiceImpl` respetando **el orden de comprobaciones de research.md D6**: ¿existe el sello? → ¿el formato sigue vigente? → ¿la revisión coincide? → recién entonces comparar huellas
  - 🔑 **El orden ES el requisito.** Comparar huellas primero y deducir el motivo después produciría «alterado» en los dos casos donde el sistema no puede pronunciarse, que es la acusación falsa que FR-007 prohíbe
- [ ] T025 [P] [US2] Crear los DTO `src/main/java/com/uniremington/api/tramita/dto/PublicSealResponse.java` y `src/main/java/com/uniremington/api/tramita/dto/VerdictResponse.java` según `contracts/openapi.yaml`. ⚠️ El público **no lleva ningún dato personal** (FR-014c, §III)
- [ ] T026 [P] [US2] Crear `src/main/java/com/uniremington/api/tramita/dto/VerifyBody.java` con el código y la huella, validados con Bean Validation
- [ ] T027 [US2] (GREEN) Crear `src/main/java/com/uniremington/api/tramita/controller/PublicSealController.java` con `GET /api/public/seals/{code}`
- [ ] T028 [US2] (GREEN) Crear `src/main/java/com/uniremington/api/tramita/controller/SealController.java` con `POST /api/seals/verify`, que recibe **la huella y no el archivo** (research.md D10)
- [ ] T029 [US2] Agregar la ruta pública a `src/main/java/com/uniremington/api/tramita/shared/config/SecurityConfig.java` con `permitAll`. ⚠️ **No tocar la configuración de CSRF**: es un `GET` y Spring no protege métodos seguros, a diferencia de la captura pública, que sí necesitó exclusión por ser `POST`
- [ ] T030 [P] [US2] (RED→GREEN) Crear `src/test/java/com/uniremington/api/tramita/controller/PublicSealControllerIT.java`: los tres veredictos sin sesión, el `404` de un código inexistente, y que la respuesta **no contiene nombre ni cédula**
- [ ] T031 [P] [US2] (RED→GREEN) Crear `src/test/java/com/uniremington/api/tramita/controller/SealControllerIT.java`: `INTACT`, `TAMPERED` con un byte alterado, **`404` con un código que no existe**, `401` sin sesión y **`400`** con cuerpo inválido
  - ⚠️ Es `400` y no `422`: el `422` pertenece al canal público de captura por un advice acotado con `assignableTypes`, no es la norma del sistema
- [ ] T032 [US2] Verificar la historia: los pasos 3, 4 y **5** del quickstart ejecutados a mano. El paso 5 es el que el propio documento declara imprescindible

**Checkpoint**: US1 + US2 entregables juntas. El sello ya sirve para lo que existe.

---

## Phase 5: User Story 3 — historial de emisiones (P3)

**Meta**: ver cuántas veces se emitió el documento de una solicitud, cuándo y quién lo pidió.

**Prueba independiente**: sobre una solicitud con emisiones, consultar el historial y obtener una entrada por emisión; sobre una sin emisiones, lista vacía y no error.

⚠️ **Es la de menor prioridad: si hay que recortar alcance, esta es la primera que cae.**

- [ ] T033 [P] [US3] Crear `src/main/java/com/uniremington/api/tramita/dto/SealEntryResponse.java` según el contrato
- [ ] T034 [US3] (RED→GREEN) Agregar `GET /requests/{id}/seals` a `src/main/java/com/uniremington/api/tramita/controller/RequestController.java` y su método en el servicio de sellos
- [ ] T035 [US3] (RED→GREEN) Agregar a `src/test/java/com/uniremington/api/tramita/controller/RequestControllerIT.java` los casos: dos emisiones devuelven dos entradas en orden, una solicitud sin emisiones devuelve **lista vacía y no 404**, y un `{id}` que no es UUID devuelve `400`
- [ ] T036 [US3] Verificar la historia: `./mvnw clean verify` y el paso 1 del quickstart con tres emisiones seguidas

---

## Phase 6: Cierre y transversales

- [ ] T037 [P] (RED) Crear `src/test/java/com/uniremington/api/tramita/dto/SubjectRequestBodyTest.java` con el caso de que una calificación con más de un decimal se rechaza (FR-013a, SC-008)
  - ⚠️ Va en `dto/`, no en `service/impl/` como lo ubicaba `plan.md:144`: es un test de un DTO y la validación es de Bean Validation, no una regla de negocio configurable. Es el **primer** test de esa capa —el paquete no existe todavía—, y eso es consecuencia de §II (package-by-layer), no un argumento en contra
- [ ] T038 [P] (GREEN) Agregar la anotación de precisión de Bean Validation a `currentGrade` y `proposedGrade` en `src/main/java/com/uniremington/api/tramita/dto/SubjectRequestBody.java`. Responde **`400`**: las calificaciones entran solo por el formulario interno
- [ ] T039 Ejecutar el **quickstart completo de punta a punta**, incluidos el paso 6 (inmutabilidad por acceso directo) y el paso 7 (precisión rechazada)
- [ ] T040 Re-medir el conteo de tests y anotarlo con el commit en que se midió. ⛔ Ningún conteo es canónico fuera de su commit
- [ ] T043 Corregir las afirmaciones que la implementación refutó y que ningún artefacto recogió. ⛔ No es cosmética: son los documentos que audita el jurado, y tres de ellos sostienen decisiones sobre mediciones que hoy dan distinto (§IV)
  - `plan.md:21-24` — «el contenido del PDF ya es determinista hoy». Acotar la medición a su alcance real: valía entre dos renders seguidos, no entre emisiones. T022a la refutó
  - `plan.md:82-83` y `plan.md:197` — la mitigación citada no existe. Lo que detecta un cambio de maquetación es `DoFr100LayoutCanaryTest` (T022c), no el test de determinismo
  - `research.md:40` (D1) — la misma afirmación que `plan.md:21-24`
  - `research.md:163-166` (D5) — describe un test que «compara contra una huella conocida». Ese test nunca existió y además **T003 lo prohíbe expresamente**: una huella dorada se rompería al imprimir el código en el pie. D5 describe una barrera que el propio plan vetó construir
  - `research.md` D5 — no menciona que `formatVersion()` incluye hoy la versión de PDFBox (T022b)
  - `spec.md:90` — alinear con la decisión tomada: lo que hace inviable recorrer el espacio es su tamaño (64 bits), no un límite de tasa (`research.md` D3 `:121` y D9 `:298`). El canal público de captura limita la tasa porque **cada envío escribe**; éste solo lee
  - `spec.md:88` — reemplazar «hay que decidir qué hacer» por la decisión ya tomada: T008 redondea a un decimal antes de declarar la restricción; 1 fila medida
  - `spec.md:103` (FR-006) — acotar los tres veredictos a la verificación exacta: el canal público expone dos (`openapi.yaml:251`)
  - `plan.md:108-110` — la ruta lleva `/api`: no hay `context-path` y los controllers lo declaran en su `@RequestMapping` (`PublicRequestController.java:25`). `research.md:289` ya la escribe completa
  - `plan.md:82` y `:197` — «bumpea/bumpear» → «subir el número de versión»
- [ ] T041 Actualizar el bloque SPECKIT de `CLAUDE.md` al estado real de la feature
- [ ] T042 Revisar que ningún mensaje de commit del ciclo afirme algo sin su línea `Verificado:` con el comando y su resultado

---

## Dependencias y orden de ejecución

### Entre fases

- **Phase 1** → **Phase 2** → historias.
- **Phase 2 es bloqueante de todo.** No es una recomendación: sin el tramo 1, los sellos que se emitan no verificarán nunca, y como la tabla es de solo anexado **no se pueden corregir después**.
- **US1 (Phase 3)** depende de la Phase 2 completa.
- **US2 (Phase 4)** depende de US1: sin sellos registrados no hay contra qué verificar.
- **US3 (Phase 5)** depende de US1, pero **no** de US2. Puede entregarse sin la verificación.
- **Phase 6** puede empezar en cualquier momento después de la Phase 1 salvo T039 a T042.

### Dentro del tramo 1, un orden que no se puede invertir

T003 (rojo) → T004 (verde). Si se implementa antes de ver el rojo, no hay evidencia de que el test mida lo que dice medir.

### Oportunidades de paralelismo

- T009 y T010 (entidad y repositorio) son archivos distintos.
- T012/T013 (generador de código) no dependen de nada del tramo 2 y pueden adelantarse.
- T025, T026 y T033 (los DTO) son independientes entre sí.
- T030 y T031 (los dos IT de verificación) tocan archivos distintos.
- T037 y T038 (FR-013a) son independientes de todo lo demás de la feature.

---

## Estrategia de entrega

### MVP: Phase 1 + Phase 2 + US1

Deja el documento reproducible, la tabla inmutable y toda emisión registrada con su marca legible impresa. **Responde por sí solo una pregunta que hoy el sistema no puede responder**: cuántas veces se emitió el documento de un trámite y quién lo pidió. El issue #11 pide además la verificación, así que el MVP no cierra SP4 solo; es un incremento entregable, no el criterio de cierre.

### Incremento siguiente: US2

Es lo que convierte el registro en un sello verificable y cierra el objetivo del issue.

### Si hay que recortar

Cae **US3** primero. Su valor es visibilidad sobre datos que la US1 ya deja escritos.

---

## Notas

- ⛔ **No reabrir**: fail-closed sin reintento, verificación por posesión, `400` en vez de `422`, el sello congelando código **y** nombre del estado, y el canal público sin límite de tasa. Todas son decisiones tomadas con su porqué en `research.md`.
- 🔑 **FR-004 es reproducibilidad a código fijo**, no «dos emisiones dan lo mismo» — eso es imposible por diseño y ya costó una corrección en el gate `review-plan`.
- Cada cambio de comportamiento lleva su línea `Verificado:` en el commit, con el comando y su resultado.
