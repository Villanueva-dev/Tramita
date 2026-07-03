# Specification Quality Checklist: Autenticación de la Coordinación Académica (login)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-07-02
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

- **Resuelto (2026-07-03)**: los 3 marcadores [NEEDS CLARIFICATION] fueron decididos y el spec quedó sin marcadores:
  1. Recuperación de contraseña olvidada → FUERA del MVP; re-provisión por script (FR-012 retirado, ver Assumptions).
  2. Política de contraseñas → mínimo 15, máximo 72, sin composición forzada, sin rotación (FR-006). Blocklist diferido a v2.
  3. Fuerza bruta → throttling temporal auto-reparable, sin bloqueo permanente (FR-010).
- Menor resuelto: nueva contraseña igual a la actual → se rechaza, sin historial de contraseñas (Edge Cases).
- Menor resuelto: US4 (cerrar sesión) se mantiene como parte del ciclo de autenticación por sesión.
- El mínimo de 15 surge de verificar **NIST SP 800-63B Rev 4 (26-ago-2025)**, que exige 15 para contraseña como único factor (corrige el mínimo de 8 de la Rev 3, superada).
- Fundamentos verificados y por documentar en Notion: NIST SP 800-63B-4 (https://pages.nist.gov/800-63-4/sp800-63b.html) y OWASP Authentication Cheat Sheet (https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).
