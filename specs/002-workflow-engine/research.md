# Research — Motor de workflow configurable (002)

**Phase 0** del plan. Cada decisión lleva su racional, las alternativas evaluadas y su
fuente. Las decisiones D1 y D2 se cerraron el 2026-08-05 en la sesión de diseño previa a
`/speckit-specify` (registro completo con alternativas en engram #866); se documentan aquí
porque este archivo es el registro trazable del plan (constitución §IV).

---

## D1 — La configuración vive en base de datos, no en código

**Decisión**: estados, transiciones y responsables de cada trámite son **filas en tablas**,
no enums ni clases. El motor (`RequestServiceImpl.advance()`) no menciona ningún trámite
concreto: busca la transición en la definición de la solicitud, la valida y la registra.

**Racional**: el documento de grado ya se comprometió — OE2 dice *«desarrollar un motor
configurable»* y el árbol de problemas §7 dice que la diferencia entre trámites *«es de
configuración, no de código»*. Con enums esa frase es falsa y el jurado lo ve leyendo el
código. FR-001 y SC-005 lo exigen de forma verificable: agregar un trámite = cargar
configuración, sin recompilar ni desplegar.

**Alternativas descartadas**:
- *Enum + configuración por tipo en código*: más type-safe y tests triviales, pero agregar
  un trámite exige recompilar. Hallazgo adicional que la hunde: con un enum común de
  estados, una transición de *novedad* que referencia un estado de *adición* **compila
  igual** — el type-safety, su ventaja principal, se erosiona justo en el escenario
  multi-trámite.
- *Motor BPMN embebido (Flowable/Camunda)*: OE2 dice **desarrollar** un motor; integrando
  uno ajeno, el aporte de ingeniería del trabajo de grado desaparece. Además traería BPMN,
  su modelo de datos y su curva de aprendizaje para un dominio de cadenas secuenciales.

## D2 — Definiciones versionadas: la versión es parte de la identidad

**Decisión**: `workflow_definition (code, version)` con `UNIQUE(code, version)`, **sin
tabla padre separada**. Editar un trámite = insertar `(ADICION_CREDITOS, 2)`. La vigente
para solicitudes nuevas es la de mayor `version`; cada solicitud guarda la FK a la versión
concreta con la que nació y se rige por ella hasta cerrar (FR-009).

**Racional**: sin versionado, editar una definición reescribe retroactivamente las reglas
de solicitudes en curso — indefendible en un sistema cuya tesis es la trazabilidad. El
costo total de la inmutabilidad histórica es una columna y un UNIQUE compuesto. El caso
concreto ya existe: la Coordinación confirmó que el paso de carga en QF por Registro Cali
sigue vigente *«de momento»* — si desaparece, se carga una versión 2 y las solicitudes en
curso no se ven afectadas (engram #878).

**Alternativa descartada**: definición mutable + FK simple. Es el diseño inicial de la
sesión del 2026-08-05; se descartó al detectar el bug de reglas cambiadas bajo los pies.
Retrofitear versionado después obligaría a inventar a qué versión pertenecía cada solicitud
existente — por eso entra el día uno (criterio de corte, D3).

## D3 — Cinco tablas en `002`; `workflow_parameter` y `guard_key` se difieren a `003`

**Decisión**: el esquema de esta feature es `workflow_definition`, `workflow_state`,
`workflow_transition`, `request`, `request_transition_log`. La tabla `workflow_parameter`
y la columna `workflow_transition.guard_key` del diseño #866 **no entran**: llegarán en la
feature `003` junto con SP2, vía migración Flyway.

**Racional**: la spec declara que en `002` el sistema **no evalúa reglas de negocio** (los
Límites del motor y el supuesto SP2→`003`); esa tabla y esa columna quedarían vacías y sin
consumidor, violando el Principio I (*«no se agregan columnas especulativas; el sistema
crece con migraciones cuando el requisito exista»*). El propio criterio de corte del diseño
lo resuelve: *«YAGNI aplica a lo barato de agregar después; lo caro de retrofitear se
decide al principio»*. Tabla nueva + columna nullable = migración trivial; el versionado
(caro de retrofitear) sí entra hoy. Decisión ratificada por el equipo el 2026-08-06 durante
este plan.

**Trade-off aceptado**: el esquema del motor queda "incompleto" respecto del diseño #866
hasta `003`. El patrón de guardas ya está decidido para entonces: **handler-by-name**
(`guard_key` guarda un nombre como `'MAX_CREDITS'`; `interface IGuard { String key();
void check(...); }` con las implementaciones inyectadas en un `Map<String, IGuard>`).
**Nunca SpEL/MVEL ni motor de expresiones**: el *qué* se valida es código, el *límite* es
dato — lo contrario es empezar a construir un intérprete.

## D4 — `requires_note`: la devolución es concepto de la configuración, no del motor

**Decisión**: `workflow_transition.requires_note BOOLEAN`. El motor exige observación
cuando la transición la declara obligatoria (FR-014); no distingue "avance" de
"devolución" — para él ambas son transiciones definidas (FR-013, nota terminológica de la
spec). En la semilla, las transiciones de retorno llevan `requires_note = true`.

**Racional**: mantiene el motor genérico (la tesis) y cumple FR-014 sin comportamiento a
medida. SC-007 (contar devoluciones y motivos) se responde consultando las entradas del
timeline que recorrieron transiciones de retorno de la definición — identificables por
configuración, no por lógica especial.

**Alternativa descartada**: columna `kind = FORWARD | RETURN` en la transición. Le daría al
motor conocimiento del negocio que la spec le niega expresamente («el motor no distingue
entre ambos casos») y no aporta nada que la configuración no exprese ya.

## D5 — `responsible` en la transición: el "en nombre de" es dato derivable

**Decisión**: `workflow_transition.responsible VARCHAR` — etiqueta de configuración del
responsable del paso (`'COORDINACION'`, `'FACULTAD'`, `'REGISTRO_CALI'`, …). El timeline
muestra el actor real (FK a `users`, siempre la Coordinación) junto al responsable del
paso, derivado por join contra la definición (FR-006: constancia de que el registro lo hizo
la Coordinación en nombre del paso externo).

**Racional**: FR-001 exige que la definición incluya *«el responsable de cada paso»*. El
join es estable porque las definiciones versionadas son inmutables (D2) — no hace falta
copiar el dato al log. Un solo rol autenticado existe hoy; la tabla N-a-N
`workflow_transition_role` quedó fuera por YAGNI (engram #866) y entraría con una migración
si algún día hay más actores.

## D6 — Concurrencia: locking optimista con `@Version` → 409

**Decisión**: `request` lleva columna `version` mapeada con `@Version` (Jakarta
Persistence). Ante dos avances casi simultáneos, la segunda transacción falla al hacer
flush y el `GlobalExceptionHandler` la traduce a **409 Conflict** RFC 7807 (edge case
declarado en la spec: *«solo prospera el que sea legal desde el estado vigente»*).

**Racional**: es el mecanismo estándar de JPA, sin locks pesimistas ni colas — adecuado
para un sistema de 1 actor donde el conflicto es excepcional (doble clic, dos pestañas).
Verificado contra el código fuente de Spring Data JPA: el provider lanza
`OptimisticLockException`/`StaleObjectStateException` y el
`PersistenceExceptionTranslationInterceptor` (activado por `@Repository` en
`SimpleJpaRepository`) la traduce a `ObjectOptimisticLockingFailureException` de Spring —
esa es la excepción a manejar.
Fuente: [SimpleJpaRepository.java (spring-projects/spring-data-jpa)](https://github.com/spring-projects/spring-data-jpa/blob/main/src/main/java/org/springframework/data/jpa/repository/support/SimpleJpaRepository.java),
consultado vía Context7 el 2026-08-06.

**Alternativa descartada**: `SELECT ... FOR UPDATE` (lock pesimista). Serializa todos los
avances para proteger un conflicto que casi nunca ocurre; complejidad sin requisito.

## D7 — El registro de la solicitud escribe la primera entrada del timeline

**Decisión**: al registrar una solicitud (US1) se inserta una entrada en
`request_transition_log` con `from_state_id = NULL` y `to_state_id` = estado inicial, con
autor y fecha. El timeline completo de una solicitud es **una sola consulta ordenada** a
una sola tabla.

**Racional**: SC-006 exige que *«la historia completa se reconstruya íntegramente desde el
sistema»* — el nacimiento (quién registró, cuándo) es parte de esa historia. Con la entrada
inicial, la consulta del timeline no mezcla fuentes (log + metadata de la solicitud) ni
sintetiza eventos en la capa de presentación.

**Alternativa descartada**: timeline solo de transiciones + nacimiento leído de
`request.created_at`. Dos fuentes para una sola vista; la vista tendría que fusionar y
ordenar en memoria lo que la tabla ya ordena sola.

## D8 — Inmutabilidad del timeline reforzada en la base de datos

**Decisión**: la migración `V2.0.0` crea un trigger `BEFORE UPDATE OR DELETE` sobre
`request_transition_log` que lanza excepción. La aplicación además no expone ninguna
operación de edición/borrado (FR-007), pero la garantía fuerte vive en la capa más baja.

**Racional**: SC-002 promete que *«ninguna entrada del timeline puede modificarse ni
eliminarse después de registrada»* — con el trigger, la promesa es verificable incluso ante
un bug de la aplicación o un acceso directo a la BD. Son ~10 líneas de SQL en una migración
que ya existe; no es especulación, es el requisito FR-007 ejecutado en su capa natural.
Fuente: [PostgreSQL 16 — CREATE TRIGGER](https://www.postgresql.org/docs/16/sql-createtrigger.html).

**Alternativa descartada**: confiar solo en la ausencia de endpoints y métodos de
repositorio. Cumple la letra de FR-007 pero deja SC-002 sin garantía ante acceso directo —
débil para una tesis cuyo argumento central es la auditabilidad.

## D9 — Identificación del estudiante: columnas explícitas, sin JSONB en `002`

**Decisión**: `request.student_name` y `request.student_document` como columnas planas,
indexadas para la localización (FR-011). El `payload JSONB` que aparecía en el borrador del
esquema (#866) **no entra** en esta feature.

**Racional**: la spec minimiza deliberadamente los datos persistidos (nombre + cédula, Ley
1581 de 2012 — supuesto explícito) y FR-011 exige buscar exactamente por esos dos campos:
columnas explícitas son consultables, indexables y validables sin ceremonias. Los campos
del formato oficial (asignatura, créditos, periodo, notas) son SP2 y llegan en `003`; si
allí conviene JSONB o tabla, se decide allá con el requisito enfrente (Principio I).

## D10 — Semilla por migración Flyway de datos; SC-005 se demuestra con un INSERT en vivo

**Decisión**: `V2.1.0__Seed_workflow_definitions.sql` carga las definiciones v1 de los dos
trámites del alcance. Como el motor lee las definiciones de BD en cada operación (sin
caché), **SC-005 se demuestra en caliente**: un `INSERT` de un tercer trámite vía `psql`
—sin recompilar ni reiniciar— y el motor lo opera de inmediato (guion en `quickstart.md`).

**Racional**: la semilla en migración da reproducibilidad total (cada ambiente y cada
corrida de Testcontainers nacen con los dos trámites operables — los IT la ejercitan
gratis). Y separar semilla (migración, para lo oficial) de demostración (INSERT en vivo,
para SC-005) mantiene honesta la métrica: lo que SC-005 promete es que cargar configuración
**no requiere desplegar**, y así se demuestra.
Fuente: [Flyway — Versioned Migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations).

**Estado de las cadenas** (insumo de la semilla, no de la estructura):
- **Adición de créditos: CERRADA** — 6 estados leídos de un hilo de correo real, más
  devolución y rechazo (engram #878).
- **Novedad de notas: PROPUESTA** — 6 estados sin rechazo, pendiente de confirmar con la
  Coordinación (3 preguntas redactadas, engram #880; reunión del martes). Si la reunión
  cambia la cadena, cambia **solo el contenido de `V2.1.0`** (o una versión 2 de la
  definición si ya se implementó) — la estructura del motor no se toca. Ese aislamiento es
  exactamente lo que la feature demuestra.

## D11 — Convenciones de nombres y detalles de esquema

- **Entidad `Request`** (tabla `request`): traducción directa de "Solicitud". Para evitar
  el tartamudeo `RegisterRequestRequest` en los DTOs de entrada, esta feature usa el sufijo
  **`Body`** para payloads de entrada (`CreateRequestBody`, `AdvanceRequestBody`) y
  mantiene `...Response` para salidas, como en `001`.
- **PK del log: `BIGSERIAL`**. A diferencia de `users` (UUID, decisión de `001`), el log es
  append-only y su identidad monótona da desempate barato al orden cronológico
  (`occurred_at, id`). Las demás tablas nuevas siguen con UUID por consistencia con el chasis.
- **RFC 7807**: se reutiliza `ProblemJsonWriter`/`GlobalExceptionHandler` de `001`. Nota de
  vigencia (patrón IEEE 830→29148): **RFC 9457 obsoleta a RFC 7807** manteniendo el media
  type `application/problem+json` y el modelo — el código no cambia; conviene un PATCH
  futuro de la constitución que actualice la cita
  ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)). No bloquea esta feature.
- **Deuda aceptada tras el code review (2026-08-06), con su porqué:**
  - **N+1 en `search()` y `getTimeline()`**: cada fila carga sus proxies LAZY, así que
    una búsqueda de N filas cuesta ~2N+1 consultas. **No se optimiza**: la escala real
    son decenas de solicitudes por semestre (árbol de problemas §9) y `@EntityGraph`
    agregaría acoplamiento a un problema que no existe (Principio I). El riesgo que lo
    agravaba —un resultado sin cota— se cerró al escapar los comodines de LIKE.
  - **FK no compuesta entre `workflow_transition` y `workflow_state`**: nada impide que
    una transición de la v2 referencie un estado de la v1. El motor compara por `code`,
    así que seguiría "funcionando" mientras reporta el `name` de la otra versión — una
    corrupción silenciosa. **No se corrige hoy**: exigiría un `UNIQUE (id, definition_id)`
    redundante en `workflow_state` más FK compuestas en dos tablas, y la única escritura
    del alcance es la semilla. Se revisa cuando exista administración de definiciones
    por UI, que es el momento en que un humano puede equivocarse a mano.
- **Errores nuevos**: transición no definida / estado final / conflicto optimista → **409**
  (`IllegalTransitionException`, `ObjectOptimisticLockingFailureException`); trámite
  inexistente al registrar y devolución sin motivo → **422** (se reutiliza
  `UnprocessableRequestException` de `001`); solicitud inexistente → **404**
  (`ResourceNotFoundException`, nueva).
