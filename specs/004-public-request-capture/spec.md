# Feature Specification: Captura pública del formato DO-FR-100

**Feature Branch**: `004-public-request-capture`

**Created**: 2026-09-15

**Status**: Draft

**Input**: Reunión con la Coordinación Académica de la Sede Cali del 2026-09-15: la Coordinación aceptó que el formato de adición de créditos lo diligencie el estudiante desde un enlace público, con firma en pantalla, en vez de diligenciarlo ella; y pidió «darse cuenta» cuando llega una solicitud nueva, sin especificar la forma.

## Contexto

Hoy el estudiante diligencia el formato oficial en un documento de texto y lo envía por
correo. La Coordinación lo recibe, lo revisa y lo transcribe. Ese paso de transcripción no
aporta nada al trámite: reintroduce a mano datos que el estudiante ya escribió, y es donde
aparecen los errores que obligan a devolver la solicitud.

Esta feature **no le da al estudiante una ventana al sistema**. En las entrevistas previas
la Coordinación rechazó explícitamente un portal donde el estudiante consultara el estado
de su trámite. Diligenciar el formato es otra cosa: el estudiante ya lo llena hoy. Lo que
cambia es el medio por el que lo entrega, no quién lo llena ni qué puede ver después.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El estudiante entrega el formato firmado sin tener cuenta (Priority: P1)

Un estudiante que necesita matricular por encima del tope de créditos de su nivel abre un
enlace público, diligencia los datos del formato oficial, firma trazando en la pantalla y
envía. Recibe una confirmación de que su solicitud llegó. No necesita usuario, contraseña
ni instalar nada.

**Why this priority**: Es la feature. Sin esto no hay nada que mostrar ni que medir, y el
paso de transcripción manual —origen de los errores que devuelven el trámite— sigue
existiendo. Entrega valor por sí sola: la solicitud queda registrada con los datos tal
como el estudiante los escribió.

**Independent Test**: Abrir el enlace en una sesión limpia, sin haber iniciado sesión
nunca, diligenciar el formato, firmar y enviar. La solicitud queda registrada y localizable
por la Coordinación con los medios que ya tiene.

**Acceptance Scenarios**:

1. **Given** un visitante sin sesión iniciada, **When** diligencia los datos obligatorios
   del formato, firma y envía, **Then** el sistema registra la solicitud y le devuelve una
   confirmación de recepción.
2. **Given** un visitante sin sesión, **When** envía el formato sin haber firmado,
   **Then** el sistema rechaza el envío indicando que falta la firma, y no registra nada.
3. **Given** un visitante sin sesión, **When** envía el formato con algún dato obligatorio
   vacío, **Then** el sistema rechaza el envío nombrando el dato faltante, y no registra nada.
4. **Given** una solicitud recibida por este canal, **When** la Coordinación consulta su
   historial, **Then** el historial nombra que la solicitud se originó en el canal público,
   igual que nombra un responsable en cualquier otro tramo.
5. **Given** un visitante sin sesión, **When** intenta que su envío corresponda a un
   trámite distinto al de adición de créditos, **Then** el sistema lo registra como
   adición de créditos de todos modos: el tipo de trámite no lo decide quien envía.
6. **Given** una solicitud recibida por este canal, **When** el estudiante recibe la
   confirmación, **Then** la confirmación **no** contiene identificador de la solicitud,
   estado, ni enlace para consultarla después.

---

### User Story 2 - La Coordinación se entera de que llegó algo nuevo (Priority: P2)

La Coordinación abre el sistema y ve, sin tener que buscar nada, las solicitudes recibidas
más recientemente. Así se entera de que un estudiante envió un formato sin que nadie se lo
avise por otro medio.

**Why this priority**: Sin esto, una solicitud que entra por el canal público es invisible:
hoy solo se puede encontrar una solicitud buscando por el nombre o la cédula del
estudiante, y no se puede buscar a alguien de cuya solicitud nadie se enteró. Va después de
la US1 porque sin solicitudes que lleguen solas, no hay de qué enterarse.

**Independent Test**: Registrar solicitudes por cualquier vía y comprobar que aparecen en
la vista de recientes sin escribir ningún criterio de búsqueda, con las más nuevas primero.

