# Feature Specification: Motor de workflow configurable con timeline de auditoría

**Feature Branch**: `002-workflow-engine`

**Created**: 2026-08-06

**Status**: Draft

**Input**: Motor de workflow configurable para trámites académicos con timeline de auditoría inmutable (alcance SP1 + SP6 del árbol de problemas). El sistema orquesta los estados de dos trámites de la Sede Cali — adición de créditos y novedad de notas — que hoy transcurren por formato Word, firmas escaneadas y cadenas de correo. La Coordinación registra solicitudes y las avanza; el sistema valida qué transiciones son legales según la definición de cada trámite. La profundidad de automatización difiere por trámite y es cuestión de configuración, no de código. Cada solicitud acumula un timeline de auditoría inmutable (todas las transiciones, fechadas y por autor) con el que la Coordinación responde consultas del estudiante en menos de un minuto (visibilidad mediada).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar una solicitud de trámite (Priority: P1)

La Coordinación Académica registra una nueva solicitud de un trámite (adición de créditos o novedad de notas) con los datos mínimos de identificación. La solicitud nace en el estado inicial definido para ese trámite y queda disponible para consultarla y avanzarla.

**Why this priority**: es la entrada de todo — sin solicitudes registradas no hay estados que orquestar ni timeline que consultar.

**Independent Test**: con los dos trámites configurados, registrar una solicitud de cada tipo y verificar que cada una nace en el estado inicial de su propio trámite.

**Acceptance Scenarios**:

1. **Given** el trámite de adición de créditos configurado, **When** la Coordinación registra una solicitud con los datos mínimos de identificación, **Then** la solicitud queda creada en el estado inicial definido para adición de créditos.
2. **Given** los dos trámites configurados, **When** se registran solicitudes de ambos tipos, **Then** cada una nace en el estado inicial de su propio trámite y ambas coexisten sin interferirse.
3. **Given** un tipo de trámite que no existe en la configuración, **When** se intenta registrar una solicitud de ese tipo, **Then** el sistema la rechaza indicando el motivo.

---

### User Story 2 - Avanzar una solicitud por sus estados (Priority: P1)

La Coordinación avanza una solicitud a través de los estados definidos para su trámite. El sistema solo permite las transiciones definidas en la configuración; cualquier otra se rechaza. Los pasos que dependen de aprobadores externos los avanza la propia Coordinación: el sistema registra que la decisión externa ocurrió, no la ejecuta.

**Why this priority**: es el corazón de SP1 — la orquestación estructurada de estados que hoy no existe y que motiva el proyecto.

**Independent Test**: con una solicitud registrada, ejecutar una transición legal (el estado avanza) y una ilegal (se rechaza sin alterar el estado).

**Acceptance Scenarios**:

1. **Given** una solicitud en un estado con transición definida hacia otro, **When** la Coordinación la avanza por esa transición, **Then** el estado cambia y quedan registrados el autor y la fecha del cambio.
2. **Given** una solicitud en un estado cualquiera, **When** se intenta una transición que no está definida para su trámite desde ese estado, **Then** el sistema la rechaza y el estado no cambia.
3. **Given** un paso a cargo de un aprobador externo (fuera del sistema), **When** la Coordinación registra que el aprobador tomó su decisión, **Then** la solicitud avanza y consta que el registro lo hizo la Coordinación en nombre de ese paso externo.
4. **Given** una solicitud en un estado final, **When** se intenta avanzarla, **Then** el sistema la rechaza: el trámite está cerrado.

---

### User Story 3 - Consultar el timeline de auditoría de una solicitud (Priority: P1)

La Coordinación abre una solicitud y ve su timeline completo: todas las transiciones, en orden cronológico, con fecha y autor de cada una. Es la herramienta con la que responde consultas de estado del estudiante en menos de un minuto (visibilidad mediada: coordinación → sistema → respuesta al estudiante).

**Why this priority**: es SP6 y la otra mitad del valor de la feature — sin timeline, el motor avanza estados pero no resuelve la ausencia de historial para la coordinación y la auditoría (causas C1, C5 y C7 del árbol de problemas).

