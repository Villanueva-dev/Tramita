# Feature Specification: Catálogo de programas y anexo exigido por programa

**Feature Branch**: `009-program-catalog-annex`

**Created**: 2026-09-25

**Status**: Aprobada — gate `review-spec` superado y auditada contra las fuentes el 2026-09-25

**Input**: issue [#40](https://github.com/Villanueva-dev/Tramita/issues/40), milestone «Sprint 3 — Operación diaria», más el issue [#50](https://github.com/Villanueva-dev/Tramita/issues/50) del catálogo de programas, abierto con esta spec. Dos decisiones del usuario la enmarcan: la regla del anexo es **por programa**, no por facultad (2026-09-21), y el programa deja de escribirse a mano y **se elige de una lista cerrada** que el sistema ofrece (2026-09-25).

## User Scenarios & Testing *(mandatory)*

Actor único de esta feature: la **Coordinación Académica de la Sede Cali**, sin roles. El estudiante diligencia el formulario público sin cuenta y no es usuario del sistema: elige su programa de la lista, y esa es toda su participación.

**Contexto medido** el 2026-09-25 en la base de desarrollo: de 61 solicitudes, 26 declaran programa, escrito de **cuatro formas**: «Ingeniería de Sistemas» (20), «Ing» (3), «Sistemas» (2) y «Programa de Prueba» (1). Tres de las cuatro formas nombran el mismo programa, y seis solicitudes —las «Ing», las «Sistemas» y la de prueba— no coincidirían con ningún nombre de un catálogo. Una regla por programa aplicada sobre texto libre fallaría en silencio en 6 de 26 solicitudes.

### User Story 1 - Elegir el programa de una lista cerrada (Priority: P1)

El estudiante, en el formulario público, y la Coordinación, en el formulario interno, eligen el programa académico de una lista que el sistema ofrece, en vez de escribirlo. El sistema no registra una solicitud nueva cuyo programa no esté en esa lista.

**Why this priority**: es la condición de la historia 2. Una regla por programa solo es confiable si el programa es un valor controlado, y hoy no lo es: el enlace público lo recibe como texto libre de hasta 120 caracteres, y el formulario interno del cliente ofrece una lista de cinco programas sin fuente, que incluye Psicología, un programa que la Coordinación no nombró entre los de la sede. Sin esta historia, la historia 2 se construye sobre arena.

**Independent Test**: se pide la lista sin sesión y se obtienen los programas del catálogo. Después se envía, por cada canal, un programa de la lista (se acepta), uno fuera de ella («Psicología») y una variante de escritura de uno que sí está («ingenieria de sistemas»); los dos últimos se rechazan nombrando el campo.

**Acceptance Scenarios**:

1. **Given** el formulario público, sin sesión, **When** pide la lista de programas, **Then** recibe los nombres de los programas del catálogo, y nada más.
2. **Given** un programa del catálogo, **When** el estudiante lo envía por el enlace público, **Then** la solicitud se registra con ese nombre tal cual.
3. **Given** un programa que no está en el catálogo, **When** se envía por el enlace público, **Then** se rechaza nombrando el campo y sin repetir el valor recibido, con el mismo mecanismo con que ese canal ya informa los campos faltantes o inválidos.
4. **Given** un nombre que difiere del catálogo solo en mayúsculas, tildes o abreviaturas («ingenieria de sistemas», «Ing. de Sistemas»), **When** se envía, **Then** se rechaza: la coincidencia es exacta con el nombre que el sistema publica.
5. **Given** el formulario interno sin programa, **When** la Coordinación registra la solicitud, **Then** se acepta: en ese canal el programa sigue siendo opcional.
6. **Given** el formulario interno con un programa fuera del catálogo, **When** la Coordinación registra, **Then** se rechaza nombrando el campo.
7. **Given** una solicitud registrada antes de esta feature con el programa escrito a mano («Ing»), **When** la Coordinación la consulta o la mueve de estado, **Then** el programa se muestra tal como se escribió, y ninguna de esas acciones la rechaza ni la reescribe.

---

### User Story 2 - Saber que el programa exige un anexo al reenviar a la facultad (Priority: P2)

Cuando la Coordinación consulta una solicitud de un programa para el que el trámite exige un anexo, el detalle se lo dice: qué documento hay que adjuntar al reenviar a la facultad y de dónde lo obtiene el estudiante. Hoy el único caso es la adición de créditos de **Ingeniería de Sistemas**, que exige la **hoja de vida académica**, que el estudiante descarga desde CLASS. La Coordinación la pide, la recibe por su correo y la adjunta al reenviar, como hoy; lo nuevo es que el sistema se lo recuerda.

**Why this priority**: es el issue #40, y lo pidió la Coordinación: *«a solo sistemas me pide que […] anexe la hoja de vida académica […] las otras facultades, yo les mando solo el formato firmado y listo»* (Entrevista 1, `material-coord/evidencia-entrevistas-coordinacion.md:184`; la tabla de anexos de la misma síntesis lo resume como «Hoja de vida académica (solo Ingeniería de Sistemas)», `:762`; que la descarga el estudiante desde CLASS está en `:553`). Comprobado en vivo el 2026-09-19: una adición de créditos de Ingeniería de Sistemas avanza a la facultad sin que nada se lo recuerde a nadie. Y el re-trabajo que esto evita ya está modelado en la suite: la devolución de ejemplo de `RequestControllerIT.java:834` dice, literalmente, «Falta la hoja de vida académica». Va después de la historia 1 porque depende de que el programa sea un valor controlado.

**Independent Test**: con la regla configurada para un programa, se consulta una solicitud de ese programa (muestra el requisito), otra de otro programa del mismo trámite (no muestra nada) y se lleva la primera a la facultad (la respuesta de esa acción trae el requisito).

**Acceptance Scenarios**:

1. **Given** una solicitud de adición de créditos de Ingeniería de Sistemas, en cualquier estado, **When** la Coordinación la consulta, **Then** el detalle indica que hay que anexar la hoja de vida académica y que la descarga el estudiante desde CLASS, con el texto que da la configuración, desde el momento del registro.
2. **Given** una solicitud de otro programa del mismo trámite, **When** la Coordinación la consulta, **Then** el detalle no trae ningún requisito de anexo.
3. **Given** la solicitud del escenario 1, **When** la Coordinación la lleva a la facultad, **Then** la respuesta de esa misma acción trae el requisito, sin una segunda consulta.
4. **Given** un trámite sin regla de anexo configurada (hoy, la novedad de notas), **When** se consulta cualquiera de sus solicitudes, **Then** no hay requisito, sea cual sea el programa.
5. **Given** que la configuración incorpora otro programa con otro anexo, u otro trámite con esta regla, sin desplegar código, **When** se consulta una solicitud de ese programa, **Then** el requisito aparece igual que en el escenario 1.
6. **Given** una solicitud anterior a esta feature cuyo programa no coincide exactamente con el catálogo («Sistemas»), **When** se consulta, **Then** no muestra requisito, y eso se acepta.
7. **Given** el requisito visible, **When** la Coordinación adjunta el documento a su correo, **Then** el sistema no registra nada: no sabe si se adjuntó, y no lo afirma.

---

### Edge Cases

- **Catálogo vacío**: si no hubiera ningún programa, ningún valor sería válido y el enlace público no podría radicar. Es un fallo cerrado y se acepta: el catálogo entra sembrado con la feature, y un test fija que la siembra no está vacía.
- **Renombrar un programa**: las solicitudes ya radicadas conservan el nombre que se escribió, porque el formulario radicado es inmutable (004). La regla de anexo tiene que seguir al programa renombrado, no al nombre viejo; las solicitudes viejas dejan de coincidir, como en el escenario 6 de la historia 2.
- **Programa que deja de ofrecerse**: se quita del catálogo como dato. Las solicitudes que lo citan conservan el texto. No hay noción de «programa inactivo» en esta feature.
- **Dos programas con el mismo nombre**: el catálogo no lo admite. El nombre es lo que identifica al programa ante el estudiante y ante la regla.
- **Tildes y eñes**: el nombre viaja tal como el sistema lo publica; un cliente que lo altere por el camino (otra normalización de caracteres, espacios de más) verá el rechazo del escenario 4. Diez de los trece nombres llevan tilde; solo Derecho, Medicina y Medicina Veterinaria no la llevan.
- **La facultad no cuenta**: el campo de facultad sigue siendo texto escrito y la regla lo ignora. Un estudiante de Ingeniería de Sistemas que escriba otra facultad recibe igual el requisito, porque la Coordinación fijó la condición por programa.
- **Lista provisional**: si la Coordinación corrige la lista, se corrige el dato. Esta spec no cambia.
- **Dónde se ve el requisito**: solo en el detalle de la solicitud. La bandeja y el resumen no lo llevan; nadie lo pidió ahí, y el detalle es donde la Coordinación decide reenviar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST ofrecer la lista de programas académicos del catálogo a quien la pida, **sin sesión**, porque el formulario público la necesita. La lista contiene solo los nombres de los programas: ningún dato personal, ningún conteo, nada derivado de las solicitudes (Principio III).
- **FR-002**: El canal público MUST rechazar una solicitud cuyo programa no coincida con un programa del catálogo. El rechazo nombra el campo y nunca repite el valor recibido, con el mismo mecanismo con que ese canal ya informa campos faltantes o inválidos (004).
- **FR-003**: El canal interno MUST mantener el programa como opcional. Si viene, MUST aplicar la misma regla que el público y nombrar el campo al rechazarlo. Es el criterio «si viene, tiene forma» que la 008 fijó para el teléfono (`specs/008-student-closure-notice/spec.md:98`, FR-010).
- **FR-004**: La coincidencia entre el programa declarado y el catálogo MUST ser por **igualdad exacta** del nombre que el sistema publica. El sistema MUST NOT normalizar mayúsculas, tildes, espacios ni abreviaturas: la lista la ofrece él mismo, así que una diferencia solo puede venir de un cliente que escribió por su cuenta.
- **FR-005**: El sistema MUST conservar el programa tal como se declaró, sin reescribirlo, y MUST NOT modificar las solicitudes ya radicadas. La regla de los FR-002 y FR-003 se aplica **solo al registrar**: consultar o mover una solicitud anterior con un programa fuera del catálogo MUST seguir funcionando (precedente FR-011 de la 008).
- **FR-006**: El catálogo de programas MUST administrarse como dato: incorporar, renombrar o quitar un programa MUST NOT requerir desplegar código. Entra con los trece programas de la sede que dictó la Coordinación (ver Assumptions).
- **FR-007**: La configuración de cada trámite MAY declarar, **por programa**, un anexo exigido al reenviar a la facultad: el nombre del documento y una indicación de dónde lo obtiene el estudiante. Un trámite sin reglas, o un programa sin regla en su trámite, no produce ningún requisito.
- **FR-008**: Una regla de anexo MUST referirse a un programa del catálogo. No puede existir una regla sobre un programa que el catálogo no conoce.
- **FR-009**: El detalle de una solicitud MUST exponer el requisito de anexo cuando la configuración de su trámite lo declare para su programa, **desde el registro y en cualquier estado**, en la respuesta de cada acción que devuelve el detalle: registrarla, moverla y consultarla. Quien lo consume decide cuándo destacarlo, sin otra consulta (patrón de la 008: el sistema expone hechos y el cliente decide, `specs/008-student-closure-notice/research.md:224`, D5).
- **FR-010**: El requisito MUST derivarse solo del programa de la solicitud y de la configuración de su trámite. El sistema MUST NOT condicionarlo a un estado concreto ni reconocer códigos de estado para decidirlo (Principio VI).
- **FR-011**: Incorporar otro programa con otro anexo, u otro trámite con esta regla, MUST hacerse sin desplegar código, y un test MUST demostrarlo (criterio de cierre del #40; Principio VI).
- **FR-012**: El sistema MUST NOT recibir ni almacenar el anexo ni ningún otro archivo, y MUST NOT registrar ni afirmar que el anexo se adjuntó. Adjuntarlo sigue siendo un acto humano de la Coordinación en su correo. Es coherente con la 006: *«Trámita no es un repositorio de archivos»* (`specs/006-verifiable-document-seal/spec.md:107`, FR-010), y con la minimización del Principio III: la hoja de vida académica contiene todas las notas del estudiante.
- **FR-013**: El cambio en la regla del programa MUST declararse como **enmienda no aditiva** de dos contratos: el de captura pública (004), que hoy acepta cualquier texto de hasta 120 caracteres, y el de registro interno. Se sigue el precedente de la 008 (`specs/008-student-closure-notice/spec.md:101`, FR-013). La lista de programas y el requisito de anexo en el detalle MUST ser aditivos: un cliente actual sigue funcionando sin cambios.

### Key Entities

- **Programa académico (catálogo)**: un programa de pregrado que la Sede Cali ofrece, identificado por su nombre publicado, único. Es dato, no código. Entra con trece programas.
- **Regla de anexo**: por trámite y programa, el nombre del documento que la facultad exige al reenviar y una indicación de dónde lo obtiene el estudiante. Vive en la configuración del trámite, junto a los demás parámetros del motor.
- **Requisito de anexo**: el hecho que el detalle expone cuando la regla aplica a la solicitud. **No se almacena**: se deriva en el momento del programa y la configuración, y no deja rastro en el historial.
- **Programa declarado**: el nombre del programa tal como quedó en la solicitud al radicarla. Es inmutable, como el resto del formulario.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El **100 %** de las solicitudes nuevas, por los dos canales, quedan registradas con un programa del catálogo o sin programa (solo en el interno); **ninguna** queda con un texto fuera de la lista.
- **SC-002**: En el conjunto de prueba, el requisito de anexo aparece en el **100 %** de las solicitudes cuyo trámite lo configura para su programa, desde su registro, y en el **0 %** de las demás.
- **SC-003**: Incorporar otro programa con otro anexo, u otro trámite con esta regla, no requiere desplegar código, y un test lo demuestra. Es la tesis del proyecto verificada sobre esta feature.
- **SC-004**: La lista de programas contiene **cero** datos personales y **cero** datos derivados de solicitudes.
- **SC-005**: **Ninguna** solicitud anterior se modifica, y todas siguen consultables y movibles de estado.
- **SC-006**: Al reenviar a la facultad, la Coordinación tiene el requisito a la vista **sin ninguna consulta adicional**: viene en la respuesta de la acción que mueve la solicitud.

## Assumptions

Salvo que se indique lo contrario, lo que sigue son **hechos medidos** el 2026-09-25. Los supuestos a validar van marcados como tales.

- **La lista de programas es PROVISIONAL**. La dictó el usuario el 2026-09-25 a partir de lo que le indicó la Coordinación, sin documento de respaldo, y la Coordinación **no la ha confirmado por escrito**. Son trece: Administración de Negocios, Administración de Empresas, Contaduría Pública, Derecho, Enfermería, Ingeniería Ambiental, Ingeniería de Sistemas, Ingeniería en Seguridad y Salud en el Trabajo, Ingeniería Industrial, Medicina, Medicina Veterinaria, Nutrición y Dietética, Química Farmacéutica. Según el Principio IV, lo que dependa de normativa institucional sin documento se marca como provisional y no auditado (`.specify/memory/constitution.md:289`); como el catálogo vive como dato (FR-006), confirmar o corregir la lista después es cambiar filas, no desplegar. Cruce con la web de la universidad, con **confianza media** (se leyeron resúmenes de las páginas, no su contenido íntegro): la [oferta de programas](https://www.uniremington.edu.co/programas/) publica Administración de Negocios y Administración de Empresas como programas **distintos**, así que no es un duplicado; la [página de la Sede Cali](https://www.uniremington.edu.co/cali/) no nombra programas y lista seis facultades (Ciencias Empresariales, Ciencias Contables, Ingenierías, Diseño, Ciencias Jurídicas y Políticas, Medicina Veterinaria), **sin una de Salud** —a la que pertenecerían Enfermería, Medicina, Nutrición y Química— y **con Diseño**, de la que la lista no trae ninguno. **Supuesto a validar con la Coordinación**: que la lista dictada es la oferta de pregrado de la sede. Psicología, que está en la lista actual del cliente, no aparece en la dictada.
- **Por qué por programa y no por facultad**: la Coordinación dijo «solo sistemas», no «solo ingeniería» (`evidencia-entrevistas-coordinacion.md:184`), y su tabla de anexos lo escribe como «solo Ingeniería de Sistemas» (`:762`). El issue #40 nombra la regla por facultad; el usuario resolvió la ambigüedad el 2026-09-21 a favor del programa. Con una regla por facultad, los demás programas de ingeniería habrían recibido un requisito que la Coordinación no pidió.
- **Solo la adición de créditos tiene regla hoy. Supuesto a validar**: la Coordinación habló del anexo al describir la adición de créditos. De la novedad de notas no consta ningún anexo por programa, así que ese trámite entra sin reglas (escenario 4 de la historia 2). La novedad sí tiene anexos, pero **universales**, no por programa: planilla de asistencia, planilla de notas y recibo de pago (`:762`). Esta feature no los modela: el gate `review-spec` del 2026-09-25 los dejó fuera. La regla del #40 es por programa, no consta que esos tres anexos se olviden —el re-trabajo medido es solo la hoja de vida (`RequestControllerIT.java:834`)—, y una regla «para todos los programas» puede agregarse después de forma aditiva, con issue propio si la Coordinación la pide.
- **El requisito es un hecho de la solicitud, no de un estado**: se muestra desde el registro porque la Coordinación tiene que pedirle el documento al estudiante **antes** de reenviar, y es el estudiante quien lo descarga (`:553`). El criterio de cierre del #40 dice «al pasar a la facultad»; exponerlo siempre lo cumple —también viene en la respuesta de esa acción (FR-009)— y no obliga a que el sistema reconozca ningún estado (FR-010). Mostrarlo solo en un estado exigiría que el motor supiera cuál es «la facultad», que es justo lo que el Principio VI le quita.
- **Coincidencia exacta, sin normalizar**: cuando el programa era texto libre, comparar normalizando (sin mayúsculas ni tildes) era la única defensa. Con la lista ofrecida por el sistema, normalizar solo escondería clientes que escriben por su cuenta. Las variantes medidas hoy («Ing», «Sistemas») no se resuelven con normalización de todos modos.
- **Lo ya radicado no se toca**: 26 solicitudes de la base de desarrollo tienen programa; 6 no coincidirían con el catálogo (tres «Ing», dos «Sistemas», un «Programa de Prueba»). Se conservan tal cual y no reciben requisito (FR-005). Es el mismo criterio que la 008 aplicó a los teléfonos anteriores a su regla.
- **Enmienda no aditiva y orden de despliegue**: hoy el cliente público envía el programa escrito por el estudiante, y el interno envía uno de su lista de cinco, de los que cuatro coinciden letra por letra con la lista dictada y uno, Psicología, no. Si el backend llega antes que el cliente, el enlace público rechazará todo programa que el estudiante no escriba exactamente como el catálogo, y el interno rechazará una de sus cinco opciones. El riesgo real está en el canal público. El cliente tiene que reemplazar los dos campos por la lista del catálogo **antes o a la vez**. El reparto exacto con el repositorio del frontend, que lleva otro agente, se fija en el plan; el precedente es el brief de la 008 (`tramita-frontend#59`).
- **La facultad, la sede y la modalidad siguen como campos escritos**, como los pide la plantilla oficial DO-FR-100 v2024, que la 004 decidió respetar en sus once campos (`specs/004-public-request-capture/research.md:414`, D10). Derivar la facultad del programa queda fuera: no se tiene el mapa programa→facultad de la sede, y agregarlo después es aditivo. **Volver seleccionable el programa se aparta de ese instrumento**, que lo pide escrito; es defendible por la calidad del dato, y el plan debe justificarlo en su research, no hacerlo en silencio.
- **El catálogo se administra como dato, sin pantalla**: igual que los demás parámetros del motor. Construir una administración de catálogo para trece filas que cambian cada varios años contradice el Principio I.
- **El sistema no recibe archivos**: el canal público recibe once campos de texto y ninguno es un archivo, y la 006 fijó que Trámita no es un repositorio de archivos (FR-012). CLASS y QF siguen siendo cajas negras.
- **Enmienda del issue #40**: su cuerpo se rectificó el 2026-09-25, antes de abrir la PR, con la convención del #42: bloque «Rectificado» arriba y el texto original en un desplegable. La regla es por programa, no por facultad, y el nombre `REQUIRED_ANNEX_BY_FACULTY` que proponía quedó desalineado. El criterio «al pasar a la facultad» pasó a «desde el registro y en cualquier estado» (FR-009). Su referencia a que el #25 «sigue abierto» había vencido: se cerró el 2026-09-21. Las líneas de la entrevista que citaba (`:184`, `:551`, `:760`) hoy son `:184`, `:553` y `:762`. Y atribuía al FR-010 de la 006 el literal «nunca recibe el archivo», que vive en su `research.md:379` y `contracts/openapi.yaml:104`; el FR-010 dice «Trámita no es un repositorio de archivos».
- **Issue del catálogo**: no existía al escribir esta spec; se abrió con ella como [#50](https://github.com/Villanueva-dev/Tramita/issues/50) (2026-09-25, Sprint 3). La PR de la feature cierra #40 y #50.
- **Alcance**: Sede Cali, programas de pregrado y los dos trámites del MVP. Quedan fuera: recibir o almacenar cualquier archivo; administrar el catálogo desde la interfaz; derivar la facultad del programa; un catálogo por sede; posgrados, tecnologías y técnicos; los tres anexos universales de la novedad de notas (planilla de asistencia, planilla de notas y recibo de pago); la ventana temporal (#42) y el tope de créditos (#17).
