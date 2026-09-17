# Plan — El documento formal del trámite (DO-FR-100)

**Feature**: `005-formal-document` · **Rama**: `feat/010-pdf-do-fr-100` · **Issue**: `Tramita#10`

> Redactado al cerrar la implementación. Describe **lo que se construyó y por qué**, no una
> planificación previa que no ocurrió en este formato. Las decisiones están en `research.md`.

## Contexto técnico

**Stack**: Java 21 · Spring Boot 4.0.7 · **Apache PDFBox 3.0.8** (dependencia nueva) ·
PostgreSQL + Flyway.

**Sin cambios de modelo.** El documento se arma con lo que `Request` ya persiste desde la
004 —incluida `student_signature` como data URL—. La única escritura es una fila de
`workflow_parameter`.

## Estructura

Package-by-layer (§II), como el resto del chasis:

| Archivo | Responsabilidad |
|---|---|
| `service/IDocumentRenderer.java` | Contrato de **un formato oficial**. Expone `documentKey()` y `render(Request)` |
| `service/impl/DoFr100Renderer.java` | El DO-FR-100: encabezado, tablas, marca del tipo, compromisos y campo de firmas |
| `service/IDocumentService.java` | Contrato de **emisión**: dado un id, el documento que le toca |
| `service/impl/DocumentServiceImpl.java` | Resuelve el formato por `DOCUMENT_TEMPLATE` y valida unicidad al arrancar |
| `util/PdfTextEncoder.java` | Sanea el texto preguntándole a la fuente qué puede escribir |
| `controller/RequestController.java` | `GET /api/requests/{id}/document` |
| `db/migration/V4.0.0__Declare_document_templates.sql` | Declara el formato para `ADICION_CREDITOS` |
| `resources/documents/logo-uniremington.png` | El logo del encabezado, reescalado a 600×139 |

**Separa decidir de dibujar**: el servicio resuelve *cuál* formato, el renderer sabe *un*
formato. Son dos responsabilidades y dos tests.

**Sin cambios en `SecurityConfig`**: `anyRequest().authenticated()` ya cubre la ruta nueva.

## Cómo se resuelve el formato

```
GET /requests/{id}/document
  └─ DocumentServiceImpl.generateFor(id)
       ├─ requestRepo.findById(id)              → 404 si no existe
       ├─ parameterRepo: DOCUMENT_TEMPLATE       → 404 si está ausente (caso por defecto)
       ├─ renderersByKey.get(valor)              → 500 si nadie lo implementa
       └─ renderer.render(request)               → byte[]
```

El índice `renderersByKey` se arma en el constructor y **falla si dos beans declaran la
misma clave**: es la deuda M5 corregida en la pieza nueva (research.md D6).

## Estrategia de testing

TDD estricto: RED observado antes de cada implementación, y **mutantes para probar que las
aserciones muerden**. Tres niveles, cada uno probando lo que el de arriba no puede:

| Nivel | Qué prueba |
|---|---|
| `PdfTextEncoderTest` | que el español sobrevive y que lo inescribible se reemplaza |
| `DoFr100RendererTest` | el **contenido** del PDF, extrayendo texto con `PDFTextStripper` |
| `DocumentServiceImplTest` | la **resolución**: unicidad, ausencia, formato inexistente, delegación |
| `RequestControllerIT` | el **cableado**: ruta, content-type, cabecera, sesión |

⛔ **Nada de `startsWith("%PDF")` como prueba de contenido.** El test del prototipo se
llamaba «genera un archivo PDF válido» y comprobaba cuatro bytes: pasaba con un documento en
blanco. Ese patrón no se repite; los cuatro bytes solo se usan en el IT, donde lo que se
prueba es el cableado y el contenido ya está cubierto.

### Lo que los tests NO pueden probar

**Que el documento se parezca al formato.** Un test de extracción de texto verifica que
ciertas cadenas están presentes; no ve la grilla, ni las posiciones, ni si el resultado se
reconoce como el papel. Eso se verifica **a ojo, contra la plantilla**, y está declarado como
paso obligatorio de la feature (SC-003).

De hecho fue un mutante el que expuso el límite: la clase entera sobrevivía a fijar el alto
de la caja del motivo, porque la extracción de texto no sabe nada de rectángulos. De ahí
salió `noTextFallsOffThePage`, que mide posiciones en vez de presencia.

## Verificación

```bash
docker start tramita-postgres && ./mvnw clean verify
```

Conteo al cerrar la feature: **108 unitarios + 72 IT** (desde 81 + 69 en `main`).

Más: los mutantes de cada fase muertos, y la comparación visual contra la plantilla.

## Fuera de alcance

El sello y el hash (SP4, `Tramita#11`) · el bloque de firma de la Facultad relleno (SP4) ·
el formato de novedad de notas (bloqueado por la Coordinación) · archivar el documento
emitido (research.md D7).