**Acceptance Scenarios**:

1. **Given** varias solicitudes registradas en distintos momentos, **When** la Coordinación
   abre la vista de recientes, **Then** las ve ordenadas de la más nueva a la más antigua,
   sin haber escrito ningún criterio de búsqueda.
2. **Given** la vista de recientes, **When** la Coordinación la consulta, **Then** **no**
   se muestra el número de documento de ningún estudiante.
3. **Given** un visitante sin sesión iniciada, **When** intenta consultar la vista de
   recientes, **Then** el sistema se lo niega.

---

### User Story 3 - El canal abierto resiste el abuso (Priority: P3)

Un canal que recibe solicitudes sin pedir credenciales queda expuesto a quien quiera
saturarlo. El sistema limita cuántos envíos acepta desde un mismo origen y cuán grande
puede ser un envío, y cuando corta lo dice con un error que el visitante entiende.

**Why this priority**: No entrega valor visible, pero su ausencia convierte la US1 en una
vía para llenar la base de datos o agotar la memoria del servidor sin credenciales. Se
puede construir y probar por separado de las otras dos.

**Independent Test**: Enviar repetidamente desde un mismo origen hasta superar el límite, y
enviar un formato desmesuradamente grande. Ambos deben ser rechazados con un error
explicativo, sin afectar a los envíos legítimos de otros orígenes.

**Acceptance Scenarios**:

1. **Given** un origen que ya superó el límite de envíos permitidos, **When** envía otro
   formato, **Then** el sistema lo rechaza indicando cuánto debe esperar antes de
   reintentar.
2. **Given** un envío cuyo contenido excede el tamaño máximo admitido, **When** llega al
   sistema, **Then** se rechaza sin llegar a procesarse.
3. **Given** un origen bloqueado por exceso de envíos, **When** transcurre el tiempo de
   espera, **Then** vuelve a poder enviar sin intervención de nadie.

---

### Edge Cases

- **Doble envío accidental**: el estudiante pulsa enviar dos veces, o reintenta porque no
  vio la confirmación. Se registran dos solicitudes y la Coordinación descarta la que
  sobra — ver «Riesgos aceptados».
- **Solicitud fuera de la ventana temporal**: la adición de créditos solo procede al inicio
  del período; un envío tardío se acepta y lo rechaza la Coordinación, como hoy (ver
  Assumptions).
- **El trámite no está configurado o su configuración está incompleta**: el sistema no
  puede aceptar la solicitud, y el error es del sistema, no del estudiante. Nunca se acepta
  en silencio una solicitud que no pudo validarse.
- **El estudiante quiere saber en qué va su trámite**: no hay forma por este canal, y es
  deliberado. Pregunta por correo a la Coordinación, como hoy.
- **Firma ilegible o un solo trazo**: el sistema la acepta; juzgar si una firma sirve es de
  quien recibe el documento, no del sistema.
- **El estudiante declara un correo con error de tipeo**: la solicitud se registra igual —
  la Coordinación tiene el nombre y el documento para ubicarlo—, pero pierde el canal de
  respuesta que el propio formato pide.

## Requirements *(mandatory)*

### Functional Requirements

**Recepción pública**

- **FR-001**: El sistema MUST aceptar solicitudes de adición de créditos de visitantes sin
  sesión iniciada ni cuenta de usuario.
- **FR-002**: El sistema MUST fijar por sí mismo el tipo de trámite de toda solicitud
  recibida por este canal; quien envía NO puede elegirlo ni alterarlo.
- **FR-003**: El sistema MUST rechazar, sin registrar nada, todo envío al que le falte
  alguno de los datos que el formato declara obligatorios, incluida la firma.
- **FR-004**: El sistema MUST conservar la firma trazada por el estudiante junto con la
  solicitud.
- **FR-005**: El sistema MUST conservar el correo electrónico y el número de contacto
  declarados, porque son el canal por el que la Coordinación responde.
- **FR-006**: El sistema MUST aplicar a las solicitudes recibidas por este canal las mismas
  reglas de negocio del trámite que aplica a las recibidas por cualquier otro.

