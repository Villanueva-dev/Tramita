# Implementation Plan: sello verificable y registro de emisiones del documento formal

**Branch**: `006-verifiable-document-seal` | **Date**: 2026-09-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-verifiable-document-seal/spec.md`

## Summary

SP4 (issue [Tramita#11](https://github.com/Villanueva-dev/Tramita/issues/11)) es el último
issue abierto del Sprint 2 y completa el objetivo específico 4 del documento de grado. Pide
dos cosas: registrar la traza de aprobaciones —**que ya existe desde SP6** y no se
reimplementa— y aplicar un sello verificable sobre los documentos que el sistema genera, que
es lo que esta feature construye.

**El enfoque técnico en una frase**: se hace reproducible el PDF fijando el `/ID` de su
trailer, se registra cada emisión en una tabla de solo anexado con el código impreso y su
huella, y se verifica **comparando esa huella guardada contra la recibida**, con tres
resultados en vez de dos para no acusar de falsificación a documentos legítimos emitidos con
otro formato u otra revisión de los datos.

El hallazgo que hizo el problema mucho más chico de lo que parecía: **el contenido del PDF
ya es determinista hoy**. De 52 348 bytes, dos renders difieren solo a partir del 52 022, y
únicamente en el identificador aleatorio del trailer. No hace falta archivar el documento
para poder verificarlo, lo que mantiene al sistema fuera del negocio de almacenar archivos.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.7 (Security 7, Data JPA, Validation, WebMVC),
PDFBox 3 (ya presente desde la feature 005), Lombok. **Ninguna dependencia nueva.**

**Storage**: PostgreSQL con Flyway en modo `validate`. Una migración nueva, `V4.1.0`.

**Testing**: JUnit 5, Mockito, Testcontainers para los tests de integración. **Strict TDD**:
rojo observado antes de implementar.

**Target Platform**: servidor Linux; base en contenedor Docker publicando en el 5433.

**Project Type**: servicio web (backend del trabajo de grado).

**Performance Goals**: no hay objetivo de rendimiento propio. La emisión gana un `INSERT` y
la verificación exacta compara dos cadenas de 64 caracteres, sin regenerar el PDF: más barata
que la emisión, que sigue siendo la única operación que lo genera.

**Constraints**: errores en `application/problem+json` (RFC 9457) · sin almacenamiento de
archivos · sin datos personales en el canal público · identificadores en inglés y
documentación en español.

**Scale/Scope**: Sede Cali, dos trámites, una decena de usuarios de Coordinación. El canal
público de verificación es de lectura y sin estado.

## Constitution Check

*GATE: debe pasar antes de la fase 0 y re-evaluarse tras la fase 1.*

Evaluado contra `.specify/memory/constitution.md` **v2.3.0**.

| Principio | Evaluación | Evidencia |
|---|---|---|
| **I. KISS + YAGNI** | ✅ Pasa | Se rechazaron explícitamente tres agregados por sobreingeniería: política de reintento (el reintento es volver a pedir el documento), método transaccional aparte, y límite de tasa propio para el canal público (la protección es el espacio de 64 bits). Se descartó además la serialización canónica de datos (D7) y la guarda de workflow. |
| **II. Arquitectura por capas** | ✅ Pasa | Piezas nuevas en `controller/`, `dto/`, `model/`, `repo/`, `service/` + `service/impl/`. Interfaces con prefijo `I`. Ninguna entidad cruza la frontera de la API. |
| **III. Seguridad por defecto + minimización** | ✅ Pasa | El canal público devuelve únicamente datos de emisión: ni nombre, ni cédula, ni correo, ni datos académicos (FR-014c). La verificación exacta recibe una huella, **nunca el archivo**, así que el sistema no puede almacenarlo ni por accidente. El `GET` público no necesita excluirse de CSRF. |
| **IV. Decisiones defendibles y trazables** | ✅ Pasa | Cada decisión de `research.md` lleva su trade-off y su alternativa rechazada. La norma institucional (Acuerdo n.º 13, art. 32) se cita contra el documento obtenido de la fuente, no contra Context7. Las mediciones son re-ejecutables. |
| **V. Testing del comportamiento sensible** | ✅ Pasa | Se testea lo que rompe el valor de la feature: determinismo del render, inmutabilidad por acceso directo, los tres veredictos, y que `NOT_VERIFIABLE` no se confunda con `TAMPERED`. |
| **VI. Workflow configurable por dato** | ✅ Pasa | La versión de formato la declara cada renderer, no un `switch` por trámite. Un formato nuevo aporta la suya sin tocar el servicio, igual que hoy aporta su `documentKey()`. |
| **VII. Trazabilidad inmutable** | ✅ Pasa | La garantía vive en la base, con el mismo mecanismo que el §VII nombra como vigente: trigger `BEFORE UPDATE OR DELETE` imitando `trg_timeline_immutable` (`V2.0.0:80-88`). |

**Resultado del gate: pasa sin violaciones.** La sección *Complexity Tracking* queda vacía a
propósito.

### Re-evaluación posterior al diseño

Tras la fase 1 no aparecieron violaciones nuevas. Dos puntos merecen registro:

- **El §I estuvo bajo presión real.** El diseño creció durante la discusión —reintentos,
  transacción propia, límite de tasa, código en Base32— y se podó hasta el mínimo que cumple
  los requisitos. Tres de esos cuatro recortes los pidió el usuario, uno salió de la
  aritmética. Queda registrado porque el propio §I advierte que el riesgo es la abstracción
  especulativa, y aquí se materializó.
- **El §VI se respeta, pero con una carga humana**: la constante de versión del formato la
  bumpea el desarrollador. Lo que impide olvidarla no es un proceso sino el test de
  determinismo, que se pone en rojo ante cualquier cambio de maquetación (D5).

## Project Structure

### Documentation (this feature)

```text
specs/006-verifiable-document-seal/
├── plan.md              # Este archivo
├── spec.md              # Qué y por qué (fase de /speckit-specify)
├── research.md          # Fase 0 — D1 a D11, con sus trade-offs
├── data-model.md        # Fase 1 — la tabla, la restricción, la migración
├── quickstart.md        # Fase 1 — recorrido manual y comprobaciones
├── contracts/
│   └── openapi.yaml     # Fase 1 — delta de contrato: tres endpoints nuevos
├── checklists/
│   └── requirements.md  # Calidad del spec (16/16)
└── tasks.md             # Fase 2 — lo genera /speckit-tasks, NO este comando
```

### Source Code (repository root)

```text
src/main/java/com/uniremington/api/tramita/
├── controller/
│   ├── PublicSealController.java          # NUEVO — GET /public/seals/{code}
│   ├── SealController.java                # NUEVO — POST /seals/verify
│   └── RequestController.java             # TOCADO — GET /requests/{id}/seals
├── dto/
│   ├── SubjectRequestBody.java            # TOCADO — precisión de un decimal (FR-013a)
│   ├── PublicSealResponse.java            # NUEVO — sin datos personales
│   ├── VerdictResponse.java               # NUEVO
│   ├── SealEntryResponse.java             # NUEVO
│   └── VerifyBody.java                    # NUEVO
├── model/
│   └── RequestDocumentSeal.java           # NUEVO — entidad de solo anexado
├── repo/
│   └── IRequestDocumentSealRepo.java      # NUEVO
├── service/
│   ├── IDocumentRenderer.java             # TOCADO — declara su formatVersion()
│   ├── IDocumentSealService.java          # NUEVO — emitir, verificar, historial
│   └── impl/
│       ├── DocumentSealServiceImpl.java   # NUEVO
│       ├── DocumentServiceImpl.java       # TOCADO — deja de ser readOnly; sella al emitir
│       └── DoFr100Renderer.java           # TOCADO — /ID fijo, pie con el sello, versión
├── util/
│   └── VerificationCodeGenerator.java     # NUEVO — 64 bits de SecureRandom en base 36
└── shared/config/
    └── SecurityConfig.java                # TOCADO — permitAll del GET público

