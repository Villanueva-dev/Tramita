# Research — Catálogo de programas y anexo exigido por programa (009)

Decisiones técnicas previas al diseño. Cada una lleva su trade-off explícito, como exige el
Principio IV: *«elegí X frente a Y, sabiendo que el costo es Z»*.

Todas las mediciones son del 2026-09-25 sobre la rama `009-program-catalog-annex` en `ef4d61a`
—que es `main` (`0cf3fa3`) más los dos commits de la spec— y cada una trae el comando que la
reproduce o la línea exacta que la respalda. Las del frontend se tomaron sobre la referencia
local `origin/main` de `tramita-frontend`, que hoy apunta a **`ff4b6ab`**; no se hizo `git fetch`
en esta sesión, así que si el remoto avanzó, esas líneas se re-verifican antes de usarlas.

Las decisiones de producto (el programa se elige de una lista cerrada, la regla es por programa y
no por facultad, la coincidencia es exacta, el requisito se muestra desde el registro y en
cualquier estado, el sistema no recibe archivos) ya están tomadas en la spec y **no se reabren
acá**: esto resuelve cómo se construye lo que la spec pide.

---

## D1 — El catálogo es una tabla propia, `academic_program`

**Decisión**: una tabla nueva `academic_program (id UUID, name VARCHAR(120) NOT NULL,
CONSTRAINT uq_academic_program_name UNIQUE (name))`, con su entidad `model/AcademicProgram` y su
repositorio `repo/IAcademicProgramRepo`, que expone dos consultas derivadas: `existsByName(String)`
(para validar, D4) y `findAllByOrderByNameAsc()` (para la lista, D5).

**Justificación**:

- FR-006 exige que el catálogo sea **dato**: incorporar, renombrar o quitar un programa es una
  fila, no un despliegue. Una tabla es la forma más directa de cumplirlo con el mecanismo que el
  repositorio ya usa para toda su configuración (Flyway siembra, Hibernate valida).
- FR-008 exige que una regla de anexo **no pueda** referirse a un programa desconocido. Eso
  necesita algo a lo que apuntar con una clave foránea (D2). Solo una tabla propia lo ofrece.
- El catálogo **no es por trámite**: el mismo programa vale para adición de créditos y para
  novedad de notas. Guardarlo en una estructura atada a una definición lo duplicaría.
- `VARCHAR(120)` no es arbitrario: es exactamente el largo de `request.program`
  (`V2.3.0__Persist_request_form_data.sql:9`, `Request.java:88-89`) y el `@Size(max = 120)` de los
  dos cuerpos de captura (`CreateRequestBody.java:41`, `PublicRequestBody.java:51`). Todo nombre del
  catálogo cabe, por construcción, en el campo donde la solicitud lo guarda.
- `name` se mapea **sin** `updatable = false`: renombrar es un caso de uso de la spec (caso borde
  «Renombrar un programa»), con el mismo criterio que `WorkflowParameter.value`
  (`WorkflowParameter.java:52-54`: *«Sin updatable = false: ajustar el valor ES el caso de uso»*).
  La aplicación no escribe el catálogo —no hay pantalla de administración (spec, *Assumptions*)—;
  el mapeo solo evita mentir sobre lo que la tabla admite.

**Alternativas descartadas**:

- *Filas en `workflow_parameter`*. Esa tabla es **por definición** (FK a la versión concreta,
  `V3.0.0__Configure_business_rules.sql:10` y su comentario `:16-19`), con clave de 50 caracteres
  y valor de 100 (`:11-12`). El catálogo tendría que repetirse por trámite y por versión, y un
  parámetro no puede ser destino de una clave foránea, así que FR-008 quedaría en manos de la
  disciplina del código.
- *Un `enum` de Java*. Viola FR-006 y el Principio VI: renombrar un programa exigiría compilar y
  desplegar. Es exactamente lo que el motor configurable vino a evitar
  (`.specify/memory/constitution.md:309-315`).
- *Una columna JSON con la lista* (en alguna fila de configuración). No tiene unicidad por
  elemento, no es destino de FK y obliga a parsear para validar. Ahorra una tabla a cambio de
  perder las dos garantías que la spec pide.
- *La lista fija en el cliente*, como hoy (`tramita-frontend`, `lib/ui-constants.ts:33-39`). El
  servidor no podría validar (FR-002 imposible) y corregir la lista exigiría desplegar el front
  (FR-006). Es el estado actual que esta feature reemplaza.

**Costo aceptado**: una tabla, una entidad y un repositorio más para trece filas. Y un endpoint
abierto nuevo para publicarlas (D5).

**Evidencia**:

```bash
sed -n '8,14p' src/main/resources/db/migration/V3.0.0__Configure_business_rules.sql
# CREATE TABLE workflow_parameter ( … parameter_key VARCHAR(50) … parameter_value VARCHAR(100) …
#     CONSTRAINT uq_workflow_parameter_definition_key UNIQUE (definition_id, parameter_key)
git grep -n 'existsBy' HEAD -- 'src/main/java/*.java'
# → IUserRepo.java:15  boolean existsByEmail(String email);   (precedente de consulta derivada)
```

Convención de nombres, medida sobre las migraciones: las **cuatro** restricciones `UNIQUE` del
esquema se llaman `uq_<tabla>_<columnas>` —`uq_workflow_definition_code_version`
(`V2.0.0__Create_workflow_tables.sql:18`), `uq_workflow_state_definition_code` (`:28`),
`uq_workflow_transition_route` (`:43`), `uq_workflow_parameter_definition_key` (`V3.0.0:13`)—. Los
índices únicos varían: dos usan `uq_` (`uq_users_email_lower`, `V1.0.0__Create_users_table.sql:18`;
`uq_workflow_state_one_initial_per_definition`, `V2.2.0__Harden_workflow_constraints.sql:15`) y uno
`ux_` (`ux_request_document_seal_code`, `V4.1.0__Register_document_seals.sql:83-84`). Para una
**restricción** `UNIQUE`, que es lo que llevan las dos tablas nuevas, el precedente es unánime: `uq_`.

---

## D2 — La regla de anexo es una tabla propia, `workflow_annex_rule`

**Decisión**: una tabla nueva:

```sql
workflow_annex_rule (
    id            UUID PRIMARY KEY,
    definition_id UUID NOT NULL REFERENCES workflow_definition (id),
    program_id    UUID NOT NULL REFERENCES academic_program (id) ON DELETE RESTRICT,
    document_name VARCHAR(120) NOT NULL,
    source_hint   VARCHAR(255) NOT NULL,
    CONSTRAINT uq_workflow_annex_rule_definition_program UNIQUE (definition_id, program_id)
)
```