**Confirmación al estudiante**

- **FR-007**: El sistema MUST confirmar al estudiante que su solicitud fue recibida.
- **FR-008**: La confirmación MUST NOT incluir identificador de la solicitud, su estado, ni
  ningún medio para consultarla posteriormente.

**Trazabilidad**

- **FR-009**: El historial de toda solicitud MUST nombrar un responsable en cada tramo,
  incluido el tramo inicial de una solicitud nacida sin usuario autenticado.
- **FR-010**: El sistema MUST permitir distinguir, mirando el historial, que una solicitud
  se originó en el canal público.
- **FR-011**: La identidad que el sistema atribuye a las solicitudes de origen público
  MUST NOT poder utilizarse para iniciar sesión.

**Vista de recientes**

- **FR-012**: El sistema MUST permitir a la Coordinación ver las solicitudes registradas
  más recientemente sin escribir ningún criterio de búsqueda.
- **FR-013**: Esa vista MUST presentar las solicitudes de la más reciente a la más antigua.
- **FR-014**: Esa vista MUST NOT exponer el número de documento de los estudiantes.
- **FR-015**: Esa vista MUST estar disponible únicamente para usuarios con sesión iniciada.

**Protección del canal abierto**

- **FR-016**: El sistema MUST limitar la cantidad de envíos que acepta desde un mismo
  origen dentro de una ventana de tiempo.
- **FR-017**: El sistema MUST rechazar los envíos cuyo contenido exceda un tamaño máximo,
  sin llegar a procesarlos.
- **FR-018**: Al rechazar por exceso de envíos, el sistema MUST indicar cuánto tiempo debe
  esperarse antes de reintentar.
- **FR-019**: El bloqueo por exceso de envíos MUST expirar por sí solo; NUNCA es permanente.

**Privacidad**

- **FR-020**: El sistema MUST NOT registrar en sus bitácoras de operación el contenido de
  los datos personales recibidos.
- **FR-021**: Ningún texto que el sistema muestre al estudiante o a la Coordinación MUST
  afirmar que la firma trazada tiene valor probatorio o validez legal.

### Key Entities

- **Solicitud de origen público**: una solicitud del trámite de adición de créditos, con
  los mismos datos que cualquier otra, más el correo y el contacto del estudiante y su
  firma trazada. Nace en el mismo estado inicial y recorre el mismo flujo.
- **Firma del estudiante**: el trazo que el estudiante dibuja al enviar. Es un dato de
  captura del formato, **no** una aprobación ni un sello verificable — esos pertenecen al
  circuito de firmas de los aprobadores, que es otro trabajo.
- **Identidad del canal público**: el responsable al que el sistema atribuye el tramo
  inicial de una solicitud que nadie autenticado registró. Existe para que el historial
  nunca tenga un tramo sin responsable, y no corresponde a ninguna persona.
- **Vista de recientes**: la lista de solicitudes registradas últimamente, sin criterio de
  búsqueda y sin números de documento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un estudiante que nunca usó el sistema completa y envía el formato en menos
  de 5 minutos, sin ayuda y sin crear ninguna cuenta.
- **SC-002**: La Coordinación identifica que llegó una solicitud nueva en menos de 10
  segundos desde que abre el sistema, sin escribir ningún criterio de búsqueda.
- **SC-003**: El 100 % de las solicitudes recibidas por el canal público conserva los datos
  exactamente como el estudiante los escribió, sin ningún paso de transcripción manual.
- **SC-004**: Cero números de documento aparecen en la vista de recientes.
- **SC-005**: El 100 % de las solicitudes tiene un responsable nombrado en todos los tramos
  de su historial, incluidas las de origen público.
- **SC-006**: Un envío masivo automatizado desde un mismo origen queda bloqueado antes de
  registrar más solicitudes de las que el límite permite, y los envíos desde otros orígenes
  siguen funcionando durante el bloqueo.

## Riesgos aceptados

### Los envíos duplicados se registran como solicitudes distintas — **decidido el 2026-09-15**

Si un estudiante envía el mismo formato dos veces —doble clic, o reintento porque no vio la
confirmación—, el sistema registra dos solicitudes.