**Independent Test**: sobre una solicitud avanzada varias veces, abrir el timeline y verificar orden cronológico, fechas y autores; verificar que no existe ninguna operación para editar o borrar entradas.

**Acceptance Scenarios**:

1. **Given** una solicitud con varias transiciones acumuladas, **When** la Coordinación consulta su timeline, **Then** ve todas las transiciones en orden cronológico, cada una con su fecha y su autor.
2. **Given** cualquier entrada ya registrada en el timeline, **When** se busca la forma de editarla o eliminarla, **Then** no existe tal operación: el timeline solo crece.
3. **Given** un estudiante que pregunta por su trámite (por fuera del sistema), **When** la Coordinación localiza la solicitud y lee su timeline, **Then** puede responder el estado actual y la historia completa sin reconstruir cadenas de correo.

---

### User Story 4 - Operar dos trámites de profundidad distinta sobre el mismo motor (Priority: P2)

La diferencia entre adición de créditos (flujo completo) y novedad de notas (seguimiento) es de configuración, no de código. La Coordinación opera la novedad registrando los avances de una cadena de firmas que transcurre por fuera (correo/OneDrive), y el timeline refleja esa historia con la misma fidelidad.

**Why this priority**: es la tesis del motor genérico — la justificación central del proyecto —, pero solo se puede demostrar cuando registrar, avanzar y auditar (US1–US3) ya funcionan.

**Independent Test**: configurar los dos trámites con definiciones distintas y verificar que ambos operan sin comportamiento a medida; definir un trámite de prueba adicional solo con configuración y verificar que opera igual, sin cambios en el sistema.

**Acceptance Scenarios**:

1. **Given** una solicitud de novedad de notas, **When** la Coordinación registra los avances de la cadena de firmas externa, **Then** el timeline refleja la historia completa aunque las firmas hayan transcurrido por correo/OneDrive.
2. **Given** una nueva definición de trámite cargada como configuración, **When** la Coordinación registra y avanza una solicitud de ese tipo, **Then** el motor la orquesta igual que a los dos trámites existentes, sin cambios en el sistema.

---

### Edge Cases

- Transición no definida para el trámite → rechazo sin alterar el estado y sin generar entrada en el timeline (el timeline registra lo que ocurrió, no lo que se intentó).
- Solicitud en estado final → no admite más transiciones; el trámite está cerrado.
- Tipo de trámite inexistente en la configuración → rechazo al registrar la solicitud.
- La definición de un trámite cambia mientras hay solicitudes en curso → las solicitudes ya iniciadas conservan las reglas con las que nacieron; los cambios aplican solo a solicitudes nuevas.
- Dos avances casi simultáneos sobre la misma solicitud → solo prospera el que sea legal desde el estado vigente al momento de aplicarse; el otro se rechaza sin corromper el estado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada trámite MUST quedar definido como configuración — sus estados, sus transiciones permitidas y el responsable de cada paso — de modo que agregar o modificar un trámite no requiera cambios en el sistema.
- **FR-002**: El sistema MUST permitir a la Coordinación registrar solicitudes de un trámite configurado, con los datos mínimos de identificación; cada solicitud nace en el estado inicial definido para su trámite.
- **FR-003**: El sistema MUST validar cada avance de estado contra la definición del trámite: solo las transiciones definidas desde el estado actual son legales.
- **FR-004**: El sistema MUST rechazar toda transición no definida sin alterar el estado de la solicitud.
- **FR-005**: El sistema MUST registrar, en cada transición efectuada, el autor y la fecha del cambio.
- **FR-006**: Los pasos a cargo de aprobadores externos MUST poder avanzarse por la Coordinación, dejando constancia de que el registro lo hizo ella en nombre del paso externo; el sistema registra que la decisión ocurrió, no la ejecuta.
- **FR-007**: Cada solicitud MUST acumular un timeline de auditoría inmutable: las entradas solo se agregan; no existe operación de edición ni de borrado.
- **FR-008**: El sistema MUST permitir a la Coordinación consultar el timeline completo de una solicitud en orden cronológico.
- **FR-009**: Los cambios en la definición de un trámite MUST NOT alterar las reglas de las solicitudes ya iniciadas: cada solicitud se rige por la definición vigente al momento de su registro.
- **FR-010**: Los dos trámites del alcance (adición de créditos y novedad de notas) MUST operar sobre el mismo motor con definiciones distintas; la diferencia de profundidad (flujo completo vs. seguimiento) MUST residir en la configuración, no en comportamiento a medida.
- **FR-011**: El sistema MUST permitir a la Coordinación localizar una solicitud registrada para consultarla (búsqueda básica suficiente para responder una consulta de estado).
- **FR-012**: Solo la Coordinación autenticada (según la feature 001) MUST poder registrar solicitudes, avanzarlas y consultar sus timelines.

