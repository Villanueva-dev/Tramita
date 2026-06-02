# PRD — Trámita

> **Estado**: versión `1.0.0`, línea base previa a la **entrevista N.º 3** con la Coordinación Académica de la Sede Cali. **Pendiente de validación** con tutor (no asignado todavía, ver memoria `project-tutor-status`) y con la coordinadora.
> **Fecha**: 2026-06-01.
> **Política de no-duplicación**: este documento referencia `../01-planteamiento/arbol-de-problemas.md` y `../02-constitucion/draft-principios.md` en vez de reescribir lo que ya está ahí. Si una sección remite, esa es la fuente de verdad — el PRD no la suplanta. La trazabilidad explícita está al final del documento.

---

## 1. Resumen ejecutivo

**Trámita** es un motor de workflow configurable que orquesta los trámites académicos de la Coordinación de la Sede Cali de la Universidad Remington, reemplazando el ciclo manual de formato Word + correos + memoria humana por un flujo estructurado, validado y auditable.

**Para quién**: la **Coordinadora Académica de la Sede Cali** (usuaria primaria, no técnica). El **estudiante** consulta el estado de sus propias solicitudes.

**Qué resuelve**: hoy los trámites de **adición de créditos** y **novedad de notas** toman entre **una semana y dos meses**. La mezcla de formatos manuales sin validación, firmas escaneadas, bandejas dispersas y ausencia de timeline produce trámites perdidos, re-trabajo y opacidad para el estudiante. Trámita le da al ciclo un **cauce explícito**: captura validada en origen, bandeja por rol, trazabilidad inmutable y PDF formal generado al cierre, listo para asentar en QF.

**Cómo se mide** (referencia árbol §9):

- **Tiempo de ciclo** — mediana entre creación y resolución de una solicitud.
- **Re-trabajo** — devoluciones por solicitud.
- **Opacidad** — capacidad de reconstruir el histórico de una solicitud en menos de un minuto.

**Entregable del MVP**: demo presentable de los **dos trámites end-to-end con el mismo motor parametrizado**, validada por la coordinadora al cierre de cada sprint y defendida ante el tutor cuando sea asignado. Plazo: ~2,5 meses desde 2026-05-19 (ver constitución § Governance).

---

## 2. Personas

### 2.1 Coordinadora Académica de la Sede Cali *(usuaria primaria)*

**Contexto operativo**:

- Orquesta manualmente los dos trámites para toda la Sede Cali.
- Trabaja a diario con **seis canales** distintos sin punto único de verdad: Class (sistema académico), QF (gestor documental de firmas), Word, correo electrónico, WhatsApp y OneDrive.
- Cada trámite pasa por una cadena de roles: Coordinación → Registro Medellín → Docente, según el caso.

**Goals**:

- Que ningún trámite se pierda en una bandeja de correo.
- Saber en cualquier momento en qué etapa está cada solicitud y quién debe actuar.
- Cerrar el ciclo con un **PDF formal** que pueda asentar en QF.
- Reducir el tiempo de respuesta al estudiante (hoy: 1 semana – 2 meses).

**Frustraciones**:

- Re-trabajo por formatos Word mal llenados (códigos de asignatura mal escritos, periodos inconsistentes).
- Perseguir firmas que se escanean, vuelven a imprimir, se vuelven a firmar.
- Estudiantes preguntando "¿en qué va mi trámite?" y depender de la memoria para responder.
- Trámites que "se quedan ahí dos meses" en algún inbox.

**Perfil técnico**: usuaria de software de oficina como herramienta operativa. **No técnica**. El sistema vive o muere por su adopción — si no lo usa por incomodidad, Trámita falla aunque esté bien implementado.

**Frecuencia de uso esperada**: diaria. Trámita es su bandeja de trabajo.

**Implicación de diseño**: cada fricción reducida importa más que cualquier feature avanzada. KISS no es estilo de código — es supervivencia del producto.

---

### 2.2 Estudiante *(consulta exclusiva)*