con su entidad `model/WorkflowAnnexRule` y su repositorio `repo/IWorkflowAnnexRuleRepo`, que
expone **una** consulta: la regla de una definición concreta para un programa dado **por su
nombre** (`findByDefinitionIdAndProgramName(UUID, String)`, que recorre la relación hasta
`academic_program.name`). Devuelve `Optional`: las dos unicidades juntas —`(definition_id,
program_id)` acá y `name` en el catálogo— garantizan a lo sumo una fila.

**Justificación**:

- **FR-008 queda garantizado por la base, no por el código.** La FK hace imposible insertar una
  regla sobre un programa que el catálogo no conoce, aunque se intente por SQL directo. Es el
  mismo espíritu que el §VII declara para la trazabilidad —*«se garantiza en la base de datos, no
  por disciplina del código de aplicación»* (`.specify/memory/constitution.md:332-335`)—, aplicado
  acá a la integridad de la configuración. No es una exigencia del §VII (que habla del timeline);
  es su criterio.
- **Renombrar un programa arrastra la regla** (caso borde «Renombrar un programa»): la FK es por
  `id`, así que un `UPDATE academic_program SET name = …` no toca la regla, y la regla sigue al
  programa y no al nombre viejo.
- **La FK a la definición apunta a la VERSIÓN**, igual que la de `workflow_parameter`
  (`V3.0.0:16-19`: *«La FK apunta a la VERSIÓN concreta de la definición»*). Consecuencia buscada:
  una solicitud se rige por la versión con la que nació, y su requisito se busca por
  `request.getDefinition().getId()`, con el mismo criterio que
  `IWorkflowParameterRepo.findByDefinitionIdAndKey` (`IWorkflowParameterRepo.java:10-15`:
  *«la búsqueda es por la definición CONCRETA de la solicitud, no por su código»*). Consecuencia
  declarada: publicar una v2 de un trámite exige sembrar sus reglas, como ya exige sembrar sus
  parámetros.
- **`ON DELETE RESTRICT`**: quitar del catálogo un programa que tiene regla falla ruidosamente;
  primero hay que quitar la regla. En PostgreSQL, `RESTRICT` y la acción por defecto `NO ACTION`
  producen ambos el error; la diferencia es que `NO ACTION` puede diferirse al final de la
  transacción y `RESTRICT` no (<https://www.postgresql.org/docs/16/sql-createtable.html>, verificado
  vía Context7). Se escribe explícito para que la intención se lea en la migración.
- `document_name` en 120 y `source_hint` en 255 porque son texto para mostrar, no claves: el
  primero es un nombre de documento como el de un programa; el segundo, una frase («La descarga el
  estudiante desde CLASS» tiene 37 caracteres).

**Alternativas descartadas**:

- *Codificar la regla como filas de `workflow_parameter` con claves `ANNEX:<programa>`*. Tipado
  por cadena, sin FK (FR-008 dependería del código), y con un valor de 100 caracteres que tendría
  que llevar nombre e indicación con un separador. Y **no cabe**: el nombre más largo de la lista
  dictada no entra en la clave de 50 (medido abajo).
- *`program_id` nulo con el significado «todos los programas»*. Es la forma de modelar los anexos
  universales de la novedad de notas, que el gate `review-spec` dejó **fuera** (spec,
  *Assumptions*). Agregar la nulabilidad después es aditivo; ponerla hoy sería una columna
  especulativa (§I).
- *`ON DELETE CASCADE`*. Quitar un programa borraría su regla sin aviso; la Coordinación perdería
  un requisito que configuró sin enterarse.
- *La regla por facultad*. Decidido en la spec a favor del programa (*Assumptions*, «Por qué por
  programa y no por facultad»).

**Costo aceptado**: una tabla más, con una fila hoy. Y la consecuencia de la versión: una v2 del
trámite nace sin reglas hasta que se le siembren.

**Evidencia**:

```bash
perl -Mutf8 -CSD -e 'for my $s ("Ingeniería en Seguridad y Salud en el Trabajo",
  "ANNEX:Ingeniería en Seguridad y Salud en el Trabajo") { print length($s), " caracteres: $s\n" }'
# 45 caracteres: Ingeniería en Seguridad y Salud en el Trabajo
# 51 caracteres: ANNEX:Ingeniería en Seguridad y Salud en el Trabajo   (> 50 de parameter_key)
```

---

## D3 — `request.program` sigue siendo el nombre, sin `program_id`

**Decisión**: la solicitud **no** gana una FK al catálogo. `request.program` sigue siendo el
texto de 120 caracteres que es hoy, sin migración ni relleno de filas. El requisito se deriva
**al leer**, por igualdad exacta: `request.program = academic_program.name`. Las solicitudes
viejas cuyo texto no coincide («Ing», «Sistemas», «Programa de Prueba») simplemente no reciben
requisito (US2 escenario 6, FR-005).

**Justificación**:

- **El formulario radicado es inmutable** (004): `program` está mapeado `updatable = false`
  (`Request.java:88-89`). Lo que la solicitud dice que el estudiante declaró es texto, y así se
  conserva.
- **El PDF formal imprime ese texto**: `DoFr100Renderer.java:308` escribe
  `request.getProgram()` en la fila «Programa académico en el que se encuentra». Con una FK, un
  renombre en el catálogo cambiaría lo que se imprime de una solicitud ya radicada.
- Con la lista ofrecida por el sistema (D5) y validada al registrar (D4), toda solicitud nueva
  guarda exactamente un nombre del catálogo (SC-001), así que la igualdad por nombre es tan precisa
  como una FK para todo lo que entra desde esta feature.

**Alternativas descartadas**:

- *`program_id` con FK en `request`*. Exige migración y relleno; las 26 filas con programa de la
  base de desarrollo quedarían, 20 mapeadas y 6 en `NULL` o mal mapeadas (medición de la spec,
  *Contexto medido*; no se repitió acá). Y un renombre se propagaría al formulario radicado y al
  PDF, contradiciendo la inmutabilidad fijada por la 004.
- *Guardar las dos cosas, nombre e id*. Dos verdades sobre el mismo dato que pueden divergir en el
  primer renombre.

**Costo aceptado**: un renombre corta la coincidencia de las solicitudes ya radicadas con el
nombre viejo (spec, caso borde «Renombrar un programa»: aceptado). Y la derivación es una igualdad
de cadenas en cada detalle (D6), a la que D8 le quita toda normalización.

---

## D4 — La pertenencia al catálogo se valida en el servicio, no con Bean Validation

