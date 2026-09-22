# Implementation Plan: Bandeja de trabajo de la coordinación

**Branch**: `007-coordination-inbox` | **Date**: 2026-09-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-coordination-inbox/spec.md`

## Summary

Dar a la Coordinación una vista de las solicitudes que **esperan su acción**, ordenadas por
cuánto llevan esperando, y sacar del cliente el reconocimiento de códigos de estado que hoy lo
obliga a conocer trámites concretos.

El enfoque técnico cabe en una frase: **no se construye casi nada, se conecta lo que ya
existe**. El endpoint está, el dato de quién debe actuar está en la configuración, el instante
de la última transición está en el timeline inmutable, y las marcas de estado inicial y final
están en la base desde la primera migración del motor. Lo que falta es el **criterio** de
selección, el **orden**, y exponer tres datos que nunca salieron.

Consecuencia directa: **esta feature no lleva migración Flyway**. La última del repositorio
sigue siendo `V4.1.0`.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.7 (Web MVC, Data JPA, Validation, Security 7),
Lombok. Ninguna dependencia nueva.

**Storage**: PostgreSQL con Flyway en modo `validate`. **Sin migración en esta feature**:
`workflow_transition.responsible`, `workflow_state.is_initial/is_final` y
`request_transition_log.occurred_at` existen desde `V2.0.0`.

**Testing**: JUnit 5 + AssertJ para unitarios; Testcontainers (`@ServiceConnection`) para los
IT. La suite base sobre `29acf33` es **157 unitarios + 96 IT**; al cerrar la implementación
(`d221b69`) es **160 unitarios + 108 IT**.

**Target Platform**: servicio HTTP, despliegue en Linux.

**Project Type**: servicio web (backend); el frontend vive en otro repositorio y no se toca en
esta entrega.

**Performance Goals**: no hay objetivo de rendimiento. El volumen documentado es de **30–40
solicitudes por semestre** para el trámite más frecuente, concentradas al inicio del periodo.

**Constraints**: la consulta de la bandeja no debe incurrir en N+1 al resolver el instante de
espera de cada solicitud (research D3). El resultado va acotado por un `Limit` explícito, que
la firma del repositorio ya exige desde la 004.

**Scale/Scope**: Sede Cali, dos trámites configurados, una cuenta de usuario. Dos endpoints
cambian de contrato (`/requests/inbox` y `/workflow-definitions`), ninguno nuevo; `StateResponse`
gana `isInitial` y eso se propaga, de forma aditiva, a toda respuesta que anide un estado.

## Constitution Check

*GATE: debe pasar antes de la Fase 0. Re-evaluado después de la Fase 1.*

| Principio | Evaluación | Evidencia |
|---|---|---|
| **I — KISS + YAGNI** | ✅ Pasa | Sin migración, sin columnas, sin caché, sin índices nuevos, sin endpoint nuevo. Se descartaron explícitamente la columna desnormalizada (D1), la vista materializada (D1) y el endpoint paralelo (D7). |
| **II — Arquitectura por capas** | ✅ Pasa | Se tocan `controller/`, `dto/`, `repo/`, `service/` y `service/impl/`, en su sitio. Sin clases nuevas fuera de esas capas. |
| **III — Seguridad y minimización** | ✅ Pasa, y se refuerza | `InboxEntryResponse` sigue **sin** documento de identidad; el quickstart lo verifica explícitamente (paso 2). Se listan nombre y trámite, que es el mínimo para identificar y priorizar. Los endpoints siguen bajo sesión. |
| **IV — Decisiones trazables** | ✅ Pasa | Ocho decisiones en `research.md`, cada una con su alternativa rechazada y su costo aceptado. La decisión del catálogo se **midió con un spike** antes de elegirse. |
| **V — Testing del comportamiento sensible** | ✅ Pasa | Lo sensible acá es el **criterio de selección** —incluir de más o de menos es el fallo caro— y la **medición de la espera**. El criterio vive en la consulta, así que sus tests son de integración contra Postgres (T005–T007) y el mutante ataca la consulta (T017); la medición se prueba en el unitario del servicio (T020–T022) con sus mutantes (T027–T028). Un test con el repositorio mockeado no puede probar una consulta. |
| **VI — Workflow configurable por dato** | ✅ Pasa, y es el eje | El responsable viaja como **parámetro**: el código no contiene ni `COORDINACION` ni ninguna otra etiqueta (D2). Un test debe demostrar que una definición nueva sembrada por SQL aparece en la bandeja sin desplegar (SC-005). |
| **VII — Trazabilidad inmutable** | ✅ Pasa | No se escribe en `request_transition_log`: solo se lee. La medición de la espera **hereda** la inmutabilidad del trigger `trg_timeline_immutable` sin agregar mecanismo. Se rechazó una columna `state_since` precisamente porque sería una copia mutable de un dato inmutable (D3). |

**Resultado del gate: pasa sin violaciones.** La sección *Complexity Tracking* queda vacía.

### Una tensión que no es violación, pero se declara

La reutilización de `GET /requests/inbox` (D7) **enmienda un contrato existente de forma no
aditiva**: la 004 lo definió como «las más recientes, sin criterio» y pasa a ser «las que
esperan a un responsable». No contradice ningún principio —de hecho el §I es lo que la
recomienda frente a un endpoint paralelo— pero cambia una promesa escrita. Se trata como
enmienda declarada en el contrato de esta feature, con el mismo procedimiento que la PR #45
usó para enmendar el de la 002, y se apoya en un hecho medido: **ningún cliente lo consume**.

## Project Structure

### Documentation (this feature)

```text
specs/007-coordination-inbox/
├── spec.md              # Qué y por qué, con las tres decisiones cerradas
├── plan.md              # Este archivo
├── research.md          # D1–D8, con trade-offs
├── data-model.md        # Qué se lee; por qué no hay migración
├── quickstart.md        # Verificación contra servidor real
├── checklists/
│   └── requirements.md  # Calidad de la spec — completo
└── tasks.md             # Lo genera /speckit-tasks, no este comando
```

### Source Code (repository root)

```text
src/main/java/com/uniremington/api/tramita/
├── controller/
│   ├── RequestController.java              # el parámetro `responsible` y la cota
│   └── WorkflowDefinitionController.java   # tipo de retorno del catálogo
├── dto/
│   ├── InboxEntryResponse.java             # + waitingSince, pendingResponsible, origin
│   ├── StateResponse.java                  # + isInitial
│   └── WorkflowDefinitionDetailResponse.java   # NUEVO, solo para el catálogo
├── repo/
│   ├── IRequestRepo.java                   # consulta por responsable (reemplaza a findAllByOrderByCreatedAtDesc, eliminada)
│   └── IRequestTransitionLogRepo.java      # timeline del lote en una consulta: origen y espera (evita N+1)
└── service/
    ├── IRequestService.java                # firma de la bandeja
    ├── IWorkflowDefinitionService.java     # tipo de retorno
    └── impl/
        ├── RequestServiceImpl.java         # criterio, orden, derivación de origin
        ├── StateResponseMapper.java        # NUEVO: único constructor de StateResponse (T033, T037)
        └── WorkflowDefinitionServiceImpl.java  # mapeo de estados

