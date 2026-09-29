# Feature Specification: Conteos de solicitudes por trámite y estado

**Feature Branch**: `010-request-counts`

**Created**: 2026-09-28

**Status**: Aprobada — gate `review-spec` superado el 2026-09-29, con las citas re-medidas contra `main` (`09c7b6f`)

**Input**: issue [#58](https://github.com/Villanueva-dev/Tramita/issues/58), más la decisión de `tramita-frontend#51` (comentario del 2026-09-29): las tarjetas del tablero cuentan **toda la base**, ni la búsqueda ni la bandeja. El pedido es de la Coordinadora, transmitido por el propietario el 2026-09-28: que vuelvan las tarjetas de estadísticas con los trámites que ya existen en la base —cuántos terminaron, cuántos siguen en curso, cuántos se rechazaron—, porque le sirven para llevar a ojo el control de lo que maneja. El usuario eligió la **opción 1** del #58 al aprobar el plan (2026-09-28): el sistema expone conteos por estado y el cliente los clasifica.

## User Scenarios & Testing *(mandatory)*

**Contexto medido** el 2026-09-28 en las bases locales, contando por estado con los estados sin solicitudes incluidos:

- En `tramita-demo` hay 12 solicitudes repartidas en 14 estados (8 de la adición de créditos y 6 de la novedad de notas), y **5 de los 14 estados tienen 0**. Una lista que solo mostrara los estados con solicitudes perdería esas cinco tarjetas.
- Los dos trámites comparten dos códigos de estado. En esa misma base, `EN_FACULTAD` tiene 2 solicitudes de adición y 1 de novedad: contar solo por código diría 3, una cifra que no corresponde a ningún trámite. Es el defecto del prototipo de métricas de la rama `router-ia` (#58, «Lo que hay hoy»).
- En `tramita-db` existe además un trámite `DEMO` cargado por SQL durante una prueba, con una solicitud cerrada. Es el caso que el Principio VI promete: un trámite que se incorpora por configuración tiene que aparecer sin desplegar código.

Hoy ningún canal permite obtener estas cifras. La búsqueda exige un término (`RequestController.java:72-76`) y la bandeja exige un responsable, trae solo lo pendiente de ese responsable y corta en 200 (`:105-110`). El cliente retiró sus tarjetas (`tramita-frontend#56`, PR #93) porque contaban solo lo que el navegador tenía cargado.

### User Story 1 - Ver cuántas solicitudes hay en cada estado de cada trámite (Priority: P1)

La Coordinación abre el tablero y ve, para cada trámite, cuántas solicitudes están en cada estado, contando toda la base. Con esos conteos el cliente arma las tarjetas —terminadas, en curso, rechazadas— según su propia clasificación.

**Why this priority**: es la única historia y la pidió la Coordinadora. Sin ella las tarjetas no pueden volver: `tramita-frontend#51` deja escrito que *«el front no puede empezar hasta que el contrato de Tramita#58 quede fijo»*.

**Independent Test**: con sesión, se piden los conteos, se radica una adición de créditos y se la lleva a un estado final, y se piden otra vez. Solo cambia, en uno, el conteo de ese estado de ese trámite.

**Acceptance Scenarios**:

1. **Given** una sesión válida, **When** se piden los conteos, **Then** aparece cada trámite configurado con **todos** los estados de su versión vigente, cada uno con su código, su nombre, si es inicial, si es final y cuántas solicitudes están hoy en él; un estado sin solicitudes aparece con 0.
2. **Given** los conteos de un momento dado, **When** se radica una adición de créditos y se la lleva a `FINALIZADA`, **Then** en la consulta siguiente `FINALIZADA` de la adición sube en uno y ningún otro conteo cambia: una solicitud cuenta solo en su estado actual.
3. **Given** los conteos de un momento dado, **When** una adición de créditos pasa a `EN_FACULTAD`, **Then** sube `EN_FACULTAD` de la adición y **no** el `EN_FACULTAD` de la novedad de notas.
4. **Given** una solicitud rechazada y otra finalizada, **When** se piden los conteos, **Then** cada una aparece en su propio estado, los dos marcados como finales, y el sistema no las rotula como «exitosa» o «rechazada»: esa clasificación es del cliente.
5. **Given** un trámite que se incorporó por configuración y todavía no tiene solicitudes, **When** se piden los conteos, **Then** aparece con todos sus estados en 0, sin que haya hecho falta desplegar código.
6. **Given** una petición sin sesión, **When** se piden los conteos, **Then** se rechaza como cualquier otra operación protegida y no revela ninguna cifra.

---

### Edge Cases

- **Versiones de un mismo trámite**: una solicitud queda atada a la versión con la que nació, y el motor admite varias versiones vivas (`WorkflowGenericityIT.java:170-195`). Los conteos se agrupan por el **código** del trámite y del estado, sumando versiones. El nombre del trámite y el nombre y las marcas de cada estado se toman de la **versión más alta** que tiene ese estado. Todo estado de la versión vigente aparece; un estado que existe solo en versiones anteriores aparece **solo si todavía tiene solicitudes**, para no ocultarlas ni llenar la lista de estados que ya no se usan. El caso ya es real: la novedad de notas tiene dos versiones desde `V5.2.0` (PR #60, H-11), y la adición de créditos una. Los seis estados de la v1 de la novedad (`V2.1.0__Seed_workflow_definitions.sql:68-73`) existen con el mismo código en la v2, que suma solo `EN_FIRMA_SEDE` (`V5.2.0__Split_novedad_preparation_and_sede_signature.sql:64`); por eso hoy ningún estado vive únicamente en una versión anterior.
- **Un estado que cambió de marcas entre versiones**: si un código fue final en una versión y no en otra, se muestra con las marcas de la versión más alta. Es coherente con el catálogo de trámites, que muestra solo la versión vigente (`IWorkflowDefinitionRepo.java:21-26`).
- **Un trámite sin ninguna solicitud**: aparece igual, con todos sus estados en 0.
- **Devoluciones de la novedad de notas**: no se pueden contar por estado, porque la novedad no tiene un estado de devolución; su devolución es un retorno a `EN_PREPARACION` (`V2.1.0__Seed_workflow_definitions.sql:90-92` en la v1; `V5.2.0__Split_novedad_preparation_and_sede_signature.sql:84-87` en la v2, que suma el retorno desde `EN_FIRMA_SEDE`). Una solicitud devuelta se cuenta en `EN_PREPARACION`. Es una limitación declarada del #58, no un defecto de esta feature.
- **La adición de créditos sí tiene devolución por estado** (`DEVUELTA`, que no es final): se cuenta ahí mientras espera la corrección.
- **Solicitudes de cualquier canal**: las radicadas por el enlace público y por el formulario interno cuentan igual.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST ofrecer los conteos solo a quien tenga sesión, con el mismo mecanismo que protege al resto de las operaciones internas.
- **FR-002**: Los conteos MUST ser por trámite y por estado actual de la solicitud. Cada estado MUST traer su código, su nombre, si es inicial, si es final y el número de solicitudes que están hoy en él.
- **FR-003**: Cada trámite MUST mostrar **todos** los estados de su versión vigente, con 0 en los que no tienen solicitudes. Un estado sin solicitudes nunca se omite.
- **FR-004**: El sistema MUST NOT sumar en un mismo conteo solicitudes de trámites distintos, aunque sus estados compartan código.
- **FR-005**: Las versiones de un trámite MUST sumarse por código de estado, con el nombre y las marcas de la versión más alta que tiene ese estado. Un estado que existe solo en versiones anteriores MUST aparecer si tiene solicitudes y MUST NOT aparecer si no las tiene. La suma supone que un mismo código conserva su sentido entre versiones: una versión que le cambie el sentido a un estado MUST usar otro código. La v2 de la novedad de notas (H-11, PR #60, `V5.2.0`) cumple esa regla solo a medias: separa la espera de la firma de la sede en `EN_FIRMA_SEDE`, así que `EN_PREPARACION` de la v1 incluye esa espera y el de la v2 no; ambos se suman bajo el mismo nombre.
- **FR-006**: Los conteos MUST cubrir **toda la base**: cualquier estado, cualquier canal de radicación y cualquier versión, sin paginar. No dependen de la búsqueda ni de la bandeja. La operación MUST NOT ofrecer filtros: un parámetro que llegue se ignora, y la respuesta es siempre la de toda la base. Agregar un filtro exige revisarlo contra el Principio III, porque un conteo filtrado puede identificar a una persona: «Ingeniería de Sistemas, rechazada: 1» señala al único estudiante de ese programa al que le negaron la adición.
- **FR-007**: Los conteos MUST reflejar la base en el momento de la consulta: una solicitud radicada o movida se ve en la consulta siguiente, sin caché.
- **FR-008**: El sistema MUST NOT clasificar los estados en «terminados», «rechazados», «devueltos» o «pendientes». Expone hechos —código, nombre, marcas y conteo— y el cliente decide cómo agruparlos, con el criterio de la 008 (`specs/008-student-closure-notice/research.md:224`, D5).
- **FR-009**: La respuesta MUST NOT contener datos personales: solo códigos, nombres de trámites y de estados, marcas y conteos (Principio III).
- **FR-010**: Un trámite incorporado por configuración MUST aparecer en los conteos sin desplegar código, y el sistema MUST NOT reconocer códigos de trámite ni de estado para calcularlos (Principio VI).
- **FR-011**: El cambio MUST ser aditivo: ningún contrato existente cambia, y un cliente actual sigue funcionando sin modificaciones.

### Key Entities

- **Conteo por estado**: cuántas solicitudes están hoy en un estado de un trámite, junto con el código, el nombre y las marcas de ese estado. No se almacena: se calcula al consultar.
- **Trámite agrupado**: todas las versiones de un trámite, identificadas por su código, con el nombre de la versión más alta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La Coordinación obtiene en **una sola consulta** los conteos de todos los trámites y estados, sin escribir una búsqueda ni recorrer la bandeja.
- **SC-002**: En cada trámite, la suma de sus conteos es **igual** al número de solicitudes de ese trámite en la base.
- **SC-003**: **Cero** conteos mezclan trámites: en la base de demo, `EN_FACULTAD` se lee 2 en la adición y 1 en la novedad, nunca 3.
- **SC-004**: **Todos** los estados de la versión vigente de cada trámite aparecen, también los que están en 0. Medido el 2026-09-28 en la base de demo, antes de la v2 de la novedad: 14 estados, 5 en 0. Con la v2 (`V5.2.0`, que suma `EN_FIRMA_SEDE`) la versión vigente de los dos trámites reúne 15 estados: 8 de la adición y 7 de la novedad.
- **SC-005**: **Cero** datos personales en la respuesta.
- **SC-006**: Un trámite incorporado por configuración aparece en los conteos sin desplegar código.

## Assumptions

- **Volumen**: 30–40 solicitudes por semestre para el trámite más frecuente (`specs/007-coordination-inbox/spec.md:134`). Contar toda la base en cada consulta es barato a esa escala.
- **Quién consulta**: el sistema no tiene roles; cualquier usuario con sesión es de la Coordinación, igual que en la bandeja (007).
- **La clasificación es del cliente**: el front ya tiene una tabla probada que distingue la devolución y el rechazo por trámite (`tramita-frontend`, `lib/request-state.ts:56-70`). ⚠️ **Deuda declarada con el Principio VI.** El motor no reconoce estados, pero la tabla del cliente sí, por código. Si se incorpora por configuración un trámite con un estado de rechazo, el cliente lo contaría como «terminado» hasta actualizar y desplegar esa tabla. Así, la promesa de no desplegar código se cumple en el motor y no en la pantalla. La deuda no nace aquí: la misma tabla ya decide hoy las insignias del detalle de una solicitud. La alternativa, marcar en la configuración qué estados finales son un rechazo y cuáles una devolución (opción 2 del #58), es la corrección de fondo de `tramita-frontend#51`. **No** entra aquí por el costo, a horas de la entrega: una migración y un cambio en el contrato de `State`, que usa toda la API.
- **Qué significa «pendientes»** en una tarjeta lo decide el cliente (`tramita-frontend#51`, pregunta abierta); estos conteos alcanzan para cualquiera de sus tres lecturas.
- **Los conteos acumulan desde la primera solicitud**, sin corte por periodo. Para el MVP no importa, porque los datos arrancan en septiembre de 2026. En unos semestres, una cifra como «Terminadas: 300» ya no servirá para llevar el control del día a día. Un corte por semestre sería un filtro, y por FR-006 pasa por el Principio III: por fecha el riesgo es bajo, por programa es alto. Queda fuera de esta feature.
- **Fuera de alcance**: el tiempo de ciclo (#37), el conteo de devoluciones a partir del historial, la prioridad o urgencia (el sistema no la modela) y cualquier filtro por fecha, programa o responsable.
- **El prototipo de `router-ia`** (`GET /api/metrics/requests`) queda solo como referencia: agrupa por código de estado sin trámite, cuenta los rechazos como completados (#51) y carga cada solicitud en memoria (#58).
