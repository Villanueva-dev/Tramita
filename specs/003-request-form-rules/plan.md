# Implementation Plan: Formularios validados y reglas de negocio por trámite

**Branch**: `003-request-form-rules` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-request-form-rules/spec.md`

## Summary

SP2 lleva al sistema el contenido real del formulario de cada trámite —datos académicos del
estudiante y las asignaturas involucradas— y hace que sus reglas de negocio sean
**configuración, no código**: el tope de créditos y el rango de notas viven como parámetros por
definición de trámite y se ajustan sin desplegar. Incluye la columna `guard_key` que la
decisión D3 de la feature `002` difirió explícitamente a esta feature.

**Enfoque técnico**: dos migraciones Flyway sobre el esquema de la `002`, una entidad y una
tabla nuevas para las asignaturas, una tabla de parámetros por definición, y un servicio de
reglas expuesto por interfaz que lee esos parámetros al registrar la solicitud. Las guardas de
transición se resuelven por nombre contra un registro de implementaciones, sin enum ni
reflection. Sin dependencias nuevas.

**Punto de partida no habitual**: existe un prototipo funcionando (`origin/router-ia`,
`82ece40`) que resolvió buena parte de esto fuera del ciclo SDD. **No se mergea**: se porta
selectivamente, corrigiendo los ocho hallazgos de su revisión. El plan documenta en cada
decisión si confirma o corrige al prototipo.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.7 (Web MVC, Data JPA, Validation, Security 7),
Lombok, Flyway. **Sin dependencias nuevas** en esta feature.

**Storage**: PostgreSQL 16. Flyway posee el schema; Hibernate en `ddl-auto: validate`.

**Testing**: JUnit 5, AssertJ, Mockito (`@MockitoBean`, nunca `@MockBean`), Testcontainers
para los tests de integración.

**Target Platform**: servicio HTTP en Linux; consumido por la SPA de la Coordinación.

**Project Type**: servicio web (backend del MVP), estructura *package-by-layer*.

**Performance Goals**: no hay objetivo cuantitativo propio de esta feature. El volumen real
—decenas de trámites por periodo, según las entrevistas— no impone requisitos de rendimiento.

**Constraints**: errores en RFC 9457 (`application/problem+json`); DTOs obligatorios en la
frontera; el contrato del POST se amplía sin romper clientes ni tests de la `002`; ninguna
migración existente se edita.

**Scale/Scope**: 2 migraciones, 2 entidades nuevas, 2 ampliadas, 1 repositorio nuevo,
1 servicio de reglas con su interfaz, 1 mecanismo de guardas. Estimado ~350 líneas de
producción más tests.

## Constitution Check

*GATE: pasa antes de Phase 0 y se re-evalúa después de Phase 1.*

| Principio | Evaluación | Estado |
|---|---|---|
| **I. Simplicidad (KISS + YAGNI)** | Tabla clave-valor en vez de motor de reglas (D1); dos parámetros en vez de formato compuesto (D4); el total de créditos no se persiste por derivable (D10); la cota superior de créditos no se hace configurable porque nada pide ajustarla (D8). **Tensión declarada**: el mecanismo de guardas se entrega sin ninguna guarda de producción (D5) — se justifica abajo. | ⚠️ con justificación |
| **II. Arquitectura por capas** | `IRequestBusinessRules` en `service/` con su `impl/`; `IWorkflowGuard` en `service/`; DTOs en `dto/`; entidades en `model/`; repositorio en `repo/`; excepción nueva en `shared/exception/`. Corrige al prototipo, que dejó la clase de reglas como `@Component` suelto en `impl/` sin contrato. | ✅ |
| **III. Seguridad por defecto** | No se persiste el correo del estudiante (FR-020, D9). DTOs en toda la frontera; ninguna entidad se expone. Los fixtures pasan a identificadores sintéticos evidentes y se corrigen las 6 ocurrencias heredadas (D11, FR-022). La autenticación se verifica sobre cada operación que la feature toca (FR-021). | ✅ |
| **IV. Decisiones trazables** | 11 decisiones con racional y alternativas en `research.md`. El tope de créditos entra **marcado provisional y no auditado**: respaldo derivado, reglamento estudiantil no obtenido. Se corrige el comentario «confirmado en entrevistas» del prototipo, que afirmaba más de lo que la fuente sostiene. | ✅ |
| **V. Testing del comportamiento sensible** | Los cuatro altos del review se cubren con test propio, incluido el que hoy **no existe**: parámetro ausente. El prototipo tiene un test que enmascara ese fallo (no stubea el repositorio y pasa por accidente). | ✅ |

**Restricciones tecnológicas**: stack sin cambios; Flyway apila `V2.3.0` y `V3.0.0` sobre
`V2.2.0`; errores RFC 9457; servicios expuestos por interfaz. ✅

**Re-evaluación post-Phase 1**: sin violaciones nuevas. El diseño no introdujo capas ni
abstracciones más allá de las declaradas.

## Project Structure

### Documentation (this feature)

```text
specs/003-request-form-rules/
├── plan.md              # Este archivo
├── spec.md              # 22 FR, 4 user stories, 8 SC
├── research.md          # Phase 0 — D1..D11
├── data-model.md        # Phase 1 — esquema y entidades
├── quickstart.md        # Phase 1 — verificación manual
├── contracts/
│   └── openapi.yaml     # Phase 1 — delta sobre el contrato de la 002
├── checklists/
│   └── requirements.md  # Gate de la spec, con trazabilidad requisito → escenario
└── tasks.md             # Phase 2 — lo genera /speckit-tasks, NO este comando
```

### Source Code (repository root)

```text
src/main/java/com/uniremington/api/tramita/
├── dto/
│   ├── CreateRequestBody.java        # ampliado + constructor de compatibilidad
│   ├── SubjectRequestBody.java       # nuevo
│   ├── SubjectResponse.java          # nuevo
│   └── RequestResponse.java          # ampliado con los datos del formulario
├── model/
│   ├── Request.java                  # ampliado + @OneToMany
│   ├── RequestSubject.java           # nuevo
│   ├── WorkflowParameter.java        # nuevo
│   └── WorkflowTransition.java       # ampliado con guardKey
├── repo/
│   └── IWorkflowParameterRepo.java   # nuevo
├── service/
│   ├── IRequestBusinessRules.java    # nuevo — contrato
│   ├── IWorkflowGuard.java           # nuevo — contrato de guarda
│   └── impl/
│       ├── RequestBusinessRulesImpl.java  # nuevo
│       └── RequestServiceImpl.java        # ampliado: cablea reglas y guardas
└── shared/exception/
    ├── IncompleteConfigurationException.java  # nueva
    └── GlobalExceptionHandler.java            # + handler de la anterior

