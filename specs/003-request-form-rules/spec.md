# Feature Specification: Formularios validados y reglas de negocio por trámite

**Feature Branch**: `003-request-form-rules`

**Created**: 2026-09-03

**Status**: Draft

**Input**: SP2 del árbol de problemas — captura estructurada del formulario de cada trámite y reglas de negocio configurables por definición, sin que el motor deje de ser genérico. Cierra el Sprint 1 (issue #9).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar una solicitud con el contenido real del formulario (Priority: P1)

Hoy la Coordinación registra una solicitud con el nombre y la cédula del estudiante, y el resto del
formulario —programa, semestre, motivo y las asignaturas involucradas— sigue viviendo en el Word
adjunto al correo. Esta historia lleva ese contenido al sistema: al registrar una solicitud, la
Coordinación captura los datos del estudiante y la lista de asignaturas del trámite, cada una con su
código, nombre, créditos y —cuando el trámite lo requiere— la nota actual y la propuesta.

**Why this priority**: sin el contenido del formulario dentro del sistema, el trámite sigue
dependiendo del documento adjunto. Todo lo demás de este sprint —validar reglas, generar el PDF
formal más adelante— necesita que estos datos existan como datos, no como texto en un archivo.

**Independent Test**: se registra una solicitud de adición de créditos con dos asignaturas y una de
novedad de notas con una asignatura y su nota propuesta; ambas se consultan después y devuelven
íntegro lo capturado, sin recurrir a ningún archivo externo.

**Acceptance Scenarios**:

1. **Given** un trámite de adición de créditos configurado, **When** la Coordinación registra una
   solicitud con los datos del estudiante y dos asignaturas con sus créditos, **Then** la solicitud
   queda registrada en el estado inicial de su trámite y al consultarla devuelve las dos asignaturas
   con todos sus datos.
2. **Given** un trámite de novedad de notas configurado, **When** la Coordinación registra una
   solicitud con una asignatura, su nota actual y la nota propuesta, **Then** ambas notas quedan
   asociadas a esa asignatura dentro de la solicitud.
3. **Given** una solicitud registrada con tres asignaturas, **When** se consulta, **Then** devuelve
   las tres, conservando la correspondencia entre cada asignatura y sus propios datos.
4. **Given** un cliente que registra solicitudes con el formulario mínimo anterior a esta feature
   —solo trámite, nombre y cédula—, **When** envía una solicitud, **Then** esta se registra
   correctamente y sin asignaturas, sin que el cliente deba cambiar.
5. **Given** una solicitud ya registrada, **When** se intenta modificar cualquier dato de su
   formulario o de sus asignaturas, **Then** el sistema no lo permite y los datos capturados
   originalmente permanecen intactos.
6. **Given** una solicitud registrada con el formulario completo, **When** se consulta lo almacenado
   y lo devuelto, **Then** no figura ningún dato de contacto del estudiante en ninguno de los dos.
7. **Given** una petición sin autenticar, **When** intenta registrar una solicitud con estos datos o
   consultar los de una existente, **Then** el sistema la rechaza sin registrar nada y sin revelar
   el contenido de la solicitud.

---

### User Story 2 - Impedir que una solicitud viole el límite del trámite (Priority: P1)

La adición de créditos existe porque hay un tope de créditos que el estudiante quiere superar. La
Coordinación necesita que el sistema rechace, en el momento de la captura, una solicitud cuyo total
de créditos exceda el máximo definido para ese trámite — en lugar de descubrirlo más adelante en la
cadena de aprobación, cuando ya circuló por varias personas.

**Why this priority**: es la razón de ser de SP2. Una regla que no se aplica en la captura reaparece
como re-trabajo aguas abajo, que es precisamente la métrica que el árbol de problemas busca reducir.

**Independent Test**: con un tope configurado en un valor conocido, se envía una solicitud que lo
excede y otra que lo alcanza exactamente; la primera se rechaza indicando el límite y la segunda se
registra.

**Acceptance Scenarios**:

1. **Given** un trámite con un máximo de créditos configurado, **When** la Coordinación registra una
   solicitud cuyo total de créditos supera ese máximo, **Then** el sistema la rechaza indicando cuál
   es el límite y la solicitud no queda registrada.
2. **Given** el mismo trámite, **When** el total de créditos es exactamente el máximo configurado,
   **Then** la solicitud se registra.
3. **Given** el mismo trámite, **When** una solicitud declara una asignatura con créditos negativos o
   cero, **Then** el sistema la rechaza, y el valor no puede usarse para reducir el total y quedar
   por debajo del límite.
4. **Given** un trámite cuyo máximo de créditos **no** está configurado, **When** la Coordinación
   registra una solicitud para ese trámite, **Then** el sistema **rechaza la operación informando
   que la configuración está incompleta**, y en ningún caso la registra como si no hubiera límite.
5. **Given** un trámite con un rango de notas configurado, **When** una solicitud declara una nota
   fuera de ese rango o que no es un valor numérico, **Then** el sistema la rechaza indicando el
   rango admitido.
6. **Given** un trámite cuyo máximo de créditos está configurado con un valor no interpretable
   —un texto donde se espera un número, o un número no positivo—, **When** la Coordinación registra
   una solicitud, **Then** el sistema la rechaza señalando configuración incompleta, y **MUST NOT**
   comportarse como si el límite fuera cero ni rechazar la solicitud atribuyéndosela al usuario.
7. **Given** el formulario de un trámite, **When** la Coordinación envía un motivo que excede la
   longitud máxima admitida, **Then** el sistema rechaza la solicitud en la captura y no la registra
   truncada.

---

### User Story 3 - Ajustar una regla de negocio sin desplegar una versión nueva (Priority: P2)

Cuando la universidad cambie el tope de créditos, o cuando se confirme el valor real contra el
reglamento estudiantil, la Coordinación necesita que ese ajuste sea un cambio de configuración y no
un cambio de código con despliegue.

**Why this priority**: es la traducción de la tesis del trabajo —configurable por dato, no por
código— al terreno de las reglas de negocio. Sin esto, cada regla nueva es una modificación del
motor, que es exactamente lo que SP1 vino a evitar.

**Independent Test**: se cambia el valor del máximo de créditos en la configuración y, sin modificar
ni redesplegar la aplicación, una solicitud que antes se rechazaba pasa a aceptarse.

**Acceptance Scenarios**:

1. **Given** un trámite con un máximo de créditos configurado en un valor, **When** se ajusta ese
   valor en la configuración, **Then** las solicitudes registradas a partir de ese momento se validan
   contra el valor nuevo, sin modificar el código de la aplicación.
2. **Given** dos trámites distintos configurados, **When** cada uno define un máximo de créditos
   diferente, **Then** cada solicitud se valida contra el máximo de su propio trámite.
3. **Given** un trámite con dos versiones publicadas de su definición, **When** cada versión declara
   un máximo de créditos distinto, **Then** cada solicitud se valida contra el máximo de la versión
   vigente al momento de su registro, y ajustar la versión nueva no altera la validación de las
   solicitudes ya registradas.

---

### User Story 4 - Condicionar un paso del trámite a una regla de negocio (Priority: P3)

Un trámite necesita poder declarar que cierto paso solo procede si se cumple una regla de negocio
—por ejemplo, que la solicitud haya sido validada contra el tope de créditos—, sin que el motor
tenga que conocer de qué trámite se trata.

**Why this priority**: habilita las guardas que los sub-problemas siguientes van a necesitar, y
completa la decisión D3 de la feature 002, que difirió esta capacidad explícitamente a esta feature.
Se prioriza por debajo de las anteriores porque su valor se realiza en sprints posteriores.

**Independent Test**: se configura una transición asociada a una regla nombrada; la transición
procede cuando la regla se cumple y se bloquea cuando no, sin que el motor incorpore conocimiento del
trámite concreto.

**Acceptance Scenarios**:

1. **Given** una transición configurada con una regla de negocio asociada, **When** la solicitud
   cumple esa regla, **Then** la transición procede normalmente.
2. **Given** la misma transición, **When** la solicitud no cumple la regla, **Then** la transición se
   bloquea sin alterar el estado de la solicitud, e informa qué regla no se cumplió.
3. **Given** una transición sin regla asociada, **When** se ejecuta, **Then** se comporta exactamente
   como las transiciones de la feature 002, sin validación adicional.
4. **Given** dos trámites distintos cuyas transiciones declaran la misma regla nombrada, **When**
   ambos se operan, **Then** la regla se evalúa correctamente en los dos sin que el motor incorpore
   conocimiento de ninguno de los dos trámites.
5. **Given** una transición que declara una regla que el sistema no reconoce, **When** se intenta
   ejecutar, **Then** la transición se bloquea señalando configuración inválida, y **MUST NOT**
   ejecutarse omitiendo la regla que no supo evaluar.

---

### Edge Cases

- **Solicitud sin asignaturas**: un trámite puede registrarse sin asignaturas (el cliente anterior a
  esta feature lo hace). La validación de créditos sobre una lista vacía da total cero y no debe
  rechazar la solicitud ni fallar.
- **Créditos negativos o cero**: no pueden compensar a otra asignatura para burlar el total.
- **Configuración incompleta**: un trámite sin su parámetro de negocio cargado no acepta solicitudes
  en silencio; falla de forma visible y atribuible a la configuración, no al usuario.
- **Configuración inválida**: un parámetro cargado con un valor no interpretable (texto donde se
  espera un número, o un número no positivo) se trata como configuración incompleta, no como límite
  cero.
- **Motivo desmedido**: un motivo que exceda la longitud admitida se rechaza en la captura.
- **Nota no numérica**: un valor de nota que no sea un número se rechaza indicando el rango admitido.
- **Asignatura duplicada**: dos asignaturas con el mismo código en una solicitud se aceptan; el
  sistema no deduplica, porque la Coordinación puede necesitar registrar dos grupos de la misma
  materia.

## Requirements *(mandatory)*

### Functional Requirements

#### Captura del formulario

- **FR-001**: El sistema MUST permitir registrar, junto con la solicitud, los datos del formulario del
  trámite: código del estudiante, programa, semestre y motivo de la solicitud. Todos son opcionales,
  de modo que una solicitud registrada con el formulario mínimo anterior siga siendo válida.
- **FR-002**: El sistema MUST permitir asociar a una solicitud una lista de asignaturas, cada una con
  su código, su nombre, sus créditos, su grupo, su nota actual y su nota propuesta, conservando la
  correspondencia entre cada asignatura y sus propios datos.
- **FR-003**: Las asignaturas MUST poder capturarse tanto en adición de créditos —donde importan los
  créditos— como en novedad de notas —donde importan las notas—, sobre la misma estructura y sin
  campos a medida por trámite.
- **FR-004**: El motivo de la solicitud MUST tener una longitud máxima admitida, y una solicitud que
  la exceda MUST ser rechazada en la captura.
- **FR-005**: Los datos del formulario MUST quedar inmutables una vez registrada la solicitud,
  coherente con la garantía de auditoría de la feature 002: corregir un dato capturado es registrar
  una devolución, no editar el registro.
- **FR-006**: Ampliar el formulario MUST NOT romper a los clientes que registran solicitudes con el
  contrato anterior a esta feature: una solicitud enviada con los datos mínimos MUST seguir
  registrándose correctamente.

#### Reglas de negocio configurables

- **FR-007**: Cada trámite MUST poder declarar sus parámetros de negocio como configuración asociada a
  su definición, de modo que agregar o ajustar un parámetro no requiera modificar el código de la
  aplicación.
- **FR-008**: El sistema MUST validar que el total de créditos de una solicitud no supere el máximo
  configurado para su trámite, y MUST rechazar la solicitud que lo supere indicando el límite.
- **FR-009**: Los créditos de una asignatura MUST ser un valor positivo y acotado. El sistema MUST
  rechazar una asignatura con créditos nulos, cero o negativos, de modo que ningún valor pueda restar
  del total y situar una solicitud excedida por debajo del límite.
- **FR-010**: Cuando el parámetro de negocio que una validación necesita no está configurado para ese
  trámite, el sistema MUST rechazar la operación señalando que la configuración está incompleta.
  El sistema MUST NOT registrar la solicitud como si el límite no existiera.
- **FR-011**: Un parámetro configurado con un valor no interpretable MUST tratarse igual que un
  parámetro ausente (FR-010), y MUST NOT interpretarse como un límite de valor cero.
- **FR-012**: El rango admitido de notas MUST ser configuración del trámite, del mismo modo que el
  máximo de créditos, y MUST NOT estar fijado en el código. El sistema MUST rechazar una nota fuera
  de ese rango o que no sea un valor numérico.
- **FR-013**: Los parámetros de negocio MUST seguir el versionado de definiciones establecido en la
  feature 002: cada solicitud se valida contra los parámetros de la versión vigente al momento de su
  registro, y ajustar una versión posterior MUST NOT alterar la validación de las solicitudes ya
  registradas.
- **FR-014**: Dos trámites distintos MUST poder declarar valores distintos para el mismo parámetro, y
  cada solicitud MUST validarse contra el valor de su propio trámite.

#### Guardas de transición

- **FR-015**: Una transición de un trámite MUST poder declarar que su ejecución está condicionada a
  una regla de negocio, identificándola por nombre.
- **FR-016**: El motor MUST evaluar esa regla antes de ejecutar la transición y MUST bloquearla sin
  alterar el estado de la solicitud cuando la regla no se cumple, informando cuál fue.
- **FR-017**: Una transición sin regla asociada MUST comportarse exactamente como en la feature 002,
  sin validación adicional.
- **FR-018**: El motor MUST conocer únicamente el nombre de la regla, no el trámite al que pertenece:
  agregar un trámite que reutilice una regla existente MUST NOT requerir modificar el motor.
- **FR-019**: Una transición que declare una regla que el sistema no reconoce MUST bloquearse
  señalando configuración inválida. El sistema MUST NOT ejecutar la transición omitiendo una regla
  que no supo evaluar.

#### Minimización de datos y seguridad

- **FR-020**: El sistema MUST NOT almacenar el correo electrónico del estudiante en esta feature. Su
  único consumidor previsto es la notificación (SP7), fuera del alcance de este sprint; el dato entra
  cuando exista quien lo use. (Constitución §III.)
- **FR-021**: Solo la Coordinación autenticada MUST poder registrar solicitudes con estos datos y
  consultarlos. La garantía MUST verificarse sobre cada operación que esta feature introduce o
  modifica.
- **FR-022**: Los datos de estudiante usados en pruebas, ejemplos y documentación MUST ser
  inequívocamente sintéticos, sin forma de un documento de identidad real.

### Key Entities *(include if feature involves data)*

- **Solicitud** (existente, ampliada): incorpora los datos del formulario —código de estudiante,
  programa, semestre, motivo— y pasa a tener una colección de asignaturas. Su estado y su timeline
  siguen rigiéndose por la feature 002.
- **Asignatura de la solicitud** (nueva): una materia involucrada en un trámite, con su código,
  nombre, créditos, grupo, nota actual y nota propuesta. Pertenece a una única solicitud y no existe
  fuera de ella.
- **Parámetro de trámite** (nueva): un valor de configuración asociado a una definición de trámite y
  a su versión, identificado por un nombre único dentro de esa definición. Es el mecanismo por el que
  una regla de negocio se ajusta sin tocar código.
- **Transición** (existente, ampliada): puede declarar el nombre de la regla de negocio que condiciona
  su ejecución.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % del contenido del formulario de un trámite del alcance queda registrado como
  datos consultables en el sistema: responder qué asignaturas y cuántos créditos involucra una
  solicitud no requiere abrir el documento adjunto ni la cadena de correos.
- **SC-002**: El 100 % de las solicitudes que exceden el máximo de créditos de su trámite se rechazan
  en el momento de la captura, antes de entrar a la cadena de aprobación.
- **SC-003**: Ninguna combinación de valores de créditos permite registrar una solicitud cuyo total
  real supere el máximo configurado.
- **SC-004**: El 100 % de los intentos de registrar una solicitud para un trámite con configuración
  de negocio incompleta se rechazan de forma visible; ninguno queda registrado sin validar.
- **SC-005**: Cambiar el máximo de créditos de un trámite requiere únicamente ajustar su
  configuración: sin modificar el código de la aplicación ni desplegar una versión nueva. El valor
  nuevo rige para las solicitudes registradas a partir de ese momento.
- **SC-006**: Un trámite adicional que reutilice reglas ya existentes queda operable cargando su
  configuración, sin modificar el motor.
- **SC-007**: Los clientes que registran solicitudes con el formulario anterior a esta feature siguen
  funcionando sin cambios.
- **SC-008**: Ningún dato de contacto del estudiante queda almacenado por esta feature.

## Assumptions

- **Las asignaturas son opcionales en el registro.** Una solicitud sin asignaturas es válida: es lo
  que hace el cliente anterior a esta feature, y FR-006 exige preservarlo. Un total de cero créditos
  no viola ningún máximo positivo.
- **El rango de notas se configura por trámite**, con el mismo mecanismo que el máximo de créditos.
  Se descartó fijarlo globalmente porque reintroduciría en el motor una constante de dominio, que es
  lo que SP1 vino a eliminar.
- **La escala de calificación aplicable es la de la institución.** Su valor concreto se carga como
  configuración y comparte la condición de provisionalidad descrita más abajo.
- **Las asignaturas duplicadas no se deduplican**: dos grupos de la misma materia son un caso real de
  la Coordinación, y el sistema no tiene fuente para decidir que un duplicado es un error.
- **La carga de la configuración de negocio se hace por el mismo mecanismo que la configuración de
  trámites de la feature 002.** Esta feature no introduce una interfaz de administración: SC-005 se
  satisface con el mecanismo existente.
- **`prioridad` y `fecha de vencimiento` quedan fuera de esta feature.** Se difieren a SP5, coherente
  con el recorte ya decidido: no tienen respaldo en las entrevistas, y su consumidor natural es la
  bandeja de trabajo del Sprint 3.

## Respaldo normativo pendiente

Conforme a la constitución v2.2.2 §IV, se declara explícitamente el estado de la evidencia:

- **El máximo de créditos queda marcado como provisional y no auditado.** El valor que se cargará
  como configuración inicial tiene respaldo **derivado** —aparece en la síntesis analítica de la
  tercera sesión de entrevista, no en un transcript crudo—, y el respaldo normativo primario es el
  **reglamento estudiantil, que aún no se ha obtenido**. Los transcritos confirman que **existe** un
  tope, no **cuál** es.
- El diseño mitiga el riesgo por construcción: el valor vive en configuración y se ajusta sin tocar
  código (FR-007, SC-005). Cuando se obtenga el reglamento, corregirlo es un cambio de configuración,
  no una modificación de esta feature.
- Ninguna afirmación de este documento debe presentarse como confirmada contra normativa institucional
  mientras esa fuente no se obtenga.

## Límites declarados del alcance

- Esta feature **no** genera el documento formal del trámite (SP3) ni sella su traza de aprobaciones
  (SP4): produce los datos que esos sub-problemas van a consumir.
- Esta feature **no** notifica al estudiante (SP7) ni construye la bandeja de trabajo de la
  Coordinación (SP5).
- Las reglas que esta feature habilita son de **captura**: se evalúan al registrar la solicitud y al
  ejecutar una transición condicionada. No introduce reglas periódicas ni evaluaciones diferidas.