**Contexto**: presenta solicitudes por el canal institucional actual (correo, mensaje a coord). En el MVP **no captura solicitudes directamente desde Trámita** — la coord lo hace en su nombre. *Pendiente de validar con coordi en entrevista 3 (pregunta #21): ¿quiere que el estudiante capture por sí mismo o lo prefiere seguir capturando ella?*

**Goals**:

- Saber el estado de su propia solicitud sin preguntarle a la coord.
- Tener certeza de cuándo se va a resolver, o si ya se resolvió.

**Frustraciones de hoy**:

- Silencio. El trámite vive en la bandeja de correo de otra persona.

**Permisos en el MVP**: solo lectura, solo de **sus** solicitudes. No edita, no aprueba, no captura.

**Frecuencia de uso esperada**: ocasional — al inicio del semestre, al cierre de notas. No es usuario diario.

---

### 2.3 Roles excluidos del MVP

**Docente, Registro Medellín como usuario del sistema, Auditor académico, Admin** — no se incluyen. Coherente con la decisión cerrada en sesión 2026-05-19 (ver constitución § Roles del MVP). Se agregan solo si la coordi los pide explícitamente o si una entrevista futura los justifica con evidencia.

---

## 3. User journeys

Cada journey describe el camino del trámite **desde la perspectiva del usuario**. Los detalles técnicos del motor de workflow viven en la constitución, Principio III.

### 3.1 Journey — Adición de créditos

| # | Quién actúa | Qué hace | Qué ve en Trámita |
|---|-------------|----------|-------------------|
| 1 | Estudiante | Presenta la solicitud por el canal actual (correo a la coord). | — (fuera de Trámita en el MVP). |
| 2 | Coordinadora | Captura la solicitud en Trámita y llena el formulario validado. | Pantalla "Nueva solicitud — Adición de créditos" con campos del estudiante, asignatura y motivo. |
| 3 | Trámita | Valida los campos en origen (código de asignatura, periodo, restricciones). | Errores en línea si la validación falla; estado **Borrador** mientras no se envíe. |
| 4 | Coordinadora | Revisa los datos, aprueba o devuelve con comentario. | Detalle de solicitud + botones "Aprobar" / "Devolver". |
| 5 | Trámita | Al aprobarse, genera el PDF formal de adición de créditos. | PDF descargable + estado **Aprobado por Coord**. |
| 6 | Coordinadora | Descarga el PDF, lo asienta en QF (fuera de Trámita) y marca la solicitud como **asentada**. | Acción "Marcar como asentado" + confirmación final → estado **Finalizado**. |
| 7 | Estudiante | En cualquier momento, consulta el estado de su solicitud. | Vista de solo lectura con el timeline completo (cuándo se capturó, cuándo se aprobó, cuándo se asentó). |

**Garantías invisibles para el usuario**:

- Cada transición queda registrada de forma **inmutable**. Cuando un estudiante pregunta "¿cómo va mi trámite?", la coord ve el timeline en menos de un minuto y responde con certeza (ataca **opacidad** del árbol §6).
- El PDF formal es **obligatorio** para cerrar el trámite — sin PDF generado, el estado no puede llegar a Finalizado (Principio III, invariante de cierre).
- Class y QF NO se integran con Trámita. La coord es el puente humano que asienta el PDF en QF (Principio VI).

**Pendientes de validar con coordi en entrevista 3**: validación de cupo de asignatura (pregunta #11), normativa aplicable al formato del PDF (pregunta #7), si el estudiante presenta directamente en el MVP (pregunta #21), firma digital vs sello electrónico (pregunta #1).

---

### 3.2 Journey — Novedad de notas

El **esqueleto es idéntico** al de adición de créditos. Lo que cambia:

| Aspecto | Diferencia respecto a adición |
|---------|-------------------------------|
| Quién origina | Puede originarla el docente. En el MVP, la coord captura igual (Docente no es usuario del sistema todavía). |
| Formulario | Plantilla distinta: datos de asignatura + nota original + nota corregida + motivo de la corrección. |
| Aprobaciones | Requiere doble paso: Coord + Registro Medellín. En el MVP, Registro Medellín NO es usuario; la coord marca "enviado a Registro" y "respuesta recibida" como hitos del trámite. *Pendiente de validar con coordi: ¿este modelo manual funciona o necesitamos Registro como usuario directo del sistema?* |
| PDF generado | Plantilla distinta (formato institucional de novedad de notas). |
| Estados | Los mismos nombres conceptuales (Borrador, Enviado, Aprobado por Coord, Finalizado) pero la transición intermedia incluye el ciclo de Registro. |

---

### 3.3 Por qué los dos journeys justifican el motor genérico

El esqueleto operativo es **el mismo**: capturar → validar → aprobar → generar PDF → asentar → consultar. Lo que cambia entre trámites es: nombre y campos del formulario, plantilla del PDF, secuencia de aprobaciones.

Esto **es configuración, no código distinto**. Si Trámita logra modelar los dos trámites con la misma maquinaria parametrizada (Principio III), está demostrada la **pregunta de investigación** del árbol §6: *"¿puede un motor de workflow configurable reducir tiempo, re-trabajo y opacidad en la tramitación de adición de créditos y novedad de notas?"*

Es **el aporte académico del proyecto**, y por eso aparece visible en este PRD — no solo en el árbol.

---

## 4. Alcance del MVP

**Resumen accionable** (el detalle vive en el árbol §8):

**Dentro del alcance**:

- 2 trámites: adición de créditos + novedad de notas.
- 1 sede: Cali.
- 2 roles: Coordinadora (acción) + Estudiante (consulta).
- Despliegue local con `docker-compose`.

**Fuera del alcance del MVP**:

- Integración técnica con Class o QF (Principio VI — son cajas negras).
- Otros procesos académicos (homologaciones, cancelaciones, reingresos, etc.).
- Otras sedes de la Universidad Remington.
- App móvil nativa.
- Producción institucional (la entrega esperada es demo presentable, no piloto institucional).
- Otros roles distintos a los dos del MVP.

---

## 5. Épicas y roadmap por sprint

Mapeo de los sub-problemas SP1–SP7 del árbol §7 a épicas con su Definition of Done de demo. El orden por sprint sigue la justificación del árbol §7: primero la columna vertebral, luego la salida formal del trámite, finalmente la experiencia operativa que cierra el ciclo.

### 5.1 Sprint 1 — Columna vertebral

**Cubre**: SP1 (motor de workflow) + SP2 (formularios validados) + SP6 (timeline de auditoría).

**Funcionalidad observable al cierre**:

- La coord captura una solicitud de adición de créditos desde un formulario validado.
- El motor transiciona la solicitud entre los estados **Borrador → Enviado → Aprobado por Coord**.
- Cada transición queda registrada de forma inmutable.
- La coord puede ver el timeline completo de la solicitud en una sola vista.

**Demo a la coord**: capturar una solicitud de prueba, ver bandeja, ver timeline. Preguntas de validación a la coord: *"¿Te sirve este modelo de captura? ¿Falta algún campo? ¿Sobra alguno? ¿La vista del timeline te sirve para responderle al estudiante?"*

**Definition of Done**:

- Schema de DB con las entidades mínimas necesarias (solicitud, evento de solicitud, usuario, rol).
- Endpoint REST + Swagger publicado para captura, aprobación y consulta.
- Frontend mínimo de captura + bandeja + detalle (a cargo del compañero/a).
- Tests del motor de workflow (transiciones e invariantes — Principio V).
- Coord aprueba la demo.

---

### 5.2 Sprint 2 — Salida formal del trámite

**Cubre**: SP3 (generación de PDF formal) + SP4 (firma/sello + auditoría de aprobaciones).

**Funcionalidad observable al cierre**:

- Al aprobarse una solicitud, Trámita genera el PDF formal automáticamente.
- Cada aprobación queda registrada con actor, timestamp y comentario (Principio IV).
- El PDF lleva un **sello electrónico verificable** (hash + timestamp + actor). Decisión cerrada como fallback frente a firma digital institucional, sujeta a confirmación en entrevista 3 pregunta #1.

**Demo a la coord**: aprobar una solicitud, descargar el PDF generado, verificar que el sello existe y es verificable. Preguntas de validación: *"¿Este PDF es asentable en QF tal como está? ¿Le falta algún campo, sello o pie de firma para que Registro lo acepte?"*

**Definition of Done**:

- Generador de PDF funcional con plantilla institucional vigente (pendiente de obtener — Anexo B de `guia-entrevista-3.md`).
- Mecanismo de sello electrónico verificable.
- Tests del generador y del sellador.
- Coord aprueba la demo.

---

### 5.3 Sprint 3 — Experiencia operativa y validación del motor genérico

**Cubre**: SP5 (bandeja por rol) + SP7 (notificaciones) + **soporte del segundo trámite** (novedad de notas).

**Funcionalidad observable al cierre**:

- Bandeja de trabajo de la coord con solicitudes pendientes ordenadas por SLA.
- Notificaciones por correo en transiciones clave (aprobada, devuelta, asentada).
- Vista del estudiante para consultar sus solicitudes.
- **El motor soporta novedad de notas con la misma maquinaria** — solo cambia configuración. Esto es la prueba viva de la genericidad.

**Demo final (a coord y, cuando esté, al tutor)**: correr **los dos trámites en el mismo sistema**, evidenciando que el código es el mismo y solo cambia configuración. Esto **cierra la pregunta de investigación** del árbol §6.

**Definition of Done del MVP**:

- Los dos trámites funcionando end-to-end con el mismo motor.
- La coord aprueba las tres demos.
- Documentación de defensa lista: cómo se respondió cada SP del árbol, cómo se midió cada variable, cómo se demuestra la genericidad.

---

## 6. Restricciones técnicas

Las restricciones del producto **no se redefinen en este PRD**. Viven en `../02-constitucion/draft-principios.md`:

- **Stack** → § Stack mandatorio.
- **Principios arquitectónicos** → Principios I–VI.
- **Plazo del MVP** → § Governance.
- **Roles permitidos** → § Roles del MVP.

Si algo de esto cambia, **se enmienda la constitución** (con bump de versión y sync impact report) — no se modifica el PRD silenciosamente. Esta separación protege la coherencia: la constitución es la fuente única de verdad técnica.

---

## 7. Métricas de éxito

Las métricas viven en el árbol §9. Acá registramos **cómo se demuestran en la defensa ante el jurado**:

- **Tiempo de ciclo**: comparar el tiempo de un trámite end-to-end en Trámita contra la estimación de la coord del proceso manual. Dato cualitativo, no experimento controlado — coherente con la decisión cerrada de "tesis de MVP funcional, no experimental" (ver memoria `project-mvp-scope-decisions`).
- **Re-trabajo**: el sistema cuenta devoluciones por solicitud y deja establecida la **primera línea base medible** para esta variable. Hoy nadie la mide; Trámita la hace observable.
- **Opacidad**: demostrar al jurado que el timeline de una solicitud se ve en menos de un minuto desde la vista de detalle. Comparar contra el escenario manual: leer hilos de correo + preguntarle a la coord.

---

## 8. Decisiones cerradas en planificación

Registro auditable de decisiones tomadas **antes de implementar**. Cada bloque apunta a la fuente de verdad en memoria semántica para que el tutor o el jurado puedan auditarlas.

### 8.1 Decisiones de scope (sesión 2026-05-19)

Memoria: `project-mvp-scope-decisions`.

1. Single-tenant explícito.
2. Dos roles: Coord (acción) + Estudiante (consulta).
3. Frontend en React.
4. Sprints de 2 semanas con demo a la coord al cierre.
5. Despliegue inicial con `docker-compose` local.
6. Convenia como **inspirar y reescribir**, no fork ni dependencia.
7. Tesis: MVP funcional con validación cualitativa, no experimento medible.
8. Commits del proyecto en español.
9. Líder del equipo: usuario (con autoridad final ante empates).

### 8.2 Decisiones técnicas y de constitución (sesión 2026-06-01)

Memoria engram: `proyecto-grado/constitution-tech-decisions`.

1. Auth con Spring Security + cookie-session, **NO JWT** — provisional hasta confirmar SSO institucional con coord.
2. Mapping DTO ↔ entidad **manual con records**, **NO MapStruct** — KISS para 2 trámites con ~5-10 entidades.
3. Auditoría con tabla `solicitud_event` append-only, **NO Hibernate Envers ni listener custom** — timeline de eventos de dominio explícitos.
4. **Multi-tenancy eliminada** del stack (single-tenant explícito).
5. **PDF formal como invariante** del Principio III — sin PDF no hay trámite cerrado.
6. Plazo 2,5 meses declarado en § Governance como vara de medir KISS automática.
7. Nombre del producto: **Trámita** (esdrújula, nombre propio acuñado).
8. Constitución pasó de 5 a 6 principios; agregado VI (Class y QF como cajas negras).

### 8.3 Heurística general que emergió

> Cuando hay tentación de heredar algo de Convenia, preguntar primero **"¿qué problema resuelve PARA ESTE MVP?"** antes de **"¿cómo lo resolvió Convenia?"**. La herencia mecánica es deuda técnica disfrazada de productividad — y le agrega fricción al lector seis meses después.

---

## 9. Pendientes de validar con la coordinadora

Trámita parte de hipótesis y decisiones internas del equipo. **17 bloqueantes + 9 importantes + 9 útiles** quedan abiertos hasta la entrevista N.º 3 — listados en detalle en `../01-planteamiento/guia-entrevista-3.md`. Este registro NO se duplica acá; se referencia.

**Sumario operativo de los bloqueantes más críticos para el cronograma**:

| Pregunta | Tema | Impacto si la respuesta desvirtúa el supuesto |
|----------|------|------------------------------------------------|
| #1 | Firma digital vs escaneada vs sello electrónico | Define qué genera Trámita en S2. Si requiere firma digital institucional formal, S2 se extiende. |
| #4 | Pénsum y códigos reales de asignaturas | Sin datos reales no se diseña la validación de S1. **URGENTE** — pedir antes del inicio de S1. |
| #9 | SSO institucional | Define si la auth provisional con cookie-session se sostiene o se migra antes de la primera demo institucional. |
| #7 | Normativa institucional aplicable | Sin marcos normativos confirmados, las reglas del motor se diseñan sobre supuestos del equipo. |
| #21 | Captura por el estudiante en el MVP | Cambia el alcance del journey de adición/novedad si la coord prefiere abrir captura al estudiante. |

**Regla operativa**: ningún diseño técnico que dependa de uno de los 17 bloqueantes se ejecuta antes de la entrevista 3. Lo que sí se puede avanzar en paralelo: motor de workflow genérico, schema de auditoría, formularios con campos genéricos (no específicos de asignaturas reales).

---

## 10. Riesgos y supuestos

El catálogo principal vive en el árbol §10. Riesgo nuevo identificado durante la elaboración de este PRD:

- **Riesgo: validación tardía de hipótesis estructurales**. Trámita se diseña hoy contra hipótesis del equipo. Si la entrevista 3 desvirtúa una hipótesis estructural (p. ej. el formato del PDF no es asentable en QF como está, o el sello electrónico no es aceptado normativamente), parte del trabajo de S1/S2 puede tener que rehacerse.
- **Mitigación**: priorizar el envío del correo formal a la coord solicitando el material del Anexo B antes del inicio de S2. Idealmente, agendar la entrevista 3 antes del Sprint 1 si la coord tiene disponibilidad.

---

## Próximos pasos

1. **Materializar la constitución** vía `/speckit-constitution` para ratificar v1.0.0 con Trámita como nombre.
2. **Enviar correo formal** en español a la coord solicitando el material del Anexo B de `guia-entrevista-3.md` (hilos de correo reales de los dos trámites, formatos Word vigentes, pénsum con códigos).
3. **Agendar la entrevista N.º 3** cuando el material haya llegado y se haya analizado.
4. **Aplicar respuestas** de la entrevista 3 al PRD (versión 1.1.0 o 2.0.0 según impacto en hipótesis estructurales).
5. **Iniciar Sprint 1** con `/speckit-specify` para la primera épica de SP1 (motor de workflow + captura validada).

---

## Trazabilidad de este documento

- **PRD versión**: `1.0.0` — línea base previa a entrevista 3.
- **Última modificación**: 2026-06-01.
- **Validado con tutor**: pendiente (tutor no asignado, ver memoria `project-tutor-status`).
- **Validado con coordinadora**: pendiente (entrevista 3).

**Fuentes de verdad referenciadas** (este PRD NO las suplanta):

- `../01-planteamiento/arbol-de-problemas.md` — problema central, métricas, SP1–SP7, alcance/exclusiones, riesgos.
- `../02-constitucion/draft-principios.md` — stack mandatorio, principios I–VI, governance, roles del MVP.
- `../01-planteamiento/guia-entrevista-3.md` — bloqueantes, importantes y útiles pendientes de validar con la coord.

**Memorias semánticas referenciadas**:

- `project-mvp-scope-decisions` — 9 decisiones de scope cerradas en sesión 2026-05-19.
- `proyecto-grado/constitution-tech-decisions` (engram) — 9 decisiones técnicas cerradas en sesión 2026-06-01.
- `project-tutor-status` — estado de asignación del tutor.
- `project-pendientes-investigacion` — pendientes externos priorizados.
