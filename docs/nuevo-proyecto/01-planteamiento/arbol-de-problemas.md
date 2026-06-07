# Planteamiento del problema — árbol de causas y efectos

> **Estado**: borrador inicial. Pendiente de validación con tutor (a asignar) y con la coordinadora académica de la Sede Cali.
> **Fecha**: 2026-05-18.
> **Autores**: equipo de trabajo de grado — Programa de Ingeniería de Sistemas, Universidad Remington (modalidad Distancia, SNIES 53112, Resolución 015939 del 1 de septiembre de 2023).
> **Método**: árbol de problemas según el enfoque de **Marco Lógico** (Metodología del Marco Lógico para la planificación, el seguimiento y la evaluación de proyectos y programas, CEPAL/ILPES — Edgar Ortegón, Juan Francisco Pacheco y Adriana Prieto, 2005).
> **Insumos primarios**: tres entrevistas semi-estructuradas a la Coordinación Académica de la Sede Cali — Entrevista 1 (`transcript-entrevista-coordi.md`), su continuación (`transcript-entrevista-coordi-2.md`) y Entrevista 3 (`material-coord/entrevista-Coordi-3.txt`, material confidencial no versionado).
> **Convención de fuentes (para las citas de este documento)**: «entrevista 1» = `transcript-entrevista-coordi.md`; «entrevista 2» = `transcript-entrevista-coordi-2.md` (continuación de la misma sesión; su encabezado se titula «Continuación de entrevista 1»); «Entrevista 3» = `material-coord/entrevista-Coordi-3.txt`.

---

## 1. Contexto

La **Universidad Remington — Sede Cali** opera procesos de gestión académica que dependen de una coordinadora académica que orquesta manualmente solicitudes formales presentadas por estudiantes y aprobadas por una cadena de roles que varía según el trámite: para **adición de créditos**, Coordinación → Facultad/Decano → Registro y Control Cali (carga el PDF en QF) → Registro y Control Nacional (Medellín, ejecuta la matrícula); para **novedad de notas**, la cadena suma además a la **Dirección de CD** y al **Área Financiera** (verifica el recibo de pago; es paso de cadena, **no firmante**) antes de Registro y Control. Las cuatro casillas de firma de la novedad son docente, Dirección de CD, Decano y Registro y Control.

Los dos procesos críticos identificados en las entrevistas son:

- **Adición de créditos**: el estudiante necesita matricular asignaturas que superan el tope de créditos que CLASS permite para su semestre (frecuente en homologantes); CLASS lo bloquea con el aviso «ha sobrepasado el límite de créditos» y el trámite autoriza la excepción. El tope máximo con adición es de **21 créditos** según el reglamento estudiantil (afirmado por la coordinación en la Entrevista 3, pero **dato provisional y no auditado**: el documento normativo está pendiente de obtener). Queda además **una ambigüedad por resolver**: si 21 es el total matriculable con adición o el número de créditos *adicionales* (la guía P5 preguntaba por los adicionales; la respuesta no lo desambiguó). Las asignaturas elegibles se limitan a las que CLASS habilita para matricular —CLASS ya bloquea prerrequisitos no cumplidos—, por lo que la validación de elegibilidad de materia se **delega a CLASS** y no la asume el sistema. A diferencia de la novedad de notas, la adición **tiene ventana temporal**: debe solicitarse al inicio del semestre (aproximadamente los dos primeros meses); presentada fuera de plazo, la facultad la rechaza de forma definitiva.
- **Novedad de notas**: el docente o el estudiante solicita **registrar una calificación faltante o no cargada** luego del cierre del periodo oficial de notas (cierre **nacional**, sin reapertura). Este trámite **no tiene plazo de caducidad**. La **corrección de una nota mal calculada** se tramita con un formato distinto —*corrección de notas*, con carta de justificación del docente— y es excepcional; queda **fuera del alcance del MVP** (ver §8).

