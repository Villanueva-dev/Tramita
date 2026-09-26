# Implementation Plan: Catálogo de programas y anexo exigido por programa

**Branch**: `009-program-catalog-annex` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-program-catalog-annex/spec.md`

## Summary

El programa académico deja de escribirse a mano: el estudiante, en el formulario público, y la
Coordinación, en el interno, lo **eligen de una lista** que el sistema publica, y el sistema no
registra una solicitud nueva con un programa fuera de ella (P1). Sobre ese valor controlado, el
detalle de una solicitud **recuerda qué anexo exige la facultad** para su programa —hoy, la hoja
de vida académica en la adición de créditos de Ingeniería de Sistemas— y de dónde lo obtiene el
estudiante (P2). El sistema no recibe el anexo ni registra que se adjuntó.

El enfoque técnico cabe en tres frases. **El catálogo y la regla son dato**: dos tablas de
configuración nuevas, `academic_program` y `workflow_annex_rule`, con una clave foránea que hace
imposible una regla sobre un programa desconocido (research D1, D2). **La pertenencia al catálogo
se valida en el servicio**, en el gancho de reglas que ya corre para los dos canales, y se informa
con el mismo mecanismo de campos inválidos de cada canal: 422 en el público, 400 en el interno
(research D4). **El requisito se deriva al leer y no se guarda**: `request.program` sigue siendo el
texto radicado, y el detalle gana un campo aditivo, `annexRequirement`, en las tres acciones que lo
devuelven (research D3, D6).

Consecuencias directas: **dos migraciones**, `V5.0.0` (esquema) y `V5.1.0` (trece programas
provisionales y una regla), las primeras desde la `V4.1.0` de la 006, y **ninguna sobre tablas
existentes**. **Un endpoint abierto nuevo**, `GET /api/public/programs`, el cuarto del sistema
(research D5). Y **una enmienda no aditiva** del campo `program` en los dos canales de captura,
que exige coordinar el despliegue con el front (research D11).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.7 (Web MVC, Data JPA, Validation, Security 7),
Lombok. **Ninguna dependencia nueva.**

**Storage**: PostgreSQL con Flyway en modo `validate`. **Dos migraciones nuevas**, las primeras
desde `V4.1.0`: `V5.0.0__Create_program_catalog_and_annex_rules.sql` crea las dos tablas y
`V5.1.0__Seed_program_catalog_and_annex_rules.sql` las siembra (research D7). **Ninguna tabla
existente cambia**: `request.program` sigue en `VARCHAR(120)` desde `V2.3.0`, sin FK ni relleno
(research D3, data-model.md).

**Testing**: JUnit 5 + AssertJ + Mockito para unitarios; Testcontainers (`@ServiceConnection`,
`postgres:16`) para los IT. La suite base es la **última registrada: 163 unitarios + 127 IT** en
`ca5decf` (`CLAUDE.md:131`). ⚠️ No se re-midió para este plan: se re-mide con `./mvnw clean verify`
al arrancar la implementación, y ese es el número que vale. Un test pasa a rojo **por diseño**
con **dos** aserciones atadas a «Programa Reservado» —`RequestControllerIT.java:490` (el 201, que pasa
a 400) y `:498` (el centinela de filtración)—, y dos constructores cambian y rompen la compilación de
sus tests unitarios (research D10). Siete mutantes previstos.

**Target Platform**: servicio HTTP, despliegue en Linux.

**Project Type**: servicio web (backend). El frontend vive en otro repositorio, lo lleva otro
agente, y recibe su parte como brief en la sección «Reparto con el frontend» de este plan.

**Performance Goals**: no hay objetivo de rendimiento. El volumen documentado es de **30–40
solicitudes por semestre** para el trámite más frecuente.

**Constraints**: el detalle de una solicitud con programa hace **una consulta más** —la regla de
su definición para su programa— en las tres acciones que lo devuelven; sin programa, ninguna
(research D6). Registrar una solicitud con programa hace una consulta más al catálogo
(`existsByName`, research D4). A este volumen es irrelevante. El `git grep` que prueba la tesis
del §VI debe seguir devolviendo **una sola línea**: el literal `ADICION_CREDITOS` de la regla vive
en SQL, no en Java (research D7).

**Scale/Scope**: Sede Cali, **13 programas (provisionales)**, **1 regla**, 2 trámites, una cuenta
de usuario. **Un endpoint nuevo**: `GET /api/public/programs`. Cambian de contrato, de forma
aditiva, las tres acciones que devuelven el detalle (`POST /api/requests`,
`POST /api/requests/{id}/transitions`, `GET /api/requests/{id}`); y de forma **no aditiva**, el
campo `program` de los dos cuerpos de captura (`POST /api/public/requests/{definitionCode}` y
`POST /api/requests`).

## Constitution Check

*GATE: debe pasar antes de la Fase 0. Re-evaluado después de la Fase 1.*

| Principio | Evaluación | Evidencia |
|---|---|---|
| **I — KISS + YAGNI** | ✅ Pasa | Las dos tablas responden a requisitos que **existen** —el §I pide crecer con migraciones *«cuando el requisito exista»* (`.specify/memory/constitution.md:213-215`)—: FR-006 exige el catálogo como dato y FR-008, una garantía que solo da una FK. Se descartaron con evidencia la pantalla de administración, el `program_id` nulo para «todos los programas», la foto del requisito en la solicitud, la caché de la lista, la normalización y el mapeo aproximado (D2, D5, D6, D8, D9). Lo único que no es tabla, entidad, repositorio o DTO es una clase de excepción. |
| **II — Arquitectura por capas** | ✅ Pasa | Cada pieza cae en su capa: `model/`, `repo/`, `dto/`, `controller/`, `service/` + `service/impl/`, `shared/exception/`, `shared/config/`. El controller nuevo inyecta una interfaz (`constitution.md:226`, `:358`). La validación que necesita el repositorio vive en el servicio, no en una anotación de `dto/`, precisamente para no acoplar `dto/` con `repo/` (D4). |
| **III — Seguridad y minimización** | ✅ Pasa, con una tensión declarada | El endpoint abierto expone **solo nombres de programas**: ningún dato personal ni derivado de solicitudes (FR-001, SC-004), sin escritura, y un IT fija que cada elemento tiene solo `name`. La validación es **autoritativa en el backend** (`constitution.md:250`); el selector del cliente es solo UX. Los errores nombran campos, **nunca valores** (FR-002; los builders existentes ya lo garantizan). El anexo —la hoja de vida académica contiene todas las notas del estudiante— **no se recibe ni se almacena** (FR-012). Los fixtures usan documentos `SIN-DATO-REAL-*`. La tensión: un **cuarto** endpoint abierto, donde tres sitios dicen «tercero y último» (ver abajo). |
| **IV — Decisiones trazables** | ✅ Pasa | Once decisiones en `research.md`, cada una con su alternativa descartada, su costo aceptado y su comando o línea. Las fuentes técnicas se verificaron vía Context7 (documentación de PostgreSQL 16: `RESTRICT` frente a `NO ACTION`, comparación de las intercalaciones deterministas). La lista de programas carece de documento institucional y se **siembra marcada como provisional y no auditada**, como exige `constitution.md:288-291` y con el precedente de `V3.0.0:32-44` (D7). |
| **V — Testing del comportamiento sensible** | ✅ Pasa | Lo sensible es **qué entra** (un programa fuera del catálogo no se registra: sin eso, la regla de anexo falla en silencio) y **qué sale** (el requisito donde debe y solo ahí). Por eso los tests principales son de integración sobre el JSON servido, con siete mutantes previstos; las variantes del escenario 4 son **una por dimensión**, porque la de la spec difiere en dos a la vez y dejaría vivo un mutante (D10). |
| **VI — Workflow configurable por dato** | ✅ Pasa, y es el eje de SC-003 | El catálogo y la regla son filas; incorporar otro programa con otro anexo, u otro trámite con esta regla, es un `INSERT` (FR-011). El requisito no reconoce estados (FR-010). La tesis sigue en una línea: `git grep -nE '"(FINALIZADA\|DEVUELTA\|ADICION_CREDITOS\|NOVEDAD_NOTAS)"' HEAD -- 'src/main/java/*.java'` → `DoFr100Renderer.java:166`, el rótulo impreso del papel (D7). `WorkflowGenericityIT` lo demuestra con el trámite `DEMO` cargado por SQL en caliente. |
| **VII — Trazabilidad inmutable** | ✅ Pasa | **Nada escribe el timeline**: ni validar, ni derivar el requisito, ni consultar el detalle. El requisito **se deriva y no se almacena**, así que no deja rastro en el historial (spec, *Key Entities*). El catálogo y las reglas son **configuración**, como `workflow_parameter`, y quedan **fuera** de la garantía de inmutabilidad, que protege el historial de la solicitud (`constitution.md:329-335`): corregirlas es su caso de uso. Consecuencia declarada: si se corrige o quita una regla, el detalle de las solicitudes ya radicadas cambia con ella (D6). Se acepta porque el requisito es un recordatorio, no un hecho histórico. |

**Resultado del gate: pasa sin violaciones.** La sección *Complexity Tracking* queda vacía.

### Una tensión que no es violación, pero se declara

Son tres, y ninguna contradice un principio; las tres cambian algo que estaba escrito.

**1. Enmienda no aditiva de dos contratos, con condición de despliegue.** El contrato de captura
pública (004) acepta hoy en `program` cualquier texto de hasta 120 caracteres
(`specs/004-public-request-capture/contracts/openapi.yaml:270-272`), y el interno (003) igual,
opcional (`specs/003-request-form-rules/contracts/openapi.yaml:159`). A partir de esta feature, los
dos exigen un nombre del catálogo, por igualdad exacta. Un cliente que mandara «Psicología» pasa de
201 a 422 (público) o 400 (interno). FR-002, FR-003 y FR-004 lo exigen y FR-013 lo declara. Se
trata con el procedimiento de la 008 para `studentPhone`: nota «ENMENDADO POR LA 009» en el contrato
histórico, forma vigente en el de esta feature. Con una diferencia que conviene no perder de vista:
la 008 anotó el canal interno **solo en el encabezado del contrato de la 004**
(`specs/004-public-request-capture/contracts/openapi.yaml:21-25`), porque `studentPhone` del cuerpo
interno nunca estuvo declarado en el de la 003 —la 004 lo agregó en prosa (`:15-19`)—. `program`,
en cambio, **sí** está declarado en el de la 003, así que la nota va también allí.

Como en la 008, **hay consumidor**: el formulario público del front pinta el programa como texto
libre (`tramita-frontend`, `components/do-fr-100/sections.tsx:164` sobre `ff4b6ab`). Por eso la
enmienda es una **condición de despliegue**, que research D11 fija y la PR del back lleva escrita en
su cuerpo: el selector del cliente entra antes, o a la vez.

**2. Apartarse del DO-FR-100 en el control de captura.** La plantilla pide el programa escrito a
mano; el sistema lo captura como elección de una lista. La 004 fijó **qué** once datos se capturan
(`specs/004-public-request-capture/research.md:414-418`), no con qué control, y el PDF formal sigue
imprimiendo el programa en su casilla (`DoFr100Renderer.java:308`). La justificación es la calidad
del dato medida en la spec —cuatro escrituras del mismo programa, 6 de 26 solicitudes sin
coincidencia posible—, y el costo declarado es que un programa real ausente de una lista
**provisional** bloquea el canal público hasta que se agregue la fila (research D9).

**3. Un cuarto endpoint abierto.** Tres sitios afirman que la consulta del sello es «el tercero y
último» endpoint sin sesión: `SecurityConfig.java:154`, `PublicSealController.java:13` y
`specs/006-verifiable-document-seal/contracts/openapi.yaml:32`. Los dos comentarios de Java describen
el código vigente y se corrigen en esta feature. ✅ **Decidido en el gate `review-plan` y hecho**: el
contrato de la 006 lleva la nota «ENMENDADO POR LA 009» junto a esa línea
(`specs/006-verifiable-document-seal/contracts/openapi.yaml:39-42`), con el estilo de la que la 008
dejó en el de la 004 (`specs/004-public-request-capture/contracts/openapi.yaml:21-25`); su texto
original se conserva como afirmación histórica.

Los campos que gana la respuesta, en cambio —la lista entera y `annexRequirement` en el detalle—,
son **aditivos** (FR-013): un cliente que lee los quince campos actuales del detalle sigue
funcionando sin cambios.

### Medido en el gate review-plan (2026-09-25)

- **D4**: tres tests validan con un `Validator` plano (`PublicCaptureExceptionHandlerTest.java:31-37`,
  `GlobalExceptionHandlerTest.java:27-32`, `SubjectRequestBodyTest.java:31-32`): confianza alta.
- **D5**: la base de desarrollo usa `en_US.utf8 / en_US.utf8` (el valor por defecto de `postgres:16`,
  la imagen de los IT): los trece nombres salen en orden alfabético natural; con `COLLATE "C"`, los
  cuatro «Ingeniería …» se reordenan. Se conserva `ORDER BY name`. CORS ya cubre `/api/**`
  (`SecurityConfig.java:213`).
- **D10**: el test FR-021 (`RequestControllerIT.java:479-499`) tiene dos aserciones atadas al
  fixture (`:490`, `:498`); el centinela pasa a «Estudiante Reservado» (`:486`).
- **D11**: el formulario público ya ata `invalidFields` al campo
  (`app/solicitud/creditos-adicionales/page.tsx:42-50`, `:28`, `:18`).
- **Spec re-medida**: `SELECT count(*), count(program) FROM request` → 61 / 26; «Ingeniería de
  Sistemas» 20, «Ing» 3, «Sistemas» 2, «Programa de Prueba» 1. **D2**: `"ANNEX:"` + el nombre más largo
  = 51 caracteres > 50.
- **Nada de esto bloquea el plan.** Lo único bloqueante es el orden de despliegue (D11), ya escrito
  como condición de la PR.

## Project Structure

### Documentation (this feature)

```text
specs/009-program-catalog-annex/
├── spec.md              # Qué y por qué; las decisiones de producto cerradas
├── plan.md              # Este archivo
├── research.md          # D1–D11, con trade-offs, comandos y líneas verificadas
├── data-model.md        # Las dos tablas nuevas, lo que se lee, lo que no se guarda, la siembra
├── quickstart.md        # Verificación contra servidor real, con la regla configurada por SQL en caliente
├── contracts/
│   └── openapi.yaml     # Delta: la lista (nuevo), el detalle (aditivo) y `program` (no aditivo)
├── checklists/
│   └── requirements.md  # Calidad de la spec — completo
└── tasks.md             # Lo genera /speckit-tasks, no este comando
```

### Source Code (repository root)

```text
src/main/resources/db/migration/
├── V5.0.0__Create_program_catalog_and_annex_rules.sql   # NUEVO (D7): academic_program y workflow_annex_rule
└── V5.1.0__Seed_program_catalog_and_annex_rules.sql     # NUEVO (D7): 13 programas PROVISIONALES + 1 regla

src/main/java/com/uniremington/api/tramita/
├── model/
│   ├── AcademicProgram.java               # NUEVO (D1): el programa del catálogo, único por nombre
│   └── WorkflowAnnexRule.java             # NUEVO (D2): la regla por versión de trámite y programa
├── repo/
│   ├── IAcademicProgramRepo.java          # NUEVO (D1): existsByName, findAllByOrderByNameAsc
│   └── IWorkflowAnnexRuleRepo.java        # NUEVO (D2): findByDefinitionIdAndProgramName
├── dto/
│   ├── ProgramResponse.java               # NUEVO (D5): {name}, el elemento de la lista
│   ├── AnnexRequirementResponse.java      # NUEVO (D6): {documentName, sourceHint}
│   └── RequestResponse.java               # + annexRequirement al final (aditivo, NON_NULL)
├── controller/
│   ├── PublicProgramController.java       # NUEVO (D5): GET /api/public/programs
│   └── PublicSealController.java          # solo el javadoc: deja de afirmar «tercer y último» (:13)
├── service/
│   ├── IAcademicProgramService.java       # NUEVO (D5): el contrato de la lista
│   ├── IRequestBusinessRules.java         # solo el javadoc: @throws de la excepción nueva
│   └── impl/
│       ├── AcademicProgramServiceImpl.java    # NUEVO (D5): lee el catálogo, sin caché
│       ├── RequestBusinessRulesImpl.java      # + la comprobación del catálogo, antes de créditos (D4)
│       └── RequestServiceImpl.java            # toResponse: una consulta más si hay programa (D6)
└── shared/
    ├── config/
    │   └── SecurityConfig.java            # + matcher PUBLIC_PROGRAMS y su permitAll; corrige el comentario de :154
    └── exception/
        ├── InvalidFieldValueException.java    # NUEVO (D4): lleva la lista invalidFields
        ├── GlobalExceptionHandler.java        # + una rama: 400 «Petición inválida», builder compartido
        └── PublicCaptureExceptionHandler.java # + una rama: 422 «Formato inválido», builder compartido

src/test/java/com/uniremington/api/tramita/
├── controller/
│   ├── PublicProgramControllerIT.java     # NUEVO: sin sesión, no vacía, solo `name`, contiene los sembrados (sin tamaño exacto)
│   ├── PublicRequestControllerIT.java     # 201 con catálogo; 422 «Psicología» sin eco; 3 variantes; blanco → missingFields
│   ├── RequestControllerIT.java           # 201 sin programa / 400 fuera; requisito en las tres acciones y en estado final;
│   │                                      # NOVEDAD_NOTAS y otro programa sin él; guarda de búsqueda y bandeja;
│   │                                      # solicitud vieja por SQL; RED por diseño en :490 y :498 (fixture :488 →
│   │                                      # catálogo; centinela de FR-021 → «Estudiante Reservado», :486)
│   └── WorkflowGenericityIT.java          # FR-011/SC-003: programa + regla por SQL en caliente para DEMO
├── service/impl/
│   ├── RequestBusinessRulesImplTest.java  # constructor (:42) + casos de la comprobación del catálogo
│   └── RequestServiceImplTest.java        # constructor (:636-638): gana el mock del repositorio de reglas
└── shared/exception/
    ├── PublicCaptureExceptionHandlerTest.java   # + la excepción nueva como 422 «Formato inválido»
    └── GlobalExceptionHandlerTest.java          # + la excepción nueva como 400 «Petición inválida»

specs/004-public-request-capture/contracts/openapi.yaml   # nota «ENMENDADO POR LA 009»: encabezado y `program` (:270-272)
specs/003-request-form-rules/contracts/openapi.yaml       # nota «ENMENDADO POR LA 009» en CreateRequestBody.program (:159)
specs/006-verifiable-document-seal/contracts/openapi.yaml # ✅ HECHO en el plan: nota «ENMENDADO POR LA 009» (:39-42)
```

**Structure Decision**: se conserva la estructura *package-by-layer* del §II sin excepciones.
No se crea ningún paquete: cada archivo nuevo cae en la capa que ya existe para su tipo. El
controller nuevo va aparte de `PublicRequestController` porque es otro recurso (`/api/public/programs`,
no `/api/public/requests`), y porque el advice público está acotado a ese controller por
`assignableTypes` (`PublicCaptureExceptionHandler.java:43`): mezclar la lista allí la dejaría bajo un
manejador de errores que no le corresponde. `contracts/` de esta feature es un **delta** sobre los
contratos previos, igual que en la 006, la 007 y la 008.

## Complexity Tracking

> Sin entradas: el Constitution Check pasó sin violaciones que justificar.

## Reparto con el frontend

Esta entrega deja el contrato listo. Lo que sigue va al repositorio del front
(`Villanueva-dev/tramita-frontend`), que lleva Codex, y se le pasa junto con la spec y el contrato
de esta feature, con el precedente del brief de la 008 (`tramita-frontend#59`). Las líneas se
verificaron sobre la referencia local `origin/main` = **`ff4b6ab`**, sin `git fetch`; se re-verifican
al escribir el brief. En orden de despliegue:

1. **Primero, el selector del formulario público** (condición de despliegue, research D11). El
   programa hoy es un `TextField` (`components/do-fr-100/sections.tsx:164`) inicializado en `''`
   (`app/solicitud/creditos-adicionales/page.tsx:18`). Pasa a un selector alimentado por
   `GET /api/public/programs`:
   - **Enviar el valor elegido tal como llegó**: sin `normalize()`, sin `trim()`, sin reescribirlo.
     La comparación es byte a byte (research D8).
   - **Nada que se pueda teclear**: ni `datalist` ni combo editable (research D9).
   - Si la lista no carga, el formulario **no** debe caer a texto libre: mostrar el error y no
     permitir el envío. Es el fallo cerrado de la spec (caso borde «Catálogo vacío»).
   - El error ya se ata al campo, sin código nuevo (confianza alta, medido en el gate):
     `fieldErrorsFromProblem` recorre `invalidFields` y marca el campo si está en `FORM_FIELDS`
     (`app/solicitud/creditos-adicionales/page.tsx:42-50`), que incluye las claves de
     `INITIAL_VALUES` (`:28`), entre ellas `program` (`:18`). Un 422 con `["program"]` marca el selector.
   - Tiene que estar en `main` **antes** del backend, o entrar en la misma ventana.
2. **El formulario interno usa el mismo endpoint** y se borra la constante `PROGRAMS`
   (`lib/ui-constants.ts:33-39`; importada en `app/requests/new/page.tsx:29`, pintada en `:293-303`).
   Hoy `useState(PROGRAMS[0])` (`:61`) **preselecciona** un programa, así que el cliente interno
   nunca omite el campo. El backend acepta omitirlo (FR-003); si el front ofrece «sin programa»,
   tiene que **omitir la clave**, no mandar `""`, que responde 400. Recomendación: no preseleccionar
   en silencio: el primero de la lista es un accidente del orden alfabético, no una elección
   (research D5).
3. **Mapear `annexRequirement` en el detalle**: `ApiRequest` (`lib/store.tsx:54-69`, donde hoy se
   mapea el programa en `:203`) y el modelo de la solicitud (`lib/types.ts`, junto a `program` en
   `:170`). Mostrar `documentName` y `sourceHint` en el detalle. **Dónde y cuándo destacarlo lo
   decide el cliente** (FR-009: el backend expone hechos). No va en la bandeja ni en la búsqueda.
4. **Tests del lado del front**: el selector pinta la lista de un endpoint mockeado; el valor
   enviado es **idéntico** al recibido, con un nombre con tilde; el detalle muestra el requisito y
   no muestra nada cuando falta; y la respuesta de la transición lo trae sin volver a consultar
   (SC-006), con el mock de `transition` que devuelve el estado posterior que `front#52` ya pide.
5. **Sin registro de «adjuntado»** en el cliente tampoco: ninguna bandera local que afirme lo que el
   sistema no sabe (FR-012, US2 escenario 7).

Y al abrir la PR del back: `Closes #40` y `Closes #50` **en texto plano**; la condición de
despliegue escrita en el cuerpo; y el brief anterior abierto como issue del front. La nota del
contrato de la 006 (tensión 3) ya quedó hecha en esta fase. Todo esto ya está declarado en la spec o en este plan;
acá solo se recuerda dónde toca.

## Lo que este plan deja explícitamente fuera

- **Recibir o almacenar el anexo, o cualquier archivo** — decidido en la spec (FR-012).
- **Registrar que el anexo se adjuntó** — decidido en la spec (US2 escenario 7). Limitación aceptada.
- **Normalizar el programa** (mayúsculas, tildes, espacios, forma Unicode) — decidido en la spec
  (FR-004) y en research D8.
- **Reescribir las solicitudes ya radicadas** o agregarles una FK al catálogo — FR-005 y research D3.
- **Una pantalla de administración del catálogo** — la spec lo descarta por el §I.
- **Derivar la facultad del programa**, un catálogo por sede, posgrados, tecnologías y técnicos —
  fuera de alcance según la spec.
- **Los anexos universales de la novedad de notas** — fuera por el gate `review-spec`; aditivo
  después (research D2).
- **Un orden de la lista según el español impuesto por el servicio** — decidido en el gate: se
  conserva el orden de la intercalación de la base, que en los entornos conocidos (`en_US.utf8`)
  ya es el alfabético natural; un `Collator` de español queda como opción documentada si apareciera
  un entorno con `C` (research D5).
- **La ventana temporal (#42) y el tope de créditos (#17)** — issues propios.
- **El frontend** — otro repositorio. Su parte está en «Reparto con el frontend».
