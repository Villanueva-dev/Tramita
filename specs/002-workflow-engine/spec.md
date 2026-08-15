# Feature Specification: Motor de workflow configurable con timeline de auditoría

**Feature Branch**: `002-workflow-engine`

**Created**: 2026-08-06

**Status**: Draft

**Input**: Motor de workflow configurable para trámites académicos con timeline de auditoría inmutable (alcance SP1 + SP6 del árbol de problemas). El sistema orquesta los estados de dos trámites de la Sede Cali — adición de créditos y novedad de notas — que hoy transcurren por formato Word, firmas escaneadas y cadenas de correo. La Coordinación registra solicitudes y las avanza; el sistema valida qué transiciones son legales según la definición de cada trámite. La profundidad de automatización difiere por trámite y es cuestión de configuración, no de código. Cada solicitud acumula un timeline de auditoría inmutable (todas las transiciones, fechadas y por autor) con el que la Coordinación responde consultas del estudiante en menos de un minuto (visibilidad mediada).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar una solicitud de trámite (Priority: P1)

La Coordinación Académica registra una nueva solicitud de un trámite (adición de créditos o novedad de notas) con los datos mínimos de identificación del estudiante: nombre y número de cédula. La solicitud nace en el estado inicial definido para ese trámite y queda disponible para consultarla y avanzarla.

**Why this priority**: es la entrada de todo — sin solicitudes registradas no hay estados que orquestar ni timeline que consultar.

**Independent Test**: con los dos trámites configurados, registrar una solicitud de cada tipo y verificar que cada una nace en el estado inicial de su propio trámite.

**Acceptance Scenarios**:

1. **Given** el trámite de adición de créditos configurado, **When** la Coordinación registra una solicitud con los datos mínimos de identificación, **Then** la solicitud queda creada en el estado inicial definido para adición de créditos.
2. **Given** los dos trámites configurados, **When** se registran solicitudes de ambos tipos, **Then** cada una nace en el estado inicial de su propio trámite y ambas coexisten sin interferirse.
3. **Given** un tipo de trámite que no existe en la configuración, **When** se intenta registrar una solicitud de ese tipo, **Then** el sistema no acepta el registro e indica el motivo.
4. **Given** una petición sin sesión autenticada de la Coordinación, **When** intenta registrar una solicitud, **Then** el sistema no la registra y responde que la operación requiere autenticación (FR-012).

---

### User Story 2 - Avanzar una solicitud por sus estados (Priority: P1)

La Coordinación avanza una solicitud a través de los estados definidos para su trámite. El sistema solo permite las transiciones definidas en la configuración; cualquier otra no se permite. Los pasos que dependen de aprobadores externos los avanza la propia Coordinación: el sistema registra que la decisión externa ocurrió, no la ejecuta.

Una transición puede llevar la solicitud a un estado posterior o devolverla a uno anterior (ver US5): para el motor, ambas son transiciones definidas y se validan igual. El recorrido de una solicitud no es necesariamente lineal.

**Why this priority**: es el corazón de SP1 — la orquestación estructurada de estados que hoy no existe y que motiva el proyecto.

**Independent Test**: con una solicitud registrada, ejecutar una transición definida (el estado cambia) y una no definida (no se permite y el estado no cambia).

**Acceptance Scenarios**:

1. **Given** una solicitud en un estado con transición definida hacia otro, **When** la Coordinación la avanza por esa transición, **Then** el estado cambia y quedan registrados el autor y la fecha del cambio.
2. **Given** una solicitud en un estado cualquiera, **When** se intenta una transición que no está definida para su trámite desde ese estado, **Then** el sistema no la permite y el estado no cambia.
3. **Given** un paso a cargo de un aprobador externo (fuera del sistema), **When** la Coordinación registra que el aprobador tomó su decisión, **Then** la solicitud avanza y consta que el registro lo hizo la Coordinación en nombre de ese paso externo.
4. **Given** una solicitud en un estado final, **When** se intenta avanzarla, **Then** el sistema no lo permite: el trámite está cerrado.
5. **Given** una petición sin sesión autenticada de la Coordinación, **When** intenta avanzar una solicitud, **Then** el sistema no aplica la transición, el estado no cambia y no se agrega ninguna entrada al timeline (FR-012).

