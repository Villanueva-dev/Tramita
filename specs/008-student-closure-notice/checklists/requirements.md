# Specification Quality Checklist: Aviso de cierre al estudiante

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-23
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

**El checklist se completó en la segunda iteración.** La spec tiene 16 requisitos funcionales,
6 criterios de éxito y 3 historias priorizadas que se pueden probar por separado.

### Qué falló en la primera iteración

- **FR-002 no era testeable**: decía «móvil colombiano» sin definirlo. Ahora lo define como
  10 dígitos que empiezan por 3, y aclara que en cualquier otro caso se mantiene el correo.

### Sobre «no implementation details»

Los requisitos no nombran tecnologías. Las *Assumptions* sí citan archivos, migraciones, la
RFC 6068 y la FAQ de WhatsApp, pero como **evidencia** de un hecho medido, no como diseño.
Es el mismo criterio que aplicó la 007, y responde al Principio IV (decisiones trazables).

### La decisión que cambió al redactar

| Decisión | Resultado | Sostén |
|---|---|---|
| **Canal del aviso** | **Correo prellenado (P1) y WhatsApp (P2)**, los dos como acciones manuales de la Coordinación. | Al verificar las citas, apareció que `arbol-de-problemas.md:164` —la línea que iba a respaldar el WhatsApp— dice que la Coordinación *«solo acepta notificación al cierre por correo institucional»* y que el chat es *«decisión de ingeniería del equipo […], no un pedido de la coordi»*. El verbatim está en `material-coord/parte2-entrevista3.md:1`. El usuario eligió ofrecer los dos canales, con el correo como principal. |

### Supuestos que quedan marcados para validar con la Coordinación

1. Que al estudiante le llega mejor por WhatsApp que por correo (justifica la P2).
2. Que el rechazo se avisa por el mismo canal que el cierre exitoso.
3. Que el aviso por correo abre en su equipo (si no abre, se agrega la opción de copiar el texto).
