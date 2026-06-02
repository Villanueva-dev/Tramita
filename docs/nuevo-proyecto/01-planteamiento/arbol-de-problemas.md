# Planteamiento del problema — árbol de causas y efectos

> **Estado**: borrador inicial. Pendiente de validación con tutor (a asignar) y con la coordinadora académica de la Sede Cali.
> **Fecha**: 2026-05-18.
> **Autores**: equipo de trabajo de grado — Programa de Ingeniería de Sistemas, Universidad Remington (modalidad Distancia, SNIES 53112, Resolución 015939 del 1 de septiembre de 2023).
> **Método**: árbol de problemas según el enfoque de **Marco Lógico** (Metodología del Marco Lógico para la planificación, el seguimiento y la evaluación de proyectos y programas, CEPAL/ILPES — Edgar Ortegón, Juan Francisco Pacheco y Adriana Prieto, 2005).
> **Insumos primarios**: dos entrevistas semi-estructuradas a la Coordinación Académica de la Sede Cali (`transcript-entrevista-coordi.md` y `transcript-entrevista-coordi-2.md`).

---

## 1. Contexto

La **Universidad Remington — Sede Cali** opera procesos de gestión académica que dependen de una coordinadora académica que orquesta manualmente solicitudes formales presentadas por estudiantes y aprobadas por una cadena de roles (coordinación → registro Medellín → docente, según el tipo de trámite).

Los dos procesos críticos identificados en las entrevistas son:

- **Adición de créditos**: el estudiante solicita inscribir una o más asignaturas fuera del periodo regular de matrícula.
- **Novedad de notas**: el docente o el estudiante solicita corregir o registrar una calificación luego de cerrado el periodo oficial de notas.

Ambos procesos comparten una **estructura de flujo idéntica**: formulario en Word → firmas escaneadas → revisión por coordinación → reenvío a Registro Medellín → confirmación por correo. La diferencia es el contenido del formato y los roles que firman; el esqueleto del proceso es el mismo. Esa simetría es la base para proponer un **motor genérico de workflow configurable**, no dos sistemas independientes.

Los sistemas institucionales preexistentes —**Class** (sistema académico) y **QF** (gestor documental de firmas)— se consideran cajas negras: el sistema propuesto no los reemplaza ni se integra con ellos, sino que se sitúa **aguas arriba** orquestando el trámite hasta el punto en que un humano lo asienta en esos sistemas.

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
| **E1** | Estudiantes que no quedan matriculados o no reciben su nota a tiempo, afectando su avance académico. | "Si esto no se hace, el estudiante no aparece matriculado en esa materia." (entrevista 1) |
| **E2** | Re-trabajo por errores formales detectados tarde en la cadena (formato mal diligenciado, firma faltante, anexo incorrecto). | El formato vuelve atrás varias veces antes de ser aceptado por Registro Medellín. |
| **E3** | Sobrecarga administrativa sobre la coordinación académica, que actúa como **punto único de orquestación humano** para todos los trámites. | La coordinadora reenvía, valida, recuerda, recolecta firmas y persigue respuestas. |
| **E4** | Trámites perdidos o estancados en bandejas de correo saturadas, sin alerta automática del retraso. | "A veces el correo se queda ahí dos meses." (entrevista 2) |
| **E5** | Tiempos de ciclo extremos e impredecibles: entre **una semana y dos meses** para un mismo tipo de trámite. | Estimación directa de la coordinación en entrevista 2. |

**Efecto raíz consolidado**: el estudiante —cliente final del proceso— no tiene certeza de **cuándo** ni **si** su solicitud va a resolverse, y la institución no tiene métricas para auditar ni mejorar el proceso.

---

## 4. Árbol de causas (raíces del problema)

Las causas están ordenadas para que cada una pueda mapearse, más adelante, a un objetivo específico y a una capacidad concreta del sistema.