---

### User Story 3 - Consultar el timeline de auditoría de una solicitud (Priority: P1)

La Coordinación abre una solicitud y ve su timeline completo: todas las transiciones, en orden cronológico, con fecha y autor de cada una. Es la herramienta con la que responde consultas de estado del estudiante en menos de un minuto (visibilidad mediada: coordinación → sistema → respuesta al estudiante).

**Why this priority**: es SP6 y la otra mitad del valor de la feature — sin timeline, el motor avanza estados pero no resuelve la ausencia de historial para la coordinación y la auditoría (causas C1, C5 y C7 del árbol de problemas).

**Independent Test**: sobre una solicitud avanzada varias veces, abrir el timeline y verificar orden cronológico, fechas y autores; verificar que no existe ninguna operación para editar o borrar entradas.

**Acceptance Scenarios**:

1. **Given** una solicitud con varias transiciones acumuladas, **When** la Coordinación consulta su timeline, **Then** ve todas las transiciones en orden cronológico, cada una con su fecha y su autor.
2. **Given** cualquier entrada ya registrada en el timeline, **When** se busca la forma de editarla o eliminarla, **Then** no existe tal operación: el timeline solo crece.
3. **Given** un estudiante que pregunta por su trámite (por fuera del sistema), **When** la Coordinación localiza la solicitud y lee su timeline, **Then** la localiza por nombre o por cédula y puede responder el estado actual y la historia completa sin reconstruir cadenas de correo.
4. **Given** una petición sin sesión autenticada de la Coordinación, **When** intenta localizar una solicitud o consultar su timeline, **Then** el sistema no devuelve ni la solicitud ni su historial (FR-012).

---

### User Story 4 - Operar dos trámites de profundidad distinta sobre el mismo motor (Priority: P2)

La diferencia entre adición de créditos (flujo completo) y novedad de notas (seguimiento) es de configuración, no de código. La Coordinación opera la novedad registrando los avances de una cadena de firmas que transcurre por fuera (correo/OneDrive), y el timeline refleja esa historia con la misma fidelidad.

**Why this priority**: es la tesis del motor genérico — la justificación central del proyecto —, pero solo se puede demostrar cuando registrar, avanzar y auditar (US1–US3) ya funcionan.

**Independent Test**: configurar los dos trámites con definiciones distintas y verificar que ambos operan sin comportamiento a medida; definir un trámite de prueba adicional solo con configuración y verificar que opera igual, sin cambios en el sistema.

**Acceptance Scenarios**:

1. **Given** una solicitud de novedad de notas, **When** la Coordinación registra los avances de la cadena de firmas externa, **Then** el timeline refleja la historia completa aunque las firmas hayan transcurrido por correo/OneDrive.
2. **Given** una nueva definición de trámite cargada como configuración, **When** la Coordinación registra y avanza una solicitud de ese tipo, **Then** el motor la orquesta igual que a los dos trámites existentes, sin modificar el código de la aplicación.
3. **Given** que la definición de adición de créditos incluye un cierre por rechazo y la de novedad de notas no, **When** se opera cada trámite, **Then** la diferencia de comportamiento proviene únicamente de sus definiciones y el motor es el mismo para ambos.

---

### User Story 5 - Devolver una solicitud para corrección y cerrarla por rechazo (Priority: P2)

Cuando un aprobador externo devuelve el trámite por un error de formato, la Coordinación registra la devolución con su motivo y la solicitud vuelve al estado que la definición indica para corregirlo. Cuando un aprobador externo lo niega de forma definitiva, la Coordinación registra el rechazo y la solicitud queda cerrada. No todos los trámites admiten cierre por rechazo: es parte de su definición.

**Why this priority**: la devolución por error formal es el re-trabajo que motiva el proyecto (efecto E2 del árbol de problemas) y una de las tres variables de la pregunta de investigación. Se apoya en US1–US3, por eso va después.

**Independent Test**: sobre una solicitud avanzada, registrar una devolución con motivo y verificar que vuelve al estado de corrección y que el timeline conserva ambos tramos; registrar un cierre por rechazo en el trámite que lo admite y verificar que queda cerrada; intentar ese mismo cierre en el trámite que no lo admite y verificar que no está permitido.

