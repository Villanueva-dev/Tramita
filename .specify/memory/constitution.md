<!--
Sync Impact Report — Constitución de Trámita
============================================
Cambio de versión: (plantilla sin versionar) → 1.0.0
Ratificada: 2026-07-02 | Última enmienda: 2026-07-02
Bump: MAJOR (ratificación inicial — de plantilla a constitución vigente)

Principios definidos (desde placeholders):
- I.   Simplicidad primero (KISS + YAGNI)
- II.  Arquitectura por feature (Screaming Architecture)
- III. Seguridad por defecto
- IV.  Decisiones defendibles y trazables
- V.   Testing del comportamiento sensible

Secciones agregadas:
- Restricciones tecnológicas
- Idioma y convenciones
- Proceso y gestión (Scrum, sprints de 2 semanas)

Plantillas dependientes (verificadas, alineadas, sin cambios):
- OK .specify/templates/plan-template.md  (el "Constitution Check" se resuelve en runtime)
- OK .specify/templates/spec-template.md  (genérico, sin principios hardcodeados)
- OK .specify/templates/tasks-template.md (tests OPTIONAL: coherente con el Principio V)

TODOs pendientes: ninguno
-->

# Constitución del proyecto Trámita

## Principios rectores

### I. Simplicidad primero (KISS + YAGNI)

Se construye lo mínimo que cumple el requisito. No se agregan columnas, capas ni
abstracciones especulativas ("por si acaso"). El sistema crece con migraciones Flyway
cuando el requisito **exista**, no cuando se anticipa.

**Rationale**: en un equipo de dos personas con plazo acotado, cada pieza de más es
deuda que hay que mantener y defender. La extensibilidad la da el proceso de migraciones
versionadas, no las estructuras pre-construidas.

### II. Arquitectura por feature (Screaming Architecture)

El código se organiza *package-by-feature*, no por capa. Cada feature es autocontenida
(controller, service, repository, model y DTOs de esa feature viven juntos). El árbol de
paquetes DEBE reflejar el dominio, en correspondencia con los diagramas C4.

**Rationale**: la estructura del código debe "gritar" el dominio (un motor de workflow),
no la mecánica técnica. Cada componente del diagrama C4 corresponde a una carpeta real,
lo que cierra la brecha modelo-código.

### III. Seguridad por defecto

La autenticación usa sesión del lado del servidor con cookie `HttpOnly; Secure;
SameSite=Strict` (patrón BFF); NO se usa JWT. Las contraseñas se almacenan con BCrypt.
Los DTOs en la frontera de la API son obligatorios — NUNCA se exponen entities. La
validación autoritativa DEBE ocurrir en el backend; la validación del frontend es solo UX.

**Rationale**: elegir la opción segura más simple que cumple el requisito, respaldada por
OWASP e IETF, en lugar de tecnología de moda que resuelve problemas que este sistema no tiene.

### IV. Decisiones defendibles y trazables

Toda decisión arquitectónica DEBE poder justificarse con un trade-off explícito
("elegí X frente a Y, sabiendo que el costo es Z"). La especificación precede al código.
Se privilegia la trazabilidad requisito → código (IEEE 830, C4 / 4+1). Las afirmaciones
técnicas y normativas que sustentan decisiones DEBEN verificarse contra documentación
oficial vigente (vía Context7) y citarse con su URL en la documentación del proyecto.

**Rationale**: es un trabajo de grado que se defiende ante un jurado. Una decisión sin
trade-off explícito ni fuente verificable no debería estar en el código ni en el documento.

### V. Testing del comportamiento sensible

El comportamiento crítico, no obvio o de alto costo de regresión DEBE tener tests. No se
testea lo trivial por dogma. Los tests se priorizan por valor, no por cobertura nominal.

**Rationale**: con un primer sprint de dos semanas, el esfuerzo de testing se invierte
donde el riesgo lo justifica, no en inflar una métrica de cobertura.

## Restricciones tecnológicas

- Stack fijo, chasis heredado de Convenia: **Spring Boot 4 / Java 21 / PostgreSQL**, Maven.
- **Flyway gestiona el schema; Hibernate solo valida** (`ddl-auto: validate`). Todo cambio
  de schema se hace con una migración nueva, nunca a mano.
- Los errores se devuelven según **RFC 7807** (`application/problem+json`).
- Los servicios se exponen siempre por interface; los controllers inyectan la interface.
- **Class** y **QF** son cajas negras: no se integran técnicamente. El sistema entrega el
  documento formal y un humano lo asienta donde corresponde.

## Idioma y convenciones

- Documentación, commits y comentarios en **español** (neutral/profesional).
- Identificadores de código (clases, métodos, variables) en **inglés**.

## Proceso y gestión

- El proyecto se gestiona con **Scrum**, en sprints de **2 semanas** (al menos para la
  primera entrega).
- Cada sprint cierra con objetivos verificables y demostrables.
- El flujo de trabajo sigue Spec-Driven Development (Spec Kit): la especificación, el plan
  y las tareas preceden a la implementación. El código se genera en la fase de implementación,
  no antes.

## Gobernanza

La constitución prevalece sobre cualquier otra práctica del proyecto. Las enmiendas se
documentan y versionan según SemVer (MAJOR: cambios incompatibles de principios; MINOR:
nuevo principio o guía materialmente ampliada; PATCH: aclaraciones y refinamientos). Cada
especificación y plan verifica su alineación con estos principios; toda complejidad
introducida debe justificarse explícitamente. La guía operativa del día a día vive en
`CLAUDE.md`.

**Versión**: 1.0.0 | **Ratificada**: 2026-07-02 | **Última enmienda**: 2026-07-02
