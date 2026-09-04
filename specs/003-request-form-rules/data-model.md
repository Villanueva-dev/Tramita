# Data model — Formularios validados y reglas de negocio por trámite (003)

**Phase 1** del plan. Parte del esquema que dejó la `002` (`V2.2.0`) y agrega dos migraciones.
Ninguna migración existente se edita.

## Resumen del cambio

| Objeto | Cambio | Migración |
|---|---|---|
| `request` | + 4 columnas del formulario | `V2.3.0` |
| `request_subject` | tabla nueva | `V2.3.0` |
| `workflow_parameter` | tabla nueva | `V3.0.0` |
| `workflow_transition` | + `guard_key` | `V3.0.0` |

---

## `V2.3.0` — Datos del formulario

### `request` (existente, ampliada)

| Columna | Tipo | Nulo | Notas |
|---|---|---|---|
| `student_code` | `VARCHAR(30)` | sí | Código institucional del estudiante |
| `program` | `VARCHAR(120)` | sí | Programa académico |
| `semester` | `VARCHAR(50)` | sí | Semestre al que aplica |
| `reason` | `VARCHAR(2000)` | sí | Motivo de la solicitud (FR-004) |

Las cuatro son opcionales: FR-001 lo exige para que una solicitud registrada con el
formulario mínimo de la `002` siga siendo válida, y porque la migración corre sobre una tabla
con filas ya existentes.

**No se agregan** (D9):

- `student_email` — FR-020, constitución §III. Su consumidor era SP7, fuera de alcance.
- `priority` — fuera del alcance por decisión de producto; sin respaldo en las entrevistas.

`reason` es `VARCHAR(2000)` y no `TEXT`: FR-004 exige una longitud máxima, y declararla en la
columna hace que la restricción exista también fuera del caso de uso. `TEXT` —lo que usa el
prototipo— deja la única cota en la anotación de validación del DTO.

### `request_subject` (nueva)

| Columna | Tipo | Nulo | Notas |
|---|---|---|---|
| `id` | `UUID` | no | PK |
| `request_id` | `UUID` | no | FK → `request(id)` |
| `code` | `VARCHAR(30)` | no | Código de la asignatura |
| `name` | `VARCHAR(150)` | no | Nombre de la asignatura |
| `credits` | `INT` | sí | `CHECK (credits > 0)` — FR-009 |
| `subject_group` | `VARCHAR(30)` | sí | `group` es palabra reservada en SQL |
| `current_grade` | `NUMERIC(3,2)` | sí | `CHECK` de sanidad `>= 0` — D3 |
| `proposed_grade` | `NUMERIC(3,2)` | sí | ídem |

- `CREATE INDEX ix_request_subject_request_id ON request_subject (request_id)`.
- **Sin `UNIQUE` sobre `(request_id, code)`**: dos grupos de la misma materia son un caso real
  de la Coordinación (Assumptions de la spec).
- `credits` es nulo-permitido porque el trámite de novedad de notas no lo usa; cuando viene,
  el `CHECK` exige que sea positivo. La obligatoriedad por trámite es regla de negocio, no de
  esquema.
- El rango efectivo de las notas lo fija la configuración (D4), no el `CHECK`, que solo impide
  valores absurdos.

---

## `V3.0.0` — Reglas de negocio configurables

### `workflow_parameter` (nueva)

| Columna | Tipo | Nulo | Notas |
|---|---|---|---|
| `id` | `UUID` | no | PK |
| `definition_id` | `UUID` | no | FK → `workflow_definition(id)` |
| `parameter_key` | `VARCHAR(50)` | no | Nombre del parámetro |
| `parameter_value` | `VARCHAR(100)` | no | Valor, como texto |

- `CONSTRAINT uq_workflow_parameter_definition_key UNIQUE (definition_id, parameter_key)`.
- La FK apunta a la **versión** concreta de la definición, que es la identidad en la `002`
  (`UNIQUE(code, version)`): con eso, FR-013 se cumple sin lógica adicional — una solicitud ya
  se rige por su versión, y los parámetros viajan con ella.
