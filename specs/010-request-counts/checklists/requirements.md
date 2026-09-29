# Specification Quality Checklist: Conteos de solicitudes por trámite y estado

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-28
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

- Validado en una iteración el 2026-09-28. Sin marcadores `[NEEDS CLARIFICATION]`: las cuatro decisiones de fondo (quién clasifica, versiones, qué no trae, número) quedaron tomadas en el plan aprobado.
- Las citas `archivo:línea` (`RequestController.java:72-76`, `V2.1.0__…:90-92`, etc.) son **evidencia** del estado actual, no diseño: es la convención de las specs del repo (la 009 cita igual). No prescriben cómo implementar.
- Los códigos de estado (`EN_FACULTAD`, `FINALIZADA`, `DEVUELTA`) aparecen como datos de ejemplo de la configuración sembrada, no como literales que el sistema deba reconocer (FR-010 lo prohíbe).
- Revisión del 2026-09-28 con el usuario, cinco correcciones: FR-005 fija que un código conserva su sentido entre versiones (caso real: la v2 de la novedad, H-11); FR-006 dice que un parámetro se ignora en vez de «no se acepta»; SC-004 deja de fijar «14» (serán 15 con la v2); los Supuestos declaran la acumulación sin corte por periodo y la deuda de la opción 1 con el Principio VI.
- Puesta al día del 2026-09-29, tras rebasar sobre `main` con H-10 y H-11 mergeadas (PR #60, #61): la v2 de la novedad dejó de ser hipotética. Se re-midieron las citas: `WorkflowGenericityIT` pasó de `:132-157` a `:170-195`, FR-005 y SC-004 citan `V5.2.0` ya mergeada (15 estados vigentes), y los casos borde de versiones y devoluciones citan también la v2. Siguen valiendo `RequestController.java:72-76` y `:105-110`, e `IWorkflowDefinitionRepo.java:21-26`.
- Cobertura de FR por escenario: FR-001 → escenario 6; FR-002/FR-003 → 1; FR-004 → 3; FR-005 → caso borde «Versiones»; FR-006/FR-007 → 2; FR-008 → 4; FR-009 → SC-005; FR-010 → 5; FR-011 → sin escenario propio (se verifica en el contrato: ningún endpoint existente cambia).