Ambos procesos comparten el **mismo esqueleto** (formulario → firmas → ruteo multi-aprobación → cierre en Registro y Control), pero **difieren en la cadena de aprobadores, en los campos y en la profundidad de automatización que admiten**: novedad de notas pasa por más áreas y más firmas (incluye Dirección de CD y Área Financiera) que adición de créditos. Esa **asimetría dentro de la simetría** es precisamente lo que justifica un **motor genérico de workflow configurable**, no dos sistemas independientes.

Un punto realista que delimita el alcance: en ambos trámites, los **aprobadores aguas abajo** (Facultad/Decano en Medellín, Dirección de CD, Área Financiera, Registro y Control) son actores externos a la coordinación de Cali que **operan por correo y OneDrive y no se incorporan como usuarios del sistema**; además, el control anti-fraude de Registro y Control —especialmente intenso en **novedad de notas**— **es el propio historial de correos** (la adición de créditos es bastante menos rigurosa, según la Entrevista 3). Por eso el sistema **no orquesta de punta a punta** la cadena de firmas: actúa como el **cockpit interno de la coordinación** (captura validada, generación del PDF, registro y avance del estado). La única salida directa hacia el estudiante es el **aviso de finalización** (correo institucional al completarse el trámite), sin portal ni login de auto-consulta — decisión explícita de la coordinación (E3-p2, Q22–Q23). Sobre ese rol común, cada trámite se configura con distinta profundidad: **adición de créditos = automatización profunda** (alto volumen, dolor de devoluciones por error de formato, cadena más corta) y **novedad de notas = seguimiento** (visibilidad de estado interna para la coordinación, sin pretender orquestar las firmas externas). Esa diferencia de profundidad es, en sí misma, una dimensión configurable del motor.

Los sistemas institucionales preexistentes —**Class** (sistema académico) y **QF** (gestor documental / banco de documentos institucional)— se consideran cajas negras: el sistema propuesto no los reemplaza ni se integra con ellos, sino que se sitúa **aguas arriba**, estructurando y dando seguimiento al trámite hasta el punto en que un humano lo asienta en esos sistemas.

---

## 2. Problema central

> **La gestión de solicitudes académicas con flujo multi-aprobación (adición de créditos, novedad de notas) en la Sede Cali de la Universidad Remington depende de correos electrónicos, formatos en Word y memoria humana, sin un workflow estructurado que orqueste el ciclo de vida del trámite, valide los datos de entrada y registre la trazabilidad de cada decisión.**

Esta formulación intenta cumplir tres criterios del Marco Lógico:

1. **Es una situación existente**, no la ausencia de una solución (no dice "no existe el sistema X", sino "la gestión depende de…").
2. **Es específica** en su alcance: dos procesos, una sede, una cadena de roles.
3. **Es verificable**: los síntomas que produce son medibles (tiempos de ciclo, número de re-trabajos, trámites perdidos).

---

## 3. Árbol de efectos (consecuencias observadas)

Los efectos están ordenados de **operativos** a **estratégicos**, de menor a mayor impacto sobre el estudiante y la institución.

| ID | Efecto | Evidencia en las entrevistas |
|----|--------|------------------------------|
| **E1** | Estudiantes que no quedan matriculados o no reciben su nota a tiempo, afectando su avance académico. | «el Class no me permite matricular esa materia» (entrevista 1); «esa nota no se carga en el sistema» (entrevista 2) |
| **E2** | Re-trabajo por errores formales detectados tarde en la cadena (formato mal diligenciado, firma faltante, anexo incorrecto). | El formato vuelve atrás varias veces antes de ser aceptado por Registro Medellín. |
| **E3** | Sobrecarga administrativa sobre la coordinación académica, que actúa como **punto único de orquestación humano** para todos los trámites. | La coordinadora reenvía, valida, recuerda, recolecta firmas y persigue respuestas. |
| **E4** | Trámites perdidos o estancados en bandejas de correo saturadas, sin alerta automática del retraso. | «se puede traspapelar … llegan tantos correos que de pronto la facultad no lo vio» (entrevista 2) |
| **E5** | Tiempos de ciclo extremos e impredecibles: entre **una semana y dos meses** para un mismo tipo de trámite. | Estimación directa de la coordinación en entrevista 2. |

