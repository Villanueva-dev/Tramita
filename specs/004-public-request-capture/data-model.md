# Data Model — Captura pública del formato DO-FR-100

**Feature**: `004-public-request-capture` | **Fecha**: 2026-09-15

Esta feature **no crea entidades nuevas**. Agrega dos columnas a la solicitud, un parámetro
de configuración y una fila de identidad. Todo lo demás lo aporta el motor existente.

---

## Cambios sobre `request`

| Columna | Tipo | Nulo | Por qué |
|---|---|---|---|
| `student_email` | `VARCHAR(255)` | sí | Canal por el que la Coordinación responde al estudiante. `V2.3.0` lo excluyó declarando *«el dato entra cuando exista quien lo use»*; el consumidor ya existe. |
| `student_signature` | `TEXT` | sí | Trazo de la firma como URL de datos. Sin longitud declarada en la columna porque el tope real lo fija el filtro sobre el cuerpo entero (D7), no el campo. |

Ambas **opcionales**, como todas las columnas que `V2.3.0` agregó: la migración corre sobre
una tabla con filas existentes, y una solicitud registrada por el formulario interno sigue
siendo válida sin ellas (FR-006 de la `002`).

Ambas `updatable = false` en la entidad, como el resto de los datos de captura: corregir un
dato capturado es registrar una devolución, no editar el registro.

### Lo que deliberadamente NO se agrega

| Campo | Por qué no |
|---|---|
| `student_phone` | El formato lo pide y el formulario lo muestra, pero **ninguna fuente documenta que la Coordinación lo use**. Un dato personal sin consumidor no se almacena (§III, FR-005a). Entra cuando aparezca quien lo use — el mismo criterio que retrasó al correo. |
| Marca de origen público | El responsable del tramo inicial del histórico ya dice que la solicitud nació en el portal. Una columna adicional sería estado duplicado (§I). |
| Marca de leído/no leído | El orden por fecha y el estado inicial ya distinguen lo nuevo. Fuera de alcance por el spec. |
| Vínculo a un duplicado | Los duplicados se registran por separado y nadie los relaciona (D9). |

---

## Configuración: `workflow_parameter`

Un parámetro nuevo, sobre la tabla que creó la `003`:

| `parameter_key` | Valor | Alcance |
|---|---|---|
| `PUBLIC_CAPTURE_ENABLED` | `true` | Solo la definición de adición de créditos, versión vigente |

**Semántica**, idéntica a la de `CAPTURES_CREDITS` en `RequestBusinessRulesImpl`:

- **Ausente** → el trámite no admite captura pública. Es el caso por defecto y **no** es
  configuración incompleta: exigir el parámetro en todo trámite encarecería crear uno nuevo,
  que es justo lo que el motor abarata.
- **`true` / `false`** → habilita o niega.
- **Cualquier otro valor** → configuración rota: error del servidor, nunca interpretado como
  `false`. Leerlo como negativo dejaría un canal declarado como público rechazando todo, y
  culpando de ello a quien envía.

La restricción `UNIQUE(definition_id, parameter_key)` que ya existe garantiza que no haya
dos valores en conflicto para el mismo trámite.

---

## Identidad: una fila en `users`

| Campo | Valor | Por qué |
|---|---|---|
| `email` | `portal-publico@tramita.local` | Es lo que el histórico muestra como responsable del tramo inicial. El dominio `.local` señala que no es una dirección real. |
| `active` | `false` | `AppUserDetailsService` marca como deshabilitado a todo usuario inactivo: **esta cuenta no puede iniciar sesión**. |
| `password_hash` | valor sin prefijo de algoritmo | Ningún codificador del sistema puede verificarlo. Defensa en profundidad; la defensa real es `active = false`. |

No es una persona. Existe para que el histórico nunca tenga un tramo sin responsable (§VII)
sin tener que aflojar la restricción que lo garantiza.

---

## Ciclo de vida de una solicitud pública

Idéntico al de cualquier otra. La captura pública **no introduce estados ni transiciones**:

```
[enlace público]
      │
      ▼
  EN_COORDINACION ──────► EN_FACULTAD ──► … ──► (final)
      │      ▲
      │      │
      └──► DEVUELTA
```

El estado inicial lo determina la definición del trámite, no el canal. La devolución en la
propia revisión de la Coordinación —el primer filtro, que contiene el riesgo de suplantación
aceptado en el spec— existe desde la feature anterior y esta reusa tal cual.

---

## Validaciones

| Dato | Regla | Dónde vive |
|---|---|---|
| Nombre, documento | Obligatorios, con las longitudes ya declaradas en el contrato | Contrato de entrada, igual que hoy |
| Correo | Obligatorio en el canal público; formato de dirección válida | Contrato de entrada |
| Firma | Obligatoria en el canal público | Contrato de entrada |
| Compromisos (`reason`) | Obligatorio en el canal público, ≤ 2000 | Contrato de entrada |
| Trámite habilitado | `PUBLIC_CAPTURE_ENABLED = true` | Servicio, antes de persistir |
| Reglas del trámite | Las mismas que aplica el registro autenticado | Reusadas sin cambios |

La obligatoriedad del correo, la firma y los compromisos aplica **solo al canal público**: el
formulario interno sigue aceptando el cuerpo mínimo de la `002`. Es una diferencia de
contrato de entrada, no de modelo: la columna admite nulo porque las filas anteriores lo
tienen.