src/main/resources/db/migration/
├── V2.3.0__Persist_request_form_data.sql      # nueva
└── V3.0.0__Configure_business_rules.sql       # nueva

src/test/java/com/uniremington/api/tramita/
├── service/impl/RequestBusinessRulesImplTest.java  # nuevo
└── controller/RequestControllerIT.java             # ampliado
```

**Structure Decision**: *package-by-layer*, según constitución §II. La feature no introduce
paquetes nuevos: cada pieza entra en la capa que le corresponde. Es el mismo árbol que
`main`, con las carpetas reales listadas arriba.

## Complexity Tracking

| Violación | Por qué se necesita | Alternativa más simple, y por qué se rechazó |
|---|---|---|
| El mecanismo de guardas (`IWorkflowGuard`, `guard_key`) se entrega **sin ninguna guarda de producción**, lo que roza el «por si acaso» del Principio I | La decisión **D3 de la feature 002** difirió explícitamente a esta feature la tabla de parámetros *y* la columna `guard_key`. Es un compromiso ya adquirido y trazado, no una anticipación nueva. Además SP3 y SP4 —el sprint siguiente— van a condicionar transiciones a que la solicitud tenga su documento generado y sellado | *Diferirlo otra vez a la `004`*: se rechaza porque la deuda ya lleva una feature de arrastre y volver a moverla la vuelve estructural. *Entregar además una guarda inventada en el seed*: se rechaza porque sería exactamente la columna especulativa que el Principio I prohíbe. El compromiso adoptado es entregar el mecanismo con guarda de prueba **solo en tests**, sin sembrar ninguna en producción |
| `reason` se declara `VARCHAR(2000)` en vez de `TEXT` | FR-004 exige longitud máxima; declararla en la columna hace que la restricción exista también fuera del caso de uso | *`TEXT` + `@Size` en el DTO* (lo que hace el prototipo): deja la única cota en la capa de aplicación, y cualquier otra vía de escritura la esquiva |
| Las notas pasan de texto a `NUMERIC(3,2)`, cambiando el tipo en el contrato | Una nota es un número; con `VARCHAR` la base acepta `'abc'` y la garantía vive solo en Java. Es el modo de falla que la `002` ya sufrió al cargar trámites por SQL crudo | *Mantener `VARCHAR(20)`* (lo que hace el prototipo): más barato hoy, pero traslada al futuro un cambio de contrato que hoy no cuesta nada porque el frontend aún no consume este formulario |

## Phase 0 — Research

Completado: [research.md](./research.md). Once decisiones (D1–D11) con racional, alternativas
descartadas y fuente. Sin `NEEDS CLARIFICATION` pendientes.

La única ambigüedad de alcance que apareció —si `priority` entraba— se resolvió **antes** de
redactar la spec, consultada al usuario y respondida «queda en SP5», con la verificación de
que no tiene respaldo en `material-coord/`.

## Phase 1 — Design & Contracts

Completado:

- [data-model.md](./data-model.md) — esquema de las dos migraciones, entidades JPA con el
  patrón Lombok establecido en `main`, repositorio, y la tabla requisito → dónde se valida.
- [contracts/openapi.yaml](./contracts/openapi.yaml) — **delta** sobre el contrato de la `002`,
  que no se edita por ser feature cerrada. Declara el `422` de regla de negocio y el `500` de
  configuración incompleta.
- [quickstart.md](./quickstart.md) — diez verificaciones manuales, incluidas las dos que
  reproducen los fallos del prototipo (créditos negativos y parámetro ausente).

## Riesgo declarado

**El valor `21` del tope de créditos es provisional y no auditado.** Su respaldo es derivado
—una síntesis analítica de entrevista, no un transcript— y el reglamento estudiantil no se ha
obtenido. Constitución v2.2.2 §IV: mientras la fuente no se obtenga, ninguna afirmación que
dependa de ella se presenta como hecho establecido.

El diseño mitiga el riesgo por construcción: el valor vive en configuración y se corrige sin
tocar código ni desplegar (FR-007, SC-005). Lo que **no** mitiga es la afirmación documental:
el comentario del seed debe decir «provisional», no «confirmado».
