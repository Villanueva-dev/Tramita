# Data Model — Motor de workflow configurable (002)

**Phase 1** del plan. Cinco tablas nuevas (research D3), dos migraciones. Convención:
UUID como PK (chasis) salvo el log, que usa `BIGSERIAL` (research D11). Todas las FK con
`ON DELETE RESTRICT` implícito (no se borra configuración referenciada; no existe operación
de borrado en el alcance).

## Diagrama

```text
workflow_definition 1──N workflow_state
        │1                    ▲
        │                     │ (from/to/current)
        ├──N workflow_transition
        │
        └──N request 1──N request_transition_log N──1 users (001)
```

## Tablas (migración `V2.0.0__Create_workflow_tables.sql`)

### `workflow_definition` — el trámite como configuración versionada (FR-001, FR-009)

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `UUID` | PK |
| `code` | `VARCHAR(50)` | NOT NULL — `'ADICION_CREDITOS'`, `'NOVEDAD_NOTAS'` |
| `version` | `INT` | NOT NULL |
| `name` | `VARCHAR(120)` | NOT NULL — nombre para mostrar |
| `created_at` | `TIMESTAMP` | NOT NULL DEFAULT now() |
| | | **`UNIQUE (code, version)`** — la versión es la identidad (research D2) |

La **vigente** para solicitudes nuevas es la de mayor `version` por `code`. Las filas de
una definición ya usada son inmutables por convención de operación (editar = insertar
versión nueva); no se refuerza con trigger porque la única escritura del alcance es la
semilla — si en el futuro hay UI de administración, la garantía se re-evalúa allí.

### `workflow_state` — estados de una definición

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `UUID` | PK |
| `definition_id` | `UUID` | NOT NULL, FK → `workflow_definition` |
| `code` | `VARCHAR(50)` | NOT NULL — `'REGISTRADA'`, `'EN_FACULTAD'`, … |
| `name` | `VARCHAR(120)` | NOT NULL |
| `is_initial` | `BOOLEAN` | NOT NULL DEFAULT false |
| `is_final` | `BOOLEAN` | NOT NULL DEFAULT false |
| | | `UNIQUE (definition_id, code)` |

- Exactamente **un** estado inicial por definición (lo garantiza la semilla; el servicio
  falla explícito si no lo encuentra).
- El cierre por rechazo es simplemente **otro estado final** (`RECHAZADA`,
  `is_final = true`): FR-015 emerge de la configuración — un trámite sin ese estado no
  puede cerrarse negativamente porque no existe transición hacia él.
- **Omitido a conciencia**: columna de orden/posición para pintar barras de progreso. Ningún
  FR la exige; si el frontend la necesita, es una migración trivial (Principio I).

### `workflow_transition` — pasos permitidos (FR-003, FR-013, FR-014)

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `UUID` | PK |
| `definition_id` | `UUID` | NOT NULL, FK → `workflow_definition` |
| `from_state_id` | `UUID` | NOT NULL, FK → `workflow_state` |
| `to_state_id` | `UUID` | NOT NULL, FK → `workflow_state` |
| `responsible` | `VARCHAR(50)` | NOT NULL — etiqueta del responsable del paso (research D5) |
| `requires_note` | `BOOLEAN` | NOT NULL DEFAULT false — observación obligatoria (research D4) |
| | | `UNIQUE (definition_id, from_state_id, to_state_id)` |

Avances y retornos son la misma cosa para el motor (FR-013): una devolución es una
transición hacia un estado anterior con `requires_note = true` en la semilla. **Sin
`guard_key`** — llega en `003` (research D3).

### `request` — la solicitud (FR-002, FR-009, FR-011)

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `UUID` | PK |
| `definition_id` | `UUID` | NOT NULL, FK → `workflow_definition` — **la versión con la que nació** |
| `current_state_id` | `UUID` | NOT NULL, FK → `workflow_state` |
| `student_name` | `VARCHAR(120)` | NOT NULL |
| `student_document` | `VARCHAR(20)` | NOT NULL — cédula, se busca por igualdad |
| `version` | `BIGINT` | NOT NULL DEFAULT 0 — `@Version`, locking optimista (research D6) |
| `created_at` | `TIMESTAMP` | NOT NULL DEFAULT now() |