**Efecto raíz consolidado**: el estudiante —cliente final del proceso— no tiene certeza de **cuándo** ni **si** su solicitud va a resolverse, y la institución no tiene métricas para auditar ni mejorar el proceso. Este dolor se ataca mediante **visibilidad mediada**: la coordinación consulta el cockpit y responde con certeza en menos de un minuto; el estudiante recibe un **aviso de finalización** al completarse el trámite. No existe un portal de auto-consulta para el estudiante — la coordinación descartó esa opción explícitamente (E3-p2, Q22).

---

## 4. Árbol de causas (raíces del problema)

Las causas están ordenadas para que cada una pueda mapearse, más adelante, a un objetivo específico y a una capacidad concreta del sistema.

| ID | Causa raíz | Manifestación operativa |
|----|------------|-------------------------|
| **C1** | No existe un **repositorio central** del estado de cada solicitud. | El estado vive en la memoria de la coordinadora y en hilos de correo dispersos. |
| **C2** | Los formatos se llenan **manualmente en Word**, sin validación de campos. | Errores tipográficos, códigos de asignatura mal escritos, periodos inconsistentes. |
| **C3** | Las **firmas se escanean** y se adjuntan al expediente en QF; el original digital se pierde. | Cuando se firma a mano, el firmante imprime, firma y vuelve a escanear; tanto la firma escaneada como la digital son válidas. |
| **C4** | No hay **bandeja por rol**: cada actor depende de su inbox personal para saber qué le toca actuar. | La coordinación tiene que recordar y empujar manualmente cada paso. |
| **C5** | Las **aprobaciones** se dan por correo electrónico, sin trazabilidad estructurada (quién aprobó qué, cuándo, con qué comentario). | No se puede reconstruir el histórico de decisiones sin leer cadenas de correos. Hoy el propio hilo de correos es el control anti-fraude: Registro y Control no asienta el trámite sin verificar en la cadena que las aprobaciones son auténticas («no falsifiqué la firma», entrevista 2). La trazabilidad estructurada que aporta el sistema **convive con** este control —**no lo reemplaza**—: la verificación por historial de correos de Registro y Control permanece externa al MVP (ver §8). |
| **C6** | Los **anexos** (capturas, soportes, comprobantes) se manejan manualmente y se reenvían por correo. | Riesgo de versiones desactualizadas y de pérdida de adjuntos. |
| **C7** | La **comunicación** del trámite está dispersa en al menos cuatro canales: correo, WhatsApp, Teams y OneDrive. | No hay un único "lugar de la verdad" para la coordinación y la auditoría. El estudiante no tiene acceso directo al sistema; recibe el aviso de cierre y sus consultas intermedias las responde la coordinación con el cockpit. |

---

## 5. Diagrama del árbol