### Key Entities *(include if feature involves data)*

- **Definición de trámite**: describe un tipo de trámite como configuración — su nombre, sus estados (incluido el inicial y los finales), las transiciones permitidas entre ellos y el responsable de cada paso. Es dato, no código.
- **Estado**: situación en la que puede encontrarse una solicitud dentro de su trámite (conceptual; cada trámite define los suyos).
- **Transición**: paso permitido de un estado a otro dentro de un trámite, con su responsable (la Coordinación o un aprobador externo cuya decisión la Coordinación registra).
- **Solicitud**: instancia de un trámite iniciada para un estudiante concreto; conoce su trámite, la definición con la que nació, su estado actual y sus datos mínimos de identificación.
- **Entrada de timeline**: registro inmutable de una transición ocurrida sobre una solicitud — de qué estado a cuál, cuándo y por quién.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La Coordinación responde una consulta de estado de un estudiante en menos de 1 minuto usando el timeline, sin abrir correos ni archivos externos.
- **SC-002**: El 100% de las transiciones efectuadas quedan registradas con autor y fecha, y ninguna entrada del timeline puede modificarse ni eliminarse después de registrada.
- **SC-003**: El 100% de los intentos de transición no definidos se rechazan sin corromper el estado de la solicitud.
- **SC-004**: Los dos trámites del alcance operan en el sistema sin que ninguno requiera comportamiento a medida: sus diferencias viven íntegramente en la configuración.
- **SC-005**: Definir un trámite adicional de estructura similar requiere únicamente cargar su configuración — cero cambios en el sistema — y queda operable de inmediato.
- **SC-006**: La historia completa de cualquier solicitud se reconstruye íntegramente desde el sistema: ninguna consulta de auditoría necesita recurrir a cadenas de correo u OneDrive.

## Assumptions

- El único actor autenticado es la Coordinación Académica (feature `001-auth-login`); esta feature no introduce roles nuevos. Estudiantes y aprobadores externos NO son usuarios del sistema (árbol de problemas, SP5): sus consultas y decisiones se median a través de la Coordinación.
- Las definiciones de los dos trámites se cargan como datos de configuración provistos por el equipo (semilla); no hay interfaz de administración de definiciones en este alcance. Los estados y transiciones concretos de cada trámite se derivan de las entrevistas a la Coordinación de la Sede Cali y se validan con ella.
- La captura de datos de la solicitud es mínima en esta feature (identificación del estudiante y del trámite). Los formularios validados y las reglas de negocio por trámite (tope de créditos, ventanas temporales) son SP2 y van en la feature `003`.
- Los intentos de transición rechazados no generan entradas en el timeline: el timeline documenta lo que ocurrió.
- La bandeja de trabajo consolidada con pendientes y SLA es SP5 (sprint 3); aquí solo existe la localización básica de solicitudes necesaria para consultar un timeline.
- La generación del PDF formal (SP3) y el sello electrónico con traza de firmas sobre documentos (SP4) son del sprint 2 y quedan fuera.
- Las notificaciones al estudiante (SP7) quedan fuera; las transiciones solo generan entradas en el timeline interno.
- Class y QF son sistemas externos (cajas negras): el resultado final lo asienta un humano donde corresponda; no hay integración técnica.