Índices: `request(student_document)` (búsqueda exacta) y `request(lower(student_name))`
(búsqueda por nombre, `ILIKE` — a esta escala no se necesita trigram). Sin `created_by`:
el autor del registro vive en la primera entrada del timeline (research D7).

### `request_transition_log` — el timeline inmutable (SP6: FR-005, FR-007, FR-008, FR-014)

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGSERIAL` | PK — desempate monótono del orden cronológico |
| `request_id` | `UUID` | NOT NULL, FK → `request` |
| `from_state_id` | `UUID` | **NULL**, FK → `workflow_state` — NULL = entrada de registro (research D7) |
| `to_state_id` | `UUID` | NOT NULL, FK → `workflow_state` |
| `actor_id` | `UUID` | NOT NULL, FK → `users` (feature 001) |
| `note` | `TEXT` | NULL — obligatoria solo si la transición la exige (FR-014) |
| `occurred_at` | `TIMESTAMP` | NOT NULL DEFAULT now() |

Índice: `request_transition_log(request_id, occurred_at, id)` — el timeline es un solo
`SELECT` ordenado.

**Trigger de inmutabilidad** (research D8), en la misma migración:

```sql
CREATE FUNCTION reject_timeline_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'request_transition_log es inmutable: solo se permite INSERT (FR-007)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_timeline_immutable
  BEFORE UPDATE OR DELETE ON request_transition_log
  FOR EACH ROW EXECUTE FUNCTION reject_timeline_mutation();