```
                                  ┌──────────────────────────────────────────────────────┐
                                  │  EFECTO RAÍZ                                         │
                                  │  Estudiante sin certeza de cuándo/si se resuelve;    │
                                  │  institución sin métricas para auditar el proceso.   │
                                  └────────────────────────┬─────────────────────────────┘
                                                           │
            ┌────────────────┬──────────────┬──────────────┼──────────────┬────────────────┐
            │ E1             │ E2           │ E3           │ E4           │ E5             │
            │ Sin matrícula  │ Re-trabajo   │ Sobrecarga   │ Trámites     │ Ciclos de      │
            │ / sin nota a   │ por errores  │ sobre la     │ perdidos en  │ 1 semana a     │
            │ tiempo         │ formales     │ coordinación │ inboxes      │ 2 meses        │
            └────────────────┴──────────────┴──────────────┴──────────────┴────────────────┘
                                                ▲
                                                │  (problema → efecto)
                                                │
                          ┌─────────────────────┴────────────────────────┐
                          │  PROBLEMA CENTRAL                            │
                          │  La gestión de solicitudes académicas        │
                          │  depende de correos, Word y memoria humana,  │
                          │  sin workflow estructurado.                  │
                          └─────────────────────┬────────────────────────┘
                                                ▲
   ┌──────────┬──────────┬───────────┬──────────┼──────────┬───────────┬──────────┐
   │ C1       │ C2       │ C3        │ C4       │ C5       │ C6        │ C7       │
   │ Sin      │ Formatos │ Firmas    │ Sin      │ Aprobac. │ Anexos    │ Comunic. │
   │ repo     │ manuales │ escaneadas│ bandeja  │ por      │ manuales  │ dispersa │
   │ central  │ en Word  │ en QF     │ por rol  │ correo   │           │ (4 canal)│
   └──────────┴──────────┴───────────┴──────────┴──────────┴───────────┴──────────┘
```

---

## 6. Pregunta de investigación

> **¿Cómo puede un sistema de gestión de solicitudes académicas con un motor de workflow configurable reducir los tiempos de ciclo, el re-trabajo y la opacidad de la tramitación de adición de créditos y novedad de notas en la Sede Cali de la Universidad Remington?**

Esta pregunta es deliberadamente **acotada**:

- A **dos procesos** y **una sede** (alcance abordable en 2,5 meses con un equipo de dos).
- A **tres variables observables** (tiempo, re-trabajo, opacidad), que permiten definir métricas de éxito en la sección de objetivos.
- A un **mecanismo concreto** (motor de workflow configurable), no a "digitalizar" en abstracto.

> **Nota de honestidad (alcance de la variable «tiempo de ciclo»)**: el sistema **no controla** el tiempo total del trámite —depende de aprobadores externos que operan por correo (ver §1)—. Reduce únicamente la **porción controlable**: devoluciones evitadas por validación de formato y trámites que ya no se traspapelan por falta de visibilidad. Las métricas de §9 reflejan esta distinción.

---

## 7. Descomposición en sub-problemas → objetivos específicos

Cada causa raíz se traduce en un sub-problema **MECE** (mutuamente excluyente, colectivamente exhaustivo) y, en el siguiente nivel, en un objetivo específico medible. Los verbos de los objetivos se eligen sobre la **taxonomía de Bloom revisada** (Anderson y Krathwohl, 2001), priorizando los niveles de **aplicar**, **analizar** y **crear**.

