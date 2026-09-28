# Data Model — Aviso de cierre al estudiante (008)

## Lo primero, porque es el hecho más relevante del diseño

**Esta feature no crea tablas, no agrega columnas, no escribe ningún registro nuevo y no lleva
migración Flyway.**

Todo lo que el aviso necesita ya está persistido:

| Dato que la feature necesita | Dónde vive hoy | Desde |
|---|---|---|
| Si el estado actual es un cierre | `workflow_state.is_final` | `V2.0.0` |
| Cómo nació la solicitud | `request_transition_log.actor` de la entrada de nacimiento (el portal público actúa con una cuenta propia) | `V2.0.0` la entrada de nacimiento; `V3.3.0` la cuenta del portal que la distingue |
| Correo declarado por el estudiante | `request.student_email` | `V3.3.0` |
| Teléfono declarado por el estudiante | `request.student_phone` (`VARCHAR(30)`) | `V3.3.0` |
| Nombre del estudiante, del trámite y del estado (el texto del aviso) | `request.student_name`, `workflow_definition.name`, `workflow_state.name` | `V2.0.0` |

La última migración del repositorio sigue siendo **`V4.1.0`** después de esta feature. Es la
consecuencia del Principio I y de tres requisitos de la spec: el sistema no envía (FR-006), no
registra «avisado» (FR-007) y avisar no deja registro (SC-006). No hay nada que agregar porque
no hay nada que guardar.

---

## Entidades leídas (ninguna se modifica)

### `Request`

Se leen, además de lo que el detalle ya devolvía, **`studentEmail`** y **`studentPhone`**. Los
dos son `updatable = false` desde `V3.3.0` (`Request.java:105-109`): el contacto declarado es
inmutable una vez radicado, y esta feature no lo toca (FR-011).

