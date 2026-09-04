# Specification Quality Checklist: Formularios validados y reglas de negocio por trámite

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-03
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

## Trazabilidad requisito → escenario

Regla heredada del gate de la feature 002: **un requisito que afirma una prohibición necesita un
escenario que la ejercite**. Un `MUST NOT` sin escenario es una intención, no un requisito
verificable.

| Requisito | Escenario que lo ejercita |
|---|---|
| FR-001 captura del formulario | US1-1 |
| FR-002 asignaturas con su cardinalidad | US1-1, US1-2, US1-3 |
| FR-003 misma estructura en ambos trámites | US1-1, US1-2 |
| FR-004 motivo acotado | US2-7 |
| FR-005 datos capturados inmutables | US1-5 |
| FR-006 compatibilidad del contrato | US1-4 |
| FR-007 parámetros como configuración | US3-1 |
| FR-008 tope de créditos | US2-1, US2-2 |
| FR-009 créditos positivos (**prohibición**) | US2-3 |
| FR-010 configuración ausente (**prohibición**) | US2-4 |
| FR-011 configuración inválida (**prohibición**) | US2-6 |
| FR-012 rango de notas configurable | US2-5 |
| FR-013 versionado de parámetros (**prohibición**) | US3-3 |
| FR-014 valores distintos por trámite | US3-2 |
| FR-015 transición condicionada | US4-1 |
| FR-016 bloqueo sin alterar estado (**prohibición**) | US4-2 |
| FR-017 transición sin regla | US4-3 |
| FR-018 motor solo conoce el nombre (**prohibición**) | US4-4 |
| FR-019 regla no reconocida (**prohibición**) | US4-5 |
| FR-020 no almacenar correo (**prohibición**) | US1-6 |
| FR-021 solo Coordinación autenticada (**prohibición**) | US1-7 |
| FR-022 fixtures sintéticos | — regla de proceso, se verifica por inspección del código, no por comportamiento en ejecución |

## Notes

**Iteración 1** — la primera redacción falló el ítem «All functional requirements have clear
acceptance criteria». Seis requisitos afirmaban una prohibición sin escenario que la ejercitara:
FR-004, FR-005, FR-011, FR-018, FR-020 y FR-021. Los tres primeros vivían solo como *edge case*,
que describe la situación pero no la convierte en criterio de aceptación.

Corregido agregando US1-5, US1-6, US1-7, US2-6, US2-7 y US4-4. Al escribir US4-5 apareció además un
requisito que faltaba —qué hace el motor ante una regla que no reconoce— y se incorporó como FR-019,
renumerando la sección de minimización de datos.

**Iteración 2** — todos los ítems pasan.

### Decisión de alcance resuelta antes de redactar

`prioridad` y `fecha de vencimiento` quedaron **fuera** de esta feature. Se consultó porque el
prototipo de referencia los traía, pero `rg -in 'prioridad|urgente' material-coord/` no devuelve
respaldo en las entrevistas y ya existía la decisión de diferirlos a SP5. Documentado en Assumptions.

### Riesgo residual declarado

El máximo de créditos entra como **provisional y no auditado** (constitución v2.2.2 §IV): su
respaldo es derivado y el reglamento estudiantil no se ha obtenido. Ver la sección «Respaldo
normativo pendiente» de la spec. Esto no bloquea la planificación —el diseño hace del valor un dato
configurable— pero debe declararse en la defensa.
