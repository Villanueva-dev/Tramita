# Data Model — Catálogo de programas y anexo exigido por programa (009)

## Lo primero, porque es el hecho más relevante del diseño

**Esta feature crea dos tablas de configuración y no toca ninguna tabla existente.** Ni
`request`, ni el timeline, ni `workflow_parameter` ganan o pierden columnas; ninguna fila ya
radicada se reescribe (FR-005, SC-005).

| Qué | Dónde vive | Desde |
|---|---|---|
| El catálogo de programas | `academic_program` (**nueva**) | `V5.0.0` crea, `V5.1.0` siembra |
| La regla de anexo por trámite y programa | `workflow_annex_rule` (**nueva**) | `V5.0.0` crea, `V5.1.0` siembra una |
| El programa que declaró cada solicitud | `request.program`, `VARCHAR(120)`, sin cambios | `V2.3.0` |
| La versión de la definición con que nació cada solicitud | `request.definition_id`, sin cambios | `V2.0.0` |
| El requisito de anexo | **en ninguna parte**: se deriva al leer | — |

Son las **primeras migraciones desde `V4.1.0`** (006). Las dos tablas nuevas son configuración, del
mismo tipo que `workflow_parameter`: no llevan trigger de inmutabilidad, porque corregirlas es su
caso de uso (FR-006) y la garantía del §VII protege el historial de la solicitud, no la
configuración (research D6).

---

## Entidades nuevas

### `AcademicProgram` → tabla `academic_program`

Un programa de pregrado que la Sede Cali ofrece, identificado ante el estudiante y ante la regla
por su **nombre publicado** (spec, *Key Entities*).

| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` | Generado por la aplicación (`GenerationType.UUID`) o por `gen_random_uuid()` en la siembra, como el resto del esquema (`V2.0.0__Create_workflow_tables.sql:4-5`) |
| `name` | `VARCHAR(120)` | `NOT NULL`, `CONSTRAINT uq_academic_program_name UNIQUE (name)` | El mismo largo que `request.program` (`V2.3.0:9`) y que el `@Size(max = 120)` de los dos cuerpos: todo nombre del catálogo cabe donde la solicitud lo guarda |

- **Mapeo**: `name` **sin** `updatable = false` —renombrar es un caso de uso (spec, caso borde)—,
  con el criterio de `WorkflowParameter.value` (`WorkflowParameter.java:52-54`). La aplicación no lo
  escribe: no hay pantalla de administración.
- **Sin** columna de facultad, sede, estado «activo» ni orden: la spec no los pide (caso borde
  «Programa que deja de ofrecerse»: se quita como dato; «La facultad no cuenta»).
- **Repositorio** `IAcademicProgramRepo extends JpaRepository<AcademicProgram, UUID>`:
  - `boolean existsByName(String name)` — la validación de D4. Consulta derivada, como
    `IUserRepo.existsByEmail` (`IUserRepo.java:15`).
  - `List<AcademicProgram> findAllByOrderByNameAsc()` — la lista de D5.

### `WorkflowAnnexRule` → tabla `workflow_annex_rule`

Por trámite (en su versión concreta) y programa: el documento que la facultad exige al reenviar y
de dónde lo obtiene el estudiante (FR-007).

| Columna | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` | |
| `definition_id` | `UUID` | `NOT NULL`, `REFERENCES workflow_definition (id)` | La **versión** concreta, como `workflow_parameter` (`V3.0.0__Configure_business_rules.sql:16-19`) |
| `program_id` | `UUID` | `NOT NULL`, `REFERENCES academic_program (id) ON DELETE RESTRICT` | FR-008 garantizado por la base. `RESTRICT` y no `CASCADE`: quitar un programa con regla falla, no borra la regla en silencio (research D2) |
| `document_name` | `VARCHAR(120)` | `NOT NULL` | «Hoja de vida académica» |
| `source_hint` | `VARCHAR(255)` | `NOT NULL` | «La descarga el estudiante desde CLASS» |
| — | — | `CONSTRAINT uq_workflow_annex_rule_definition_program UNIQUE (definition_id, program_id)` | A lo sumo una regla por trámite y programa |

