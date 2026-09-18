# Modelo de datos — Feature 006

**Rama**: `006-verifiable-document-seal` · **Investigación**: [research.md](./research.md)

Esta feature agrega **una tabla** y **una restricción** sobre una tabla existente. No
modifica ninguna entidad del dominio ni altera las nueve tablas vigentes más allá de esa
restricción.

---

## Entidad nueva: `request_document_seal`

El registro permanente de que un documento formal salió del sistema. De solo anexado.

| Columna | Tipo | Nulo | Qué es |
|---|---|---|---|
| `id` | `UUID` | no | Clave primaria |
| `request_id` | `UUID` | no | Solicitud cuyo documento se emitió → `request (id)` |
| `verification_code` | `VARCHAR(13)` | no | Código no adivinable impreso en el documento. **Único** |
| `document_sha256` | `VARCHAR(64)` | no | Huella del documento emitido, con el código ya impreso dentro |
| `format_version` | `VARCHAR(80)` | no | Versión del formato con que se emitió (D5) |
| `request_version` | `BIGINT` | no | Revisión de los datos al emitir — el `@Version` de la solicitud (D7) |
| `state_code` | `VARCHAR(50)` | no | Código del estado del trámite al emitir. Se copia, no se referencia |
| `state_name` | `VARCHAR(120)` | no | **Nombre legible** del estado al emitir, congelado. Es el texto que el pie imprime |
| `actor_id` | `UUID` | no | Quién pidió la emisión → `users (id)` |
| `issued_at` | `TIMESTAMP` | no | Momento de la emisión |

### Decisiones del esquema, con su porqué

**`verification_code` es `VARCHAR(13)`, no `UUID`.** Son 64 bits en base 36 (D3). Se eligió
por el SC-004: un `UUID` habría sido el tipo natural del proyecto pero obliga a transcribir
36 caracteres desde un papel.

**El estado se copia en vez de referenciarse, y se copian LAS DOS columnas.** Una clave
foránea diría *en qué estado está hoy aquella transición*, y lo que el sello necesita
registrar es **en qué estado estaba el trámite cuando el documento se emitió**. Un sello es
una fotografía.

🔑 **Guardar solo el código no alcanza, y esto es lo que hace falta entender.** El pie
impreso muestra el **nombre** legible del estado, no su código —`workflow_state` tiene las
dos columnas (`V2.0.0:24-25`)—. Si el sello guardara únicamente el código, reconstruir el
documento obligaría a resolver el nombre contra `workflow_state` **en tiempo de
verificación**, y entonces:

1. Se emite un documento en `EN_FACULTAD`; el pie imprime «En facultad» y la huella lo cubre.
2. Alguien renombra ese estado a «En decanatura». Es un cambio de **pura configuración**, que
   es justamente lo que el §VI habilita, y **no toca nada de lo que el sello vigila**: ni la
   revisión de la solicitud —renombrar un estado no incrementa el `@Version` de `Request`— ni
   la versión del formato, que depende del renderer y del logo.
3. Las dos guardas del FR-007 pasan, el sistema compara huellas, el pie regenerado dice «En
   decanatura» y el original decía «En facultad» → **`TAMPERED`**.

Es decir: una acusación de falsificación contra un documento legítimo, disparada por un
renombre. Exactamente el modo de fallo que esta feature existe para impedir. Congelar
`state_name` lo cierra, y cuesta una columna.

**`request_version` es `BIGINT`** para coincidir con el `long` del `@Version`
(`Request.java:129-131`).

**Los anchos copian los de su origen, no uno más angosto.** `state_code` es `VARCHAR(50)` y
`state_name` `VARCHAR(120)` porque así están declarados en `workflow_state` (`V2.0.0:24-25`).
Con fail-closed (FR-012), una columna más corta que su origen no produciría un sello
truncado: **apagaría la emisión del documento entero**. El código más largo hoy tiene 22
caracteres, así que el margen existe igual; copiar el ancho lo hace irrelevante.