| SP | Causa cubierta | Sub-problema | Objetivo específico | Sprint |
|----|----------------|--------------|---------------------|--------|
| **SP1** | C1, C4 | No hay orquestación estructurada de los estados del trámite. | **Diseñar e implementar** un motor de workflow configurable que modele transiciones de estado, roles y reglas de paso, **incluida la profundidad de automatización por trámite** (adición: flujo completo; novedad: seguimiento). Los pasos a cargo de aprobadores externos los **avanza la coordinación** (el sistema registra que ocurrieron, no los ejecuta). | S1 |
| **SP2** | C2 | Los datos del trámite entran sin validación al sistema. | **Construir** un módulo de formularios validados por backend que reemplace el llenado manual del Word y detecte errores en la captura, **incluyendo las reglas de negocio de cada trámite** (adición: tope de créditos —provisional **21**, pendiente de normativa— y ventana temporal de solicitud; novedad: sin plazo de caducidad). El tope se modela como **parámetro de configuración del motor**, no como constante en código (Principio III del borrador de constitución, `draft-principios.md`), para ajustarlo al llegar el reglamento sin tocar código. La validación de elegibilidad de la materia se **delega a CLASS**. | S1 |
| **SP3** | C3 (parcial), C6 | El documento físico/escaneado es el artefacto canónico del trámite. | **Generar** automáticamente el PDF formal del trámite a partir de los datos validados, listo para anexar a QF cuando se requiera. | S2 |
| **SP4** | C3, C5 | No existe trazabilidad estructurada de las firmas y aprobaciones. | **Registrar** la traza de cada aprobación (estado, autor declarado, sello de tiempo) que la coordinación avanza, y **aplicar un sello electrónico verificable** (hash + sello de tiempo) sobre los documentos que el sistema genera. Las firmas de los aprobadores externos siguen en el correo/OneDrive: el sistema **no las captura**. Para la firma del solicitante se admite escaneada o trazada digital, **no el nombre mecanografiado**; la validez legal queda pendiente (ver §10). | S2 |
| **SP5** | C4 | La coordinación carece de una vista consolidada de pendientes por rol. | **Desarrollar** la bandeja de trabajo de la **coordinación** (solicitudes pendientes de su acción y su SLA). El estudiante no es usuario del sistema; sus consultas de estado se responden a través del cockpit de la coordinación (**visibilidad mediada**). Los aprobadores externos tampoco son usuarios del sistema. | S3 |
| **SP6** | C1, C5, C7 | No hay historial accesible ni para la coordinación ni para auditoría. | **Construir** un timeline de auditoría inmutable por solicitud: **profundo para la coordinación y la auditoría** (todas las transiciones, fechadas y por autor). El timeline es la herramienta con la que la coordinación responde al estudiante en menos de un minuto (**visibilidad mediada**). | S1 |
| **SP7** | C7 | Los canales de comunicación son cuatro y descoordinados. | **Integrar** un **puerto de notificación** que avise al estudiante **solo al completarse** el trámite (estado FINALIZADO). Adaptador canónico: correo institucional automático + acción opcional de chat con plantilla desde el cockpit. Las transiciones intermedias generan únicamente entradas en el **timeline interno** (coordinación y auditoría). Nota de riesgo: el chasis no incluye infraestructura de correo; el adaptador email depende de SMTP (dependencia externa — derivar a profe Diego, Q34); **chat-template = fallback** si SMTP no está disponible a tiempo. | S3 |

**Justificación del orden por sprint**:

- **Sprint 1 (SP1 + SP2 + SP6)**: la columna vertebral. Sin motor de workflow ni datos validados ni auditoría, los demás módulos no tienen dónde apoyarse.
- **Sprint 2 (SP3 + SP4)**: lo que permite que el trámite "salga" del sistema con valor formal (PDF generado, con sello electrónico verificable y traza de aprobaciones auditable).
- **Sprint 3 (SP5 + SP7)**: la experiencia operativa diaria de los actores. Llega último porque presupone que el flujo y el documento ya funcionan.

> **Nota sobre profundidad diferencial**: SP1–SP7 se aplican **en plenitud a la adición de créditos** (el trámite "profundo"). Para la **novedad de notas** ("seguimiento"), el motor se configura para que la coordinación **registre y avance el estado** del trámite mientras la cadena de firmas transcurre por fuera (correo/OneDrive); el estudiante recibe el **aviso de finalización** cuando el trámite se completa, y sus consultas intermedias se responden de forma **mediada** (coordinación → cockpit → respuesta al estudiante). No se intenta orquestar las firmas externas ni replicar el control anti-fraude de Registro y Control. La diferencia es de **configuración**, no de código: ambos trámites corren sobre el mismo motor.

---

## 8. Alcance y exclusiones

**Dentro del alcance**:

- Proceso de **adición de créditos** y **novedad de notas**, en la **Sede Cali**.
- Backend (API REST + Swagger) + frontend mínimo a cargo del compañero del equipo.
- Motor de workflow capaz de modelar los dos procesos sin código duplicado, con **profundidad de automatización configurable** (adición: flujo completo; novedad: seguimiento de estado).
- Persistencia en PostgreSQL; autenticación (la E3 confirma **correo institucional** como identificador mínimo, sin autenticación estricta; **SSO institucional pendiente** de confirmar con tecnología — la decisión técnica de **sesión por cookie**, no JWT, es del equipo, registrada en `draft-principios.md` § Stack mandatorio como provisional y migrable a SSO); generación de PDF; auditoría inmutable.
- **Trazabilidad de estado para la coordinación (administración)** con precisión y profundidad: cada transición registrada, fechada y auditable.
- **Aviso de finalización al estudiante** — **puerto de notificación**. Lo **confirmado por la coordinación en E3-p2 (Q22–Q23)**: descartó el portal/login de auto-consulta y **solo acepta notificación al cierre por correo institucional** (*«cuando se [completa] el proceso le llegó una notificación al correo institucional del estudiante»*). El **adaptador de chat con plantilla** desde el cockpit es **decisión de ingeniería del equipo** (fallback de SMTP; refleja el canal WhatsApp informal de E1/E2), **no un pedido de la coordi**.

**Fuera del alcance del MVP** (hipótesis a validar con tutor):

- **Portal o login de auto-consulta para el estudiante** — descartado explícitamente por la coordinación (E3-p2, Q22). El estudiante no es usuario del sistema; recibe el aviso de finalización y sus consultas intermedias se atienden de forma mediada. **Motivación de la coordinación** (clave para la defensa): no quiere que el estudiante audite o persiga el trabajo interno de la coordinación — *«no porque no nos audite el trabajo de nosotros… el estudiante [no estaría] en persecución»* (E3-p2, Q22). El "no portal" no es solo una simplificación técnica: es una preferencia organizacional explícita.
- Integración técnica con **Class** o **QF** (cajas negras: el sistema entrega el PDF y el ser humano lo asienta donde corresponda).
- Otros procesos académicos (homologaciones, cancelaciones, reingresos, etc.).
- Otras sedes de la Universidad Remington.
- App móvil nativa.
- Despliegue en producción institucional (la entrega esperada es **demo presentable**, no piloto institucional).
- **Orquestación de los aprobadores externos** (Facultad/Decano en Medellín, Dirección de CD, Área Financiera, Registro y Control): no se incorporan como usuarios; la cadena de firmas sigue transcurriendo por correo y OneDrive.
- **Reemplazo del control anti-fraude de Registro y Control**: la verificación por historial de correos sigue siendo externa al sistema.
- **Validación automática de si el estudiante efectivamente cursó la materia** (revisión de la planilla de asistencia): se mantiene **manual por decisión explícita de la coordinación**.
- **Corrección de nota mal calculada**: se tramita con el formato *corrección de notas* (distinto al de novedad, con carta del docente) y es excepcional.

> **Nota — vistas (resuelto)**: Q21–Q23 de la guía fueron respondidas en E3-p2. Resultado: cockpit de la coordinación (profundo) + aviso de finalización al estudiante (sin portal ni login). Ver síntesis §8 para el detalle de cada pregunta.

> **Nota de riesgo — adaptador email**: el chasis (Convenia) no incluye infraestructura de correo (`pom.xml` sin `spring-boot-starter-mail`, sin `JavaMailSender`, sin `spring.mail`). El adaptador email es el canónico del puerto de notificación, pero **depende de un servidor SMTP** (dependencia externa; derivar a profe Diego, Q34). El camino crítico del MVP usa el **chat-template como fallback** si SMTP no está disponible a tiempo.

> **Nota de consistencia**: la decisión «sesión por cookie, no JWT» está registrada en `docs/nuevo-proyecto/02-constitucion/draft-principios.md` (§ Stack mandatorio); **falta materializarla** en `.specify/memory/constitution.md` vía `/speckit-constitution` (hoy ese archivo sigue siendo plantilla vacía).

---

## 9. Métricas de éxito propuestas (a refinar con tutor)

Las tres variables de la pregunta de investigación se operacionalizan así:

| Variable | Métrica propuesta | Línea base (manual) | Meta MVP |
|----------|-------------------|---------------------|----------|
| **Tiempo de ciclo** | Mediana del tiempo entre creación y resolución de una solicitud. | 1 semana – 2 meses (rango). | El sistema **no controla** el tiempo total (depende de firmas externas); se trata como **variable contextual**. La meta medible se traslada a indicadores propios del sistema: **tasa de devoluciones por error de formato** (sobre N solicitudes de prueba) y **tiempo de reconstrucción del histórico** de una solicitud. |
| **Re-trabajo** | Número promedio de devoluciones por solicitud. | No medido formalmente. | Ganancia principal en **adición de créditos**: el formulario validado reduce las devoluciones por error de formato. Línea base medible vía auditoría del propio sistema. |
| **Opacidad** | Capacidad de reconstruir el histórico de una solicitud en menos de 1 minuto. | Imposible sin leer hilos de correo. | Ganancia transversal, **principal en novedad de notas**: timeline profundo para la coordinación y la auditoría. La coordinación reconstruye el estado en menos de un minuto y responde al estudiante de forma mediada; el estudiante recibe además el aviso de finalización el día del cierre. |

> **Nota**: estas métricas son propuestas iniciales; deben validarse con el tutor cuando sea asignado y con la coordinación académica antes de fijar criterios de aceptación finales.

> **Nota sobre SLA institucional (Entrevista 3)**: la coordinación indicó que existe una normativa de tiempos de respuesta —del orden de 3 a 5 días para solicitudes, hasta 15 días hábiles para otras; PQR 5 días hábiles, derecho de petición 15 días— pero no precisó el documento que la contiene (posiblemente el PEI o el reglamento estudiantil). De confirmarse, ese SLA formal sería una línea base más defendible que el rango observado. Documento pendiente de obtener.

---

## 10. Supuestos y riesgos identificados

| Tipo | Descripción | Estrategia de mitigación |
|------|-------------|--------------------------|
| **Supuesto** | La coordinación académica de Sede Cali estará disponible para validar prototipos al menos una vez por sprint. | Calendarizar revisiones al cierre de cada sprint desde el sprint 0. |
| **Supuesto** | La normativa institucional permite que un trámite se origine fuera de Class siempre que el documento formal final se asiente en QF. | Confirmar por escrito con la coordinación antes del cierre del sprint 1. |
| **Riesgo** | La firma digital institucional no es estandarizable a corto plazo. | Como fallback, sello electrónico verificable (hash + timestamp + actor) sobre el PDF generado por el sistema. |
| **Riesgo** | El plazo de 2,5 meses se reduce si la asignación del tutor se retrasa. | Avanzar el documento de requisitos y la arquitectura como artefactos independientes del tutor; obtener feedback informal en paralelo. |
| **Riesgo** | La plantilla institucional del trabajo de grado y la rúbrica de evaluación todavía no están en posesión del equipo. | Pivotar el formato del documento cuando lleguen; mantener el contenido portable en Markdown para reformatear rápido. |
| **Riesgo** | Una firma escaneada puede quedar no visible al subirse a QF (documento «editable»), provocando devolución del trámite. | Aplanar/normalizar el PDF generado antes de la entrega y verificar que la firma sea visible. |
| **Supuesto** | El rol firmante de la facultad se modela como **«Decano»** (firmante de derecho), asumiendo que su asistente opera de hecho por delegación. La Entrevista 3 no pudo confirmarlo: *«no me consta… ni modo de preguntar al decano»*. | Documentar el supuesto para la defensa; si más adelante se confirma la delegación, el modelo de roles lo absorbe como atributo, no como cambio estructural. |
| **Supuesto** | El seguimiento —sobre todo en novedad de notas— supone que la coordinación **registrará y avanzará el estado** del trámite de forma sostenida, por ser el punto único de orquestación humana. | Validar la carga operativa real con la coordinación en el piloto; minimizar la fricción de actualización de estado en la interfaz. |
| **Riesgo** | Si la coordinación no mantiene el estado actualizado, la **visibilidad interna** (valor central del cockpit, especialmente para novedad de notas) se degrada a información desactualizada; la visibilidad mediada al estudiante también se ve afectada. | Diseñar la actualización de estado como **subproducto de acciones que la coordinación ya realiza** (generar el PDF, registrar el envío), no como trabajo adicional. |
| **Riesgo** | El adaptador email (aviso de finalización al estudiante) depende de infraestructura SMTP no incluida en el chasis. Si SMTP no está disponible a tiempo, el puerto de notificación queda sin implementar. | Diseñar el puerto como interfaz swappable. Usar el **chat-template como fallback** (la coordinación copia una plantilla y la envía por chat), sin bloquear el resto del MVP. Atar la decisión a Q34 (profe Diego). |
| **Riesgo** | La **validez legal** de la firma escaneada/trazada y del sello electrónico verificable no está confirmada (depende de la normativa pendiente y de la pregunta 29 de la guía, no transcrita). | Tratar el sello electrónico como mecanismo **provisional**; confirmar con la normativa institucional cuando llegue; no afirmar valor probatorio sin respaldo documental. |