**Decisión**: el sistema no intenta adivinar la intención de quien envía. Cada envío es una
solicitud.

**Por qué**: el caso que se evita es peor que el que se acepta. Si el sistema fusionara
envíos parecidos, un estudiante que corrigió un dato y reenvió a propósito creería haber
enviado la corrección sin haberla enviado — un fallo silencioso, del que nadie se entera
hasta que el trámite se devuelve. Un duplicado visible, en cambio, la Coordinación lo ve y
lo descarta, exactamente como hoy descarta el segundo de dos correos iguales.

Además, detectar repeticiones exige elegir una ventana de tiempo para la que no hay
evidencia: ni las entrevistas ni la normativa dicen cuánto tarda un estudiante en reenviar.
Sería un número inventado gobernando una regla de negocio.

**Consecuencia asumida**: la vista de recientes puede mostrar duplicados. Si en el piloto
resulta molesto, la mitigación no es fusionar en el servidor sino evitar el doble clic en el
formulario, que ataca la causa frecuente sin inventar reglas.

### El canal es anónimo y admite suplantación — **aceptado el 2026-09-15**

Un enlace público sin credenciales permite que cualquiera radique una solicitud a nombre de
cualquier estudiante, conociendo solo su nombre y su documento. El proceso actual también lo
permitiría, pero el correo institucional deja rastro de quién envió; un formulario anónimo no
deja ninguno.

**Decisión**: se acepta el riesgo para el MVP y se declara explícitamente, en lugar de exigir
una prueba de identidad.

**Por qué es defendible**: toda solicitud pasa por la revisión de la Coordinación antes de
seguir a la facultad — es el primer filtro del trámite, el mismo que la entrevista describe
como *«Yo reviso si está bien. Si está mal, se lo regreso»*. Una radicación falsa no avanza:
muere en esa revisión, igual que hoy moriría un correo falso. El canal no otorga ninguna
autorización por sí mismo; solo entrega un documento para que un humano lo evalúe.

**Qué se descartó y por qué**: exigir confirmación por correo institucional eliminaría el
anonimato, pero depende de infraestructura de envío de correo que no está disponible — la
misma que bloquea el aviso al estudiante (SP7). Habría estirado el MVP un sprint para cubrir
un riesgo que el filtro humano ya contiene.

**Límite de la decisión**: ⚠️ esto **no fue validado con la institución**. Es un juicio del
equipo sobre un riesgo institucional. Si la Coordinación o la tutora lo objetan, la mitigación
natural es la confirmación por correo, que entra sin rehacer el canal: se agrega un paso antes
de que la solicitud entre al motor.

## Assumptions

- **La ventana temporal del trámite no se valida por este canal.** La adición de créditos
  solo procede al inicio del período, pero la fecha exacta de corte no está confirmada con
  la Coordinación. Un envío tardío se registra y lo rechaza la Coordinación, que es lo que
  ocurre hoy. Cuando la fecha se confirme, la validación se agrega sin rehacer este canal.
- **El tope de créditos no se evalúa por este canal.** El formato oficial no tiene tabla de
  asignaturas ni campo de créditos: la asignatura viaja en prosa dentro del texto libre de
  compromisos. No es una carencia del sistema sino del documento, y quien valida el tope es
  la Coordinación.
- **El estudiante no es usuario del sistema.** No tiene cuenta, no inicia sesión, y no
  consulta el estado de su trámite. Su única interacción es entregar el formato.
- **La Coordinación entra al sistema con regularidad.** La vista de recientes cumple su
  función de aviso solo si alguien la mira; no hay aviso activo en esta feature.
- **El aviso por correo queda fuera.** Requiere infraestructura de envío de correo que no
  está disponible, y es un trabajo con su propio alcance.
- **La firma se conserva pero su validez legal no está confirmada.** No se obtuvo respuesta
  institucional sobre si una firma trazada tiene valor probatorio. Se conserva el trazo y se
  evita toda afirmación sobre su valor.
- **El formato de referencia es el DO-FR-100 versión 01 (plantilla 2024)**, el mismo que la
  Coordinación entregó como material de la entrevista.