**Invariante nuevo sobre `student_phone`, y su alcance exacto.** A partir de esta feature,
**toda fila nueva** tiene un teléfono de exactamente diez dígitos `[0-9]{10}`, o ninguno si
entró por el canal interno sin declararlo. La garantía vive en el contrato de entrada
(`PublicRequestBody`, `CreateRequestBody`; research D3), **no en la base**: la columna sigue en
`VARCHAR(30)` y **las filas anteriores no se reescriben**. Consecuencia declarada: una solicitud
radicada antes de la feature puede tener un teléfono que no cumpla el formato —en la base de
desarrollo, ninguna de las cuatro públicas lo cumple (spec, casos borde)—; para ellas el cliente
no ofrece WhatsApp y el correo sigue disponible. Se decidió así porque acortar la columna
cuenta caracteres y no dígitos, y chocaría con esas filas (Engram #2412).

### `WorkflowState`

Se lee `isFinalState()` para `currentState`, como ya lo hacía `StateResponseMapper`. **No se
reconoce ningún código**: `FINALIZADA` y `RECHAZADA` son finales porque la semilla los marca así
(`V2.1.0:32-34`), y uno marcado mañana por SQL lo será igual (§VI, research D7).

### `RequestTransitionLog`

Se lee la **entrada de nacimiento** (la única con `fromState = null`) para derivar el origen,
con el mismo `originOf` de la 007 (research D2). La tabla es inmutable por `trg_timeline_immutable`
(§VII): el origen no puede falsearse después. **Nunca se escribe en ella por esta feature**: ni
al consultar el detalle ni al ofrecer el aviso; el test de SC-006 lo fija.

### `WorkflowDefinition`

Se lee `name` para el `WorkflowDefinitionResponse` anidado, como siempre. Sin cambios.

---

## Lo que NO es entidad, a propósito

### El aviso de cierre

**No se almacena, no tiene tabla, no tiene DTO propio.** Se compone en el cliente, en el momento,
con tres datos que ya viajan en el detalle: `studentName`, `definition.name` y
`currentState.name` (FR-005). Un `mailto:` y un enlace `wa.me` son URL construidas por quien
presenta, no recursos del servidor (research D5). Que no exista como entidad es lo que hace
verdaderos FR-006 y FR-007.

### «Avisado»

No existe. La spec lo acepta como limitación: el sistema no sabe si el estudiante fue avisado, y
no lo afirma. Un campo o tabla para registrarlo sería el primer paso de vuelta hacia el
mecanismo que se descartó (research D6).

---

## Contratos de salida y de entrada (DTOs)

### `RequestOrigin` — nuevo, y es un movimiento

Enum `{COORDINATION, PUBLIC_LINK}`, hoy anidado como `InboxEntryResponse.Origin`. Pasa a un
archivo propio en `dto/` porque desde esta feature lo usan dos DTO (research D1). **El JSON no
cambia**: los valores serializan igual. `COORDINATION` sigue significando «cualquier cuenta
autenticada», no un rol, como lo documenta el javadoc de la 007.

### `RequestResponse` — se **amplía**, no se reemplaza

Hoy lleva doce campos: `id`, `definition`, `studentName`, `studentDocument`, `studentCode`,
`program`, `semester`, `reason`, `subjects`, `currentState`, `availableTransitions`,
`createdAt`.

| Campo nuevo | Tipo | Qué es | Cuándo falta |
|---|---|---|---|
| `origin` | `RequestOrigin` | Cómo nació la solicitud, derivado de la entrada de nacimiento (007, FR-007; 008, FR-008) | Solo si no hay entrada de nacimiento, que `register` escribe siempre: anomalía de datos, no un tercer origen |
| `studentEmail` | texto | El correo que declaró el estudiante: destinatario del canal principal (FR-001) | Solicitud registrada por la Coordinación sin correo, o anterior a `V3.3.0` |
| `studentPhone` | texto | El teléfono que declaró: destinatario del canal alternativo (FR-002). Tal como se guardó, sin reescribir (FR-011) | Ídem |

Se omiten cuando son nulos por el `@JsonInclude(NON_NULL)` que el record ya tiene. **Es
aditivo**: los doce campos anteriores conservan forma y significado (FR-013).

**El javadoc del record cambia** (FR-008a): deja de afirmar que «no expone ningún dato de
contacto (FR-020)» —una cita que sobrevivió a su base— y pasa a decir por qué los expone ahora:
existe el consumidor, y es esta feature (research D4).

### `RequestSummaryResponse` e `InboxEntryResponse` — **no cambian**

Ni la búsqueda ni la bandeja llevan correo ni teléfono. Listan; el contacto solo tiene
consumidor en el detalle de una solicitud concreta (§III). Es el mismo criterio con que la
bandeja nunca lleva el documento, y se fija igual: sobre el JSON servido (research D4, D8).

### `PublicRequestBody.studentPhone` — cambia la regla, no el campo

De `@NotBlank @Size(max = 30)` a `@NotBlank @Pattern(regexp = "[0-9]{10}")`. Sigue siendo
obligatorio; ahora además tiene forma. Un valor que no la cumpla responde 422 nombrando el campo
en `invalidFields`; uno en blanco, 422 nombrándolo en `missingFields` —solo ahí, porque la
ausencia domina (`ValidationFields`)—. **Enmienda no aditiva** del contrato de la 004 (FR-013).

### `CreateRequestBody.studentPhone` — cambia la regla, sigue opcional

De `@Size(max = 30)` a `@Pattern(regexp = "[0-9]{10}")`. Omitirlo o mandarlo `null` sigue
siendo válido; si viene, tiene que cumplir la forma (FR-010), y `""` cuenta como «vino»: 400
con el campo en `invalidFields`. **Enmienda no aditiva** del contrato interno (FR-013).

---

## Reglas de derivación

1. **`origin`** = actor de la primera entrada del timeline (`fromState = null`): la cuenta del
   portal público → `PUBLIC_LINK`; cualquier otra → `COORDINATION`; sin entrada → `null`. Es la
   regla de la 007, sin cambios.
2. **«Se ofrece el aviso»** = `origin == PUBLIC_LINK && currentState.isFinal`. **La aplica el
   cliente**, no el servidor (research D5). El servidor entrega los dos hechos.
3. **«Se ofrece WhatsApp»** = lo anterior y además `studentPhone` cumple `^3[0-9]{9}$`. También
   del cliente (FR-002, FR-012).
4. **Texto del aviso** = `studentName` + `definition.name` + `currentState.name`, y nada más
   (FR-005). Lo compone el cliente con lo que el detalle ya trae.

## Índices

Ninguno nuevo. La consulta adicional del detalle (research D2) es por `request_id` sobre el
timeline, y `V2.0.0:77-78` ya declara `ix_request_transition_log_timeline` sobre
`(request_id, occurred_at, id)` —el mismo orden con que se lee—. A 30–40 solicitudes por
semestre no hay nada que medir todavía.