- `parameter_value` es texto para todos los parámetros. No se introduce una columna de tipo:
  cada consumidor interpreta el valor y falla explícitamente si no puede (FR-011, D2).

### Seed inicial

| Definición | Parámetro | Valor | Respaldo |
|---|---|---|---|
| `ADICION_CREDITOS` v1 | `MAX_CREDITS` | `21` | **Derivado — provisional y no auditado** |
| `ADICION_CREDITOS` v1 | `MIN_GRADE` / `MAX_GRADE` | escala institucional | ídem |
| `NOVEDAD_NOTAS` v1 | `MIN_GRADE` / `MAX_GRADE` | escala institucional | ídem |

El seed se ata a `code + version`, nunca a un UUID literal.

⚠️ **El comentario de la migración no debe decir «confirmado en entrevistas»** —es lo que dice
el prototipo y es falso: el respaldo es una síntesis derivada, y el reglamento estudiantil no
se ha obtenido. Constitución v2.2.2 §IV: se marca **provisional y no auditado**.

### `workflow_transition` (existente, ampliada)

| Columna | Tipo | Nulo | Notas |
|---|---|---|---|
| `guard_key` | `VARCHAR(50)` | sí | Nombre de la regla que condiciona la transición |

`NULL` = sin guarda, comportamiento idéntico a la `002` (FR-017, D6). Las transiciones ya
sembradas quedan en `NULL` sin migración de datos.

---

## Entidades JPA

Todas siguen el patrón establecido en `main`: `@NoArgsConstructor(PROTECTED)` +
`@AllArgsConstructor(PRIVATE)` + `@Builder` + `@Getter`. **Nunca `@Data` ni `@Setter`.**

| Entidad | Estado |
|---|---|
| `Request` | ampliada — 4 campos + `@OneToMany` a `RequestSubject` |
| `RequestSubject` | nueva |
| `WorkflowParameter` | nueva |
| `WorkflowTransition` | ampliada — `guardKey` |

- Todo el estado de captura lleva `updatable = false` (FR-005), coherente con el diseño
  inmutable de la `002`.
- `Request.subjects`: `@OneToMany(mappedBy = "request", cascade = ALL, orphanRemoval = true,
  fetch = LAZY)`, con `@Builder.Default` a lista vacía. El lado dueño se setea al construir
  cada `RequestSubject`.
- `WorkflowParameter.key` y `.value` mapean a `parameter_key` / `parameter_value`; `key` lleva
  `updatable = false`, `value` no —ajustar un parámetro es el caso de uso de SC-005—.

## Repositorios

| Repositorio | Método | Notas |
|---|---|---|
| `IWorkflowParameterRepo` | `findByDefinitionIdAndKey(UUID, String)` | Query derivada; no requiere `@Query` |

`RequestSubject` no necesita repositorio propio: se persiste por cascada desde `Request` y se
lee por la relación.

## Reglas de validación por requisito

| Requisito | Dónde se aplica |
|---|---|
| FR-004 motivo acotado | `@Size(max = 2000)` en el DTO + `VARCHAR(2000)` en base |
| FR-009 créditos positivos | `@Min(1)` + cota superior en el DTO, `CHECK (credits > 0)` en base |
| FR-008 tope de créditos | Servicio de reglas, contra `MAX_CREDITS` |
| FR-010 / FR-011 config incompleta | Servicio de reglas → `IncompleteConfigurationException` → 500 |
| FR-012 rango de notas | Servicio de reglas, contra `MIN_GRADE` / `MAX_GRADE` |
| FR-019 guarda desconocida | Motor de transición → `IncompleteConfigurationException` |

La validación de forma vive en el DTO (Jakarta Validation); la de negocio, en el servicio de
reglas. Ninguna vive en el controller.