src/test/java/com/uniremington/api/tramita/
├── controller/
│   ├── RequestControllerIT.java            # bandeja extremo a extremo, incluido el criterio (T005–T007)
│   └── WorkflowDefinitionControllerIT.java # estados en el catálogo
├── service/impl/
│   └── RequestServiceImplTest.java         # medición de la espera y origin (el criterio va en el IT)
└── controller/
    └── WorkflowGenericityIT.java           # SC-005 (T040) e invariante de configuración (T018, FR-014)
```

**Structure Decision**: se conserva la estructura *package-by-layer* del §II sin excepciones.
No se crea ningún paquete nuevo: cada pieza cae en la capa que ya existe. `contracts/` de esta
feature es un **delta** sobre los contratos previos, igual que en la 006.

## Complexity Tracking

> Sin entradas: el Constitution Check pasó sin violaciones que justificar.

## Lo que este plan deja explícitamente fuera

- **Roles y control de acceso** — decidido en la spec (FR-003a). La bandeja filtra, no impide.
- **Umbral de vencimiento y días hábiles** — decidido en la spec (FR-005). Se mide, no se juzga.
- **Orden del recorrido / stepper** — decidido en la spec (FR-011b) y en research D6.
- **El frontend** — otro repositorio. Esta entrega le deja el contrato listo; `front#9`,
  `front#10` y `front#13` se atienden allá.
- **Notificaciones** — son SP7 (issue #13), otra feature.
