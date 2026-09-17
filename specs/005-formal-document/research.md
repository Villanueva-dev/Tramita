# Research — El documento formal del trámite (DO-FR-100)

**Feature**: `005-formal-document` · **Issue**: `Tramita#10` (SP3)

Las decisiones de esta feature, con la evidencia que las sostiene. Varias salieron de
**medir antes de diseñar**, y dos invirtieron lo que el plan daba por supuesto.

---

## D1 — El PDF reproduce el formato oficial; la constancia propia queda descartada

El prototipo de `origin/router-ia` generaba un documento propio titulado «DOCUMENTO OFICIAL
DE CIERRE»: cabecera institucional, datos del estudiante, detalle de asignaturas y
justificación, con layout inventado.

**No sirve.** Lo que la Coordinación tramita, imprime y anexa es el formato oficial, y el
propio issue lo dice: *«un PDF que no reproduce el formato oficial no sirve para lo que el
trámite necesita»*. Además producía una incoherencia con el frontend, que en su Fase 1 se
esmeró en reproducir los bloques y rótulos exactos del DO-FR-100: el estudiante llenaría el
formato oficial y recibiría una constancia que no se le parece.

**Decisión del usuario, 2026-09-17**: el PDF reproduce el DO-FR-100. Del prototipo se cosecha
**la mecánica de PDFBox**, no el layout.

## D2 — Fidelidad: grilla fiel, sin las trece casillas de motivos

«Reproducir el formato» admitía tres lecturas, y se resolvió mirando un PDF de muestra real
antes de escribir el renderer definitivo:

| Opción | Qué implicaba |
|---|---|
| Réplica fiel completa | tablas con bordes **más** las 13 casillas de motivos impresas sin marcar |
| **Grilla fiel sin motivos** ← elegida | tablas con bordes, casilla del tipo marcada, dos páginas, sin los motivos |
| Fidelidad de contenido | mismos rótulos y orden, sin dibujar la grilla |

**Se eligió la intermedia.** Las trece casillas pertenecen a otros tipos de solicitud
—cancelación de semestre, bajo rendimiento— y la Coordinación confirmó que casi no se
diligencian. El formulario de captura ya decidió no mostrarlas; el PDF la acompaña, y front
y documento quedan coherentes.

La plantilla tiene **6 tablas en Arial, sin imágenes en el cuerpo, y un salto de página**
antes del campo de firmas (`<w:br w:type="page">` en `word/document.xml`): por eso el
documento son **dos hojas**.

## D3 — Se corrige la ortografía del original

La plantilla escribe, las dos veces, **sin tilde**: `SOLICITUD DE EXCEPCIÓN DE MATRICULA` en
el encabezado y `Matricula créditos adicionales` en el tipo de solicitud.

El documento emitido escribe **«Matrícula»**. Criterio: **la RAE manda sobre la errata del
documento fuente**. Alinea además el PDF con el formulario del front, que ya había
normalizado la grafía en su spec.

⚠️ Queda registrado acá porque **quien compare el PDF contra el papel va a ver la
diferencia**, y sin este párrafo parece un defecto de transcripción.

## D4 — 🔑 Helvetica escribe todo el español: el saneador del prototipo era daño gratuito

El prototipo aplicaba `replaceAll("[^\x20-\x7E]", "?")` sobre todo el texto, lo que convierte
en interrogante cada tilde y cada eñe de un documento oficial en español.

**Medido antes de diseñar** (`PDType1Font` HELVETICA, WinAnsiEncoding):

| Caso | Resultado |
|---|---|
| `á é í ó ú` · `Á É Í Ó Ú` | ✅ |
| `ñ Ñ` · `ü Ü` | ✅ |
| `¿ ¡ º ª` | ✅ |
| `«»` | ✅ |
| `— –` | ✅ |
| `“ ” ‘ ’` · `…` | ✅ |

**Nueve de nueve.** No hacía falta embeber ninguna fuente, y **no había problema de
codificación que resolver**: aquel parche mutilaba el idioma del documento sin arreglar nada.

⚠️ La hipótesis previa también era falsa, aunque menos: se suponía que fallarían `«»` y `—`
por no estar en WinAnsi. **Están.** CP1252 cubre la puntuación tipográfica que este proyecto
usa.

**Lo que sí queda**: `reason` admite 2000 caracteres libres desde un canal anónimo, y un
emoji pegado desde un teléfono haría lanzar a PDFBox en pleno trazado, convirtiendo la
generación en un 500. El saneador **le pregunta a la fuente** si puede codificar cada
carácter, en vez de llevar lista blanca —que sería una suposición y vencería al cambiar de
fuente— y recorre por **code points** para no partir un emoji en dos mitades inválidas.