```

## Entidades JPA (package-by-layer, `model/`)

| Entidad | Notas de mapeo |
|---|---|
| `WorkflowDefinition` | `@OneToMany` a states y transitions (LAZY); el servicio la carga completa como agregado de solo lectura. |
| `WorkflowState` | `@ManyToOne` a definition. |
| `WorkflowTransition` | `@ManyToOne` a definition, fromState, toState. |
| `Request` | `@ManyToOne` a definition y currentState; **`@Version private long version;`**. |
| `RequestTransitionLog` | `@ManyToOne` a request y states; `@ManyToOne` a `User`. Sin setters de negocio: se construye completa y se persiste una vez. |

Las entidades de configuración no exponen operaciones de escritura en el alcance de esta
feature (la única escritura es la semilla SQL); los repos correspondientes son de lectura.

## El motor — `RequestServiceImpl.advance(requestId, targetStateCode, note, actor)`

1. Carga la solicitud y **su** definición (la versión con la que nació — FR-009).
2. Busca la transición `(current_state → target_state)` en esa definición. No existe o el
   estado actual es final → `IllegalTransitionException` (409, FR-003/FR-004).
3. Si `requires_note` y no llegó observación → 422 (FR-014).
4. Inserta la entrada del log (autor, fecha, nota — FR-005) y mueve `current_state_id`.
5. Commit; conflicto de `@Version` → 409 (research D6). El estado no se corrompe: gana la
   transacción que vio el estado vigente.

El método no contiene ningún literal de negocio: ni `'ADICION_CREDITOS'`, ni `'DEVUELTA'`,
ni el 21. Esa ausencia es verificable leyendo la clase y es el argumento de la defensa.

## Semilla (migración `V2.1.0__Seed_workflow_definitions.sql`)

### Adición de créditos v1 — **CERRADA** (hilo de correo real; engram #878)

Estados: `REGISTRADA*` → `EN_FACULTAD` → `APROBADA_FACULTAD` → `EN_REGISTRO_CALI` →
`EN_REGISTRO_NACIONAL` → `FINALIZADA†` · `DEVUELTA` · `RECHAZADA†`  (* inicial, † final)

| Transición | `responsible` | `requires_note` |
|---|---|---|
| REGISTRADA → EN_FACULTAD | COORDINACION | no |
| EN_FACULTAD → APROBADA_FACULTAD | FACULTAD | no |
| APROBADA_FACULTAD → EN_REGISTRO_CALI | COORDINACION | no |
| EN_REGISTRO_CALI → EN_REGISTRO_NACIONAL | REGISTRO_CALI | no |
| EN_REGISTRO_NACIONAL → FINALIZADA | REGISTRO_NACIONAL | no |
| EN_FACULTAD → DEVUELTA | FACULTAD | **sí** |
| EN_REGISTRO_CALI → DEVUELTA | REGISTRO_CALI | **sí** |
| EN_REGISTRO_NACIONAL → DEVUELTA | REGISTRO_NACIONAL | **sí** |
| DEVUELTA → EN_FACULTAD | COORDINACION | no |
| EN_FACULTAD → RECHAZADA | FACULTAD | no |

El rechazo (extemporánea) solo existe desde `EN_FACULTAD`: es la facultad quien niega.

### Novedad de notas v1 — **PROPUESTA, provisional** (engram #880; confirmar con la Coordinación)

Estados: `REGISTRADA*` → `EN_PREPARACION` → `EN_FACULTAD` → `EN_REVISION_FINANCIERA` →
`EN_REGISTRO_CONTROL` → `FINALIZADA†`  — **sin estado de rechazo** (E3-Q19: «siempre termina»)

| Transición | `responsible` | `requires_note` |
|---|---|---|
| REGISTRADA → EN_PREPARACION | COORDINACION | no |
| EN_PREPARACION → EN_FACULTAD | SEDE | no |
| EN_FACULTAD → EN_REVISION_FINANCIERA | FACULTAD | no |
| EN_REVISION_FINANCIERA → EN_REGISTRO_CONTROL | FINANCIERA | no |
| EN_REGISTRO_CONTROL → FINALIZADA | REGISTRO_NACIONAL | no |
| EN_FACULTAD → EN_PREPARACION | FACULTAD | **sí** |
| EN_REVISION_FINANCIERA → EN_PREPARACION | FINANCIERA | **sí** |
| EN_REGISTRO_CONTROL → EN_PREPARACION | REGISTRO_NACIONAL | **sí** |

Aquí la devolución **no es un estado**: es la transición de retorno a `EN_PREPARACION`
(donde vive la carpeta editable). Que un trámite modele la devolución como estado y el otro
como retorno directo, sobre el mismo esquema, es parte de la demostración de US4.

**La asimetría completa que demuestra la tesis** (SC-004): mismo motor, dos definiciones —
una con `RECHAZADA` y estado `DEVUELTA`, la otra sin rechazo y con retorno directo. Cero
`if` por trámite en el código.

> ⚠️ Si la reunión del martes cambia la cadena de novedad (3 preguntas pendientes, engram
> #880), se edita **solo esta migración** si aún no corrió en ningún ambiente compartido, o
> se carga una definición v2 si ya corrió. La estructura no se toca.

## Trazabilidad requisito → esquema

| Requisito | Dónde vive |
|---|---|
| FR-001 (trámite = configuración) | las 3 tablas `workflow_*` |
| FR-002 (registrar con datos mínimos) | `request` + entrada inicial del log |
| FR-003/FR-004 (solo transiciones definidas) | `workflow_transition` + paso 2 del motor |
| FR-005 (autor y fecha por transición) | `request_transition_log.actor_id / occurred_at` |
| FR-006 (en nombre del paso externo) | `workflow_transition.responsible` + actor real en el log |
| FR-007 (timeline inmutable) | log solo-INSERT + trigger |
| FR-008 (timeline cronológico) | índice `(request_id, occurred_at, id)` |
| FR-009 (reglas congeladas al nacer) | `request.definition_id` → versión concreta |
| FR-010 (dos trámites, un motor) | dos filas en `workflow_definition`, cero código a medida |
| FR-011 (localizar por nombre/cédula) | índices sobre `student_document` / `lower(student_name)` |
| FR-012 (solo Coordinación autenticada) | filter chain de `001` sobre `/api/**` (contrato) |
| FR-013 (retornos y cierres negativos como config) | transiciones de retorno + estados finales |
| FR-014 (observación obligatoria en devolución) | `requires_note` + `note` en el log |
| FR-015 (rechazo solo si la definición lo tiene) | ausencia/presencia del estado final de rechazo |