- **Mapeo**: `@ManyToOne(fetch = LAZY, optional = false)` hacia `WorkflowDefinition` y hacia
  `AcademicProgram`, con `nullable = false, updatable = false` en las dos claves —una regla no se
  «mueve» de programa: se borra y se crea otra—. `documentName` y `sourceHint` **sin**
  `updatable = false`: corregir el texto es el caso de uso.
- **Repositorio** `IWorkflowAnnexRuleRepo extends JpaRepository<WorkflowAnnexRule, UUID>`:
  - `Optional<WorkflowAnnexRule> findByDefinitionIdAndProgramName(UUID definitionId, String programName)`
    — la única consulta. Recorre `program.name`; como `name` es único en el catálogo y
    `(definition_id, program_id)` es único acá, devuelve a lo sumo una fila.
- **No** existe `program_id` nulo con el significado «todos los programas»: los anexos universales
  de la novedad de notas quedaron fuera (spec, *Assumptions*). Agregarlo después es aditivo.

---

## Entidades leídas (ninguna se modifica)

### `Request`

Se lee **`program`** (`Request.java:88-89`: `@Column(updatable = false, length = 120)`, nulable) y
**`definition`**. Nada más cambia en la entidad.

**Invariante nuevo sobre `program`, y su alcance exacto.** A partir de esta feature, **toda fila
nueva** tiene en `program` un nombre del catálogo **vigente al momento de registrarla**, o ninguno
si entró por el canal interno sin declararlo (SC-001). La garantía vive en la capa de servicio
(`RequestBusinessRulesImpl`, research D4), **no en la base**: no hay FK desde `request`
(research D3) y **las filas anteriores no se reescriben**. Consecuencias declaradas:

- Una solicitud radicada antes de la feature puede tener cualquier texto («Ing», «Sistemas»,
  «Programa de Prueba»: 6 de 26 en la base de desarrollo, según la spec). Se consulta y se mueve
  igual (FR-005) y no recibe requisito (US2 escenario 6).
- Si un programa se **renombra** o se **quita** del catálogo después, las solicitudes que lo
  citaban conservan el nombre con que se radicaron (caso borde «Renombrar un programa»), y dejan de
  coincidir con la regla.

### `WorkflowDefinition`

Se lee su `id` para buscar la regla: la de la **versión con que nació la solicitud**
(`request.getDefinition()`), no la vigente del código. Consecuencia: una v2 de un trámite nace sin
reglas hasta que se le siembren, exactamente como con sus parámetros.

---

## Lo que NO es entidad, a propósito

### El requisito de anexo

**No se almacena, no tiene tabla y no deja rastro en el historial** (spec, *Key Entities*). Se
deriva en cada respuesta de detalle del programa de la solicitud y de la configuración de su
definición (FR-010). Que no exista como entidad tiene una consecuencia aceptada: si la
Coordinación corrige o quita una regla, el detalle de las solicitudes ya radicadas cambia con ella
(research D6).

### «Adjuntado»

No existe. El sistema no sabe si la Coordinación adjuntó el documento a su correo, y no lo afirma
(US2 escenario 7, FR-012). Un campo para registrarlo sería afirmar algo que el sistema no puede
comprobar.

### El anexo mismo

Ni archivo, ni ruta, ni huella. El sistema no recibe archivos (FR-012; 006, FR-010: *«Trámita no es
un repositorio de archivos»*), y la hoja de vida académica contiene todas las notas del estudiante
(§III, minimización).

### Los anexos universales de la novedad de notas

