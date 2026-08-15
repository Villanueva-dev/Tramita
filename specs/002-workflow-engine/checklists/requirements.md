# Specification Quality Checklist: Motor de workflow configurable con timeline de auditoría

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-06
**Last run**: 2026-08-06 (tercera corrida, tras cubrir FR-012 con escenarios propios)
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

### Primera corrida (2026-08-06)

Se marcaron los 16 ítems en la primera iteración. **Ese resultado no se sostuvo**: una auditoría posterior contra las tres entrevistas a la Coordinación y el árbol de problemas encontró un hallazgo crítico y siete importantes. Al menos dos ítems estaban mal marcados — «Edge cases are identified» (faltaba el comportamiento de devolución y rechazo, que es el re-trabajo, una de las tres variables de la pregunta de investigación) y «Requirements are testable and unambiguous» (los «datos mínimos de identificación» y la «búsqueda básica» no eran verificables). Queda registrado: un checklist de auto-validación que aprueba todo a la primera no está funcionando como gate.

### Segunda corrida (2026-08-06, tras corregir el spec)

**Ítem no cumplido — «All functional requirements have clear acceptance criteria»**: FR-012 (solo la Coordinación autenticada puede registrar, avanzar y consultar) no tenía ningún acceptance scenario que lo ejercitara. Todos los escenarios existentes asumían que quien actúa es la Coordinación autenticada; ninguno ejercitaba la prohibición que el requisito afirma. Faltaba decidir si se cubría con escenarios propios en esta feature o si se consideraba heredado de `001-auth-login`. **Era el único ítem abierto y bloqueaba el cierre del checklist.**

### Tercera corrida (2026-08-06, tras cubrir FR-012)

**Los 16 ítems pasan.** El checklist queda cerrado y la feature habilitada para `/speckit-plan`.

**Resolución de FR-012**: se optó por **escenarios propios en esta feature**, no por herencia de `001-auth-login`. Se agregó un escenario de acceso no autenticado a cada una de las tres user stories que introducen operaciones nuevas — US1 (registrar), US2 (avanzar) y US3 (consultar y localizar) —, y FR-012 se precisó para declarar la garantía verificable por operación.

**Por qué no se declaró heredado**: la feature `001-auth-login` provee y prueba el **mecanismo** de autenticación, pero sus pruebas cubren los endpoints que existían cuando se escribieron. Los endpoints de esta feature no existían entonces. Si una operación nueva quedara fuera de la protección, ninguna prueba existente lo detectaría. Se distribuyeron los escenarios por user story —en lugar de concentrarlos en uno solo— para preservar la testeabilidad independiente de cada una.

Observaciones sobre otros ítems, para que consten:

- **SC-001** («menos de 1 minuto») es medible pero **no automatizable**: se verifica con la Coordinación en el piloto, no en la suite de pruebas. Proviene del árbol de problemas §9, donde está marcado como métrica a validar con el tutor.
- **FR-009** expresa la garantía de no-retroactividad como comportamiento observable; el mecanismo que la implementa se decide en `/speckit-plan`.
- El alcance se acota contra SP2 (feature `003`), SP3/SP4 (sprint 2) y SP5/SP7 (sprint 3) en Assumptions, y los límites de capacidad del motor se declaran explícitamente en «Límites declarados del motor».
- Las cadenas concretas de estados y transiciones de cada trámite **no están en el spec** — se derivan de las entrevistas y se validan con la Coordinación. Es insumo de `/speckit-plan` y sigue pendiente de escribirse y confirmarse.