**Acceptance Scenarios**:

1. **Given** una solicitud en un estado cuya definición contempla devolución, **When** la Coordinación registra la devolución indicando el motivo, **Then** la solicitud vuelve al estado de corrección definido y el timeline registra el retorno con su motivo, su fecha y su autor.
2. **Given** una devolución que se intenta registrar sin motivo, **When** se envía, **Then** el sistema no la acepta: el motivo es obligatorio.
3. **Given** una solicitud de un trámite cuya definición incluye un estado final de rechazo, **When** la Coordinación registra que el aprobador externo la negó de forma definitiva, **Then** la solicitud queda en ese estado final y no admite más transiciones.
4. **Given** una solicitud de un trámite cuya definición NO incluye estado final de rechazo, **When** se intenta cerrarla por rechazo, **Then** la transición no está definida y el sistema no la permite: ese trámite no se cierra negativamente.
5. **Given** una solicitud devuelta y luego corregida, **When** la Coordinación la vuelve a avanzar, **Then** el timeline conserva el tramo anterior, la devolución y el nuevo avance, sin sobrescribir nada.

---

### Edge Cases

- Transición no definida para el trámite → no se permite, sin alterar el estado y sin generar entrada en el timeline (el timeline registra lo que ocurrió, no lo que se intentó).
- Solicitud en estado final —cerrada satisfactoriamente o por rechazo— → no admite más transiciones; el trámite está cerrado.
- Tipo de trámite inexistente en la configuración → no se acepta el registro de la solicitud.
- Devolución hacia un estado ya recorrido → la solicitud retrocede, pero el timeline no: conserva el tramo anterior y suma la entrada de la devolución con su motivo.
- Devolución sin motivo → no se acepta; el motivo es el dato que hace útil la devolución.
- Cierre por rechazo en un trámite cuya definición no lo contempla → transición no definida; no se permite.
- La definición de un trámite cambia mientras hay solicitudes en curso → las solicitudes ya iniciadas conservan las reglas con las que nacieron; los cambios aplican solo a solicitudes nuevas.
- Dos avances casi simultáneos sobre la misma solicitud → solo prospera el que sea legal desde el estado vigente al momento de aplicarse; el otro no se aplica y el estado no se corrompe.

## Requirements *(mandatory)*

> **Nota terminológica**: *transición no permitida* califica un intento que la definición del trámite no contempla — es un error de uso del sistema. *Devuelta* y *rechazada* califican al trámite mismo — son decisiones reales de la cadena de aprobación, registradas por la Coordinación. Son cosas distintas y no deben confundirse.

### Functional Requirements

- **FR-001**: Cada trámite MUST quedar definido como configuración — sus estados, sus transiciones permitidas y el responsable de cada paso — de modo que agregar o modificar un trámite no requiera modificar el código de la aplicación.
- **FR-002**: El sistema MUST permitir a la Coordinación registrar solicitudes de un trámite configurado, con los datos mínimos de identificación del estudiante — al menos nombre y número de cédula —; cada solicitud nace en el estado inicial definido para su trámite.
- **FR-003**: El sistema MUST validar cada cambio de estado contra la definición del trámite: solo las transiciones definidas desde el estado actual son legales.
- **FR-004**: El sistema MUST bloquear toda transición no definida sin alterar el estado de la solicitud.
- **FR-005**: El sistema MUST registrar, en cada transición efectuada, el autor y la fecha del cambio.
- **FR-006**: Los pasos a cargo de aprobadores externos MUST poder avanzarse por la Coordinación, dejando constancia de que el registro lo hizo ella en nombre del paso externo; el sistema registra que la decisión ocurrió, no la ejecuta.
- **FR-007**: Cada solicitud MUST acumular un timeline de auditoría inmutable: las entradas solo se agregan; no existe operación de edición ni de borrado.
- **FR-008**: El sistema MUST permitir a la Coordinación consultar el timeline completo de una solicitud en orden cronológico.
- **FR-009**: Los cambios en la definición de un trámite MUST NOT alterar las reglas de las solicitudes ya iniciadas: cada solicitud se rige por la definición vigente al momento de su registro.
- **FR-010**: Los dos trámites del alcance (adición de créditos y novedad de notas) MUST operar sobre el mismo motor con definiciones distintas; la diferencia de profundidad (flujo completo vs. seguimiento) MUST residir en la configuración, no en comportamiento a medida.
- **FR-011**: El sistema MUST permitir a la Coordinación localizar una solicitud registrada por nombre o por número de cédula del estudiante — los dos datos con los que hoy identifica el trámite al recibir la consulta — para consultarla.
- **FR-012**: Solo la Coordinación autenticada (según la feature 001) MUST poder registrar solicitudes, avanzarlas y consultar sus timelines. La garantía MUST verificarse sobre cada operación que introduce esta feature: la feature 001 provee el mecanismo de autenticación, pero no cubre operaciones que no existían cuando se especificó.
- **FR-013**: La definición de un trámite MUST poder incluir transiciones de retorno (devolución a un estado anterior) y estados finales que representen un cierre negativo (rechazo); el motor MUST tratarlas como cualquier otra transición o estado final definido, sin comportamiento a medida.
- **FR-014**: Cada entrada del timeline MUST poder llevar una observación, y esta MUST ser obligatoria cuando la transición registrada es una devolución, de modo que el motivo de la corrección quede en el historial.
- **FR-015**: La existencia de un cierre por rechazo MUST ser propia de cada definición: un trámite cuya definición no lo contemple MUST NOT poder cerrarse negativamente.

