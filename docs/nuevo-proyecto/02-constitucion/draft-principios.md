# Constitución — borrador de principios (pre-Spec Kit)

> **Estado**: borrador con auditoría aplicada el **2026-06-01** (fixes **[C1]**, **[I1]**, **[I2]**, **[I3]**, **[I4]**, **[M1]**, **[M2]** del reporte del skill `auditar-vs-entrevistas` + revisiones KISS sobre herencia de Convenia confirmadas por el usuario). **Pendiente de materializar** en `.specify/memory/constitution.md` vía `/speckit-constitution`.
> **Próximo paso**: el usuario aprueba esta versión y se ejecuta el flujo del skill `speckit-constitution` para escribir el archivo final con sync impact report y commit.
> **No editar** `.specify/memory/constitution.md` directamente — eso lo hace el flujo del skill.

---

## Principios propuestos (6)

### I. Simplicidad sobre pureza arquitectónica

**Regla**: KISS, SOLID y Clean Code son **heurísticas guía**, no dogma. Patrones avanzados (DDD táctico, hexagonal, CQRS, event sourcing) **solo si una decisión concreta los justifica** documentadamente.

**Por qué**: MVP de 2,5 meses con equipo de 2 personas y demo presentable como entregable. El principal riesgo de scope es la sobre-ingeniería, no la deuda técnica. Coherente con la regla #6 del CLAUDE.md global del usuario.

---

### II. Reutilizar el chasis de Convenia con criterio selectivo

**Regla**: el stack base se hereda del proyecto hermano `../convenia/` **únicamente cuando agrega valor a este MVP**: Spring Boot 4, Java 21 LTS, PostgreSQL, Flyway-valida-Hibernate (`spring.jpa.hibernate.ddl-auto: validate`), errores RFC 7807, OpenAPI/Springdoc desde el día 1. **NO se hereda mecánicamente**: cada elemento se evalúa contra KISS de este MVP, no contra "así se hace en Convenia". Herencias explícitamente descartadas — multi-tenancy, MapStruct, JWT y listener de auditoría custom — se documentan en § Stack mandatorio con la justificación KISS de cada descarte.

**Por qué**: el árbol §11.4 declara la reutilización del chasis como decisión de partida, pero el dominio y el perfil del usuario son distintos. Convenia gestiona convenios con 5-6 protagonistas; acá hay un backoffice para una usuaria **no técnica** con dos trámites. Copiar plomería que no se va a usar es deuda técnica disfrazada de productividad y le agrega fricción al lector seis meses después.

---

### III. Workflow configurable por dato, no por código

**Regla**: el motor de workflow debe modelar **ambos trámites** (adición de créditos + novedad de notas) con la **misma maquinaria** parametrizada por configuración/datos. Si terminás con dos code-paths casi idénticos por trámite, se perdió el punto del proyecto.

**Invariante asociado (elevado desde SP3 del árbol, [M2] del reporte)**: **toda solicitud que alcanza el estado FINISHED produce un PDF formal**. El motor no permite cerrar el trámite sin el PDF generado. Razón operativa: la coordinadora necesita ese PDF como **evidencia para asentar el trámite en QF**; sin PDF no hay trámite válido en la cadena institucional.

**Por qué**: es la **pregunta de investigación misma** del árbol §6. La genericidad es el aporte académico; sin ella el proyecto colapsa a "dos formularios con flujo cableado", lo cual ya existe en el statu quo (Word + correo). El PDF como invariante asegura que ningún diseño futuro pueda "olvidarse" del entregable que cierra el ciclo con QF.

---

### IV. Trazabilidad inmutable como invariante, no como feature

**Regla**: toda transición de estado y toda aprobación generan un evento de auditoría **inmutable**. El histórico de una solicitud se reconstruye en cualquier momento y es consultable por estudiante, coordinador y auditor. El sistema combate **tres variables del árbol §6** — tiempo de ciclo, re-trabajo y opacidad — y la trazabilidad inmutable es el mecanismo que las tres requieren para poder medirse.

**Mecanismo**: tabla `solicitud_event` append-only con columnas mínimas `(solicitud_id, event_type, actor_id, occurred_at, payload_json, comment)`. UPDATE y DELETE revocados a nivel SQL para que ni el código de aplicación pueda modificar el histórico. Toda transición del motor de workflow escribe una fila — la invariante se concentra en el motor (Principio III), no se duplica en cada service. **No se usa Hibernate Envers** porque importa un modelo de "snapshot de entidad por revisión" cuando lo que necesitamos es un timeline de eventos de dominio explícitos; resolver con martillo lo que pide destornillador.