**Decisión**: en `RequestBusinessRulesImpl.validate(definition, body)` —el gancho de la 003 que ya
corre para **los dos canales**—, antes de las reglas de créditos y notas: si `body.program() !=
null` y `!programRepo.existsByName(body.program())`, se lanza una excepción **nueva**,
`shared/exception/InvalidFieldValueException`, que transporta `List<String> invalidFields` (acá,
`["program"]`). Cada canal la presenta **exactamente como presenta un campo inválido de Bean
Validation**:

- `PublicCaptureExceptionHandler` (advice acotado a `PublicRequestController`, con
  `HIGHEST_PRECEDENCE`) → **422 «Formato inválido»**, `invalidFields: ["program"]`,
  `missingFields: []`.
- `GlobalExceptionHandler` → **400 «Petición inválida»**, `invalidFields: ["program"]`,
  `missingFields: []`.

Cada handler factoriza la construcción del cuerpo que hoy vive en su rama de
`MethodArgumentNotValidException` en un método que recibe las dos listas `(missing, invalid)`, y
la rama nueva lo llama con `(List.of(), ex.getInvalidFields())`. Título, `detail` y miembros de
extensión salen idénticos a los de Bean Validation, y **el valor nunca se repite** (FR-002),
porque esos builders solo nombran campos (`PublicCaptureExceptionHandler.java:61-78`,
`GlobalExceptionHandler.java:68-84`).

**Justificación**:

- **El gancho ya existe y ya corre en los dos canales.** `register` llama a
  `businessRules.validate(definition, body)` antes de persistir (`RequestServiceImpl.java:126`), y
  el canal público delega en `register` (`:192`), con el programa copiado tal cual del cuerpo
  público (`:234`). Una sola línea de código cubre FR-002 y FR-003.
- **El catálogo es configuración persistida, y la 003 valida la configuración en el servicio**:
  `MAX_CREDITS` se lee de `workflow_parameter` dentro de `RequestBusinessRulesImpl`
  (`:36-39`, `:70`), no en una anotación.
- **Una anotación que consulta la base acopla `dto/` con `repo/`** (§II,
  `.specify/memory/constitution.md:221-226`), hace I/O dentro de `@Valid` y rompe los tests que
  validan con un `Validator` plano, sin contexto de Spring. **Confianza alta, medido en el gate
  `review-plan` (2026-09-25)**: `PublicCaptureExceptionHandlerTest` (`:31-37`) y
  `GlobalExceptionHandlerTest` (`:27-32`) construyen el `LocalValidatorFactoryBean` a mano —`new` y
  `afterPropertiesSet()`, sin `ApplicationContext`, así que sin `SpringConstraintValidatorFactory` y
  sin inyección— y con él validan el record real: `PublicRequestBody` (`PublicCaptureExceptionHandlerTest.java:114-117`)
  y `CreateRequestBody` (`GlobalExceptionHandlerTest.java:74-83`). `SubjectRequestBodyTest` usa
  `Validation.buildDefaultValidatorFactory()` (`:31-32`). Un validador con el repositorio
  inyectado recibiría ahí `null`. El único constraint propio del repositorio, `@AtMostOneDecimal`,
  es puro (`AtMostOneDecimalValidator.java:15-18`): esa es la frontera.

  ```bash
  grep -rn "buildDefaultValidatorFactory\|LocalValidatorFactoryBean" src/test/java
  # → SubjectRequestBodyTest.java, GlobalExceptionHandlerTest.java, PublicCaptureExceptionHandlerTest.java
  ```
- **`UnprocessableRequestException` no sirve**: responde 422 «Regla de negocio incumplida» con el
  motivo como texto y **sin nombres de campo** (`GlobalExceptionHandler.java:86-93`), y en el canal
  público llegaría igual, porque `PublicCaptureExceptionHandler` solo atiende
  `MethodArgumentNotValidException` (`:47-59`). FR-002 exige nombrar el campo **con el mecanismo con
  que ese canal ya informa campos inválidos**.
- **La resolución entre advices es explícita.** `PublicCaptureExceptionHandler` está acotado por
  `assignableTypes` y ordenado con `@Order(Ordered.HIGHEST_PRECEDENCE)`
  (`PublicCaptureExceptionHandler.java:43-44`), así que para el controller público su rama nueva
  gana, y para `RequestController` solo aplica la del global. El comentario de
  `GlobalExceptionHandler.java:51-53` explica que, para `MethodArgumentNotValidException`, se
  sobrescribió el método heredado en vez de declarar un `@ExceptionHandler` nuevo, para no alterar
  la resolución entre advices. Para la excepción nueva no hay método heredado que sobrescribir: se
  declara un `@ExceptionHandler` en cada advice, y la precedencia la sigue fijando el `@Order` del
  público. Dos IT lo fijan (D10).

**Qué pasa con cada valor** (la ausencia domina: `ValidationFields.java:16-19`):

| Canal | Valor de `program` | Resultado |
|---|---|---|
| Público | «Ingeniería de Sistemas» (del catálogo) | 201 |
| Público | «Psicología», «ingeniería de sistemas», «Ingenieria de Sistemas», «Ingeniería de Sistemas␠» | 422 «Formato inválido», `invalidFields: ["program"]`, `missingFields: []`; el `detail` no contiene el valor |
| Público | `""` o solo espacios | 422 «Formato incompleto», `missingFields: ["program"]` —lo resuelve `@NotBlank` antes del servicio, como hoy— |
| Público | «Psicología» **y** otro campo en blanco | 422 «Formato incompleto» con el otro campo; `program` **no** se informa en esa respuesta (costo aceptado, abajo) |
| Interno | ausente o `null` | 201: sigue siendo opcional (FR-003) |
| Interno | `""` | 400 «Petición inválida», `invalidFields: ["program"]`: vino, y no está en el catálogo (mismo criterio que el `""` del teléfono en la 008, su research D3) |
| Interno | «Psicología» | 400 «Petición inválida», `invalidFields: ["program"]` |
| Interno | «Ingeniería de Sistemas» | 201 |

Va **antes** de créditos y notas porque es un error del formulario, no del contenido de negocio: el
cliente lo corrige eligiendo de la lista, sin leer una regla.

**Alternativas descartadas**:

- *Un constraint propio `@InProgramCatalog` con el repositorio inyectado*. Tiene a favor que la
  forma de la respuesta sale gratis y que un cuerpo con dos defectos se informa en una sola
  respuesta. Se descarta por los tres costos de arriba: acoplamiento entre capas, I/O en
  `@Valid` y tests de validador plano que se rompen.
- *Reusar `UnprocessableRequestException`*: no nombra el campo (ver arriba).
- *Validar en cada controller*: duplicaría la regla en dos sitios, cuando el camino público ya pasa
  por `register`.

