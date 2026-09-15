# Implementation Plan: Captura pública del formato DO-FR-100

**Branch**: `004-public-request-capture` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-public-request-capture/spec.md`

## Summary

Se abre un canal de recepción sin autenticación para que el estudiante entregue el formato
DO-FR-100 firmado, y se agrega a la Coordinación una vista de solicitudes recientes que no
expone documentos de identidad.

El enfoque reusa el motor existente en vez de duplicarlo: la recepción pública construye el
mismo cuerpo de creación que ya valida y persiste la `003`, y delega en el registro
existente. Los tres elementos genuinamente nuevos son la apertura controlada del canal (con
límite de envíos y tope de tamaño calcado del que protege el login), la identidad sintética
que permite que el histórico siga nombrando un responsable en el tramo inicial, y la
habilitación por configuración de qué trámites admiten captura pública.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.7 (Security 7, Data JPA, Validation, WebMVC), Lombok. **Ninguna dependencia nueva.**

**Storage**: PostgreSQL con Flyway (`ddl-auto: validate`)

**Testing**: JUnit 5, Testcontainers (Postgres 16 vía `@ServiceConnection`), MockMvc, AssertJ, Mockito

**Target Platform**: Servidor Linux

**Project Type**: Servicio web con frontend separado (`../tramita-frontend`, Next.js)

**Performance Goals**: No hay meta de throughput. El volumen documentado es de 30–40 adiciones por semestre (Sesión 2, P24): el canal no está dimensionado por carga sino por resistencia al abuso.

**Constraints**: El cuerpo de un envío admite una firma trazada en base64 (~20–30 KB típicos); el tope se fija en 256 KB. El límite de envíos por origen reusa la ventana deslizante ya configurada para el login (5 eventos / 15 minutos).

**Scale/Scope**: Sede Cali, un único trámite habilitado inicialmente. 3 endpoints tocados (1 nuevo público, 1 nuevo autenticado, 0 modificados), 1 migración, 1 filtro nuevo.

## Constitution Check

*GATE: evaluado contra la constitución v2.3.0 antes de Phase 0 y re-evaluado tras Phase 1.*

| Principio | Estado | Evaluación |
|---|---|---|
| **§I** Simplicidad (KISS + YAGNI) | ✅ Pasa | Se eliminó la persistencia del teléfono por no tener consumidor documentado (FR-005a), aplicando el mismo criterio con que `V2.3.0` excluyó el correo. El throttling no inventa maquinaria: extrae la ventana deslizante que ya existe. Sin dependencias nuevas. |
| **§II** Arquitectura por capas | ✅ Pasa | Todo lo nuevo cae en las capas existentes (`controller/`, `dto/`, `service/` + `service/impl/`, `security/`). El filtro nuevo se acoge a la **excepción documentada** del propio §II: no lleva estereotipo, lo construye `SecurityConfig` con `new`. |
| **§III** Seguridad por defecto | ⚠️ Tensión justificada | Se abre el segundo endpoint sin autenticación del sistema. Ver Complexity Tracking. La minimización se refuerza (FR-005a, FR-014, FR-020) y **no se exponen entities**: la vista de recientes usa un DTO propio sin documento de identidad. |
| **§IV** Decisiones trazables | ✅ Pasa | Cada decisión con su trade-off en `research.md` (D1–D9). Los dos riesgos aceptados quedaron en el spec con lo que se descartó y por qué. |
| **§V** Testing del comportamiento sensible | ✅ Pasa | Los tests cubren seguridad (acceso sin sesión, cuenta sintética no autenticable), privacidad (ausencia de documento en el listado) y límites (413, 429). No se testea el mapeo trivial. |
| **§VI** Workflow configurable por dato | ✅ Pasa | **Corregido por este gate.** El borrador fijaba `ADICION_CREDITOS` en el servidor, lo que habría exigido desplegar código para habilitar novedad de notas. Se parametrizó con `PUBLIC_CAPTURE_ENABLED` en `workflow_parameter`. |
| **§VII** Trazabilidad inmutable | ✅ Pasa | No se toca el timeline ni su trigger. La identidad sintética existe precisamente para **no** aflojar la restricción de que todo tramo nombre un responsable. |

### Re-evaluación tras Phase 1

Con `research.md`, `data-model.md` y el contrato ya escritos, el gate se vuelve a pasar y el
resultado no cambia. Dos observaciones que el diseño confirmó:

- **§I se reforzó, no se relajó**: el diseño no agregó ninguna tabla ni entidad. Todo lo
  nuevo son dos columnas opcionales, una fila de configuración y una de identidad.
- **§III sigue con su tensión abierta y acotada**: la apertura del canal está confinada a
  una ruta, con su propio filtro, y el contrato declara explícitamente los cuatro modos de
  rechazo (404, 413, 422, 429). El resto del sistema no cambia su postura de seguridad.

Aparece además una **incertidumbre nueva que el diseño no puede cerrar**: no está
determinado si una firma manuscrita digitalizada constituye dato biométrico bajo la Ley 1581
de 2012, lo que cambiaría su régimen de tratamiento. Queda marcada como provisional y no
auditada según el §IV, y registrada en `research.md` D6.

### Lo que este gate corrigió

Dos cambios entraron al spec por haberlo evaluado contra la constitución, no antes:

1. **§VI** — el trámite pasó de literal en código a configuración en base de datos. Sin este
   gate, el motor configurable habría tenido su entrada más nueva cableada a un trámite:
   exactamente lo que la pregunta de investigación del proyecto dice evitar.
2. **§I + §III** — el teléfono dejó de persistirse. Es un dato personal sin consumidor
   documentado, y el repo ya tenía el precedente de excluir el correo por esa misma razón.

## Complexity Tracking

| Violación | Por qué es necesaria | Alternativa más simple, y por qué se rechazó |
|---|---|---|
| **Un endpoint sin autenticación** (§III abre su segunda excepción; hasta hoy solo el login) | Es el requisito. El estudiante no tiene ni tendrá cuenta: darle una contradiría la decisión, tomada con la Coordinación, de que no es usuario del sistema. | *Que la Coordinación siga transcribiendo*: es el statu quo que la feature viene a eliminar, y la fuente de los errores que devuelven el trámite. *Darle cuenta al estudiante*: multiplica el padrón de usuarios, exige recuperación de contraseña y contradice lo acordado. |
| **Desactivar CSRF en ese endpoint** | CSRF protege contra que un tercero actúe usando la sesión de la víctima. Sin sesión no hay identidad que suplantar: un atacante envía igual con cualquier cliente HTTP. Mantenerlo obligaría a una petición previa para obtener el token, sin ganancia de seguridad. | *Dejarlo activo*: da una sensación de protección que no existe y agrega un paso que puede fallar. Lo que sí protege este canal es el límite de envíos, que sí se implementa. |
| **Una fila en `users` que no es una persona** | La columna `actor_id` del timeline es `NOT NULL`, por §VII. Una solicitud pública no tiene usuario autenticado. | *Hacer `actor_id` nullable*: rompe la garantía de que todo tramo nombra un responsable, que es el §VII entero, para un solo caso. La fila sintética preserva la garantía y se marca inactiva, de modo que no puede iniciar sesión. |

## Project Structure

### Documentation (this feature)

```text
specs/004-public-request-capture/
├── plan.md              # Este archivo
├── spec.md              # Qué y por qué (fase anterior)
├── research.md          # Phase 0: decisiones D1–D9 con su trade-off
├── data-model.md        # Phase 1: campos nuevos y su justificación
├── quickstart.md        # Phase 1: cómo ejercitar el canal a mano
├── contracts/
│   └── openapi.yaml     # Phase 1: contrato de los dos endpoints
├── checklists/
│   └── requirements.md  # Checklist de calidad del spec
└── tasks.md             # Phase 2 (/speckit-tasks — NO lo crea este comando)
```

### Source Code (repository root)

```text
src/main/java/com/uniremington/api/tramita/
├── controller/
│   ├── PublicRequestController.java        # NUEVO — recepción sin sesión
│   └── RequestController.java              # + vista de recientes
├── dto/
│   ├── PublicRequestBody.java              # NUEVO — sin código de trámite en el cuerpo
│   ├── PublicReceiptResponse.java          # NUEVO — sin identificador ni estado
│   ├── InboxEntryResponse.java             # NUEVO — sin documento de identidad
│   └── CreateRequestBody.java              # + correo y firma, opcionales
├── model/
│   └── Request.java                        # + correo y firma
├── security/
│   └── PublicSubmissionThrottlingFilter.java  # NUEVO — sin estereotipo (§II)
├── service/
│   ├── IRequestService.java                # + registro público y vista de recientes
│   └── impl/
│       ├── RequestServiceImpl.java         # delega en el registro existente
│       ├── LoginAttemptService.java        # delega en el contador extraído
│       └── SlidingWindowCounter.java       # NUEVO — mecánica compartida
├── repo/
│   └── IRequestRepo.java                   # + consulta de recientes
└── shared/config/
    └── SecurityConfig.java                 # + apertura, exclusión CSRF, filtro

src/main/resources/db/migration/
└── V3.3.0__Enable_public_capture.sql       # NUEVO

src/test/java/com/uniremington/api/tramita/
├── controller/PublicRequestControllerIT.java   # NUEVO
├── controller/RequestControllerIT.java          # + vista de recientes
└── security/PublicSubmissionThrottlingFilterTest.java  # NUEVO
```

**Structure Decision**: se conserva la estructura *package-by-layer* vigente (§II). No se
crea ningún paquete nuevo: la recepción pública es un controlador más, su DTO vive con los
demás y su filtro con los de seguridad. El frontend vive en su propio repositorio
(`../tramita-frontend`) y se coordina por el contrato OpenAPI, no por acoplamiento directo.
