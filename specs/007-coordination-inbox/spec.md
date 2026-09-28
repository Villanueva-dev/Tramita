# Feature Specification: Bandeja de trabajo de la coordinación

**Feature Branch**: `007-coordination-inbox`

**Created**: 2026-09-21

**Status**: Aprobada — gate `review-spec` superado y auditada contra las fuentes el 2026-09-21

**Input**: SP5 — issue [#12](https://github.com/Villanueva-dev/Tramita/issues/12), milestone «Sprint 3 — Operación diaria». Decisión acoplada: issue [#22](https://github.com/Villanueva-dev/Tramita/issues/22).

## User Scenarios & Testing *(mandatory)*

Actor único de esta feature: la **Coordinación Académica de la Sede Cali**, la persona que hoy sostiene el proceso desde su bandeja de correo. El estudiante **no** es usuario del sistema y no aparece en ninguna de estas historias; los aprobadores externos (facultad, registro, financiera) tampoco.

### User Story 1 - Ver qué espera mi acción (Priority: P1)

La Coordinación abre el sistema y ve, en una sola vista, las solicitudes que están detenidas esperando que **ella** haga algo — no todas las solicitudes vivas, ni las que están en manos de la facultad o de registro. Hoy esa lista no existe en ninguna parte: se reconstruye leyendo el correo, solicitud por solicitud.

**Why this priority**: es la causa C4 del árbol de problemas y el 100 % del valor de la feature. Sin esto, las demás historias no tienen dónde mostrarse. Es también el mínimo demostrable: una lista correcta, sin indicadores ni recorrido, ya reemplaza la revisión manual del correo.

**Independent Test**: se registran solicitudes en distintos estados del flujo —algunas esperando a la Coordinación, otras esperando a la facultad o a registro— y se comprueba que la vista lista exactamente las primeras. Entrega valor por sí sola.

**Acceptance Scenarios**:

1. **Given** una solicitud detenida en un punto donde la siguiente acción corresponde a la Coordinación, **When** la Coordinación abre su bandeja, **Then** esa solicitud aparece en la lista.
2. **Given** una solicitud que ya se reenvió y espera la firma de la facultad, **When** la Coordinación abre su bandeja, **Then** esa solicitud **no** aparece, porque no espera su acción.
3. **Given** una solicitud finalizada o rechazada, **When** la Coordinación abre su bandeja, **Then** no aparece: un trámite cerrado no espera acción de nadie.
4. **Given** una solicitud que nació por el enlace público y otra registrada por la propia Coordinación, **When** ambas esperan su acción, **Then** las dos aparecen en la lista y se distingue de dónde vino cada una.
5. **Given** un trámite nuevo incorporado por configuración, sin desplegar código, **When** alguna de sus solicitudes espera acción de la Coordinación, **Then** aparece en la bandeja igual que las demás.
6. **Given** una solicitud devuelta para corrección, cuyo reingreso registra la Coordinación cuando el estudiante la corrige, **When** la Coordinación abre su bandeja, **Then** aparece —aunque el formato esté en manos del estudiante—, porque la siguiente acción en el sistema es de ella, y su espera se cuenta desde la devolución. Es el retraso que la entrevista describe como el peor: *«me he demorado y medio dos meses, pero es porque el estudiante lo mandó mal. Yo le dije que lo arreglara, se le olvidó»* (Sesión 1 parte A).

---

### User Story 2 - Saber qué lleva más tiempo esperando (Priority: P2)

Sobre esa misma lista, la Coordinación ve **cuánto tiempo lleva esperando** cada solicitud y las atiende por antigüedad en lugar de por orden de llegada. El sistema **no dictamina** que una solicitud esté vencida: muestra el dato y ella juzga, porque hoy no existe ningún plazo que el sistema pueda invocar sin inventarlo.

**Why this priority**: es la mitad del criterio de cierre del issue y lo que convierte una lista en una herramienta de priorización. Depende de la P1 —no hay dónde mostrarlo sin la lista— pero la lista sirve sin esto.

**Independent Test**: con la bandeja ya funcionando, se registran solicitudes con distintas antigüedades de espera y se comprueba que el instante expuesto permite distinguirlas y que el orden devuelto pone primero la que más lleva esperando.

**Acceptance Scenarios**:

1. **Given** dos solicitudes esperando acción de la Coordinación con antigüedades distintas, **When** consulta su bandeja, **Then** puede distinguir cuál lleva más tiempo esperando.
2. **Given** una solicitud que lleva semanas esperando, **When** consulta su bandeja, **Then** el tiempo mostrado lo refleja sin ambigüedad y queda por encima de las recientes en el orden.
3. **Given** cualquier solicitud de la bandeja, **When** consulta su bandeja, **Then** el sistema **no** la califica de «vencida», «urgente» ni equivalente: no emite un juicio que ninguna norma respalde.

---

### User Story 3 - Responderle al estudiante en qué área está su trámite (Priority: P3)

Cuando un estudiante pregunta por su trámite —por correo, por teléfono o en ventanilla—, la Coordinación puede decirle en qué punto del recorrido está y qué falta, sin abrir el historial de correos. Es la **visibilidad mediada** que la Coordinación pidió: el estudiante obtiene la respuesta a través de ella, no de un portal propio.

**Why this priority**: responde textualmente lo que la Coordinación pidió en la entrevista (*«¿En qué área está el documento? […] mediante las firmas que vayan haciendo»*), pero es consulta puntual y no bloquea la operación diaria. La P1 ya permite responderlo con más trabajo.

**Independent Test**: se toma una solicitud en un punto intermedio del flujo y se comprueba que el sistema informa en qué área está y qué falta para cerrarla, sin exponer información que la Coordinación no deba dar.

**Acceptance Scenarios**:

1. **Given** una solicitud detenida en la facultad, **When** la Coordinación la consulta, **Then** el sistema indica que está en la facultad, y no una etiqueta genérica que agrupe áreas distintas.
2. **Given** una solicitud que fue devuelta para corrección, **When** la Coordinación la consulta, **Then** puede saber que hubo una devolución, aunque el trámite haya vuelto a un punto por el que ya había pasado. Esa respuesta sale del **historial**, no del estado actual: en uno de los dos trámites la devolución es un movimiento, no un estado, y ningún catálogo de estados puede responderla.
3. **Given** el catálogo de trámites, **When** un cliente lo consulta, **Then** puede saber qué estados tiene cada trámite y cuáles son el inicial y los finales, sin reconocer códigos concretos.

---

### Edge Cases

- **Una solicitud cuya siguiente acción no corresponde a nadie**: si por configuración un estado no ofrece ninguna transición y no es final, la solicitud queda detenida sin dueño. No debe desaparecer sin rastro: es un defecto de configuración que alguien tiene que ver.
- **Una solicitud que espera acción de la Coordinación y de otra área a la vez**: si desde un estado salen transiciones con responsables distintos, aparece en la bandeja de **cada uno**, y el responsable que la respuesta declara es el de la bandeja consultada. Ninguna configuración sembrada tiene hoy ese caso; el criterio lo resuelve así sin código adicional.
- **Volumen**: 30–40 solicitudes de adición de créditos por semestre, concentradas al inicio. La bandeja tiene que ser útil el día pico, no en promedio.
- **Antigüedad sin cierre**: una solicitud que lleva meses esperando —los peores casos registrados llegan a dos meses— no debe romper el indicador ni ordenarse como si acabara de llegar.
- **Reloj**: los plazos se cuentan en la zona horaria de la sede, no en la del servidor, y el resultado no puede cambiar según dónde se despliegue.
- **Bandeja vacía**: es un estado legítimo y frecuente fuera del pico. Debe distinguirse de un fallo al consultar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST ofrecer a la Coordinación una vista de las solicitudes que esperan **su** acción, excluyendo las que esperan acción de otra área y las que ya están cerradas.
- **FR-002**: El sistema MUST determinar a quién le toca actuar a partir de la **configuración del trámite**, sin conocer trámites concretos: incorporar un trámite ya cubierto por el motor no debe requerir desplegar código (Principio VI).
- **FR-003**: El sistema MUST determinar que una solicitud espera acción de la Coordinación **leyendo el responsable que la configuración del trámite ya declara** para el siguiente movimiento. NO se introducen roles de usuario ni control de acceso por perfil en esta feature.
- **FR-003a**: El sistema MUST NOT presentar la bandeja como un control de acceso. Filtra lo que le toca a la Coordinación; no impide que la cuenta existente avance un trámite de otra área. Esa restricción es trabajo futuro y no la cubre esta feature.
- **FR-003b**: El sistema MUST registrar como actor de cada movimiento a **quien lo ejecutó en el sistema**, sin atribuirle la aprobación de un tercero. Cuando la Coordinación registre que la facultad aprobó, el historial debe poder distinguir «lo registró la Coordinación» de «el decano firmó»: la firma ocurre en el papel, fuera del sistema, igual que con los sistemas externos que el MVP no integra.
- **FR-004**: El sistema MUST exponer, por cada solicitud de la bandeja, cuánto tiempo lleva esperando.
- **FR-005**: El sistema MUST exponer la antigüedad de cada solicitud como un **hecho medible**, y MUST NOT calificarla de vencida, urgente o equivalente. No existe plazo institucional para estos dos trámites: el Reglamento Estudiantil de Pregrado (Acuerdo n.º 13 de 2023) solo fija plazo para la **corrección** de una nota ya reportada (art. 35 §3, quince días hábiles), trámite que el árbol excluye del alcance (§8). El árbol lo registraba como «documento pendiente de obtener» antes de obtenerse; obtenido, lo que hay es un vacío normativo, y el §IV prohíbe invocar un plazo que ninguna fuente respalda.
- **FR-005a**: El sistema MUST quedar preparado para incorporar un umbral **cuando la norma se obtenga**, sin rehacer lo construido: la antigüedad que esta feature calcula es el insumo que ese umbral necesitaría.
- **FR-006**: El sistema MUST contar los plazos en la zona horaria de la sede, de modo que el resultado no dependa de dónde se despliegue.
- **FR-007**: El sistema MUST permitir distinguir el origen de cada solicitud de la bandeja —registrada por la Coordinación o recibida por el enlace público—, porque el origen cambia qué se ha verificado antes de que llegue.
- **FR-008**: El sistema MUST limitar los datos personales de la vista a los mínimos para identificar y priorizar un trámite. La minimización acá **no** consiste en no listar, sino en no listar más de lo necesario (Principio III).
- **FR-009**: El sistema MUST ordenar la bandeja de forma que lo más urgente quede primero, con un criterio estable y explicable ante quien lo use.
- **FR-010**: El sistema MUST distinguir una bandeja legítimamente vacía de un fallo al consultarla.
- **FR-011**: El sistema MUST permitir saber en qué área está un trámite que ya no espera acción de la Coordinación, para responder consultas del estudiante por la vía mediada.
- **FR-011a**: El catálogo de trámites MUST exponer los estados de cada definición, indicando de cada uno si es **inicial** y si es **final**. Resuelve la decisión acoplada del issue #22: el cliente deja de reconocer códigos de estado a mano para saber si un trámite empezó o terminó, que es conocimiento de trámites concretos viviendo fuera del motor (Principio VI). El dato ya está almacenado; hoy simplemente no sale.
- **FR-011b**: El sistema MUST NOT exponer un orden lineal del recorrido. El flujo de un trámite **no es una secuencia**: admite devoluciones que regresan a un punto anterior y ramas de rechazo que terminan antes. Un indicador de «paso N de M» afirmaría una linealidad que la configuración no tiene, y obligaría a inventar un orden y mantenerlo por definición.
- **FR-011c**: La extensión del catálogo MUST ser aditiva: un cliente que hoy consume el catálogo debe seguir funcionando sin cambios.
- **FR-012**: El sistema MUST permitir distinguir una solicitud devuelta para corrección de una que nunca salió de ese punto, sabiendo que en uno de los dos trámites del alcance la devolución es un movimiento del historial y no un estado.
- **FR-013**: El sistema MUST NOT ofrecer al estudiante ninguna vista propia del estado de su trámite: la visibilidad sigue siendo mediada por la Coordinación. Es la decisión que el árbol de problemas fija para SP5 («visibilidad mediada») a partir de la Sesión 2 parte B, donde la Coordinación lo consideró innecesario para el estudiante y de alcance administrativo.
- **FR-014**: El sistema MUST hacer visible una solicitud detenida sin responsable posible, en lugar de omitirla silenciosamente de toda vista.

### Key Entities

- **Solicitud en espera**: una solicitud viva cuyo avance depende de una acción humana pendiente. Atributos relevantes para esta feature: el trámite al que pertenece, el punto del flujo en que está, desde cuándo espera, su origen y los datos mínimos para identificar al estudiante ante la Coordinación.
- **Responsable**: el área a la que la configuración del trámite le atribuye la siguiente acción. Hoy es un dato declarado en la configuración, no una identidad del sistema.
- **Antigüedad en espera**: el tiempo transcurrido desde que la solicitud quedó esperando su siguiente acción. Es un hecho medible, no un juicio. No se convierte en «vencida» porque no hay plazo institucional que lo respalde.
- **Estado de un trámite**: cada punto en que una solicitud puede detenerse, con su código, su nombre y dos marcas: si es el punto de partida y si es un cierre. Las tres cosas ya existen en la configuración. El conjunto de estados de una definición **no forma una secuencia**: hay devoluciones que vuelven atrás y rechazos que cierran antes de tiempo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La Coordinación identifica todas las solicitudes que esperan su acción **en una sola consulta**, sin abrir el correo ni revisar solicitud por solicitud.
- **SC-002**: Ninguna solicitud que espera acción de la Coordinación queda fuera de la bandeja, y ninguna que espera a otra área aparece en ella: **cero falsos positivos y cero falsos negativos** en el conjunto de prueba.
- **SC-003**: La Coordinación puede decir cuál de dos solicitudes atender primero **sin consultar otra fuente**.
- **SC-004**: Responder «¿en qué área está mi trámite?» pasa de revisar el historial de correos a una sola consulta en el sistema.
- **SC-005**: Incorporar un trámite nuevo por configuración —sin desplegar código— hace que sus solicitudes aparezcan en la bandeja sin ningún cambio adicional. Es la verificación de la tesis del proyecto aplicada a esta feature.
- **SC-006**: El resultado de la bandeja y de sus indicadores es idéntico con independencia de la zona horaria del entorno donde corra.

## Assumptions

Estos son hechos **medidos** el 2026-09-21 contra el estado real del sistema, no supuestos cómodos. Se listan porque condicionan el alcance.

- **La lista existe, el criterio no**: el sistema ya ofrece una consulta de solicitudes recientes, pero devuelve las más nuevas sin filtrar por estado ni por responsable, y ningún cliente la consume. Lo que esta feature construye es el **criterio** y el **indicador**, no el listado.
- **No hay roles de usuario**: existe una sola cuenta —la de la Coordinación— y el sistema no distingue perfiles. La configuración de cada trámite sí declara qué área es responsable de cada movimiento, pero nadie compara ese dato con quién está autenticado. De ahí Q1.
- **El plazo no tiene fuente, y por eso no se juzga**: la Coordinación mencionó plazos «de 3 a 5 días» y «hasta 15 días hábiles» sin poder ubicar la norma (Sesión 2 parte A); el árbol de problemas lo registró como *«documento pendiente de obtener»*. La norma se obtuvo después: el Reglamento Estudiantil de Pregrado (Acuerdo n.º 13 de 2023) fija quince días hábiles solo para la **corrección** de una nota ya reportada (art. 35 §3), que no es la novedad del alcance; para los dos trámites del MVP no hay plazo. No es un documento pendiente: es un vacío normativo. Esta feature **mide y no dictamina**. Se descartó sembrar un umbral provisional: haría que el sistema afirmara en pantalla algo que no se puede citar, y con 30–40 solicitudes por semestre el dato crudo ya permite priorizar.
- **Los días hábiles se descartaron con el umbral**: medirlos en Colombia exige modelar unos 18 festivos anuales, varios trasladados al lunes por la Ley Emiliani y cinco móviles atados a la Pascua — un calendario que hay que mantener cada año. Desproporcionado para esta feature, y **innecesario** si no se dictamina vencimiento. La antigüedad se expresa en tiempo transcurrido, sin calendario laboral. *(Nota: la única cifra en días hábiles que la entrevista atribuye a un tramo concreto son «cinco días hábiles» para la facultad; ningún plazo global está respaldado.)*
- **El cliente actual mide mal y lo dice mal**: muestra un vencimiento de «6 días hábiles» que no sale de ninguna fuente, calculado con una función que **suma días corridos pese a llamarse `addBusinessDays`** y que no contempla festivos. Esta feature no hereda ese número ni esa función.
- **El origen de las solicitudes no es uniforme**: desde la captura pública, una solicitud puede nacer sin que ninguna persona con sesión la haya registrado. El actor es el portal.
- **El estudiante no tendrá vista propia**: decidido y registrado en entrevista. La bandeja es la vía de visibilidad, no un paso hacia un portal de auto-consulta.
- **El decano no será usuario del sistema**: el equipo consideró darle a la facultad una vía para firmar sin intervención de la Coordinación (idea surgida en la sesión de especificación, no en la entrevista: en el verbatim la Coordinación ni siquiera sabe con certeza quién firma — *«No sé si el decano solo revisa o si hace la firma, no me consta»*, Sesión 1 parte A), pero darle cuenta, rol y permisos a un aprobador externo para que entre una sola vez a firmar no se justifica en este alcance, y el circuito alternativo —enviarle el documento, recibir su firma y devolverla al sistema— depende de correo saliente, que es una dependencia externa sin resolver. Queda fuera. Si algún día entra, el patrón a reusar es el que el proyecto ya aplicó dos veces —acceso sin cuenta, autorizado por posesión de un enlace o un código—, no un rol.
- **Exponer los estados es aditivo y está medido**: se construyó un spike descartable para comprobarlo antes de decidir. Son **5 archivos tocados y uno nuevo, +16/−9 líneas**, la suite completa pasa **sin modificar un solo test**, y los clientes actuales del catálogo siguen funcionando porque solo se agregan campos. El dato de «estado inicial» ya está almacenado desde la primera migración del motor: nunca se expuso.
- **El recorrido no se expone como secuencia porque no lo es**: la configuración declara transiciones de retorno —una devolución vuelve a un punto anterior— y transiciones de rechazo que cierran antes del final. Ordenar eso en línea recta exigiría inventar el orden, sembrarlo por definición y mantenerlo. Se descartó.
- **Las métricas del prototipo no cubren esto**: el código no integrado de la rama de exploración calcula agregados globales por trámite y por estado. No es una bandeja y no cierra este issue.
- **Alcance**: Sede Cali, los dos trámites del MVP, y los sistemas externos siguen siendo cajas negras. Esta feature no los integra.
- **Volumen**: 30–40 solicitudes por semestre para el trámite más frecuente, según la entrevista. El diseño no necesita escalar más allá de eso.
