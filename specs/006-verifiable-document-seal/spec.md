# Feature Specification: Sello verificable y registro de emisiones del documento formal

**Feature Branch**: `006-verifiable-document-seal`

**Created**: 2026-09-17

**Status**: Draft

**Input**: SP4 — issue [Tramita#11](https://github.com/Villanueva-dev/Tramita/issues/11), último issue abierto del milestone «Sprint 2 — Salida formal del trámite». Ataca las causas **C3 y C5** del árbol de problemas (`docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md:131`) y completa el **objetivo específico 4** del documento de grado.

## Por qué existe esta feature

Desde la feature `005-formal-document`, Trámita emite el DO-FR-100 diligenciado como PDF. Ese papel sale del sistema, se imprime, se firma a mano y viaja por correo hasta la decanatura —que es quien autoriza la adición de créditos según el Reglamento Estudiantil de Pregrado, Acuerdo n.º 13 del 1 de agosto de 2023, artículo 24 §1— y hasta quien lo asienta en Class.

Hoy, una vez que el documento sale, **el sistema pierde todo rastro de él**: no registra que se emitió, ni quién lo pidió, ni cuándo, y no hay forma de comprobar después que un papel es el que Trámita produjo y no una versión modificada por el camino. Esa es la trazabilidad estructurada de firmas y aprobaciones que el árbol de problemas señala como inexistente.

**El sello tiene que tener dos caras**, porque sus dos destinatarios son distintos:

- Una **cara legible**, impresa en el documento, que una persona con el papel en la mano contrasta contra el sistema sin necesitar cuenta ni herramientas. La decanatura y quien asienta en Class no tienen usuario en Trámita, y nadie va a teclear 64 caracteres de una huella criptográfica.
- Una **cara verificable**, la huella exacta, que responde sin ambigüedad si dos archivos son el mismo documento.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Toda emisión queda sellada y con rastro (Priority: P1)

La Coordinación emite el documento formal de una solicitud. El sistema, además de entregar el archivo, registra de forma permanente que ese documento se emitió: su huella, el momento exacto, quién lo pidió, sobre qué revisión de los datos y con qué versión del formato. El documento entregado sale con esa información impresa de manera legible.

**Why this priority**: es el cimiento. Sin registro de emisión no hay nada que verificar después, y es lo único que convierte un archivo anónimo en un documento atribuible. Entregada sola, ya responde preguntas que hoy el sistema no puede responder: cuántas veces se emitió el documento de un trámite, quién lo pidió y cuándo.

**Independent Test**: se emite el documento de una solicitud y se comprueba que (a) quedó un registro nuevo asociado a esa solicitud, (b) el documento entregado muestra la marca legible, y (c) el registro no admite modificación ni borrado.

**Acceptance Scenarios**:

1. **Given** una solicitud existente, **When** la Coordinación emite su documento formal, **Then** queda registrado un sello con la huella del documento, la fecha y hora de emisión, el autor, la revisión de los datos y la versión del formato.
2. **Given** un sello ya registrado, **When** se intenta modificarlo o borrarlo por cualquier vía, **Then** la operación falla y el registro permanece intacto.
3. **Given** una solicitud cuyo documento ya se emitió tres veces, **When** se consulta su historial de emisiones, **Then** se ven las tres emisiones con su autor y su momento, en orden.
4. **Given** una solicitud, **When** se emite su documento, **Then** el documento entregado muestra de forma legible la fecha de emisión, el estado del trámite y la revisión de los datos con que se emitió.
5. **Given** una solicitud cuyas calificaciones se registraron correctamente, **When** se emite su documento, **Then** los valores que el documento muestra son idénticos a los que la solicitud tiene almacenados, sin redondeo ni discrepancia.

---

### User Story 2 - Verificar si un documento es auténtico (Priority: P2)

Alguien tiene en la mano un documento que dice venir de Trámita y necesita saber si es el que el sistema emitió. Pregunta al sistema y recibe **uno de tres resultados**, nunca una acusación que el sistema no pueda sostener.

**Why this priority**: es el valor que el issue nombra explícitamente, pero depende por completo de que la US1 esté entregada: sin registro de sellos no hay contra qué comparar.

**Independent Test**: se emite un documento, se verifica y debe dar **íntegro**; se altera un byte del archivo, se verifica y debe dar **alterado**; se cambia la versión del formato, se verifica y debe dar **no verificable**, jamás «alterado».

**Dos caras, dos alcances**: la consulta por el código impreso está abierta a quien tenga el documento y responde si el sello existe y con qué datos de emisión —suficiente para desenmascarar un documento fabricado fuera del sistema, que es el ataque realista, dado que el formato no lleva ningún dato autorizable—. La comparación exacta del archivo, que además detecta la alteración de un campo, es de la Coordinación.

**Acceptance Scenarios**:

1. **Given** un documento tal como el sistema lo emitió, **When** se verifica, **Then** el resultado es **íntegro**.
2. **Given** un documento cuyo contenido fue modificado después de emitirse, **When** se verifica, **Then** el resultado es **alterado**.
3. **Given** un documento emitido con una versión del formato que ya no es la vigente, **When** se verifica, **Then** el resultado es **no verificable**, acompañado del motivo, y **nunca** «alterado».
4. **Given** un documento emitido sobre una revisión de los datos anterior a la actual de la solicitud, **When** se verifica, **Then** el resultado es **no verificable**, y **nunca** «alterado».
5. **Given** un documento que el sistema nunca emitió, **When** se verifica, **Then** el resultado es **alterado** o «sin sello conocido», y en ningún caso «íntegro».
6. **Given** una persona sin cuenta en el sistema que tiene el documento impreso, **When** consulta el código que el documento lleva impreso, **Then** obtiene si el sello existe, su fecha de emisión, el estado del trámite y la revisión, **sin** ningún dato personal del solicitante.
7. **Given** una persona que no tiene el documento, **When** intenta adivinar o derivar un código válido a partir del identificador de una solicitud o de otro código, **Then** no lo consigue.

---

### User Story 3 - Consultar el historial de emisiones y sellos de una solicitud (Priority: P3)

La Coordinación abre una solicitud y ve, junto al recorrido del trámite que ya existe, cuántas veces se emitió su documento formal, quién lo pidió y con qué sello quedó cada emisión.

**Why this priority**: es consulta sobre datos que la US1 ya deja escritos; aporta visibilidad, no capacidad nueva. La traza de aprobaciones propiamente dicha —estado, autor, sello de tiempo de cada avance— **ya existe** desde SP6 y no se reimplementa.

**Independent Test**: sobre una solicitud con emisiones registradas, consultar su historial y comprobar que devuelve una entrada por emisión, con autor y momento, sin exponer datos personales que no hagan falta.

**Acceptance Scenarios**:

1. **Given** una solicitud con dos emisiones registradas, **When** se consulta su historial de emisiones, **Then** se devuelven ambas, en orden, con autor, momento y versión del formato.
2. **Given** una solicitud cuyo documento nunca se emitió, **When** se consulta su historial de emisiones, **Then** se devuelve una lista vacía, no un error.

---

### Edge Cases

- **El formato cambia** (logo, maquetación, tipografías, textos fijos del papel): todos los sellos anteriores dejan de poder reconstruirse **a la vez y en silencio**. El sistema debe reportarlos como **no verificables**, nunca como alterados. Un cambio de formato no es una falsificación.
- **Los datos de la solicitud cambian después de emitir**: el documento emitido sobre la revisión 3 no coincide con lo que el sistema produciría hoy, en la revisión 5. Tampoco es una falsificación: es un documento viejo. Debe dar **no verificable**, no «alterado».
- **El mismo documento se emite varias veces sin que nada cambie**: cada emisión se registra por separado y lleva **su propio código**, así que los archivos NO son idénticos entre sí — se distinguen justamente por el código impreso. Cada uno verifica contra su propio sello. La pregunta «cuántas veces se emitió y quién lo pidió» es parte de la trazabilidad que esta feature entrega, y por eso no se deduplica.
- **Falla el registro del sello mientras se emite el documento**: no se entrega documento (FR-012). Los modos de fallo se enumeraron antes de aceptar ese bloqueo, y ninguno deja un caso en que entregar sin sello destrabe un trámite: los fallos de infraestructura —base sin responder, en solo lectura o sin espacio— **ya impiden hoy emitir el documento o bloquean por igual crear y avanzar solicitudes**, que también escriben; y los fallos por defecto de programación conviene que rompan ruidosamente en las pruebas en lugar de emitir documentos sin sello en silencio.
- **Se verifica un documento de una solicitud que ya no existe**: el sistema responde sin exponer si la solicitud existió, y nunca «íntegro».
- **Un documento emitido antes de esta feature**: no tiene sello. Verificarlo debe dar «sin sello conocido», no «alterado».
- **Se registra una calificación con más precisión de la que la norma admite** (por ejemplo `3.456`): se rechaza al entrar, no se guarda redondeada. Si se guardara, el documento mostraría un valor y la solicitud tendría otro, y la huella dejaría de describir lo que el trámite registró.
- **Ya existen calificaciones almacenadas con dos decimales** de antes de esta feature: hay que decidir qué hacer con ellas al ajustar la precisión, sin perder datos en silencio.
- **Alguien consulta un código de verificación que no existe**: se responde que no hay sello con ese código, sin revelar si alguna vez existió ni ningún dato de otra solicitud.
- **Alguien intenta recorrer el espacio de códigos** para descubrir documentos emitidos: el canal debe resistirlo, igual que el canal público de captura resiste el envío masivo.
- **El documento se altera pero se le deja el pie intacto**: la consulta pública dirá que el sello existe. Es una limitación conocida y declarada de la cara legible, y por eso la comparación exacta del archivo existe además de ella. El formato no lleva datos autorizables —ni créditos, ni asignaturas, ni notas—, así que el margen de daño de una alteración parcial es acotado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST registrar cada emisión del documento formal de una solicitud, guardando la huella del documento emitido, el momento exacto, el autor que la solicitó, la revisión de los datos de la solicitud y la versión del formato con que se produjo.
- **FR-002**: El registro de sellos MUST ser de solo anexado: un sello ya escrito no puede modificarse ni borrarse por ninguna vía, incluida el acceso directo al almacenamiento (constitución §VII, trazabilidad inmutable).
- **FR-003**: El documento emitido MUST mostrar de forma legible para una persona, sin herramientas, al menos: la fecha de emisión, el estado del trámite en ese momento y la revisión de los datos. Esta marca existe para quien recibe el papel impreso y no tiene cuenta en el sistema.
- **FR-004**: Reconstruir el documento de una solicitud **a partir de un sello ya registrado** —mismos datos, mismo formato y el mismo código de verificación que ese sello guarda— MUST producir un documento idéntico al que se emitió. Sin esta reproducibilidad, la huella no verifica nada.
  - ⚠️ **No dice «emitir dos veces produce lo mismo», y es deliberado.** Cada emisión imprime su propio código de verificación, así que dos emisiones producen documentos distintos **a propósito**: son dos papeles distinguibles, cada uno con su sello. Lo que la verificación necesita no es que dos emisiones coincidan, sino poder **reconstruir una emisión concreta** y obtener sus bytes exactos. Esa es la propiedad que el sistema garantiza y la única que hace falta.
- **FR-005**: Documentos de solicitudes distintas MUST distinguirse entre sí por su identificador interno: no pueden compartir un identificador común.
- **FR-006**: La verificación de un documento MUST devolver exactamente uno de tres resultados: **íntegro**, **alterado**, o **no verificable**.
- **FR-007**: El sistema MUST devolver **no verificable**, acompañado del motivo, cuando el documento se emitió con una versión del formato que ya no está vigente o sobre una revisión de los datos que ya no se puede reconstruir. En esos casos el sistema NEVER debe responder «alterado»: una acusación de falsificación que el sistema no puede sostener es peor que admitir que no puede pronunciarse.
- **FR-008**: El registro del sello MUST guardar la versión del formato con que se emitió el documento, desde la primera emisión. Es el dato que permite distinguir FR-007 de una alteración real.
- **FR-009**: Users MUST be able to consultar el historial de emisiones y sellos de una solicitud.
- **FR-010**: El sistema MUST NOT almacenar los archivos PDF emitidos. Trámita no es un repositorio de archivos; la verificación se resuelve con la huella y la reconstrucción del documento.
- **FR-011**: El sistema MUST NOT capturar ni pretender validar las firmas de los aprobadores externos, que siguen circulando por correo y OneDrive.
- **FR-012**: Cuando el registro del sello no pueda completarse, la emisión MUST fallar y el sistema MUST NOT entregar el documento. Registrar y entregar son una sola operación indivisible: no existe un documento emitido sin sello registrado. Con el código de verificación impreso en el papel, entregar sin registrar sería peor que fallar, porque produciría documentos legítimos que el propio sistema declararía desconocidos.
- **FR-013**: El documento y su huella MUST construirse sobre valores que no puedan diferir entre lo que se envía y lo que se almacena. Para lograrlo, el sistema MUST rechazar toda calificación que no pueda representarse exactamente en la escala oficial —**un número entero y un número decimal, de 0.0 a 5.0**, según el Reglamento Estudiantil de Pregrado, Acuerdo n.º 13 del 1 de agosto de 2023, artículo 32— y el almacenamiento MUST admitir exactamente esa precisión, ni más ni menos.
- **FR-013a**: El sistema MUST NOT aceptar en silencio una calificación que luego almacenará redondeada. Una calificación con más precisión de la que la norma admite se rechaza con un mensaje que dice por qué, en vez de guardarse alterada. El rechazo usa el mismo código de estado con que este sistema ya responde a una violación del contrato de entrada en el formulario interno, que es el único canal por el que se capturan calificaciones.
- **FR-014**: La verificación MUST estar disponible **sin cuenta** para quien tenga el documento en la mano. Su destinatario es la decanatura —quien autoriza, Acuerdo n.º 13 art. 24 §1— y quien asienta el resultado en los sistemas académicos: ninguno tiene usuario en Trámita, y un canal que los excluya deja la cara legible del sello sin destinatario.
- **FR-014a**: El documento emitido MUST portar impreso un **código no adivinable** que identifica su sello. Conocer el identificador de la solicitud, el de otro sello o el patrón de los códigos NOT MUST permitir derivarlo: la posesión del documento es lo que autoriza la consulta.
- **FR-014b**: Con ese código, cualquier persona MUST poder consultar si el sello existe, cuándo se emitió, en qué estado estaba el trámite y sobre qué revisión de los datos.
- **FR-014c**: La consulta pública MUST NOT devolver datos personales del solicitante. Quien tiene el documento ya los ve impresos; quien no lo tiene, no debe obtenerlos del sistema (constitución §III, minimización).
- **FR-014d**: La comparación exacta de un archivo contra su sello —la que detecta una alteración del contenido, no solo del pie— MUST estar disponible para la Coordinación, con sesión.
- **FR-014e**: El pie legible MUST quedar completo desde la primera emisión sellada. Agregarle algo después cambia la versión del formato y deja **no verificables** todos los sellos anteriores (FR-007), un daño que el propio sistema se causaría.

### Key Entities

- **Sello de emisión**: el registro permanente de que un documento formal salió del sistema. Guarda la solicitud a la que pertenece, la huella del documento, el momento de emisión, el autor que la solicitó, la revisión de los datos y la versión del formato. Una vez escrito, no cambia nunca.
- **Versión del formato**: la identificación del molde con que se produjo un documento —plantilla, imágenes institucionales y maquetación—. Cambia cuando el papel cambia, y es lo que permite responder «no verificable» en vez de acusar falsamente.
- **Código de verificación**: el identificador no adivinable que el documento lleva impreso y que autoriza a consultar su sello por el solo hecho de poseerlo. Es lo que permite que un tercero verifique sin cuenta y sin que el sistema abra datos personales a desconocidos.
- **Solicitud** *(ya existe)*: aporta los datos del documento y lleva un contador de revisión que cambia cada vez que se modifica.
- **Registro de transiciones** *(ya existe, desde SP6)*: la traza de estado, autor y momento de cada avance del trámite. **No se reimplementa**: esta feature se apoya en él y no lo duplica.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Reconstruir un documento a partir de su sello produce un archivo idéntico al emitido el **100 %** de las veces, mientras los datos y el formato no hayan cambiado.
- **SC-002**: **Cero** emisiones del documento formal quedan sin rastro: por cada documento entregado existe un sello consultable, o bien la emisión no ocurrió.
- **SC-003**: Un cambio en el formato del documento produce **cero** reportes de «alterado» sobre documentos legítimos emitidos antes del cambio.
- **SC-004**: Una persona que recibe el documento impreso puede contrastar su marca legible contra el sistema en **menos de un minuto**, sin cuenta y sin transcribir códigos largos.
- **SC-005**: **Ningún** sello registrado puede modificarse ni borrarse: el **100 %** de los intentos falla, incluidos los que no pasan por la aplicación.
- **SC-006**: Un documento modificado en un solo carácter se detecta como **alterado** el **100 %** de las veces.
- **SC-007**: Se puede responder «cuántas veces se emitió el documento de este trámite y quién lo pidió» para **cualquier** solicitud, cosa hoy imposible.
- **SC-008**: **Cero** calificaciones se almacenan con un valor distinto del que se envió: toda calificación que no quepa exactamente en la escala oficial se rechaza al entrar en vez de guardarse redondeada.

## Assumptions

- **La traza de aprobaciones ya está entregada.** El registro de estado, autor y sello de tiempo de cada avance existe desde SP6 (issue #8, cerrado) y se consulta en el recorrido del trámite. Esta feature **no lo reimplementa**; lo que agrega es el sello sobre el documento y el rastro de sus emisiones.
- **Cada emisión se registra por separado, sin deduplicar**, y cada una lleva su propio código de verificación impreso. Dos emisiones de la misma solicitud son dos papeles distintos y distinguibles, cada uno verificable contra su propio sello.
- **Esta feature cierra la deuda M2 alineando la precisión de las calificaciones con la norma**, en vez de elegir entre sellar el valor enviado o el almacenado. Es una ampliación deliberada del alcance, y la pide el propio issue #11 al exigir que «la respuesta sea la misma en los dos lados»: mientras la escala admita una precisión que la norma no reconoce, cualquiera de las dos respuestas deja viva una discrepancia. El respaldo es citable —Acuerdo n.º 13 de 2023, art. 32— y la corrección toca la validación de entrada introducida en la feature `003-request-form-rules`.
- **El límite máximo de calificación ya está bien configurado**: el valor sembrado es `5.0`, coincidente con el art. 32. El «100» que circulaba en el enunciado de la deuda M2 era un supuesto hipotético del argumento, no un valor real del sistema. Lo que sí está desalineado es la precisión admitida por el almacenamiento.
- **No se programa ninguna política de reintento.** Ante un fallo transitorio, reintentar es volver a pedir el documento —la misma acción que el usuario ya hace—, así que el reintento existe sin escribirlo. Añadir una política automática sería resolver con código un problema que la interfaz ya resuelve.
- **La autorización por posesión es un patrón nuevo en este repositorio.** El canal público existente identifica el trámite por su código, que es público (`POST /public/requests/{definitionCode}`); no hay ningún precedente de código no adivinable que cosechar. Se diseña aquí por primera vez.
- **El ataque contra el que el sello protege es la fabricación completa del documento**, no la alteración de un campo. El formato no lleva ningún dato autorizable —ni asignaturas, ni créditos, ni calificaciones— y el bloque «Firma de la Facultad» sale vacío: es una solicitud, no un permiso. Por eso una consulta que confirme existencia y datos de emisión cubre el caso realista.
- **No se introduce ninguna guarda de workflow en esta feature.** Condicionar el avance del trámite a que el documento esté emitido y sellado sería la primera implementación de producción del mecanismo de guardas y arrastraría deuda conocida (dos guardas con la misma clave se resuelven en silencio). Queda fuera por KISS+YAGNI (constitución §I); si el negocio lo pide, es una feature aparte.
- **El documento se sigue emitiendo en cualquier estado del trámite.** Lo decidió la feature `005-formal-document`: el formato es el papel que circula *para* ser firmado. Esta feature no lo restringe.
- **La revisión de los datos usa el contador que la solicitud ya lleva**, sin necesidad de un dato nuevo para eso.
- **Riesgo aceptado explícitamente**: si el formato cambia, los sellos anteriores quedan sin respaldo y **no pueden repararse hacia atrás**, porque al emitir no se guardó nada que permita reconstruir el documento viejo. Se asume conscientemente para un MVP con SP5 y SP7 todavía sin empezar. El precio de no asumirlo sería almacenar los archivos, que está descartado (FR-010).
- **La validez legal del sello queda fuera de alcance.** El sistema aporta evidencia de integridad, no valor probatorio jurídico. La pregunta sigue abierta en §10 del árbol de problemas y coincide con dos preguntas sin responder de la Coordinación.