**Costo aceptado**: un cuerpo con un campo faltante **y** un programa fuera del catálogo se
informa en **dos** vueltas, porque Bean Validation falla primero. Es aceptable porque un cliente
que presenta la lista del catálogo nunca envía un programa fuera de ella: la segunda vuelta solo
la ve un cliente que escribe por su cuenta. Y una clase de excepción nueva, con una rama en cada
handler.

---

## D5 — Un endpoint público nuevo: `GET /api/public/programs`

**Decisión**: `PublicProgramController` expone `GET /api/public/programs`, que devuelve
`[{"name": "…"}, …]` (DTO `ProgramResponse(name)`) ordenado por nombre, sin sesión. Se abre en
`SecurityConfig` con un `PathPatternRequestMatcher` declarado una vez, junto a `PUBLIC_CAPTURE` y
`PUBLIC_SEAL_LOOKUP` (`SecurityConfig.java:58-60`, `:72-74`), dentro del bloque `permitAll`
(`:150-157`). El controller inyecta la interfaz `service/IAcademicProgramService`, implementada
por `service/impl/AcademicProgramServiceImpl` (§II: `.specify/memory/constitution.md:226`, `:358`).
El formulario interno del cliente usa **el mismo** endpoint.

**Justificación**:

- **FR-001**: el formulario público la necesita y no tiene sesión.
- **Sin CSRF**: es un `GET`, no cambia estado; es el mismo razonamiento que la 006 escribió para
  el sello (`SecurityConfig.java:67-70`).
- **Sin límite de tasa**: el filtro de la captura pública solo intercepta
  `POST /api/public/requests/*` (`PublicSubmissionThrottlingFilter.java:49-50`). La lista son trece
  filas públicas —la universidad publica su oferta en su sitio—, sin escritura ni dato personal.
- **CORS ya lo cubre**: la configuración registra `/api/**` con `GET` y `POST`
  (`SecurityConfig.java:206`, `:213`).
- **Sin caché**: cada petición lee la tabla. Es lo que hace que un programa agregado por SQL
  aparezca sin reiniciar (FR-006), con el mismo criterio que el catálogo de definiciones
  (`WorkflowGenericityIT.java:119`: *«no hay caché de definiciones»*).
- **Objetos y no cadenas sueltas**: un campo futuro (la facultad, si algún día se tiene el mapa)
  se agrega sin romper a nadie.
- **Sin `id`**: FR-001 pide solo los nombres, y la regla coincide por nombre (D3), así que el
  cliente no tiene uso para un identificador.

**El orden depende de la intercalación de la base, y se declara.** `ORDER BY name` ordena según la
*collation* de la base de datos. Diez de los trece nombres conservan su posición con cualquier
intercalación; los otros tres «Ingeniería …» cambian de orden: con una intercalación byte a byte
(`C`) las mayúsculas van antes que las minúsculas; con una lingüística, no. Medido con `sort` de glibc, análogo a una collation
libc de PostgreSQL:

```bash
N='Ingeniería Industrial\nIngeniería de Sistemas\nIngeniería Ambiental\nIngeniería en Seguridad y Salud en el Trabajo'
printf "$N\n" | LC_ALL=C sort
# Ingeniería Ambiental / Ingeniería Industrial / Ingeniería de Sistemas / Ingeniería en Seguridad…
printf "$N\n" | LC_ALL=es_CO.UTF-8 sort
# Ingeniería Ambiental / Ingeniería de Sistemas / Ingeniería en Seguridad… / Ingeniería Industrial
```

**Medido en el gate `review-plan` (2026-09-25) y re-medido para este documento** sobre
`tramita-postgres`, la base de desarrollo:

```bash
docker exec tramita-postgres psql -U postgres -d tramita-db -Atc \
  "SELECT datcollate||' / '||datctype FROM pg_database WHERE datname = current_database();"
# en_US.utf8 / en_US.utf8
docker exec tramita-postgres printenv LANG
# en_US.utf8
```

Es el valor por defecto de la imagen `postgres:16`, la misma que fijan los IT
(`TestcontainersConfiguration.java:17`). Con esa intercalación, los cuatro «Ingeniería …» salen
**Ambiental | de Sistemas | en Seguridad y Salud en el Trabajo | Industrial**, y con `COLLATE "C"`,
**Ambiental | Industrial | de Sistemas | en Seguridad y Salud en el Trabajo** (medido con
`string_agg(n, ' | ' ORDER BY n)` y `ORDER BY n COLLATE "C"` sobre los mismos valores). Los trece
nombres salen en `en_US.utf8` en el orden alfabético natural en español.

**Decisión del gate**: se conserva `ORDER BY name` con la intercalación de la base. El contrato dice
«orden alfabético según la intercalación de la base (`en_US.utf8` en los entornos conocidos)», y los
tests siguen sin afirmar un orden exacto. Si algún entorno llegara con `C`, la opción documentada es
un `Collator` de español en el servicio (abajo).

**Alternativas descartadas**:

- *Reusar `GET /api/workflow-definitions`*: exige sesión (`WorkflowDefinitionController.java:11-14`)
  y el catálogo no es por trámite.
- *Un endpoint autenticado para el formulario interno, además del público*: los mismos datos, sin
  nada personal; duplicarlo es mantener dos rutas para lo mismo (§I).
- *Ordenar en Java con un `Collator` de español*: determinista, pero agrega código para un orden
  que en los entornos conocidos ya sale bien de la base, y que la spec no pide. **Queda como la
  opción documentada** si aparece un entorno con intercalación `C`: es un cambio local al servicio.

**Costo aceptado**: **un cuarto endpoint abierto.** Tres sitios afirman hoy que el del sello es
«el tercero y último», y dejan de ser ciertos:

```bash
git grep -niE 'último endpoint|ÚLTIMO endpoint|tercer y último' HEAD -- src specs
# specs/006-verifiable-document-seal/contracts/openapi.yaml:32
# src/main/java/com/uniremington/api/tramita/controller/PublicSealController.java:13
# src/main/java/com/uniremington/api/tramita/shared/config/SecurityConfig.java:154
```

Los dos comentarios de Java describen el código vigente y se corrigen en esta feature. El contrato
de la 006 lleva desde el gate `review-plan` una nota «ENMENDADO POR LA 009»
(`specs/006-verifiable-document-seal/contracts/openapi.yaml:39-42`), con el estilo de la que la 008
dejó en el de la 004; su texto original se conserva como registro de su momento.

---

## D6 — El detalle gana `annexRequirement`, de forma aditiva