Planilla de asistencia, planilla de notas y recibo de pago
(`material-coord/evidencia-entrevistas-coordinacion.md:762`, archivo local fuera del control de
versiones, `.gitignore:93`). Fuera de esta feature por decisión del gate `review-spec`.

---

## Contratos de salida y de entrada (DTOs)

### `ProgramResponse` — nuevo

Record `ProgramResponse(String name)`. Es el elemento de la lista de `GET /api/public/programs`.
**Solo el nombre**: ni `id`, ni conteos, ni nada derivado de solicitudes (FR-001, SC-004). Un objeto
y no una cadena suelta para que un campo futuro sea aditivo (research D5).

### `AnnexRequirementResponse` — nuevo

Record `AnnexRequirementResponse(String documentName, String sourceHint)`, copiado tal cual de la
regla. Sin identificadores de la regla ni del programa: el cliente solo necesita qué pedir y de
dónde sale.

### `RequestResponse` — se **amplía**, no se reemplaza

Hoy lleva quince campos (`RequestResponse.java:38-56`): `id`, `definition`, `studentName`,
`studentDocument`, `studentCode`, `program`, `semester`, `reason`, `subjects`, `currentState`,
`availableTransitions`, `createdAt`, `origin`, `studentEmail`, `studentPhone`.

| Campo nuevo | Tipo | Qué es | Cuándo falta |
|---|---|---|---|
| `annexRequirement` | `AnnexRequirementResponse` | El anexo que la configuración de su trámite exige para su programa (FR-009) | Sin programa; programa sin regla en su trámite; programa que no coincide con el catálogo (solicitudes viejas); trámite sin reglas (hoy, `NOVEDAD_NOTAS`) |

Se omite cuando es nulo, por el `@JsonInclude(NON_NULL)` que el record ya tiene
(`RequestResponse.java:37`). **Es aditivo**: los quince campos anteriores conservan forma y
significado (FR-013).

### `RequestSummaryResponse` e `InboxEntryResponse` — **no cambian**

Ni la búsqueda ni la bandeja llevan el requisito (spec, caso borde «Dónde se ve el requisito»). Sus
mapeos son propios en `RequestServiceImpl` y no se tocan; una guarda sobre el JSON servido lo fija
(research D10).

### `PublicRequestBody.program` — cambia la regla, no el campo

Las anotaciones **no cambian**: sigue `@NotBlank @Size(max = 120)` (`PublicRequestBody.java:51`).
Lo que cambia es la semántica: además, tiene que ser **exactamente** uno de los nombres que publica
`GET /api/public/programs` (FR-002, FR-004). Uno en blanco sigue respondiendo 422 con el campo en
`missingFields`; uno fuera del catálogo, 422 con el campo en `invalidFields`, sin eco del valor.
**Enmienda no aditiva** del contrato de la 004 (FR-013).

### `CreateRequestBody.program` — cambia la regla, sigue opcional

Las anotaciones **no cambian**: sigue `@Size(max = 120)` (`CreateRequestBody.java:41`). Omitirlo o
mandarlo `null` sigue siendo válido (FR-003); si viene, tiene que estar en el catálogo, y `""`
cuenta como «vino»: 400 con el campo en `invalidFields`. **Enmienda no aditiva** del contrato
interno (FR-013).

---

## Reglas de derivación

1. **Pertenencia al catálogo** (al registrar, en los dos canales):
   `program IS NULL` → se acepta (solo alcanzable en el interno: el público lo exige con
   `@NotBlank`); si no, se acepta solo si existe una fila con `academic_program.name = program`,
   comparada **byte a byte** (FR-004, research D8).
2. **Requisito de anexo** (al construir el detalle, en registrar, avanzar y consultar):

   ```sql
   -- solo si request.program IS NOT NULL
   SELECT r.document_name, r.source_hint
   FROM workflow_annex_rule r
   JOIN academic_program p ON p.id = r.program_id
   WHERE r.definition_id = :request_definition_id   -- la versión con que nació
     AND p.name = :request_program;                 -- igualdad exacta, sin normalizar
   ```

   Cero filas → sin requisito. Una fila → `annexRequirement`. Más de una es imposible por las dos
   unicidades. **No interviene el estado actual** ni ningún código de estado (FR-010, §VI).

