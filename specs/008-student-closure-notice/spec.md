# Feature Specification: Aviso de cierre al estudiante

**Feature Branch**: `008-student-closure-notice`

**Created**: 2026-09-23

**Status**: Aprobada — gate `review-spec` superado el 2026-09-24, tras releer los 23 issues abiertos de los dos repositorios (ninguno la bloquea)

**Input**: SP7 — issue [#13](https://github.com/Villanueva-dev/Tramita/issues/13), milestone «Sprint 3 — Operación diaria». Cubre la última parte del objetivo específico 5 del documento de grado: *«notificación de finalización al estudiante»*.

## User Scenarios & Testing *(mandatory)*

Actor único de esta feature: la **Coordinación Académica de la Sede Cali**. El estudiante es **destinatario** del aviso, no usuario del sistema: no tiene vista propia ni portal, y el mensaje no le ofrece ninguno (decisión de la Sesión 2 parte B, sostenida en la 007).

El sistema **no envía nada por sí mismo**. Prepara el mensaje con el destinatario y el texto listos, y es la Coordinación quien lo revisa y lo envía desde sus propios canales.

### User Story 1 - Avisarle por correo que su trámite terminó (Priority: P1)

Cuando una solicitud recibida por el enlace público llega a un estado final, la Coordinación abre desde su detalle un correo dirigido al estudiante, con asunto y texto ya escritos, y lo envía desde su cuenta institucional. Hoy ese aviso no existe: el estudiante se entera preguntando.

**Why this priority**: es el canal que la Coordinación pidió y el momento que fijó. Lo dijo así: *«cuando se [completa] el proceso le llegó una notificación al correo institucional del estudiante»*, y a la pregunta de si avisar en cada cambio de estado respondió *«No, al final. Solamente cuando ya se complete»* (Sesión 2 parte B, Q22–Q23). Además cubre el 100 % de las solicitudes del enlace público, porque ahí el correo es obligatorio. Sin esta historia no hay aviso.

**Independent Test**: se lleva una solicitud del enlace público a un estado final y se comprueba dos cosas. Primero, que la respuesta de esa misma acción trae lo necesario para preparar el correo. Segundo, que el mensaje resultante nombra solo al estudiante, el trámite y el estado alcanzado.

**Acceptance Scenarios**:

1. **Given** una solicitud del enlace público en un estado intermedio, **When** la Coordinación la consulta, **Then** no se ofrece ningún aviso.
2. **Given** una solicitud del enlace público que la Coordinación acaba de llevar a un estado final, **When** el sistema responde esa misma acción, **Then** la respuesta ya permite ofrecer el aviso, sin una segunda consulta.
3. **Given** el aviso de una solicitud cerrada, **When** la Coordinación lo abre, **Then** el mensaje va dirigido al correo que declaró el estudiante y nombra al estudiante, el trámite y el estado alcanzado con los nombres que da la configuración. No trae nada más.
4. **Given** una solicitud rechazada, **When** la Coordinación abre el aviso, **Then** el mensaje dice que el trámite quedó **rechazado** y nunca lo presenta como aprobado.
5. **Given** una solicitud registrada por la propia Coordinación, **When** llega a un estado final, **Then** no se ofrece aviso: esa solicitud le llegó por su propio correo, y la conversación con el estudiante ya existe en ese hilo.
6. **Given** un trámite nuevo incorporado por configuración, sin desplegar código, **When** una de sus solicitudes del enlace público llega a cualquiera de sus estados finales, **Then** el aviso se ofrece igual que en los demás trámites.

---

### User Story 2 - Avisarle por WhatsApp (Priority: P2)

En el mismo momento y sobre la misma solicitud, la Coordinación puede abrir una conversación de WhatsApp con el móvil que declaró el estudiante, con el mismo texto ya escrito. Es un canal **alternativo** al correo, no un reemplazo.

**Why this priority**: amplía el alcance del aviso, pero la Coordinación no lo pidió. El árbol de problemas lo registra como *«decisión de ingeniería del equipo […], no un pedido de la coordi»* (`arbol-de-problemas.md:164`). Sin él, el correo ya cubre el aviso.

**Independent Test**: sobre una solicitud del enlace público en estado final, se comprueba que la opción de WhatsApp aparece solo con un móvil colombiano y que lleva el mismo texto que el correo.

**Acceptance Scenarios**:

1. **Given** una solicitud del enlace público en estado final, con un móvil colombiano válido, **When** la Coordinación la consulta, **Then** se ofrece también el aviso por WhatsApp, con el mismo texto que el del correo.
2. **Given** una solicitud en estado final cuyo teléfono es un fijo, **When** la Coordinación la consulta, **Then** no se ofrece WhatsApp, pero el correo sigue disponible.
3. **Given** una solicitud radicada antes de esta feature con un teléfono que no cumple el formato, **When** llega a un estado final, **Then** no se ofrece WhatsApp, el correo sigue disponible y la solicitud no se modifica.

---

### User Story 3 - Recibir un teléfono utilizable desde la captura (Priority: P3)

El formulario público deja de aceptar como teléfono cualquier texto: exige un número colombiano de 10 dígitos. Así, cada solicitud nueva llega con un número que sirve para avisar.

**Why this priority**: sin esta historia, el WhatsApp de la P2 depende de que el estudiante haya escrito bien el número a mano. Hoy el sistema acepta cualquier texto de hasta 30 caracteres, y como el formulario radicado es inmutable, un número mal escrito deja a la solicitud sin WhatsApp para siempre. Aun así, la P2 se puede probar sin esta historia, con datos válidos.

**Independent Test**: se envían al canal público teléfonos con distintos formatos y se comprueba cuáles se aceptan y que el rechazo nombra el campo sin repetir el valor.

**Acceptance Scenarios**:

1. **Given** el formulario público con el teléfono `3001234567`, **When** se envía, **Then** se acepta.
2. **Given** un teléfono con espacios, guiones o prefijo internacional (`300 123 4567`, `+57 3001234567`), **When** se envía, **Then** se rechaza nombrando el campo y sin repetir el valor recibido.
3. **Given** un teléfono fijo de 10 dígitos (`6025551234`), **When** se envía, **Then** se acepta. El teléfono sigue sirviendo de contacto; solo que no habilita WhatsApp.
4. **Given** una solicitud registrada por la Coordinación sin teléfono, **When** se envía, **Then** se acepta, porque en ese canal el teléfono es opcional.
5. **Given** una solicitud registrada por la Coordinación con un teléfono que no cumple el formato, **When** se envía, **Then** se rechaza nombrando el campo.

---

### Edge Cases

- **Datos anteriores a la feature**: en la base de desarrollo, ninguna de las 4 solicitudes del enlace público tiene un móvil válido: dos tienen 10 dígitos que empiezan por 0 y dos tienen 9 dígitos (medido el 2026-09-23, con datos enmascarados). Ninguna tendrá WhatsApp; todas conservan el correo. Nada se reescribe: el formulario radicado no se edita.
- **Correo con error de tipeo**: el sistema no verifica que el correo sea del estudiante. La Coordinación ve la dirección antes de enviar, y si el correo rebota, el rebote le llega a ella. La 004 ya aceptó este caso (`specs/004-public-request-capture/spec.md:131-133`).
- **El correo declarado no tiene por qué ser institucional**: el formato oficial pide «Correo electrónico», sin más (`DoFr100Renderer:306`). La Coordinación habló de correo institucional; el sistema usa el que declaró el estudiante.
- **Origen desconocido**: si el historial de una solicitud no permite saber quién la registró, se trata como no pública y no se ofrece aviso.
- **El correo no abre**: el aviso por correo depende de que el equipo de la Coordinación tenga un programa de correo asociado. Si en la prueba real no abre, la mitigación es ofrecer copiar el texto. No se construye antes de observarlo (Principio I).
- **Número sin WhatsApp**: el enlace abre la conversación, pero si el número no tiene cuenta, WhatsApp se lo indica a la Coordinación. El sistema no puede saberlo de antemano.
- **Doble aviso**: nada impide abrir el aviso dos veces, y nada lo registra. El sistema no sabe si el estudiante fue avisado.
- **Tildes, eñes y saltos de línea**: el texto tiene que llegar intacto a los dos canales. Los nombres propios de Colombia los llevan con frecuencia.
- **Trámite sin rechazo**: la novedad de notas tiene un solo estado final, `FINALIZADA`. La adición de créditos tiene dos, `FINALIZADA` y `RECHAZADA`, y al rechazo solo se llega desde la facultad (`V2.1.0__Seed_workflow_definitions.sql:32-34,53,73`).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST ofrecer a la Coordinación, para cada solicitud recibida por el enlace público que esté en un estado final, un aviso por **correo electrónico**. El aviso es un mensaje dirigido al correo que declaró el estudiante, con asunto y texto prellenados, que ella revisa y envía desde su propia cuenta.
- **FR-002**: El sistema MUST ofrecer, en las mismas condiciones, un aviso por **WhatsApp** cuando el teléfono declarado sea un móvil colombiano, con el mismo texto que el del correo. Para esta feature, un móvil colombiano son **10 dígitos que empiezan por 3**. En cualquier otro caso no se ofrece WhatsApp, pero el correo se mantiene.
- **FR-003**: El sistema MUST determinar que un estado es final **leyendo la configuración del trámite**, sin reconocer códigos concretos. Vale cualquier estado final, rechazo incluido (Principio VI).
- **FR-003a**: El sistema MUST NOT ofrecer el aviso en estados intermedios. Es la única restricción de momento que la Coordinación fijó: *«Solamente estado final, únicamente»* (Sesión 2 parte B, Q23).
- **FR-004**: El sistema MUST NOT ofrecer el aviso para solicitudes registradas por la Coordinación ni para aquellas cuyo origen no se pueda determinar.
- **FR-005**: El mensaje MUST contener únicamente tres datos: el nombre del estudiante, el nombre del trámite y el nombre del estado alcanzado, tal como los da la configuración. MUST NOT incluir el documento de identidad, el código, el programa, las asignaturas, el motivo, las notas ni enlaces al sistema (Principio III). Por WhatsApp, el texto viaja en el propio enlace y pasa por un tercero; ese es un motivo más para mantenerlo mínimo.
- **FR-005a**: El mensaje MUST NOT afirmar más de lo que dice el estado: un rechazo no se presenta como aprobación, y el mensaje no promete pasos siguientes que el sistema no conoce.
- **FR-006**: El sistema MUST NOT enviar ningún mensaje por sí mismo: ni correo automático, ni integración con servicios de mensajería, ni proveedor externo. El envío es un acto humano de la Coordinación.
- **FR-007**: El sistema MUST NOT registrar ni afirmar que el estudiante fue avisado. Ofrecer o abrir el aviso no escribe nada en el sistema.
- **FR-008**: El detalle de una solicitud MUST exponer su **origen**, el **correo** declarado y el **teléfono** declarado, en la respuesta de cada acción que lo devuelve (registrarla, moverla y consultarla). Así, quien lo consume decide si ofrecer el aviso sin hacer otra consulta.
- **FR-008a**: La documentación del detalle MUST dejar de afirmar que no expone datos de contacto «por FR-020». Ese requisito (`specs/003-request-form-rules/spec.md:247`) prohibía **almacenar** el correo en la 003, no exponerlo, y ya anticipaba esta feature: *«Su único consumidor previsto es la notificación (SP7) […]; el dato entra cuando exista quien lo use»*. La 004 lo reemplazó por FR-005 (`specs/004-public-request-capture/spec.md:155`): *«es el canal por el que la Coordinación responde al estudiante»*. La razón vigente es la minimización del Principio III: un dato de contacto se expone solo cuando tiene quién lo use. Esta feature es ese consumidor.
- **FR-009**: El canal público MUST rechazar un teléfono que no tenga exactamente 10 dígitos. El rechazo nombra el campo y nunca repite el valor, con el mismo mecanismo con que ese canal ya informa campos faltantes o inválidos. Solo se aceptan números colombianos.
- **FR-010**: El canal interno MUST mantener el teléfono como opcional. Si viene, MUST aplicar la misma regla y nombrar el campo al rechazarlo.
- **FR-011**: El sistema MUST conservar el teléfono tal como llega una vez validado, sin reescribirlo. MUST NOT modificar las solicitudes ya radicadas.
- **FR-012**: La validación de la captura MUST NOT exigir que el teléfono sea un móvil. Esa regla solo se aplica al decidir si se ofrece WhatsApp. Los costos de equivocarse son asimétricos: un falso rechazo en la captura bloquea todo el formulario del estudiante, mientras que un error al decidir el WhatsApp solo oculta una opción.
- **FR-013**: El cambio en la regla del teléfono MUST declararse como **enmienda no aditiva** de dos contratos: el de captura pública (004), que hoy acepta cualquier texto de hasta 30 caracteres, y el de registro interno. Se sigue el precedente de la 007 (`specs/007-coordination-inbox/plan.md:72-77`). Los campos nuevos del detalle MUST ser aditivos: un cliente actual del detalle sigue funcionando sin cambios.

### Key Entities

- **Aviso de cierre**: el mensaje que la Coordinación envía al estudiante cuando su trámite termina. **No se almacena**: se compone en el momento con datos que ya existen, así que no es un registro ni deja rastro en el historial.
- **Origen de la solicitud**: si la registró la Coordinación o llegó por el enlace público. Se deduce del historial, por quién registró su nacimiento; no es un dato que se capture.
- **Contacto declarado**: el correo y el teléfono tal como el estudiante los escribió en el formato oficial. Son inmutables una vez radicados.
- **Estado final**: un punto de la configuración de un trámite marcado como cierre. Puede ser un éxito o un rechazo: *final* no significa *aprobado*.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ante una solicitud del enlace público en estado final, la Coordinación tiene el aviso listo para enviar **en una sola acción**, sin copiar la dirección ni el número y sin redactar el texto.
- **SC-002**: En el conjunto de prueba, el aviso se ofrece en el **100 %** de las solicitudes del enlace público en estado final, y en el **0 %** de las que están en estados intermedios o fueron registradas por la Coordinación.
- **SC-003**: El mensaje contiene **cero** datos distintos del nombre del estudiante, el trámite y el estado alcanzado, en los dos canales.
- **SC-004**: El **100 %** de las solicitudes nuevas del enlace público entran con un teléfono de 10 dígitos; ninguna entra con otro formato.
- **SC-005**: Un trámite nuevo incorporado por configuración, sin desplegar código, ofrece el aviso en sus estados finales. Es la tesis del proyecto verificada sobre esta feature.
- **SC-006**: Avisar no deja **ningún** registro nuevo en el sistema.

## Assumptions

Salvo que se indique lo contrario, lo que sigue son **hechos medidos** el 2026-09-23. Los supuestos a validar van marcados como tales.

- **Lo que pidió la Coordinación, y lo que se le puede dar**: pidió un aviso **al correo institucional**, y **solo al final** (material-coord/parte2-entrevista3.md, Sesión 2 parte B, Q22–Q23). Lo imaginó automático (*«le llegó una notificación»*), pero sin infraestructura institucional de correo el sistema no puede enviar como la universidad: es la dependencia que el árbol ata a Q34 (`arbol-de-problemas.md:134`) y que la 004 ya registró (`specs/004-public-request-capture/spec.md:293-294`). Un correo prellenado que ella envía desde su cuenta es lo más cercano posible. Es un correo institucional, lo envía la oficina responsable y llega en el momento que ella fijó. La diferencia es que alguien tiene que pulsar «enviar», y ese es el precio de no tener el servidor de correo.
- **El correo automático queda descartado**: sin el servidor institucional no hay correo oficial, y un proveedor externo mandaría correos que no son de la universidad y le entregaría los datos a un tercero. Lo decidió el usuario el 2026-09-23 y no se reabre.
- **WhatsApp es una decisión del equipo, no un pedido**: `arbol-de-problemas.md:164` lo dice textualmente. En las transcripciones no consta que la Coordinación avise el cierre por WhatsApp caso por caso; el único rastro es una mención suelta a un «WhatsApp de formación con el estudiante», en un pasaje de transcripción deficiente. **Supuesto a validar con la Coordinación**: que al estudiante le llega mejor por WhatsApp que por correo. Por eso WhatsApp es la opción P2 y no la principal.
- **WhatsApp como tercero, y por qué se acepta**: igual que el proveedor de correo descartado, WhatsApp es un tercero, y el texto prellenado viaja en el enlace. La diferencia defendible es triple:
  - el sistema no integra nada, y es la Coordinación quien decide usarlo;
  - el texto es mínimo (FR-005);
  - el teléfono lo declaró el estudiante como su contacto en el formato oficial.

  El formato del enlace es el que documenta WhatsApp: número internacional sin ceros, paréntesis ni guiones, y texto codificado ([Cómo usar click to chat](https://faq.whatsapp.com/5913398998672934/)). El número tiene que tener una cuenta activa.
- **El correo prellenado sigue una norma**: el esquema `mailto` con asunto y cuerpo está definido en la [RFC 6068](https://www.rfc-editor.org/rfc/rfc6068). Según su §2, el cuerpo está pensado para mensajes de texto cortos, y según su §5, los saltos de línea se codifican como `%0D%0A`.
- **El rechazo también se avisa. Supuesto a validar**: la Coordinación dijo *«Solamente estado final, únicamente»*, y la fila SP7 del árbol habla de «estado FINALIZADO» (`arbol-de-problemas.md:134`). Esta feature ofrece el aviso en cualquier estado final, rechazo incluido, por dos razones: para el estudiante el rechazo también es el fin del trámite, y la configuración lo marca como final. Queda por confirmar que la Coordinación avisa el rechazo por el mismo canal. Por eso el mensaje nombra el estado y no presupone el resultado (FR-005a).
- **Por qué solo el enlace público**: las solicitudes que registra la Coordinación le llegaron por correo. El formato se envía *«por correo formal al correo institucional de Cali»* (material-coord/2026-06-03-entrevista1-sintesis-analitica.md:45), así que la conversación con el estudiante ya existe en ese hilo. Las del enlace público, en cambio, no tienen ningún hilo previo.
- **Teléfono colombiano**: en Colombia, fijos y móviles tienen 10 dígitos ([CRC, cambio de marcación](https://www.crcom.gov.co/es/noticias/comunicado-prensa/desde-1-septiembre-cambia-forma-hacer-llamadas-en-colombia)). Que los móviles empiecen por 3 tiene **confianza media**: no se encontró en una fuente primaria (se buscó en la Resolución CRC 5050, Título VI). Por eso esa regla no bloquea la captura (FR-012). Los números extranjeros quedan fuera por decisión del usuario.
- **No cambia lo almacenado**: el correo y el teléfono se guardan desde la 004, y el campo de teléfono admite hasta 30 caracteres, así que 10 dígitos caben. Se midió y se descartó acortar el campo: cuenta caracteres, no dígitos, y chocaría con las filas existentes. Tampoco hace falta un campo de «avisado» (FR-007).
- **Orden de despliegue entre repositorios**: el formulario público del cliente hoy envía el teléfono tal como lo escribe el estudiante. Si el cambio del backend llega antes de que el cliente filtre los dígitos, un estudiante que escriba `300 123 4567` recibirá un rechazo que nombra el campo. Es recuperable, pero degrada la captura. El filtro del cliente debería llegar primero, o a la vez.
- **Reparto**: este repositorio expone los datos del detalle y valida el teléfono. Mostrar las opciones y armar los enlaces lo implementa el cliente, en el repositorio del frontend, que lleva otro agente. Un spike descartable midió la parte del backend en +20/−4 líneas de producción, con la suite en verde (`spike/008-wa`). Es referencia, no base: esta feature se implementa con TDD desde esta spec.
- **Enmienda del issue #13**: dos de sus criterios de cierre se reformulan.
  - *«Puerto como interfaz, con al menos un adaptador»*: no se construye puerto porque el sistema no envía nada, y una interfaz sin ningún adaptador que ejecute contradice el Principio I.
  - *«Se dispara solo en FINALIZADO, nunca en transiciones intermedias»*: pasa a «se ofrece solo en estados finales según la configuración, nunca en intermedios» (FR-003, FR-003a).

  La enmienda del issue se hace al abrir la PR.
- **Issues #38 y #39**: describen el puerto de notificación de la rama de exploración `router-ia`, que no se integra. Esta feature no cosecha esa pieza. Sin puerto ni tabla de avisos, los dos quedan sin objeto, y su cierre se decide al abrir la PR.
- **El árbol de problemas queda desactualizado**: la fila SP7 (`:134`), la línea `:164` y el riesgo `:216` describen el correo automático como adaptador canónico y el chat como respaldo. Esta feature no construye el correo automático y ofrece los dos avisos como acciones manuales. El árbol es la fuente de verdad del planteamiento y debe enmendarse para reflejarlo, como se hizo con el plazo en la PR #44.
- **Alcance**: Sede Cali y los dos trámites del MVP. Los sistemas externos siguen siendo cajas negras, y esta feature no integra ninguno.