**Decisión**: `RequestResponse` —que ya lleva `@JsonInclude(NON_NULL)` (`RequestResponse.java:37`)—
suma al final un campo `AnnexRequirementResponse annexRequirement` con `documentName` y
`sourceHint`. Se deriva en `RequestServiceImpl.toResponse` (`:565-595`): si `request.getProgram()`
no es nulo, **una** consulta a `IWorkflowAnnexRuleRepo` por `(definition.id, program)`; si hay regla,
se mapea; si no, el campo queda nulo y **no viaja**. Como `toResponse` es el único mapeo del
detalle, el requisito sale en las tres acciones que lo devuelven: registrar (`:168`), avanzar
(`:302`) y consultar (`:370`) —FR-009, SC-006—.

**Justificación**:

- FR-009 pide el requisito **en la respuesta de cada acción que devuelve el detalle**, y SC-006,
  que al llevar la solicitud a la facultad no haga falta otra consulta. Un solo punto de mapeo lo
  garantiza para los tres caminos a la vez.
- FR-010: la derivación lee **solo** el programa y la configuración de la definición de la
  solicitud. No consulta el estado actual ni reconoce códigos; por eso sale «desde el registro y
  en cualquier estado».
- Sin programa, sin coincidencia o sin reglas en la definición → sin requisito (FR-007). La
  guarda del programa nulo evita la consulta inútil y no depende de cómo trate un `null` la
  consulta derivada.
- **La búsqueda y la bandeja NO lo llevan** (spec, caso borde «Dónde se ve el requisito»): sus
  mapeos son propios (`toSummary`, `:483-493`; `toInboxEntry`, `:500-513`) y no se tocan. Se fija
  con una guarda sobre el JSON servido, con la forma de la de la 008
  (`RequestControllerIT.java:1284`, `searchAndInboxNeverExposeContact`).

**Alternativas descartadas**:

- *Guardar el requisito en la solicitud al registrarla* (una foto). La spec dice que **no se
  almacena** (*Key Entities*, «Requisito de anexo»); además exigiría migrar `request` y congelaría
  una configuración que la Coordinación puede corregir.
- *Una bandera `annexRequired: true`*. Sin el nombre del documento y de dónde sale, el cliente no
  puede decir **qué** pedir (FR-007, FR-009).
- *Un endpoint `GET /requests/{id}/annex-requirement`*. Es la segunda consulta que SC-006 prohíbe.

**Costo aceptado**:

- **Una consulta más por respuesta de detalle** con programa (ninguna sin él). A 30–40 solicitudes
  por semestre es irrelevante. Se espera una sola sentencia —`documentName` y `sourceHint` son
  columnas de la propia regla, sin relaciones perezosas que cargar—; si el review lo pide, se mide
  con `Statistics` de Hibernate como hizo la 008 (su research D2).
- **El requisito refleja la configuración vigente, no la del día del registro.** Si la
  Coordinación corrige el texto de una regla o la quita, el detalle de las solicitudes ya
  radicadas cambia con ella. Es la consecuencia directa de «se deriva y no se almacena», y se acepta
  porque el requisito es un recordatorio operativo, no un hecho histórico: el historial no lo
  registra (spec, *Key Entities*) y la trazabilidad del §VII protege el timeline, no la
  configuración.

---

## D7 — Dos migraciones: el esquema y la siembra

**Decisión**:

- `V5.0.0__Create_program_catalog_and_annex_rules.sql` — crea las dos tablas (D1, D2).
- `V5.1.0__Seed_program_catalog_and_annex_rules.sql` — siembra los **trece programas**, marcados
  como **provisionales y no auditados** (§IV, `.specify/memory/constitution.md:288-291`), y **una**
  regla: `ADICION_CREDITOS` v1 × «Ingeniería de Sistemas» → `document_name` = «Hoja de vida
  académica», `source_hint` = «La descarga el estudiante desde CLASS». Las referencias se resuelven
  por `(code, version)` y por nombre, **sin UUID literales**, como exige el encabezado de
  `V2.1.0__Seed_workflow_definitions.sql:4-5`, y con `gen_random_uuid()` como `V3.0.0:47`.

**Justificación**:

- **Mayor 5 porque abre una familia nueva de tablas**: V1 usuarios, V2 motor, V3 reglas y captura,
  V4 documento y sellos. `V3.0.0:1-4` subió de familia por el mismo motivo: *«introduce la
  configurabilidad de las reglas […] es el mismo salto conceptual que la V2.0.0 hizo con los
  estados»*.
- **El literal `ADICION_CREDITOS` vive en SQL, no en Java**, igual que en la siembra de `MAX_CREDITS`
  (`V3.0.0:46-53`).
  La tesis del §VI se sigue probando con una sola línea:

```bash
git grep -nE '"(FINALIZADA|DEVUELTA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' HEAD -- 'src/main/java/*.java'
# HEAD:src/main/java/com/uniremington/api/tramita/service/impl/DoFr100Renderer.java:166:            "ADICION_CREDITOS", "Matrícula créditos adicionales");
```

- **La marca de provisional tiene precedente exacto**: `V3.0.0:32-44` siembra el tope de créditos
  con *«⚠️ VALOR PROVISIONAL Y NO AUDITADO»* y la mitigación *«corregirlos es un UPDATE, no un
  cambio de código»*. La lista de programas está en la misma situación (spec, *Assumptions*: la
  Coordinación no la confirmó por escrito).
- Corregir la lista después es una migración de datos nueva o un cambio de filas, **nunca código**
  (FR-006).

**Alternativas descartadas**:

- *Una sola migración con esquema y datos*. El repositorio tiene los dos patrones: separado
  (`V2.0.0` crea, `V2.1.0` siembra) y junto (`V3.0.0` y `V3.3.0` alteran y siembran en el mismo
  archivo). Se elige separar porque los datos son **provisionales**: una corrección posterior se
  revisa contra un archivo solo de datos, y el esquema queda estable. La ventaja es modesta
  —legibilidad de la revisión—, y se declara como tal.
- *Sembrar desde Java* (un `CommandLineRunner` como `CoordinationUserSeeder`). Ese seeder existe
  porque la cuenta lleva credenciales de variables de entorno (`CoordinationUserSeeder.java:16-19`);
  la configuración de los trámites se siembra en SQL desde la 002. El catálogo es configuración.
- *`V4.2.0`*: sería presentar una familia nueva como una extensión de documentos y sellos.

**Costo aceptado**: un `INSERT … SELECT` cuyo `JOIN` no encuentra filas inserta **cero** sin error
—un nombre mal escrito en la siembra de la regla la dejaría sin efecto en silencio—. Lo detecta el
IT que afirma el requisito sobre la siembra real (D10), no la migración.

