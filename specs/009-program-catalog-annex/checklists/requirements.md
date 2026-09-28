# Specification Quality Checklist: Catálogo de programas y anexo exigido por programa

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-25
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

- Validación del 2026-09-25, misma sesión en que se escribió la spec: 16/16. Las citas a archivos y líneas (`PublicRequestBody.java:51`, `RequestControllerIT.java:834`, `evidencia-entrevistas-coordinacion.md:184/553/762`) son **evidencia**, no diseño: siguen el precedente de la 008 y no prescriben implementación.
- Sin marcadores `[NEEDS CLARIFICATION]`. Dos decisiones se tomaron con valor por defecto y quedaron escritas en Assumptions para el gate `review-spec`; el gate las confirmó (abajo).
- Dos supuestos siguen marcados **a validar con la Coordinación**: que la lista de trece programas es la oferta de pregrado de la sede, y que la novedad de notas no tiene anexo por programa.

### Gate `review-spec` del 2026-09-25

Se re-ejecutaron las nueve citas a líneas de la spec (004 D10, 006 FR-010, 008 FR-010/FR-013/D5, constitución `:289`, `RequestControllerIT.java:834`, evidencia `:184/:553/:762`) y todas devuelven lo que la spec afirma. Las tres decisiones que la spec dejaba al gate:

| Decisión | Resultado | Sostén |
|---|---|---|
| **¿La facultad se deriva del programa?** | **No: sigue escrita**, como la piden los once campos del DO-FR-100 (004, D10). | No existe el mapa programa→facultad de la sede; agregarlo después es aditivo. |
| **¿El requisito de anexo se muestra solo en un estado?** | **No: siempre**, desde el registro (FR-009, FR-010). | Mostrarlo en un estado obligaría al motor a saber cuál es «la facultad», que es justo lo que el §VI le quita; y la Coordinación pide el documento **antes** de reenviar (`:553`). |
| **¿Entran los tres anexos universales de la novedad de notas?** | **Fuera de la 009.** | La regla del #40 es por programa; el re-trabajo medido es solo la hoja de vida (`RequestControllerIT.java:834`, `:184`); una regla «para todos los programas» se agrega después de forma aditiva, con issue propio si la Coordinación la pide. |

### Qué corrigió el gate

Dos errores de hecho, ambos medidos con comando antes de corregirlos:

- **Conteo de tildes**: la spec decía que cuatro de los trece nombres llevan tilde; son **diez** (solo Derecho, Medicina y Medicina Veterinaria no).
- **La lista del cliente interno**: la spec decía que cuatro de sus cinco programas no estaban en el catálogo; es al revés. `lib/ui-constants.ts` en `origin/main` del front trae Ingeniería de Sistemas, Administración de Empresas, Contaduría Pública, Derecho y Psicología: **cuatro coinciden y solo Psicología queda fuera**. El orden de despliegue sigue valiendo, pero por el canal público, no por el interno.

Además se nombró el issue del catálogo, #50, que al redactar la spec todavía no existía.