### Key Entities *(include if feature involves data)*

- **Definición de trámite**: describe un tipo de trámite como configuración — su nombre, sus estados (incluido el inicial y los finales), las transiciones permitidas entre ellos y el responsable de cada paso. Es dato, no código.
- **Estado**: situación en la que puede encontrarse una solicitud dentro de su trámite (conceptual; cada trámite define los suyos). Un estado final representa el cierre del trámite, sea satisfactorio o por rechazo.
- **Transición**: paso permitido entre dos estados dentro de un trámite, con su responsable (la Coordinación o un aprobador externo cuya decisión la Coordinación registra). Puede llevar a un estado posterior o devolver a uno anterior; el motor no distingue entre ambos casos.
- **Solicitud**: instancia de un trámite iniciada para un estudiante concreto; conoce su trámite, la definición con la que nació, su estado actual y sus datos mínimos de identificación (nombre y cédula del estudiante).
- **Entrada de timeline**: registro inmutable de una transición ocurrida sobre una solicitud — de qué estado a cuál, cuándo, por quién y, cuando corresponde, con qué observación (obligatoria en las devoluciones).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La Coordinación responde una consulta de estado de un estudiante en menos de 1 minuto usando el timeline, sin abrir correos ni archivos externos.
- **SC-002**: El 100% de las transiciones efectuadas quedan registradas con autor y fecha, y ninguna entrada del timeline puede modificarse ni eliminarse después de registrada.
- **SC-003**: El 100% de los intentos de transición no definidos se bloquean sin corromper el estado de la solicitud.
- **SC-004**: Los dos trámites del alcance operan en el sistema sin que ninguno requiera comportamiento a medida: sus diferencias viven íntegramente en la configuración — incluida la de que uno admite cierre por rechazo y el otro no.
- **SC-005**: Definir un trámite adicional de estructura similar — mismos tipos de estado y transición, sin reglas de validación nuevas y sin pasos en paralelo — requiere únicamente cargar su configuración: sin modificar el código de la aplicación ni desplegar una versión nueva. Queda operable en cuanto la configuración está cargada.
- **SC-006**: La historia completa de cualquier solicitud se reconstruye íntegramente desde el sistema: ninguna consulta de auditoría necesita recurrir a cadenas de correo u OneDrive.
- **SC-007**: Cada solicitud permite contabilizar cuántas devoluciones acumuló y por qué motivo, lo que aporta la línea base medible de la métrica de re-trabajo del árbol de problemas (§9).

## Assumptions