---

## D8 — Coincidencia exacta, sin normalizar

**Decisión**: el programa declarado se compara con el catálogo **byte a byte**, como llega (FR-004).
Ni el servidor ni la consulta normalizan mayúsculas, tildes, espacios ni forma Unicode.

**Justificación**:

- La lista la publica el propio sistema (D5): un cliente que devuelve lo que recibió coincide
  siempre. Una diferencia solo puede venir de un cliente que escribió por su cuenta, y normalizar
  la escondería.
- **PostgreSQL ya compara así**: con una intercalación determinista —la de todas las columnas del
  repositorio—, dos cadenas que no son iguales byte a byte son distintas
  (<https://www.postgresql.org/docs/16/sql-createcollation.html>, parámetro `DETERMINISTIC`,
  verificado vía Context7). Una misma palabra en NFC y en NFD **no** coincide.
- **La siembra y la spec están en NFC** (medido): los trece nombres se escriben en la migración con
  letras precompuestas, igual que el resto de las semillas.

```bash
perl -CSD -ne '$n++ while /[\x{0300}-\x{036F}]/g; END { print "diacríticos combinantes: ", ($n//0), "\n" }' \
  src/main/resources/db/migration/*.sql
# diacríticos combinantes: 0
perl -CSD -ne '$n++ while /[\x{0300}-\x{036F}]/g; END { print "en la spec: ", ($n//0), "\n" }' \
  specs/009-program-catalog-annex/spec.md
# en la spec: 0
```

(Control: el mismo patrón cuenta 1 sobre `Ingeniera` + `U+0301`. Los siete `\p{Mn}` que aparecen en
las migraciones son `U+FE0F`, el selector de variante del emoji ⚠️ en comentarios, no tildes.)

**Riesgo declarado para el brief del front**: el cliente **no debe transformar** las cadenas que
recibe de `GET /api/public/programs` —ni `normalize()`, ni `trim()`, ni reescribirlas—: debe
enviar el valor de la opción elegida tal cual. Un campo que el usuario pueda teclear (como un
`datalist`) reintroduce el riesgo (D9). Hoy el front no normaliza en ningún sitio (medido en el
gate: `git -C ../tramita-frontend grep -n "\.normalize(" origin/main -- '*.ts' '*.tsx'` no devuelve
nada, sobre `ff4b6ab`).

**Alternativas descartadas**:

- *Comparar sin mayúsculas ni tildes* (`lower`/`unaccent`, o una collation no determinista de
  ICU). Esconde clientes que escriben por su cuenta, exige una extensión o collation que el
  repositorio no usa, y **no arregla** las variantes medidas («Ing», «Sistemas»: 5 de las 6 que no
  coinciden, según la spec).
- *`Normalizer.normalize(…, NFC)` en Java antes de comparar*. Parece inocuo, pero FR-004 lo prohíbe
  (*«MUST NOT normalizar»*), y el valor guardado dejaría de ser el recibido.

**Costo aceptado**: un cliente que altere la cadena por el camino ve el rechazo de US1 escenario 4
(spec, caso borde «Tildes y eñes»). Es el comportamiento buscado.

---

## D9 — Apartarse del DO-FR-100 en el control de captura, no en los datos

**Decisión**: la plantilla oficial pide el programa **escrito a mano**; el sistema lo captura como
**elección de una lista**. El dato capturado es el mismo —el nombre del programa—, y el PDF formal
lo sigue imprimiendo en su casilla.

**Justificación**:

- **La 004 fijó *qué* once datos se capturan, no con qué control.** Su D10
  (`specs/004-public-request-capture/research.md:414-418`) decide que el canal público *«exige los
  once campos del formato […] y los persiste»*; la lista de campos está verificada contra la
  plantilla v2024 (`PublicRequestBody.java:30-35`). Nada ahí prescribe un campo de texto libre.
- **El papel no cambia de forma**: `DoFr100Renderer.java:308` imprime `request.getProgram()` en la
  fila «Programa académico en el que se encuentra», igual que hoy. Lo que cambia es que el valor
  impreso es uno del catálogo.
- **Calidad del dato, medida en la spec** (*Contexto medido*): 26 solicitudes con programa, escrito
  de **cuatro** formas, y **6 de 26** que no coincidirían con ningún catálogo. Una regla por
  programa sobre texto libre fallaría en silencio en esas seis.
- La spec exige justificar este apartamiento en el research y no hacerlo en silencio
  (*Assumptions*, «La facultad, la sede y la modalidad siguen como campos escritos»). Esta es la
  justificación.

**Alternativas descartadas**:

- *Texto libre y normalización en el servidor*: FR-004 la prohíbe, y «Ing» o «Sistemas» no se
  resuelven normalizando.
- *Un `datalist` de HTML*: sugiere la lista pero deja teclear cualquier cosa. Con la validación de
  D4, el estudiante escribiría algo plausible y recibiría un rechazo; sin ella, la regla fallaría en
  silencio. Es lo peor de los dos mundos.
- *Texto libre con mapeo aproximado al catálogo*: una heurística que decide por el estudiante qué
  programa quiso decir, sin forma de auditarla.

**Costo aceptado, y declarado**:

- El formulario digital se aparta del papel en el control, aunque no en el dato. Se defiende ante
  la Coordinación y el jurado con el argumento de calidad de arriba.
- **Un programa que no esté en la lista no puede radicar por el enlace público** (spec, caso borde
  «Catálogo vacío», generalizado a «catálogo incompleto»). Como la lista es **provisional** —y ya
  deja fuera a Psicología, que el cliente ofrece hoy—, un programa real omitido bloquea al estudiante
  hasta que se agregue la fila. La mitigación es FR-006: corregirlo es un `INSERT`, no un despliegue.

---

## D10 — Estrategia de tests: RED observado, mutantes sobre lo sensible

**Decisión**: TDD con el RED visto antes de cada cambio, siempre con `./mvnw clean verify` (el
incremental de Maven produce rojos falsos). Lo sensible acá (§V) es **qué entra** —un programa fuera
del catálogo no puede registrarse— y **qué sale** —el requisito aparece donde debe y solo ahí—. Por
eso las pruebas principales son de integración sobre el JSON servido, con Testcontainers, como en
la 008. El detalle por tarea lo fija `/speckit-tasks`; esto es el mapa.