## D5 — 🔑 No hay flujo del motivo a otra página, y es una corrección

La primera implementación traía maquinaria para partir el motivo y continuarlo en una hoja
nueva. **Un mutante la delató como inalcanzable**: fijar el alto de la caja en cuatro líneas
sobrevivía a toda la clase de tests.

Al medir por qué, apareció el motivo: `reason` está acotado a **2000 caracteres** (`@Size` en
el DTO y `VARCHAR(2000)` en la migración), y ese máximo ocupa **~18 líneas** contra las **~19
que caben** desde el inicio de la caja hasta el margen. **El máximo del campo cabe en la
página.** La hoja de continuación nunca se creaba.

Era defender un caso que el contrato de entrada no permite — lo que el **§I** prohíbe. Se
eliminó, y la suite siguió verde: la prueba de que era código muerto.

Lo que vigila que siga siendo cierto es `noTextFallsOffThePage`, que mide la posición del
texto más bajo contra el margen. Si alguien sube el límite del campo o angosta la caja, ese
test cae.

## D6 — El formato se elige por dato, no por código

Un `if (definitionCode.equals("ADICION_CREDITOS"))` mataría la tesis del proyecto. La
definición declara `DOCUMENT_TEMPLATE` y el valor selecciona la implementación, con el
**mismo patrón que `IWorkflowGuard`** (§VI).

**La unicidad se valida al arrancar**, y eso es una corrección deliberada: el motor resuelve
las guardas con `.findFirst()` sin comprobar que dos beans no declaren la misma clave, de
modo que con dos gana el primero de la lista inyectada y cuál es el primero depende del orden
de escaneo de Spring, sin error ni log (deuda **M5** del review de la 003). La pieza nueva no
la hereda: dos formatos con la misma clave **impiden arrancar**.

⚠️ **La deuda original sigue viva** en `RequestServiceImpl`. Esta feature no la toca.

**Ausencia y rotura son cosas distintas**:

| Situación | Respuesta | Por qué |
|---|---|---|
| Parámetro ausente | **404** | el trámite no emite documento — caso por defecto, misma lectura que `PUBLIC_CAPTURE_ENABLED` |
| Formato declarado sin implementación | **500** | configuración rota; un 404 la escondería |

## D7 — Se genera bajo demanda y sin compuerta de estado

El prototipo exigía que la solicitud estuviera en estado final. **Acá sería un defecto**: el
DO-FR-100 es el documento que circula *para* ser firmado, y si solo saliera al cerrar el
trámite no serviría para aquello por lo que existe.

Que una solicitud en revisión pueda emitir su formato no la vuelve aprobada, y **el propio
documento lo evidencia**: el bloque «Firma de la Facultad» va vacío, igual que el papel.

No se persiste. Archivar sin sellar produce un artefacto viejo sin garantía de integridad,
indistinguible de uno vigente — peor que no tenerlo. Congelar y sellar es SP4 (`Tramita#11`).

## D8 — PDFBox 3.0.8, no la 3.0.3 del prototipo

PDFBox **no lo gestiona el parent de Spring Boot**: no pertenece al ecosistema Spring y su
versión no sale de ningún BOM, así que va explícita en el `pom`.

El prototipo usaba **3.0.3**, que está afectada por `CVE-2026-23907` y `CVE-2026-33929`
(path traversal), corregidas en 3.0.7 y 3.0.8 respectivamente
([fuente](https://pdfbox.apache.org/security.html)). Ambas viven en el módulo `examples`,
que este proyecto no usa, así que el riesgo real es nulo — **se sube igual** porque fijar una
versión con vulnerabilidades conocidas en un trabajo que se audita cuesta más explicarlo que
corregirlo.

## D9 — El logo institucional viaja como recurso, reescalado

El encabezado del formato lleva el logo. Se extrae de la plantilla (`word/media/image1.jpg`,
2036×470, 190 KB) y se incluye reescalado a **600×139, 48 KB**: se dibuja a 120 puntos de
ancho, y cargar 190 KB en el repositorio por eso no tiene sentido.

## Lo que esta feature NO resuelve

- **El sello y el hash** → SP4 (`Tramita#11`). El objetivo específico 4 los promete.
- **El formato de novedad de notas** → bloqueado por la Coordinación: el papel es **por
  asignatura con varios estudiantes** mientras el modelo es un estudiante con N asignaturas,
  y lleva **cuatro notas parciales del 25 % más la definitiva** contra las dos columnas que
  hoy existen. Por eso `NOVEDAD_NOTAS` **no declara formato**.