src/main/resources/db/migration/
└── V4.1.0__Register_document_seals.sql    # NUEVO

src/test/java/com/uniremington/api/tramita/
├── controller/
│   ├── PublicSealControllerIT.java        # NUEVO
│   └── SealControllerIT.java              # NUEVO
├── repo/
│   └── DocumentSealImmutabilityIT.java    # NUEVO — imita TimelineImmutabilityIT
└── service/impl/
    ├── PdfDeterminismProbeTest.java       # YA COMMITEADA (eadad66) → pasa a ser el test de FR-004
    ├── SubjectRequestBodyTest.java        # NUEVO — la precisión rechazada (FR-013a)
    ├── DocumentSealServiceImplTest.java   # NUEVO — los tres veredictos
    └── DoFr100RendererTest.java           # TOCADO — el pie y el /ID
```

**Structure Decision**: package-by-layer, como exige el §II y como está el resto del
backend. No se crea ningún paquete nuevo: cada pieza entra en la capa que le corresponde.
`VerificationCodeGenerator` va a `util/` porque no tiene estado ni reglas de negocio.

## Orden de implementación sugerido

Cada tramo es entregable y verificable por separado, y el orden está dictado por las
dependencias reales, no por las capas:

1. **Determinismo del render** — fijar el `/ID` y convertir la sonda en el test de FR-004.
   Sin esto, nada de lo demás verifica nada. *Entrega valor solo: el documento pasa a ser
   reproducible.*
   - ⚠️ **El test debe nacer con una costura para fijar el código de verificación**, aunque
     en este tramo todavía no exista ninguno. Si se escribe comparando contra una huella
     dorada del documento «tal como sale hoy», el tramo 3 lo pondrá en rojo al imprimir el
     código en el pie, y la reacción natural —actualizar la huella dorada— desactiva en la
     práctica la única barrera que D5 declara contra olvidar bumpear la versión del formato.
     Lo que el test debe afirmar es que **reconstruir con un código fijo da bytes idénticos**
     (FR-004), no que el documento de hoy tenga un hash concreto.
2. **Migración y entidad** — la tabla, los índices, el trigger, la restricción de precisión
   con su saneamiento. *Se comprueba con el test de inmutabilidad por acceso directo.*
3. **Sellado al emitir** — el código de verificación, el pie impreso, el `INSERT` dentro de
   la transacción, y `generateFor()` dejando de ser `readOnly`. *Cierra la US1 completa.*
   - ⚠️ **Este tramo romperá dos tests de `DoFr100RendererTest`, y el mecanismo no es obvio**:
     `lowestTextBaseline` (`:376-394`) descarta del cálculo las líneas que contienen el
     literal `"Generado por Trámita"`, porque el pie va deliberadamente bajo el margen. Dos
     tests asertan que el resto del contenido no baja de `BOTTOM = 70`
     (`DoFr100Renderer:78`). Cualquier línea nueva del pie —código, fecha, estado, revisión—
     cae por debajo de ese umbral y entra al cálculo, salvo que el filtro se extienda. No es
     un defecto del cambio: es un filtro escrito contra un literal. Extenderlo es parte de
     este tramo, no una sorpresa a descubrir en rojo.
4. **Verificación** — los tres veredictos, el canal público y el autenticado. *Cierra la US2.*
5. **Historial de emisiones** — `GET /requests/{id}/seals`. *Cierra la US3, la de menor
   prioridad: si hay que recortar, es lo primero que cae.*
6. **Validación de precisión en la entrada** — FR-013a, en `SubjectRequestBody` con la
   anotación de precisión que Bean Validation ya ofrece. Responde **`400`**, no `422`: el
   `422` pertenece al canal público de captura por un advice acotado, y las calificaciones
   solo entran por el formulario interno.

⚠️ El tramo 1 **debe ir primero**. Sellar antes de que el render sea reproducible produciría
sellos que nunca verifican, y como la tabla es de solo anexado, no se pueden corregir
después.

## Riesgos vivos

| Riesgo | Mitigación |
|---|---|
| Un cambio de formato deja «no verificables» todos los sellos anteriores, sin reparación posible | Aceptado explícitamente en el spec. Es el precio de no almacenar archivos |
| Alguien cambia la maquetación y olvida bumpear la versión del formato | El test de determinismo se pone en rojo y obliga a mirar (D5). ⚠️ Solo funciona si ese test compara reconstrucciones a código fijo; ver la advertencia del tramo 1 |
| El trigger se escribe cubriendo también `INSERT` | Apagaría la emisión entera por fail-closed. Es el modo de fallo más caro que esta feature puede introducirse a sí misma; se vigila con un test que inserta |
| Quitar `readOnly` reactiva el dirty checking | Hoy el renderer solo lee. Se anota en el código para quien venga después |
| La migración falla por la fila con `proposed_grade = 3.46` | La migración la sanea antes de declarar la restricción (medido, no supuesto) |

## Complexity Tracking

Sin violaciones de la constitución que justificar. Sección vacía a propósito.