**No hay columna de «documento»**, porque el archivo no se guarda (FR-010). El sello apunta
a la solicitud y a la revisión con que se reconstruye.

**No hay `updated_at` ni `deleted_at`**: la tabla no admite actualización ni borrado, así que
esas columnas describirían estados imposibles.

### Índices

```sql
CREATE UNIQUE INDEX ux_request_document_seal_code
    ON request_document_seal (verification_code);

CREATE INDEX ix_request_document_seal_request
    ON request_document_seal (request_id, issued_at, id);
```

El único sirve a la consulta pública, que entra por el código, y garantiza que no haya dos
sellos con el mismo. El segundo sirve al historial de emisiones de una solicitud, en orden;
es la misma forma que `ix_request_transition_log_timeline` (`V2.0.0:77-78`), que resuelve la
misma clase de consulta.

### Inmutabilidad

Se imita el mecanismo que ya está en `main` y que el §VII de la constitución nombra como
vigente:

```sql
CREATE FUNCTION reject_document_seal_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'request_document_seal es inmutable: solo se permite INSERT (FR-002)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_document_seal_immutable
    BEFORE UPDATE OR DELETE ON request_document_seal
    FOR EACH ROW EXECUTE FUNCTION reject_document_seal_mutation();
```

⚠️ **El trigger cubre `UPDATE` y `DELETE`, nunca `INSERT`.** Incluir `INSERT` bloquearía la
creación de sellos y dejaría el sistema sin poder emitir ningún documento — con fail-closed
(D11), eso equivale a apagar la emisión por completo. Es el modo de fallo más caro que esta
feature puede introducir, y el único que se introduce a sí misma.

---

## Restricción sobre entidad existente: `request_subject`

```sql
ALTER TABLE request_subject
    ADD CONSTRAINT ck_request_subject_grades_one_decimal
    CHECK ((current_grade  IS NULL OR current_grade  = round(current_grade,  1))
       AND (proposed_grade IS NULL OR proposed_grade = round(proposed_grade, 1)));
```

**Qué NO se toca**: el tipo sigue siendo `NUMERIC(3,2)`. `V2.3.0:43-45` declara que el rango
efectivo de las notas lo fija la configuración del trámite y no la columna; reescribir el
tipo movería la regla institucional al esquema y contradiría esa decisión (D8).

**Precedente que lo respalda**: `ck_request_subject_credits_positive` (`V2.3.0:38-41`) pone
la misma clase de regla en la validación y en la base, con el porqué escrito.

**Saneamiento previo, obligatorio**: la instancia local tiene **una fila** que incumpliría la
restricción (`proposed_grade = 3.46`), así que la migración redondea antes de declararla. Sin
ese paso, Flyway falla al aplicarla y el arranque queda roto.

---

## Entidades existentes que esta feature usa sin modificar

| Entidad | Qué aporta | Por qué no se toca |
|---|---|---|
| `request` | Los datos del documento y su `version` (la revisión) | El `@Version` ya existe desde la 002 |
| `request_transition_log` | La traza de aprobaciones que el issue pide en su primera mitad | **Ya la cubre desde SP6.** Reimplementarla sería duplicar |
| `users` | El actor que pidió la emisión | Igual que en el timeline |
| `workflow_parameter` | Qué formato emite cada trámite (`DOCUMENT_TEMPLATE`) | Se lee, no se modifica |

---

## Migración

**`V4.1.0__Register_document_seals.sql`** — la última aplicada es `V4.0.0`
(`Declare_document_templates.sql`), de la feature 005.

Contiene, en este orden:

1. El saneamiento de las calificaciones con más de un decimal.
2. La restricción de precisión sobre `request_subject`.
3. La tabla `request_document_seal` con sus índices.
4. La función y el trigger de inmutabilidad.

El orden importa: la restricción tiene que declararse **después** del saneamiento, o falla.