- El único actor autenticado es la Coordinación Académica (feature `001-auth-login`); esta feature no introduce roles nuevos. Estudiantes y aprobadores externos NO son usuarios del sistema (árbol de problemas, SP5): sus consultas y decisiones se median a través de la Coordinación.
- Las definiciones de los dos trámites se cargan como datos de configuración provistos por el equipo (semilla); no hay interfaz de administración de definiciones en este alcance. Los estados y transiciones concretos de cada trámite se derivan de las entrevistas a la Coordinación de la Sede Cali y se validan con ella.
- La captura de datos de la solicitud es mínima en esta feature: nombre y cédula del estudiante, más el trámite. Son los dos datos con los que la Coordinación identifica hoy un trámite cuando el estudiante consulta por su estado. El resto de los campos del formato oficial (código de asignatura, créditos, periodo, notas) pertenece a los formularios validados de SP2 y va en la feature `003`, junto con las reglas de negocio por trámite (tope de créditos, ventanas temporales).
- Los intentos de transición no permitidos no generan entradas en el timeline: el timeline documenta lo que ocurrió.
- El cierre por rechazo aplica a la adición de créditos —presentada fuera de la ventana de solicitud, la facultad la niega de forma definitiva— y no a la novedad de notas, que según la Coordinación siempre termina aunque se demore. Esa diferencia se declara en la definición de cada trámite, no en el código.
- En esta feature el sistema NO determina si una solicitud es extemporánea ni evalúa ninguna otra regla de negocio: la Coordinación registra la decisión que tomó el aprobador externo. La regla que evalúa la ventana temporal es SP2 y va en la feature `003`.
- La bandeja de trabajo consolidada con pendientes y SLA es SP5 (sprint 3); aquí solo existe la localización básica de solicitudes necesaria para consultar un timeline.
- La generación del PDF formal (SP3) y el sello electrónico con traza de firmas sobre documentos (SP4) son del sprint 2 y quedan fuera.
- Las notificaciones al estudiante (SP7) quedan fuera; las transiciones solo generan entradas en el timeline interno.
- Class y QF son sistemas externos (cajas negras): el resultado final lo asienta un humano donde corresponda; no hay integración técnica.
- En novedad de notas, la Coordinación de la Sede Cali no ejecuta los pasos aguas abajo: una vez el trámite sale hacia la facultad, quien lo mueve entre dependencias es la auxiliar de la Dirección de Sede desde Medellín, y la Coordinación se entera por copia de correo. Que esos avances queden registrados depende de que la Coordinación lea esas copias y las asiente en el sistema. Es un supuesto operativo, ya identificado como riesgo en el árbol de problemas (§10): si no se sostiene, la visibilidad se degrada a información desactualizada.
- El sistema persiste datos personales de estudiantes (nombre y cédula). La Coordinación pidió expresamente minimizar la retención de información sensible y priorizar el estado del trámite por encima del documento; por eso esta feature persiste únicamente los datos de identificación necesarios para localizar la solicitud y leer su historial. La norma aplicable es la Ley 1581 de 2012 (régimen general de protección de datos personales, Colombia), confirmada con la Coordinación. Esta feature no afirma cumplimiento normativo: se limita a minimizar la superficie de datos persistidos.

## Límites declarados del motor

Se declaran explícitamente para que la generalidad del motor no se lea como ilimitada.

- **No soporta pasos en paralelo.** El motor modela cadenas secuenciales de aprobación, no bifurcaciones con sincronización posterior. Las cadenas de los dos trámites del alcance son secuenciales según las entrevistas, por lo que el límite no los afecta. Salvedad conocida: en novedad de notas, la firma del docente y la de la Dirección de Sede se gestionan sobre una misma carpeta compartida y en la práctica pueden ocurrir en cualquier orden; al modelarlas como dos pasos ordenados, el timeline reporta el orden en que se registraron, no necesariamente aquel en que se firmaron. Un trámite futuro que sí requiera pasos en paralelo no se resolvería con configuración.
- **No evalúa reglas de negocio.** Ninguna decisión de negocio se calcula aquí: la Coordinación registra la decisión que tomó el aprobador externo. La evaluación de reglas (tope de créditos, ventana temporal) es SP2 y va en la feature `003`.
- **No orquesta a los aprobadores externos.** La cadena de firmas sigue transcurriendo por correo y OneDrive; el sistema deja constancia de que ocurrió, no la ejecuta.