| Qué se prueba | Dónde | Por qué ahí |
|---|---|---|
| `GET /api/public/programs` sin sesión → 200, no vacía, cada elemento con **solo** la clave `name`, contiene los trece sembrados | `PublicProgramControllerIT` (nuevo) | FR-001, SC-004 y el caso borde «Catálogo vacío» (la siembra no está vacía) |
| Público: 201 con un nombre del catálogo, guardado tal cual | `PublicRequestControllerIT` | US1 escenario 2 |
| Público: 422 `invalidFields: ["program"]` para «Psicología», y el cuerpo **no contiene** «Psicología» | `PublicRequestControllerIT` | FR-002, US1 escenario 3; sin eco |
| Público: 422 para tres variantes de **una sola dimensión** cada una: «ingeniería de sistemas» (mayúsculas), «Ingenieria de Sistemas» (tilde), «Ingeniería de Sistemas␠» (espacio) | `PublicRequestControllerIT` | FR-004, US1 escenario 4 (ver abajo por qué tres) |
| Público: en blanco sigue dando `missingFields: ["program"]` | `PublicRequestControllerIT` | La ausencia domina (`ValidationFields.java:16-19`) |
| Interno: 201 sin programa; 400 `invalidFields: ["program"]` con «Psicología» | `RequestControllerIT` | FR-003, US1 escenarios 5 y 6 |
| El detalle trae `annexRequirement` para `ADICION_CREDITOS` × «Ingeniería de Sistemas» en **registrar, avanzar a `EN_FACULTAD` y consultar**, y también en un estado final | `RequestControllerIT` | FR-009, SC-002, SC-006, US2 escenarios 1 y 3; «en cualquier estado» |
| No lo trae para otro programa del catálogo ni para `NOVEDAD_NOTAS` con «Ingeniería de Sistemas» | `RequestControllerIT` | US2 escenarios 2 y 4, FR-007 |
| La búsqueda y la bandeja nunca lo llevan | `RequestControllerIT`, con la forma de `:1284` | Caso borde «Dónde se ve el requisito» |
| Una solicitud vieja con programa «Sistemas» se consulta y se mueve sin requisito, y su fila no cambia | `RequestControllerIT`, patrón de `detailReturnsLegacyPhoneVerbatim` (`:1352`): se radica por la API y el valor viejo se escribe por SQL | FR-005, SC-005, US1 escenario 7, US2 escenario 6 |
| Un programa y una regla insertados por SQL en caliente para el trámite `DEMO`: el programa se acepta al registrar y el detalle trae el requisito, sin reiniciar ni tocar código | `WorkflowGenericityIT`, con `jdbcTemplate` como `insertDefinition` (`:432-471`) | FR-006, FR-011, SC-003 (US2 escenario 5) |
| La regla del catálogo: nulo no consulta; fuera del catálogo lanza `InvalidFieldValueException(["program"])`; se evalúa antes que créditos | `RequestBusinessRulesImplTest` (repositorio mockeado) | La decisión, aislada del transporte |
| Los dos handlers presentan `InvalidFieldValueException` como su campo inválido de Bean Validation (422 / 400, títulos, listas, sin eco) | `PublicCaptureExceptionHandlerTest`, `GlobalExceptionHandlerTest` | La forma, sin MockMvc; la resolución entre advices la fijan los IT |

**Por qué tres variantes y no la de la spec.** El ejemplo de US1 escenario 4, «ingenieria de
sistemas», difiere del catálogo en **dos** dimensiones a la vez (mayúsculas y tilde). Un mutante que
normalizara solo las mayúsculas (`existsByNameIgnoreCase`) lo seguiría rechazando por la tilde, y el
test pasaría con el mutante vivo. Una variante por dimensión mata un mutante por dimensión.

**Mutantes previstos**, cada uno con el test que debe ponerlo en rojo:

1. `existsByName` → `existsByNameIgnoreCase` → la variante de mayúsculas.
2. Quitar la guarda `program != null` → el 201 interno sin programa.
3. Mover la validación a `registerFromPublicChannel` (solo público) → el 400 interno.
4. Derivar el requisito ignorando la definición (buscar solo por programa) → `NOVEDAD_NOTAS` con
   «Ingeniería de Sistemas».
5. Condicionar el requisito a que el estado no sea final → el caso en estado final.
6. Agregar `annexRequirement` a `RequestSummaryResponse` → la guarda de búsqueda y bandeja.
7. Incluir el valor recibido en el `detail` → la aserción de que el cuerpo no contiene «Psicología».

**Lo que rompe por diseño, y lo que hay que tocar** (medido):

```bash
git grep -n '"program"' HEAD -- 'src/test/java/*.java'
# PublicRequestControllerIT.java:509   form.put("program", "Ingeniería de Sistemas");   → sigue pasando
# RequestControllerIT.java:137         "program": "Ingeniería de Sistemas",              → sigue pasando
# RequestControllerIT.java:488         "program": "Programa Reservado"                   → pasa a 400: RED por diseño
```

- `RequestControllerIT.java:479-499` (`readingARequestWithoutSessionLeaksNothing`, la prueba de
  no filtración de FR-021) tiene **dos** aserciones atadas a «Programa Reservado», y las tareas
  tienen que contar **dos líneas, no una**: `:490` espera `status().isCreated()` —pasa a 400— y
  `:498` afirma `not(containsString("Programa Reservado"))` sobre el 401. Arreglo preferido para la
  fase de tareas: `program` (`:488`) pasa a un nombre del catálogo, y el centinela de la filtración
  pasa al nombre del estudiante que el mismo fixture ya usa, `"Estudiante Reservado"` (`:486`), para
  que la aserción siga siendo distintiva —un nombre del catálogo es un texto común, el centinela no
  debe serlo—. Alternativa: insertar esa solicitud por SQL y conservar el centinela.
- **Dos constructores cambian y rompen la compilación de sus tests**: `RequestBusinessRulesImplTest`
  construye con `new RequestBusinessRulesImpl(parameterRepo)` (`:42`) y `RequestServiceImplTest` con
  `new RequestServiceImpl(definitionRepo, requestRepo, logRepo, userRepo, businessRules,
  parameterRepo, List.of(guards))` (`:636-638`). Ganan un mock cada uno. En `RequestServiceImplTest`
  las solicitudes de `requestAt` no tienen programa (`:647-653`), así que la guarda del nulo no
  llega al repositorio; la del canal público (`:334-336`) sí lo trae, y el mock devuelve vacío.
- Los fixtures del renderer que usan «Ingeniería de Sistemas» construyen la entidad sin pasar por el
  servicio y **no se tocan** (el canario de la 008 compara un SHA-256 literal).

