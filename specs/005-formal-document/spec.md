# Feature Specification: El documento formal del trámite (DO-FR-100)

**Feature Branch**: `feat/010-pdf-do-fr-100`

**Created**: 2026-09-17

**Status**: Implementada

**Issue**: [`Tramita#10`](https://github.com/Villanueva-dev/Tramita/issues/10) — SP3

> ⚠️ **ESTE DOCUMENTO SE ESCRIBIÓ DESPUÉS DE LA IMPLEMENTACIÓN, y conviene decirlo.** Las
> features 001–004 recorrieron el ciclo de Spec Kit antes de escribir código; ésta se
> implementó desde un plan de sesión y sus artefactos se redactaron al cerrarla. No se
> inventa una historia de fases que no ocurrió: lo que sigue documenta **decisiones reales
> y verificables contra el código**, no un proceso retroactivo.
>
> Por eso **no hay `tasks.md`**. Escribir una lista de tareas ya cumplidas sería fabricar
> evidencia de un proceso que no se siguió, que es justo lo que este proyecto castiga.

## Contexto

El artefacto canónico de estos trámites es **el papel**. La Coordinación recibe el formato
DO-FR-100 diligenciado, lo revisa, lo hace firmar y lo anexa donde corresponda. Un sistema
que captura los datos pero no puede devolver ese formato deja el trabajo a mitad de camino:
obliga a transcribir de vuelta a un documento de Word lo que el sistema ya tiene.

La feature `004` cerró la entrada —el estudiante diligencia y firma desde un enlace—. Ésta
cierra la salida: **el sistema emite el formato oficial diligenciado**, listo para imprimir,
firmar y anexar.

Es el **objetivo específico 4** del documento de grado: *«Generar automáticamente el PDF
formal del trámite partiendo de los datos validados, con un sello electrónico»*. El sello es
SP4 (`Tramita#11`); esta feature entrega el documento.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - La Coordinación descarga el formato diligenciado (Priority: P1)

Una funcionaria de la Coordinación abre una solicitud de adición de créditos y descarga su
formato oficial. Recibe un PDF de dos páginas que **reproduce el DO-FR-100**: el encabezado
institucional con su código y versión, la ciudad y la fecha, el tipo de solicitud marcado,
los nueve campos del solicitante con sus rótulos oficiales, los compromisos adquiridos, y
—en hoja aparte— el campo de firmas con la firma del estudiante ya trazada.

Lo imprime y lo hace circular. No transcribe nada.

**Por qué es P1**: sin esto, la captura de la 004 no ahorra trabajo — lo mueve.

### User Story 2 - Un trámite sin formato declarado no finge tener uno (Priority: P2)

Una solicitud de novedad de notas no tiene documento formal en el sistema todavía. Pedirlo
responde «este trámite no emite documento formal», no un PDF vacío ni un error de servidor.

**Por qué es P2**: un formato equivocado circulando con sello institucional es peor que no
tener formato.

### Edge Cases

- **Solicitud sin firma**: las que entran por el formulario interno de la Coordinación no
  capturan firma. El documento se emite igual, con el recuadro vacío — como un papel que
  todavía no se firmó.
- **Motivo en el límite del campo**: 2000 caracteres es el máximo que admite `reason`. Si no
  cabe en la primera hoja, continúa en una hoja intermedia y el campo de firmas sigue
  cerrando el documento. **Nada se recorta**: es un documento oficial.
- **Motivo sin espacios**: 2000 caracteres en una sola palabra son entrada legal. Se parten
  por carácter, porque una línea más ancha que la hoja se dibuja fuera y deja de existir para
  quien imprime.
- **Texto que la fuente no puede escribir**: un emoji pegado desde un teléfono en los
  compromisos. Se reemplaza solo ese carácter; el español no se toca.
- **Solicitud inexistente**: 404.
- **Petición sin sesión**: 401.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-023**: El sistema **MUST** emitir el documento formal de una solicitud como PDF.
- **FR-024**: El documento **MUST** reproducir la estructura del formato oficial
  (DO-FR-100, plantilla v2024 — ver la salvedad sobre el estado de esa versión en *Assumptions*):
  encabezado con código y versión, ciudad y fecha, tipo de solicitud,
  datos del solicitante con sus rótulos literales, compromisos adquiridos y campo de firmas.
- **FR-025**: El tipo de solicitud **MUST** aparecer marcado, y **MUST NOT** haber más de
  una marca: un formato con dos tipos marcados es una solicitud ambigua.
- **FR-026**: El documento **MUST NOT** presentar las trece casillas de motivos del formato.
  Pertenecen a otros tipos de solicitud, y el formulario de captura ya tomó esa decisión.
- **FR-027**: El campo de firmas **MUST** ir en una página distinta de los datos, como en el
  papel, y **MUST** mostrar la firma del estudiante cuando exista.
- **FR-028**: El bloque de firma de la Facultad **MUST** emitirse vacío. Llenarlo es SP4.
- **FR-029**: Qué formato emite cada trámite **MUST** resolverse por configuración
  (`DOCUMENT_TEMPLATE` en `workflow_parameter`), **NUNCA** por el código del trámite.
- **FR-030**: Un trámite que no declara formato **MUST** responder 404. La ausencia del
  parámetro es el caso por defecto, no una configuración incompleta.
- **FR-031**: Un trámite que declara un formato sin implementación **MUST** fallar como
  error del servidor, no degradarse a «no tiene documento».
- **FR-032**: Dos formatos que declaren la misma clave **MUST** impedir el arranque.
- **FR-033**: El documento **MUST** exigir sesión. No es un recurso del estudiante.
- **FR-034**: El nombre del archivo **MUST NOT** contener datos personales.
- **FR-035**: El documento **MUST** emitirse en cualquier estado de la solicitud. Es el papel
  que circula *para* ser firmado.

### Key Entities

Ninguna entidad nueva. El documento se arma con lo que `Request` ya persiste desde la 004,
incluida `student_signature`. La única fila nueva es un `workflow_parameter`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La Coordinación obtiene el formato diligenciado **sin transcribir ningún dato**.
- **SC-002**: Los nueve rótulos del formato aparecen **literales** en el documento emitido,
  verificable extrayendo el texto del PDF.
- **SC-003**: El documento emitido, puesto **al lado de la plantilla oficial**, se reconoce
  como el mismo formato. Esta verificación es a ojo y no se puede automatizar.
- **SC-004**: Un trámite cuyo caso el formato **ya contempla** se habilita declarando el
  parámetro, **sin desplegar código**. Uno que no —porque el formato tendría que marcar una
  casilla de tipo que no le corresponde— necesita además que el renderer lo aprenda.

## Riesgos aceptados

### El documento se genera bajo demanda y no se archiva — **decidido el 2026-09-17**

Cada descarga vuelve a dibujar el PDF desde los datos vigentes. Dos descargas en momentos
distintos pueden diferir si la solicitud cambió entre medio, y **no queda registro de qué se
imprimió**.

Se acepta porque archivar sin sellar es peor: produce un artefacto viejo sin garantía de
integridad, indistinguible de uno vigente. Congelar y sellar es SP4 (`Tramita#11`), que ya
trae el diseño de `request_document` con su `sha256`.

### El documento se emite para solicitudes en revisión — **decidido el 2026-09-17**

Cualquiera con sesión puede emitir el formato de una solicitud que todavía no fue aprobada.

Se acepta porque es lo que el papel hace: el formato circula *para* ser firmado. Lo contiene
el propio documento, que muestra el bloque de la Facultad vacío — un formato sin esa firma no
está aprobado, y se ve.

### El PDF corrige la ortografía del formato oficial — **decidido el 2026-09-17**

La plantilla v2024 escribe «MATRICULA» y «Matricula» sin tilde. El documento emitido escribe
**«Matrícula»**, por la RAE. Quien compare el PDF contra el papel va a ver la diferencia.

Se acepta porque alinea el documento con el formulario de captura, que ya había normalizado
la grafía, y porque reproducir una errata ortográfica en un documento generado no honra al
formato: lo copia.

## Assumptions

- **La Sede es Cali.** La ciudad va **preimpresa** en el documento, porque así viene en el
  papel: pertenece al formulario, no al solicitante. El campo `campus` llena la celda «Sede»,
  que es otra. Ambas dicen Cali en el alcance del MVP, y esa coincidencia fue lo que hizo
  pasar inadvertida la confusión hasta que se miró un documento generado desde la base.
- **El logo institucional puede distribuirse con el sistema.** Se incluye como recurso para
  reproducir el encabezado del formato.
- **El formato reproducido es la plantilla v2024** obtenida de la Coordinación
  (`material-coord/2026-06-03-coord-DO-FR-100-formato-solicitud-excepcion-de-matricula-v2024.docx`).
  Si la institución publica una versión nueva, cambian los rótulos y el encabezado.

  ⚠️ **«v2024» es el rótulo del archivo entregado, no una versión que la institución haya
  confirmado como vigente.** La Coordinación dio **tres identificadores distintos** en tres
  momentos: «versión 0.1» (Sesión 2, leyendo el ejemplar en pantalla), el archivo entregado
  rotulado **v2024**, y de memoria en la Sesión 1 «formato es 00001-2025». El documento de
  evidencia lo deja explícitamente sin resolver: *«La versión vigente del formato de adición de
  créditos no está establecida y debe verificarse contra el portal institucional de formatos
  antes de fijar la plantilla del generador de PDF»*
  (`material-coord/evidencia-entrevistas-coordinacion.md:544`).

  Construir contra el único ejemplar físico entregado es lo único que se puede hacer hoy, y no
  se cuestiona. Lo que esta salvedad fija es que **el número de versión impreso en el PDF hereda
  esa incertidumbre**: no debe presentarse como confirmado —ante la tutora, el jurado o la propia
  Coordinación— hasta verificarlo contra el portal institucional.