## Índices y restricciones

| Nombre | Sobre | Para qué |
|---|---|---|
| `uq_academic_program_name` | `academic_program (name)` | Unicidad del nombre (caso borde «Dos programas con el mismo nombre») y, por el índice que crea, la búsqueda de `existsByName` y del `JOIN` por nombre |
| `uq_workflow_annex_rule_definition_program` | `workflow_annex_rule (definition_id, program_id)` | Una regla por trámite y programa; su índice sirve a la búsqueda por `definition_id`, que es su columna inicial |
| FK `program_id` `ON DELETE RESTRICT` | `workflow_annex_rule → academic_program` | FR-008 |
| FK `definition_id` | `workflow_annex_rule → workflow_definition` | La regla pertenece a una versión existente |

Ningún índice adicional: PostgreSQL no indexa por sí solo la columna que referencia una FK, pero
sobre `program_id` solo lo consultaría el chequeo de `RESTRICT` al borrar un programa, sobre una
tabla de una fila. A 30–40 solicitudes por semestre no hay nada que medir.

---

## Filas de la siembra (`V5.1.0`)

### Programas — ⚠️ PROVISIONALES Y NO AUDITADOS

La lista la dictó el usuario el 2026-09-25 a partir de lo que le indicó la Coordinación, **sin
documento de respaldo y sin confirmación escrita** (spec, *Assumptions*). Según el §IV
(`.specify/memory/constitution.md:288-291`), se siembra marcada como provisional, con el mismo
bloque de advertencia que `V3.0.0:32-44` usa para el tope de créditos. Corregirla es un cambio de
filas, no de código (FR-006).

Los trece, **exactamente** como en la spec y en NFC:

1. Administración de Negocios
2. Administración de Empresas
3. Contaduría Pública
4. Derecho
5. Enfermería
6. Ingeniería Ambiental
7. Ingeniería de Sistemas
8. Ingeniería en Seguridad y Salud en el Trabajo
9. Ingeniería Industrial
10. Medicina
11. Medicina Veterinaria
12. Nutrición y Dietética
13. Química Farmacéutica

El orden de la lista es el de la spec, no el de la siembra ni el de la respuesta (research D5: la
respuesta sale en orden alfabético según la intercalación de la base, `en_US.utf8` en los entornos
conocidos).

### Regla de anexo — una

| Trámite | Programa | `document_name` | `source_hint` |
|---|---|---|---|
| `ADICION_CREDITOS` v1 | Ingeniería de Sistemas | Hoja de vida académica | La descarga el estudiante desde CLASS |

Respaldo: *«a solo sistemas me pide que […] anexe la hoja de vida académica»*
(`material-coord/evidencia-entrevistas-coordinacion.md:184`), resumido como «Hoja de vida académica
(solo Ingeniería de Sistemas)» (`:762`); la descarga el estudiante desde CLASS (`:553`). Se resuelve
por `(code, version)` y por nombre, sin UUID literales (`V2.1.0__Seed_workflow_definitions.sql:4-5`).
`NOVEDAD_NOTAS` entra **sin reglas** (spec, *Assumptions*; US2 escenario 4).

---

## Nota sobre la forma Unicode (NFC)

La coincidencia es byte a byte (research D8), así que la forma Unicode importa: «Ingeniería» en NFC
(`í` = `U+00ED`) y en NFD (`i` + `U+0301`) son cadenas **distintas** para PostgreSQL con
intercalación determinista. La siembra se escribe en NFC, como todas las migraciones y la propia
spec (medido: cero diacríticos combinantes en ambas; research D8). El cliente debe devolver la
cadena que recibió sin transformarla.