| ID | Causa raíz | Manifestación operativa |
|----|------------|-------------------------|
| **C1** | No existe un **repositorio central** del estado de cada solicitud. | El estado vive en la memoria de la coordinadora y en hilos de correo dispersos. |
| **C2** | Los formatos se llenan **manualmente en Word**, sin validación de campos. | Errores tipográficos, códigos de asignatura mal escritos, periodos inconsistentes. |
| **C3** | Las **firmas se escanean** y se adjuntan al expediente en QF; el original digital se pierde. | Cada nuevo paso requiere re-imprimir, firmar a mano y volver a escanear. |
| **C4** | No hay **bandeja por rol**: cada actor depende de su inbox personal para saber qué le toca actuar. | La coordinación tiene que recordar y empujar manualmente cada paso. |
| **C5** | Las **aprobaciones** se dan por correo electrónico, sin trazabilidad estructurada (quién aprobó qué, cuándo, con qué comentario). | No se puede reconstruir el histórico de decisiones sin leer cadenas de correos. |
| **C6** | Los **anexos** (capturas, soportes, comprobantes) se manejan manualmente y se reenvían por correo. | Riesgo de versiones desactualizadas y de pérdida de adjuntos. |
| **C7** | La **comunicación** del trámite está dispersa en al menos cuatro canales: correo, WhatsApp, Class y OneDrive. | No hay un único "lugar de la verdad" para el estudiante o el coordinador. |

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
                                                │  (causa  → efecto)
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

---

## 7. Descomposición en sub-problemas → objetivos específicos

Cada causa raíz se traduce en un sub-problema **MECE** (mutuamente excluyente, colectivamente exhaustivo) y, en el siguiente nivel, en un objetivo específico medible. Los verbos de los objetivos se eligen sobre la **taxonomía de Bloom revisada** (Anderson y Krathwohl, 2001), priorizando los niveles de **aplicar**, **analizar** y **crear**.

| SP | Causa cubierta | Sub-problema | Objetivo específico | Sprint |
|----|----------------|--------------|---------------------|--------|
| **SP1** | C1, C4 | No hay orquestación estructurada de los estados del trámite. | **Diseñar e implementar** un motor de workflow configurable que modele transiciones de estado, roles aprobadores y reglas de paso entre etapas. | S1 |
| **SP2** | C2 | Los datos del trámite entran sin validación al sistema. | **Construir** un módulo de formularios validados por backend que reemplace el llenado manual del Word y detecte errores en el momento de la captura. | S1 |
| **SP3** | C3 (parcial), C6 | El documento físico/escaneado es el artefacto canónico del trámite. | **Generar** automáticamente el PDF formal del trámite a partir de los datos validados, listo para anexar a QF cuando se requiera. | S2 |
| **SP4** | C3, C5 | No existe trazabilidad estructurada de las firmas y aprobaciones. | **Implementar** firma digital (o sello electrónico verificable) y registro de auditoría de cada aprobación, con sello de tiempo y autor. | S2 |
| **SP5** | C4 | Los actores no saben qué les toca actuar ni cuándo. | **Desarrollar** una bandeja de trabajo por rol que liste las solicitudes pendientes de acción y su SLA. | S3 |
| **SP6** | C1, C5, C7 | No hay historial accesible ni para el estudiante ni para auditoría. | **Construir** un timeline de auditoría inmutable por solicitud, consultable por estudiante, coordinador y auditor. | S1 |
| **SP7** | C7 | Los canales de comunicación son cuatro y descoordinados. | **Integrar** notificaciones automáticas (correo en MVP; otros canales como hipótesis posterior) disparadas por las transiciones del workflow. | S3 |

**Justificación del orden por sprint**:

- **Sprint 1 (SP1 + SP2 + SP6)**: la columna vertebral. Sin motor de workflow ni datos validados ni auditoría, los demás módulos no tienen dónde apoyarse.
- **Sprint 2 (SP3 + SP4)**: lo que permite que el trámite "salga" del sistema con valor formal (PDF firmado y auditable).
- **Sprint 3 (SP5 + SP7)**: la experiencia operativa diaria de los actores. Llega último porque presupone que el flujo y el documento ya funcionan.

---

## 8. Alcance y exclusiones

**Dentro del alcance**:

- Proceso de **adición de créditos** y **novedad de notas**, en la **Sede Cali**.
- Backend (API REST + Swagger) + frontend mínimo a cargo del compañero del equipo.
- Motor de workflow capaz de modelar los dos procesos sin código duplicado.
- Persistencia en PostgreSQL, autenticación JWT, generación de PDF, auditoría inmutable.

