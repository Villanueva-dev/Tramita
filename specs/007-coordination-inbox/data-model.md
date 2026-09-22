# Data Model — Bandeja de trabajo de la coordinación (007)

## Lo primero, porque es el hecho más relevante del diseño

**Esta feature no crea tablas, no agrega columnas y no lleva migración Flyway.**

Todo lo que la bandeja necesita ya está persistido:

| Dato que la feature necesita | Dónde vive hoy | Desde |
|---|---|---|
| Quién debe actuar en cada paso | `workflow_transition.responsible` | `V2.0.0` |
| Estado actual de una solicitud | `request.current_state_id` | `V2.0.0` |
| Cuándo entró a ese estado | `request_transition_log.occurred_at` | `V2.0.0` |
| Si un estado es inicial o final | `workflow_state.is_initial` / `is_final` | `V2.0.0` |
| Origen de la solicitud | `request_transition_log.actor` (el portal público es una cuenta) | `V3.3.0` |

La última migración del repositorio sigue siendo **`V4.1.0`** después de esta feature. Es la
consecuencia directa del Principio I: el requisito existía, el esquema ya lo soportaba, y no
había nada que agregar.

---

## Entidades leídas (ninguna se modifica)

### `Request`

Se lee `id`, `definition`, `studentName`, `currentState`, `createdAt`. **No** se lee
`studentDocument` para la bandeja: su ausencia en el DTO es la garantía de minimización que el
javadoc de `toInboxEntry` ya declara (§III).

### `WorkflowTransition`

`fromState`, `toState`, `responsible`. Es la relación que responde «quién debe actuar ahora»:
una solicitud espera a **X** si alguna de sus transiciones sale de su estado actual y su
`responsible` es **X** (research D1).

Un estado sin transiciones de salida y no final deja a la solicitud **detenida sin
responsable**: no aparece en ninguna bandeja. FR-014 exige que eso sea visible en lugar de
desaparecer en silencio — y se cubre en **dos capas**, porque la base no lo garantiza (`V2.2.0`
no lo restringe):

1. **Guarda de runtime en el motor** (review A1 del 2026-09-21): `register` y `advance` rechazan
   dejar una solicitud en un estado no final sin transiciones de salida, con
   `IncompleteConfigurationException` (500 de configuración, sin datos del estudiante en el
   mensaje). El callejón se detecta en el momento en que una solicitud entraría en él, también
   para una definición cargada por SQL en caliente, que es la vía que SC-005 promueve.
2. **Invariante sobre la configuración sembrada** (tasks T018): todo estado no final tiene al
   menos una salida y ningún estado final tiene alguna. Vale para lo que está en la base cuando
   corre; por eso sola no bastaba.

### `WorkflowState`

`code`, `name`, `isInitial`, `isFinal`. Los cuatro campos ya existen; `isInitial` nunca se
expuso (research D6).

### `RequestTransitionLog`

Se lee `occurredAt` de la **última** entrada por solicitud, que es el instante desde el cual
espera (research D3). La tabla es inmutable por `trg_timeline_immutable` (§VII), así que ese
instante no puede falsearse ni corregirse a posteriori — la medición de la bandeja hereda esa
garantía sin hacer nada.

---

## Contratos de salida (DTOs)

### `InboxEntryResponse` — se **amplía**, no se reemplaza

Hoy lleva `id`, `definition`, `studentName`, `currentState`, `createdAt`.

| Campo nuevo | Tipo | Qué es |
|---|---|---|
| `waitingSince` | `OffsetDateTime` con el offset de la sede (`CampusTime.toCampus`) | Desde cuándo espera: `occurredAt` de su última transición, o `createdAt` si aún no tiene ninguna (research D3) |
| `pendingResponsible` | texto | El responsable que la solicitud espera. Redundante con el filtro pedido, y deliberado: hace la respuesta legible por sí sola y sobrevive al día en que la consulta acepte varios |
| `origin` | texto | Cómo nació la solicitud: capturada por la Coordinación o recibida por el enlace público (FR-007) |

**Sigue sin llevar documento de identidad.** Es el invariante de este DTO y no cambia.

**No** lleva duración en días: se expone el instante y el cliente resta (research D4).

⚠️ **`createdAt` sigue siendo `LocalDateTime` en UTC sin marcador** (contrato de la 004). Se decidió
el 2026-09-21 dejarlo así: el DTO lleva dos instantes en dos formatos, y el contrato lo advierte.

### `StateResponse` — gana un campo

`code`, `name`, `isFinal` → se suma **`isInitial`**. Es el campo que saca del cliente el
reconocimiento de códigos (research D6). Se construye en un solo lugar —desde la
implementación, `StateResponseMapper` en `service/impl/`, compartido por las respuestas de
solicitud y por el catálogo—, así que el cambio es puntual y un mutante sobre las marcas
alcanza a todas las respuestas.

### `WorkflowDefinitionDetailResponse` — nuevo

`code`, `name`, `version`, `states[]`. **Solo para el catálogo.**

⚠️ **No confundir con `WorkflowDefinitionResponse`**, que se anida en cada respuesta de
solicitud y **no se toca**. Ampliar aquel metería el listado de estados dentro de cada
solicitud devuelta y dispararía carga perezosa en cada una (research D6).

---

## Reglas de derivación

1. **Pendiente de X** = existe `WorkflowTransition` con `fromState = request.currentState` y
   `responsible = X`. Un estado final no tiene transiciones de salida, así que un trámite
   cerrado nunca aparece — sin necesidad de filtrarlo aparte. Que un estado final no tenga
   salidas lo garantiza el invariante de T018, no la base.
2. **`waitingSince`** = `max(occurredAt)` de las entradas de timeline de la solicitud;
   `createdAt` si no hay ninguna.
3. **Orden** = `waitingSince` ascendente: primero lo que más espera (research D5). Bajo la cota,
   el corte previo es por radicación (research D8).
4. **`origin`** = se deriva del actor de la **primera** entrada del timeline. El canal público
   actúa con una cuenta propia, así que el dato ya está registrado y no hay que guardarlo de
   nuevo.

## Índices

Ninguno nuevo en esta entrega. A 30–40 solicitudes por semestre las consultas son triviales;
agregar índices «por si acaso» es lo que el Principio I prohíbe. Si el volumen crece, el
candidato natural es `request_transition_log (request_id, occurred_at desc)`, y la decisión se
tomará con una medición, no con una intuición.