---

## 11. Próximos pasos

1. **Validar este documento** con el usuario (revisor: tú) y, cuando esté disponible, con el tutor asignado y la coordinación académica.
2. **Bajar SP1–SP7 al backlog Scrum**: épicas, historias de usuario y criterios de aceptación.
3. **Documento de requisitos (SRS, IEEE 830)**: requisitos funcionales y no funcionales derivados de SP1–SP7.
4. **Arquitectura inicial** (vistas C4 + 4+1 + modelo entidad-relación), apuntando a reusar el chasis Spring Boot 4 / Java 21 / PostgreSQL del proyecto Convenia.

**Insumos abiertos a cerrar antes (o durante) la fase IEEE 830:**

- **Normativa de los dos trámites** (reglamento estudiantil / PEI / políticas) — la coordinación la buscará en la semana siguiente a E3.
- **Q21–Q35 — RESUELTAS** en `material-coord/parte2-entrevista3.md`: vistas (Q21–Q23 → cockpit interno + aviso de cierre; NO portal del estudiante), volumetría Q24 (30–40 adiciones/sem, ~100% aprobación), calendario Q27–Q28, escalamiento Q30, demos Q31–Q32, piloto estudiantil Q33. Pendientes menores que persisten: volumen novedad (Q25), población activa (Q26), normativa firmas (Q29), hosting (Q34 → profe Diego), ley de datos (Q35 → probable Ley 1581/2012, verificar).
- **Hilo de correo de novedad de notas anonimizado** (pendiente de la coordinación).

---

## Referencias

- Ortegón, E., Pacheco, J. F. y Prieto, A. (2005). *Metodología del marco lógico para la planificación, el seguimiento y la evaluación de proyectos y programas*. Serie Manuales N.º 42, CEPAL/ILPES. Naciones Unidas.
- Anderson, L. W. y Krathwohl, D. R. (eds.) (2001). *A Taxonomy for Learning, Teaching, and Assessing: A Revision of Bloom's Taxonomy of Educational Objectives*. Longman.
- Universidad Remington. Resolución 015939 del 1 de septiembre de 2023 (renovación del registro calificado del programa de Ingeniería de Sistemas, modalidad Distancia, SNIES 53112).
- Entrevistas a la Coordinación Académica de la Sede Cali: Entrevista 1 (`transcript-entrevista-coordi.md`) y su continuación (`transcript-entrevista-coordi-2.md`); Entrevista 3 (`material-coord/entrevista-Coordi-3.txt`) y su síntesis analítica (`material-coord/2026-06-04-entrevista3-sintesis-analitica.md`). La Entrevista 3 y su síntesis son material confidencial no versionado: se citan por referencia, sin reproducir contenido literal ni datos personales.