**Aislamiento de datos entre IT**: los IT comparten la base del contenedor dentro del mismo contexto
de Spring, y `WorkflowGenericityIT` inserta configuración en caliente (sus helpers son idempotentes
*«para no chocar entre tests»*, `:414`, `:432-439`). Por eso el IT de la lista **no afirma el tamaño
exacto** —afirma que contiene los sembrados— y los inserts del test de genericidad son idempotentes.
Los envíos públicos usan cada uno un origen propio del rango de documentación (`publicSubmission`,
`PublicRequestControllerIT.java:548`, fija la IP en `:533`) para no chocar con el límite de envíos;
el tramo `203.0.113.90–99` no lo usa hoy ningún test
(`git grep -nE '203\.0\.113\.9[0-9]\b' HEAD -- src/test` no devuelve nada).

**Suite base**: último conteo registrado, **163 unitarios + 127 IT** en `ca5decf` (`CLAUDE.md:131`).
⚠️ No se re-midió para este plan: se re-mide con `./mvnw clean verify` al arrancar la
implementación, y ese es el número que vale. Archivos hoy: 22 `*Test.java` —uno de ellos,
`TramitaIntegrationTest.java`, es la anotación común de los IT (`:25-38`), no un test— y 11 `*IT.java`:

```bash
find src/test/java -name '*Test.java' | wc -l   # 22
find src/test/java -name '*IT.java' | wc -l     # 11
```

**Alternativa descartada**: *probar la derivación del requisito con un unitario de
`RequestServiceImpl` y el repositorio mockeado*. Afirmaría por construcción lo que el mock devuelve;
la garantía es sobre el JSON servido en las tres acciones, y eso lo ve un IT.

---

## D11 — Reparto con el frontend y orden de despliegue

**Decisión**: este repositorio entrega el catálogo, la validación y el requisito. Todo lo demás es
del repositorio del front, que lleva otro agente (Codex), y se le entrega como brief desde
`plan.md`, sección «Reparto con el frontend», con el precedente del brief de la 008
(`tramita-frontend#59`; su research D9). **El selector del formulario público debe desplegarse
antes que el backend, o a la vez.**

**Justificación**: es una **enmienda no aditiva** de dos contratos (FR-013), y a diferencia de una
nota en papel, tiene consumidor:

- El formulario **público** manda el programa como texto libre: `app/solicitud/creditos-adicionales/page.tsx:18`
  lo inicializa en `''` y `components/do-fr-100/sections.tsx:164` lo pinta como `TextField`. Si el
  backend llega primero, todo estudiante que no escriba el nombre letra por letra como el catálogo
  recibe 422. **Es el riesgo real.**
- El formulario **interno** ofrece la constante `PROGRAMS` (`lib/ui-constants.ts:33-39`, cinco
  nombres), importada en `app/requests/new/page.tsx:29` y pintada en su `Select` (`:293-303`). Cuatro
  coinciden con la lista dictada; «Psicología» no, y pasaría a 400. Además `useState(PROGRAMS[0])`
  (`:61`) **preselecciona** un programa: el cliente interno nunca omite el campo.
- Los tipos del programa viven en `lib/types.ts:67` (cuerpo público) y `:170` (modelo de la
  solicitud), `lib/api.ts:277` (cuerpo interno, opcional) y sus envíos en `:289`, `:292`, `:311` y
  `:326`. El detalle se mapea en `lib/store.tsx` (`ApiRequest`, `:54-69`; el programa en `:203`),
  donde entraría `annexRequirement`.
- **El error ya se ata al campo, sin código nuevo** (confianza alta, medido en el gate):
  `fieldErrorsFromProblem` recorre `invalidFields` y marca el campo si está en `FORM_FIELDS`
  (`app/solicitud/creditos-adicionales/page.tsx:42-50`); `FORM_FIELDS` se arma con las claves de
  `INITIAL_VALUES` más `signature` (`:28`), y `program` es una de ellas (`:18`). Un 422 con
  `invalidFields: ["program"]` marca el selector tal como hoy marcaría el campo de texto.

⚠️ **Las líneas del front se movieron desde que se armó este plan**: el mapeo previo se hizo sobre
`bd1b69d`, y la referencia local `origin/main` ya es `ff4b6ab` (PRs #67 y #68 del asistente del
formulario). El `TextField` del programa pasó de `sections.tsx:112` a `:164`; las demás líneas
citadas se verificaron sobre `ff4b6ab` con `git -C ../tramita-frontend show origin/main:<ruta>`. Se
re-verifican al escribir el brief.

🔁 **Re-medido el 2026-09-26 al cerrar la implementación**, con `git fetch`: `origin/main` del front ya es
**`0650548`** (PRs #70, «asistente cableado», y #71, README). La condición sigue sin cumplirse: el
`TextField` del programa vive en `sections.tsx:161`; `PROGRAMS` sigue en `lib/ui-constants.ts:33` y en
`app/requests/new/page.tsx:29`, `:61`, `:298`; sin `normalize(`; nadie consume `/api/public/programs`;
`INITIAL_VALUES` en `app/solicitud/creditos-adicionales/page.tsx:36` (`program: ''` en `:41`) y
`fieldErrorsFromProblem` en `:70`. El brief (T048) y el cuerpo de la PR (T047) citan estas líneas.

**Alternativas descartadas**:

- *Desplegar el backend primero y aceptar el 422 transitorio*: el costo lo paga un estudiante en el
  canal sin nadie que le explique (mismo rechazo que la 008, su D9).
- *Separar la validación en una PR posterior*: dejaría la regla de anexo (P2) apoyada en texto
  libre entre una PR y otra, que es justo lo que US1 existe para evitar.

**Costo aceptado**: la PR del back no se mergea sola. Lleva como condición explícita, en su cuerpo,
que el selector del formulario público esté en `main` del front o entre en la misma ventana.

---

## Lo que esta feature NO investiga, y por qué

- **Recibir o almacenar el anexo**: fuera por FR-012 y por la 006 (*«Trámita no es un repositorio
  de archivos»*). No se investiga carga de archivos.
- **Los anexos universales de la novedad de notas**: el gate `review-spec` los dejó fuera; agregar
  una regla «para todos los programas» es aditivo (D2).
- **El mapa programa → facultad**: no se tiene de la sede (spec, *Assumptions*). La facultad sigue
  escrita y la regla la ignora.
- **Una pantalla de administración del catálogo**: trece filas que cambian cada varios años; el §I
  lo descarta (spec, *Assumptions*).
- **Confirmar la lista con la Coordinación**: es un supuesto de la spec que se valida con ella, no
  con código. Mientras tanto, la siembra se marca provisional (D7).
- **Un orden de la lista según el español en cualquier entorno**: en los conocidos
  (`en_US.utf8`) ya sale así (D5); el `Collator` queda documentado para el caso contrario.