**Por qué**: SP6 del árbol; las tres variables — tiempo, re-trabajo, opacidad — son los efectos a combatir. Sin trazabilidad inmutable el sistema no es auditable y pierde justificación frente al proceso manual actual, donde el problema es justamente que el estado vive en correos y memoria humana.

---

### V. Testing pragmático selectivo

**Regla**:

- **Sí test** (no negociable): workflow engine (transiciones, invariantes), auth/RBAC, reglas de negocio derivadas de normativa institucional, generación de PDF / sello / firma, auditoría inmutable, validaciones de formularios.
- **No test**: CRUD trivial sin lógica, mapping de DTO ↔ entidad manual, configuración Spring, getters/setters generados.
- **Heurística**: si la capa toca datos académicos del estudiante, decisiones aprobables o trazabilidad → sí test. Si solo mueve datos sin transformarlos → no.
- **No TDD estricto** salvo solicitud explícita para una pieza concreta.

**Por qué**: MVP corto + equipo pequeño + tutor pendiente. Cobertura exhaustiva no aporta valor proporcional al tiempo disponible. Política ya guardada en memoria semántica (ver `feedback-testing-pragmatic.md`).

---

### VI. Sistemas externos institucionales como cajas negras

**Regla**: **Class** (sistema académico institucional) y **QF** (gestor documental de firmas) son **cajas negras** para este MVP. El sistema **no se integra técnicamente** con ninguno de los dos: no consume sus APIs, no escribe en sus bases de datos, no asume contratos sobre su comportamiento. El MVP se sitúa **aguas arriba**: orquesta el trámite, lo valida, lo audita, genera el PDF formal — y un humano (la coordinadora) lo asienta en Class y QF donde corresponda.

**Por qué**: el árbol §1 y §8 declaran esta posición explícitamente como decisión de scope. Integrar técnicamente con Class o QF requiere coordinación institucional y permisos de IT que son inviables en 2,5 meses. El valor del MVP no depende de esa integración: depende de que el ciclo de la solicitud quede orquestado y auditado **hasta el punto de asiento**. Cuando la coordinadora confirme apetito y madurez, futuras versiones pueden plantear integración — pero NO en este MVP.

---

## Secciones adicionales

### § Stack mandatorio (Additional Constraints)

Stack obligatorio del MVP, ya filtrado contra KISS:

- **Java 21 LTS**.
- **Spring Boot 4.x**.
- **PostgreSQL** como base de datos.
- **Flyway** gestiona el schema; Hibernate solo valida (`spring.jpa.hibernate.ddl-auto: validate`).
- **Mapping DTO ↔ entidad manual** con records y constructores explícitos. **No usar MapStruct** hasta que la cantidad de mappings duela visiblemente. Justificación: para 2 trámites con ~5-10 entidades, MapStruct es plomería con annotation processor innecesario que un compañero/a nuevo tendría que aprender para leer el repo.
- **Errores devueltos como `application/problem+json`** (RFC 7807) — default de Spring Boot 3+, casi gratis.
- **Autenticación provisional** con Spring Security + sesión por cookie HTTP. Razón KISS: el frontend es una SPA React en un único dominio, sin app móvil ni clientes externos, y el manejo de tokens/refresh de JWT agrega complejidad sin valor en este contexto. **Pendiente de confirmar**: si la universidad pide SSO institucional (pregunta #9 de `guia-entrevista-3.md`), la auth se migra a OAuth2/SAML antes de la primera demo institucional.
- **Auditoría con tabla `solicitud_event`** append-only, UPDATE/DELETE revocados a nivel SQL. Ver Principio IV para el detalle.
- **OpenAPI/Springdoc desde el día 1** para que el compañero/a genere cliente TypeScript en React.
- **Despliegue local con `docker-compose`** para el MVP. Producción institucional **no** está en alcance.

Cambios al stack requieren **enmienda formal de la constitución** con justificación y bump de versión.

**Herencias de Convenia explícitamente descartadas** (justificación KISS, decisión cerrada en sesión de 2026-06-01):

- ~~Multi-tenancy por filtro manual en cada query~~ — el MVP es **single-tenant explícito** (Sede Cali, una universidad). Sin respaldo en las entrevistas. Eliminado por **[C1]** del reporte de auditoría.
- ~~MapStruct~~ — overkill para el volumen de mapping de este MVP. Reemplazado por mapping manual con records.
- ~~Autenticación JWT~~ — cookie-session es más simple para SPA en un único dominio. Se reevalúa cuando se confirme SSO institucional.
- ~~Listener de auditoría custom~~ — reemplazado por tabla `solicitud_event` explícita (Principio IV).

---

### § Roles del MVP

El MVP tiene exactamente **dos roles** (decisión cerrada en sesión 2026-05-19; ver `project-mvp-scope-decisions.md` y árbol §8). Aplicado por **[I4]** del reporte.

- **Coordinadora Académica de la Sede Cali** — única usuaria que **actúa** sobre solicitudes: las recibe, valida, aprueba o devuelve, persigue firmas y reenvía a Registro Medellín. Es la **usuaria primaria** del MVP; el sistema vive o muere por su adopción. Perfil **no técnico**: experiencia con Word, correo, Class y QF como herramientas operativas, no con software de oficina avanzado.
- **Estudiante** — rol de **consulta exclusiva**. Ve el estado de **sus propias** solicitudes (no de otros estudiantes). **No actúa**: no aprueba, no edita estados ajenos, no carga documentos en nombre de terceros.

**Fuera del MVP** (a evaluar tras feedback de la coordinadora): rol Docente, rol Registro Medellín como usuario del sistema, rol Auditor académico, rol Admin del sistema. **No se agregan proactivamente** — solo si la coordinadora los pide explícitamente o una entrevista futura los justifica con evidencia. Coherente con KISS: ningún recurso se gasta en roles que la usuaria no pidió.

---

### § Workflow de desarrollo

**Spec Kit SDD obligatorio**: el ciclo `constitution → specify → plan → tasks → implement` se respeta. No implementar sin spec aprobada. No planificar sin spec. No saltar fases por "ir rápido".

**Auto-commits** de los hooks `after_*` en `.specify/extensions.yml` quedan **habilitados** — la historia de git seguirá el ritmo del Spec Kit. Los mensajes de commit van en **español** (ver `feedback-commits-spanish.md`), incluyendo los generados por los hooks.

**Decisiones grandes** (alcance, métricas, criterios de aceptación, arquitectura) marcadas como **"pendiente de validación con tutor"** hasta que el tutor sea asignado. Coherente con el riesgo §10 del árbol de problemas y con `project-tutor-status.md`.

**Cita de fuentes**: toda afirmación basada en documentación oficial, normativa institucional, RFCs o literatura académica debe incluir referencia exacta — coherente con la regla #4 del CLAUDE.md global del usuario.

---

### § Governance

**Plazo del MVP**: este MVP tiene un horizonte de **~2,5 meses** desde el inicio del planteamiento (2026-05-19), con entrega académica estimada hacia **principios/mediados de agosto 2026**. Toda decisión arquitectónica, de scope o de proceso que requiera más tiempo del que cabe en este horizonte **debe justificarse explícitamente** contra esta restricción o postergarse a una eventual fase post-MVP. Aplicado por **[M1]** del reporte.

**Enmiendas** a esta constitución requieren:

1. Documentación del **por qué** (qué cambia, motivación, alternativas consideradas).
2. **Sync** con templates dependientes (`.specify/templates/plan-template.md`, `spec-template.md`, `tasks-template.md`) — el skill `speckit-constitution` lo hace en su flujo paso 4.
3. **Bump de versión semver**:
   - MAJOR: principio removido o redefinido de forma incompatible.
   - MINOR: principio añadido o ampliación material.
   - PATCH: clarificaciones, redacción, typos.
4. **Commit** dedicado con mensaje en español (formato `docs: enmienda constitución a vX.Y.Z (…)`).
5. **Sync Impact Report** prepended como comentario HTML al inicio del archivo.

**Validación con tutor**: cuando el tutor sea asignado, su feedback puede forzar enmienda — potencialmente MAJOR si toca principios estructurales.

---

## Metadatos para la versión final

- **Constitution Version**: `1.0.0`
- **Ratification Date**: `2026-05-19`
- **Last Amended Date**: `2026-06-01`
- **Project Name**: **Trámita** — nombre propio acuñado, esdrújula intencional (*TRÁ-mi-ta*). Etimología: del verbo *tramitar*, convertido en esdrújula para crear identidad de producto. La tilde es parte del nombre y debe escribirse siempre. Decisión cerrada el 2026-06-01.

---

## Lo que falta decidir antes de escribir

- [x] ~~**Nombre del producto**~~ — decidido el 2026-06-01: **Trámita** (esdrújula, nombre propio acuñado del verbo *tramitar*).
- [ ] **Validar con la coordinadora** en entrevista N.º 3 los pendientes bloqueantes #1–#17 de `guia-entrevista-3.md` (especialmente firma digital vs escaneada, SSO institucional, normativa y formato de datos reales).
- [ ] **Ratificar esta versión 1.0.0** y ejecutar `/speckit-constitution` para materializarla en `.specify/memory/constitution.md` con sync impact report.
