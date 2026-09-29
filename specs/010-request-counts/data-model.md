# Data Model — Conteos de solicitudes por trámite y estado (010)

## Lo primero, porque es el hecho más relevante del diseño

**Esta feature no crea tablas, no agrega columnas y no escribe nada.** No hay migración: la última sigue
siendo `V5.2.0`. Los conteos **no se almacenan**: se calculan al consultar (spec, *Key Entities*), así que
no pueden desincronizarse de las solicitudes y no dejan rastro en el historial.

| Qué | Dónde vive | Desde |
|---|---|---|
| El trámite, con su versión | `workflow_definition` (`code`, `version`, `name`), sin cambios | `V2.0.0` |
| Los estados de cada versión y sus marcas | `workflow_state` (`code`, `name`, `is_initial`, `is_final`), sin cambios | `V2.0.0` |
| La solicitud y su estado actual | `request.definition_id` y `request.current_state_id`, sin cambios | `V2.0.0` |
| El conteo por estado | **en ninguna parte**: se deriva al leer | — |

No hay trigger nuevo: la garantía del §VII protege el historial de la solicitud
(`trg_timeline_immutable`, `trg_document_seal_immutable`, `constitution.md:342-349`) y esta feature ni lo
lee ni lo escribe.

---

## Entidades leídas (ninguna se modifica)

### `WorkflowState` → `workflow_state`

Se leen `code`, `name`, `initial` (`is_initial`) y `finalState` (`is_final`)
(`WorkflowState.java:44-54`) y la asociación `definition` (`:38-40`). Es el **punto de partida** de la
consulta: por eso un estado sin solicitudes aparece.

### `WorkflowDefinition` → `workflow_definition`

Se leen `code` (`WorkflowDefinition.java:46`), `version` (`:53`) y `name` (`:57`). El código identifica el
trámite entre versiones; la versión mayor es la vigente, con el mismo criterio de
`IWorkflowDefinitionRepo.findAllCurrent` (`IWorkflowDefinitionRepo.java:21-26`).

### `Request` → `request`

Solo se lee la asociación `currentState` (`Request.java:66-68`), que es a lo que se une la consulta. **No
se lee ningún dato personal**: ni nombre, ni documento, ni contacto, ni programa. Es la garantía de FR-009
(y del §III) por construcción: el dato no entra a la consulta, así que no puede salir.

`Request.definition` (`:63-64`, inmutable) no interviene: el trámite de una solicitud se obtiene a través
de su **estado actual**, que pertenece a la misma versión con la que nació.

---

## Lo que se lee, en el orden en que se lee

### 1. La fila de la consulta: `IRequestRepo.StateCountRow`

**Primera proyección del código.** Un `record` anidado en `IRequestRepo`, uno por cada estado de cada
versión de cada trámite:

| Componente | Tipo | Sale de |
|---|---|---|
| `definitionCode` | `String` | `d.code` |
| `definitionVersion` | `int` | `d.version` |
| `definitionName` | `String` | `d.name` |
| `stateCode` | `String` | `s.code` |
| `stateName` | `String` | `s.name` |
| `initial` | `boolean` | `s.initial` |
| `finalState` | `boolean` | `s.finalState` |
| `count` | `Long` | `count(r)` |

- **`Long` y no `long`** en el conteo, porque es lo que devuelve `count(...)` y así lo pide la resolución
  del constructor (research D6). Los demás componentes espejan el tipo de la columna; que `int` y `boolean`
  primitivos emparejen con la selección es un **supuesto que confirma el arranque del contexto** en el
  primer IT (research D6, *Costo aceptado*).
- **El `record` no sale por la API.** La frontera es `RequestCountsResponse` (abajo): la fila lleva la
  versión, que el cliente no necesita, y el nombre del trámite repetido en cada estado.
- **Cuántas filas**: una por cada estado existente en la base, sea de la versión que sea. En la semilla son **21**: 8
  de la adición v1, 6 de la novedad v1 y 7 de la novedad v2 (las de la 010 en la base de demo se miden en
  el quickstart).

### 2. La consulta

```java
select d.code, d.version, d.name, s.code, s.name, s.initial, s.finalState, count(r)
from WorkflowState s
join s.definition d
left join Request r on r.currentState = s
group by d.code, d.version, d.name, s.code, s.name, s.initial, s.finalState
```

- **`left join`**: un estado sin solicitudes produce **una** fila con `count(r) = 0`. Con `inner join`
  desaparecería (mutante 1).
- **Sin `where`, sin parámetros**: cubre toda la base (FR-006) y no hay nada que un cliente pueda
  inyectar.
- **`group by`** con todas las columnas seleccionadas que no son agregados, como exige PostgreSQL.
- Devuelve una fila por estado, **no** por solicitud: lo que cruza a Java es del orden de una veintena de
  filas, no de la tabla de solicitudes.

### 3. El plegado, en `RequestCountServiceImpl`

Convierte las filas en la respuesta. Reglas, en orden:

| # | Regla | Requisito |
|---|---|---|
| 1 | Agrupar por `definitionCode`: los trámites nunca se mezclan | FR-004 |
| 2 | En cada grupo, versión mayor = `max(definitionVersion)` | FR-005 |
| 3 | Conservar las filas de la versión mayor **o** con `count > 0` | FR-003, FR-005 |
| 4 | Agrupar las conservadas por `stateCode`, sumando `count` | FR-005 |
| 5 | Nombre y marcas de cada estado: los de la fila de **mayor versión** de ese código | FR-005 |
| 6 | Nombre del trámite: el de la fila de mayor versión del grupo | FR-005 |
| 7 | Ordenar los trámites por nombre (empate: por código); los estados, sin orden significativo | research D7 |

**Ejemplo con la semilla actual.** Los valores de `count` son **ilustrativos**, inventados para mostrar el
plegado; los estados y sus versiones son los reales (`V2.1.0`, `V5.2.0`):

| Trámite | Código de estado | v1 `count` | v2 `count` | Se conserva | Sale |
|---|---|---|---|---|---|
| Novedad de notas | `REGISTRADA` | 0 | 1 | v2 (mayor); v1 no (0) | **1** |
| Novedad de notas | `EN_PREPARACION` | 2 | 1 | ambas (v1 tiene 2) | **3**, nombre y marcas de la v2 |
| Novedad de notas | `EN_FIRMA_SEDE` | — | 0 | v2 (mayor) | **0**, existe solo en la v2 |
| Novedad de notas | `FINALIZADA` | 1 | 0 | ambas (v1 tiene 1) | **1**, marcada final como en la v2 |
| Adición de créditos | `EN_FACULTAD` | 2 | — | v1 (mayor y única) | **2** |
| Novedad de notas | `EN_FACULTAD` | 1 | 0 | ambas (v1 tiene 1) | **1**, aparte del de la adición |

La última fila es el caso central de SC-003: `EN_FACULTAD` de la adición (2) y el de la novedad (1) salen
en **dos entradas distintas** y nunca como 3.

**Un estado que existe solo en una versión anterior**: hoy no hay ninguno (los seis de la v1 de la novedad
existen en la v2), así que el caso lo cubre el unitario con filas construidas a mano. Si apareciera, se
conserva con `count > 0` y se descarta con 0 (D2).

**Un límite conocido**: si la versión mayor de un trámite **no tuviera estados**, no produciría filas y su
nombre saldría del de una versión anterior con solicitudes. No se da en la semilla. El invariante de
`WorkflowGenericityIT` (javadoc `:198-206`) **no** lo cubre: revisa las salidas de cada estado, y una
versión sin estados no tiene ninguno que revisar. Se declara y no se trata.

---

## Lo que sale: los DTOs de la frontera

Dos `record` nuevos en `dto/`. `StateResponse` **ya existe** y no cambia
(`StateResponse.java:16`: `code, name, isInitial, isFinal`).

### `RequestCountsResponse`

| Campo | Tipo | Notas |
|---|---|---|
| `definitionCode` | `String` | El código estable del trámite entre versiones |
| `definitionName` | `String` | De la versión más alta |
| `states` | `List<StateCountResponse>` | Sin orden significativo; nunca vacía en la práctica |

### `StateCountResponse`

| Campo | Tipo | Notas |
|---|---|---|
| `state` | `StateResponse` | Código, nombre, `isInitial`, `isFinal`: las mismas cuatro claves del resto de la API |
| `count` | `long` | ≥ 0; solicitudes que están **hoy** en ese estado, sumadas entre versiones |

La respuesta es una **lista de `RequestCountsResponse`**, una por trámite, ordenada por nombre. Sin trámites
configurados sería `[]`.

**Conjunto exacto de claves** (lo fija un IT, FR-009 y SC-005): `definitionCode`, `definitionName`,
`states`; dentro de cada estado `state` y `count`; dentro de `state`, `code`, `name`, `isInitial`,
`isFinal`. **Ninguna** otra: ni `id`, ni `version`, ni total, ni datos personales.

---

## Invariantes de la respuesta

Cada uno lo comprueba un test (plan, *Plan de pruebas*):

| Invariante | Requisito |
|---|---|
| Una solicitud cuenta **una sola vez**, en su estado actual | US1, escenario 2 |
| La suma de `count` de un trámite es el número de solicitudes de ese trámite | SC-002 |
| Dos trámites que comparten un código de estado no se mezclan | FR-004, SC-003 |
| Todo estado de la versión vigente aparece, con 0 si no tiene solicitudes | FR-003, SC-004 |
| Cada código de estado aparece **una sola vez** por trámite | FR-005 |
| Un trámite cargado por SQL, sin solicitudes, aparece con todos sus estados en 0 | FR-010, SC-006 |
| Ninguna clave nombra un dato personal | FR-009, SC-005 |
| Ningún literal de trámite ni de estado en el código | FR-010 (§VI) |

**Un invariante que NO se afirma**: que los conteos sean un registro histórico. Una solicitud devuelta de la
novedad de notas se cuenta en `EN_PREPARACION`, y el `EN_PREPARACION` de la v1 incluye una espera que en la
v2 vive en `EN_FIRMA_SEDE` (research D2). Son los conteos de **hoy**, para llevar el control a ojo.

---

## Lo que esta feature NO modela

- **Una clasificación de estados** (terminado, rechazado, devuelto): es del cliente (research D1). La
  corrección de fondo sería una marca en `workflow_state`, con migración, y queda fuera.
- **Un total** ni conteos por programa, fecha o responsable.
- **Ninguna caché ni tabla de resumen**: cada llamada lee la base (FR-007).