**Fuera del alcance del MVP** (hipótesis a validar con tutor):

- Integración técnica con **Class** o **QF** (cajas negras: el sistema entrega el PDF y el ser humano lo asienta donde corresponda).
- Otros procesos académicos (homologaciones, cancelaciones, reingresos, etc.).
- Otras sedes de la Universidad Remington.
- App móvil nativa.
- Despliegue en producción institucional (la entrega esperada es **demo presentable**, no piloto institucional).

---

## 9. Métricas de éxito propuestas (a refinar con tutor)

Las tres variables de la pregunta de investigación se operacionalizan así:

| Variable | Métrica propuesta | Línea base (manual) | Meta MVP |
|----------|-------------------|---------------------|----------|
| **Tiempo de ciclo** | Mediana del tiempo entre creación y resolución de una solicitud. | 1 semana – 2 meses (rango). | Reducir la mediana, con dato comparable simulado en demo. |
| **Re-trabajo** | Número promedio de devoluciones por solicitud. | No medido formalmente. | Establecer línea base medible vía auditoría del propio sistema. |
| **Opacidad** | Capacidad de reconstruir el histórico de una solicitud en menos de 1 minuto. | Imposible sin leer hilos de correo. | Timeline accesible al estudiante y al coordinador en una sola vista. |

> **Nota**: estas métricas son propuestas iniciales; deben validarse con el tutor cuando sea asignado y con la coordinación académica antes de fijar criterios de aceptación finales.

---

## 10. Supuestos y riesgos identificados

| Tipo | Descripción | Estrategia de mitigación |
|------|-------------|--------------------------|
| **Supuesto** | La coordinación académica de Sede Cali estará disponible para validar prototipos al menos una vez por sprint. | Calendarizar revisiones al cierre de cada sprint desde el sprint 0. |
| **Supuesto** | La normativa institucional permite que un trámite se origine fuera de Class siempre que el documento formal final se asiente en QF. | Confirmar por escrito con la coordinación antes del cierre del sprint 1. |
| **Riesgo** | La firma digital institucional no es estandarizable a corto plazo. | Como fallback, sello electrónico verificable (hash + timestamp + actor) sobre el PDF generado por el sistema. |
| **Riesgo** | El plazo de 2,5 meses se reduce si la asignación del tutor se retrasa. | Avanzar el documento de requisitos y la arquitectura como artefactos independientes del tutor; obtener feedback informal en paralelo. |
| **Riesgo** | La plantilla institucional del trabajo de grado y la rúbrica de evaluación todavía no están en posesión del equipo. | Pivotar el formato del documento cuando lleguen; mantener el contenido portable en Markdown para reformatear rápido. |

---

## 11. Próximos pasos

1. **Validar este documento** con el usuario (revisor: tú) y, cuando esté disponible, con el tutor asignado y la coordinación académica.
2. **Bajar SP1–SP7 al backlog Scrum**: épicas, historias de usuario y criterios de aceptación.
3. **Documento de requisitos (SRS, IEEE 830)**: requisitos funcionales y no funcionales derivados de SP1–SP7.
4. **Arquitectura inicial** (vistas C4 + 4+1 + modelo entidad-relación), apuntando a reusar el chasis Spring Boot 4 / Java 21 / PostgreSQL del proyecto Convenia.

---

## Referencias

- Ortegón, E., Pacheco, J. F. y Prieto, A. (2005). *Metodología del marco lógico para la planificación, el seguimiento y la evaluación de proyectos y programas*. Serie Manuales N.º 42, CEPAL/ILPES. Naciones Unidas.
- Anderson, L. W. y Krathwohl, D. R. (eds.) (2001). *A Taxonomy for Learning, Teaching, and Assessing: A Revision of Bloom's Taxonomy of Educational Objectives*. Longman.
- Universidad Remington. Resolución 015939 del 1 de septiembre de 2023 (renovación del registro calificado del programa de Ingeniería de Sistemas, modalidad Distancia, SNIES 53112).
- Entrevistas a la Coordinación Académica de la Sede Cali, registradas en `transcript-entrevista-coordi.md` y `transcript-entrevista-coordi-2.md`.
