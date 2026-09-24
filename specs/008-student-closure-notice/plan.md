# Implementation Plan: Aviso de cierre al estudiante

**Branch**: `008-student-closure-notice` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-student-closure-notice/spec.md`

## Summary

Cuando una solicitud recibida por el enlace público llega a un estado final, la Coordinación
puede abrir desde su detalle un **correo prellenado** al estudiante (P1, lo que ella pidió) y,
si el móvil lo permite, una **conversación de WhatsApp** con el mismo texto (P2). El sistema no
envía nada y no registra nada: prepara, y la Coordinación decide.

El enfoque técnico cabe en dos frases. **El backend expone hechos y el cliente decide**: el
detalle de una solicitud pasa a traer su origen, el correo y el teléfono declarados —tres campos
aditivos—, y con `currentState.isFinal`, que ya viajaba, el cliente tiene todo para ofrecer o no
el aviso sin una segunda consulta (research D4, D5). **Y el teléfono entra utilizable**: el API
exige diez dígitos en los dos canales de captura (research D3), que es la única enmienda no
aditiva de la feature y la que exige coordinar el despliegue con el front (research D9).

Consecuencia directa: **esta feature no lleva migración Flyway, no crea tablas, no emite eventos
y no construye ningún puerto de notificación**. La última migración del repositorio sigue siendo
`V4.1.0`. El puerto que el issue #13 pedía se reformula en la PR, porque un puerto sin nada que
ejecutar contradice el Principio I (spec, *Assumptions*, «Enmienda del issue #13»).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.7 (Web MVC, Data JPA, Validation, Security 7),
Lombok. **Ninguna dependencia nueva**: en particular, no entra `spring-boot-starter-mail`.

**Storage**: PostgreSQL con Flyway en modo `validate`. **Sin migración en esta feature**:
`request.student_email` y `request.student_phone` existen desde `V3.3.0`, `workflow_state.is_final`
y el timeline desde `V2.0.0`. La columna del teléfono sigue en `VARCHAR(30)`; la regla de diez
dígitos vive en el contrato de entrada, no en la base (data-model.md).

**Testing**: JUnit 5 + AssertJ para unitarios; Testcontainers (`@ServiceConnection`) para los
IT. La suite base es **163 unitarios + 112 IT**, medida en `cb85fd6` (la punta de la 007 que
mergeó como `412a5e0`). ⚠️ Se re-mide con `./mvnw clean verify` al arrancar la implementación;
el conteo no se cita de memoria. Un test existente pasa a rojo **por diseño** y se invierte a
conciencia (research D4); dos fixtures rompen por la regla del teléfono y se corrigen; los cuatro
fixtures del renderer **no se tocan**, porque uno de ellos es un canario con SHA-256 literal
(research D3).

**Target Platform**: servicio HTTP, despliegue en Linux.

**Project Type**: servicio web (backend). El frontend vive en otro repositorio, lo lleva otro
agente, y recibe su parte como brief en la sección «Reparto con el frontend» de este plan.

**Performance Goals**: no hay objetivo de rendimiento. El volumen documentado es de **30–40
solicitudes por semestre** para el trámite más frecuente.

**Constraints**: el detalle de una solicitud hace **un SELECT más** —el timeline— para derivar
el origen, en las tres acciones que lo devuelven (research D2). A este volumen es irrelevante;
la optimización, si algún día una medición la pide, está anotada y no se construye antes. El
`git grep` que prueba la tesis del §VI debe seguir devolviendo **una sola línea** (research D7).

**Scale/Scope**: Sede Cali, dos trámites configurados, una cuenta de usuario. **Ningún endpoint
nuevo**. Cambian de contrato, de forma aditiva, las tres acciones que devuelven el detalle
(`POST /requests`, `POST /requests/{id}/transitions`, `GET /requests/{id}`); y de forma **no
aditiva**, el campo `studentPhone` de los dos cuerpos de captura (`POST /public/requests/{code}` y
`POST /requests`).

## Constitution Check

*GATE: debe pasar antes de la Fase 0. Re-evaluado después de la Fase 1.*

| Principio | Evaluación | Evidencia |
|---|---|---|
| **I — KISS + YAGNI** | ✅ Pasa | Sin migración, sin tabla, sin evento, sin listener, sin puerto, sin dependencia de correo, sin endpoint nuevo. Lo único «nuevo» es un archivo de un enum que ya existía anidado (D1). Se descartaron con evidencia B′, el correo automático, `is_success`, la bandera derivada y el endpoint de «aviso» (D5, D6). |
| **II — Arquitectura por capas** | ✅ Pasa | Se tocan `dto/` y `service/impl/`, en su sitio. Ni `controller/` ni `repo/` cambian: los endpoints y la consulta del timeline ya existen. |
| **III — Seguridad y minimización** | ✅ Pasa, con la razón escrita | El contacto se expone **solo en el detalle** de una solicitud concreta, bajo sesión, y porque ahora tiene consumidor (D4; FR-008a corrige el javadoc que citaba un FR-020 que nunca habló de exponer). La búsqueda y la bandeja **no** lo llevan, y una guarda sobre el JSON servido lo fija con el precedente exacto de la 007. La validación del teléfono es **autoritativa en el backend** (D3); la regla del móvil que vive en el cliente es solo para decidir si ofrece un botón, nunca para aceptar o rechazar datos. Los fixtures usan números que no pertenecen a nadie conocido; ⚠️ no se encontró un rango de numeración reservado para ficción en Colombia (confianza baja), así que se prefieren valores de forma evidentemente sintética. |
| **IV — Decisiones trazables** | ✅ Pasa | Nueve decisiones en `research.md`, cada una con su alternativa rechazada, su costo aceptado y su comando o fuente: RFC 6068, FAQ de WhatsApp, CRC, `Pattern` de Java 21 y `@Pattern` de Bean Validation 3.0. El backend mínimo se **midió con un spike** (`spike/008-wa`) antes de planear. |
| **V — Testing del comportamiento sensible** | ✅ Pasa | Lo sensible es **qué sale y qué entra**: exponer de más en un listado es el fallo caro (§III), y aceptar un teléfono inutilizable deja la solicitud sin WhatsApp para siempre (FR-011). Los tests son de integración porque el origen lo produce el flujo real y la garantía es sobre el JSON servido (D8), con cinco mutantes previstos y el test que se invierte a conciencia (D4). |
| **VI — Workflow configurable por dato** | ✅ Pasa, y es el eje de SC-005 | «Final» se lee de `workflow_state.is_final` a través de `StateResponse.isFinal`, que ya viaja; el código no reconoce `FINALIZADA` ni `RECHAZADA` (D7). El aviso se ofrece igual para un trámite DEMO cargado por SQL, y `WorkflowGenericityIT` lo demuestra como en la 007. |
| **VII — Trazabilidad inmutable** | ✅ Pasa | Se **lee** la entrada de nacimiento del timeline; nunca se escribe. Avisar no deja registro (SC-006) y consultar el detalle tampoco: un test lo fija. Se descartó a propósito una tabla de avisos, que habría sido un registro nuevo con datos personales y sin ruta de corrección (issue #38, D6). |

**Resultado del gate: pasa sin violaciones.** La sección *Complexity Tracking* queda vacía.

### Una tensión que no es violación, pero se declara

La regla del teléfono (D3) **enmienda dos contratos existentes de forma no aditiva**: el de
captura pública (004) hoy acepta en `studentPhone` cualquier texto de hasta 30 caracteres, y el
interno (003/004) lo acepta opcional y sin forma. A partir de esta feature, los dos exigen
exactamente diez dígitos. Un cliente que mandara `300 123 4567` pasa de 201 a 422 (público) o
400 (interno). No contradice ningún principio —FR-009 y FR-010 lo exigen, y FR-013 lo declara—,
pero cambia una promesa escrita, y se trata con el mismo procedimiento que la 007 usó al
reutilizar `GET /requests/inbox` (`specs/007-coordination-inbox/plan.md:72-77`) y la PR #45 al
enmendar el contrato de la 002: nota de enmienda en el contrato histórico, forma vigente en el de
esta feature.

Hay una diferencia con la 007 que conviene no perder de vista: allá la enmienda se apoyaba en
que **nadie consumía** el endpoint. Acá **sí hay un consumidor**: el formulario público del
front manda el teléfono tal como se escribe (`Villanueva-dev/tramita-frontend#2`). Por eso la
enmienda no es solo una nota: es una **condición de despliegue**, que D9 fija y la PR del back
lleva escrita en su cuerpo. El filtro de dígitos del cliente entra antes, o a la vez.

Los tres campos que gana el detalle, en cambio, son **aditivos** (FR-013): un cliente que lee los
doce actuales sigue funcionando sin cambios.

## Project Structure

### Documentation (this feature)

```text
specs/008-student-closure-notice/
├── spec.md              # Qué y por qué; las decisiones de producto cerradas
├── plan.md              # Este archivo
├── research.md          # D1–D9, con trade-offs y comandos
├── data-model.md        # Qué se lee; por qué no hay migración; el invariante del teléfono
├── quickstart.md        # Verificación contra servidor real, con el mailto y el wa.me armados a mano
├── contracts/
│   └── openapi.yaml     # Delta: el detalle (aditivo) y el teléfono (no aditivo)
├── checklists/
│   └── requirements.md  # Calidad de la spec — completo
└── tasks.md             # Lo genera /speckit-tasks, no este comando
```

### Source Code (repository root)

```text
src/main/java/com/uniremington/api/tramita/
├── dto/
│   ├── RequestOrigin.java              # NUEVO (D1): el enum que hoy anida InboxEntryResponse; el JSON no cambia
│   ├── InboxEntryResponse.java         # usa RequestOrigin; deja de declarar el enum
│   ├── RequestResponse.java            # + origin, studentEmail, studentPhone (aditivo); javadoc de FR-008a
│   ├── CreateRequestBody.java          # studentPhone: @Pattern("[0-9]{10}") en vez de @Size(max = 30)
│   └── PublicRequestBody.java          # studentPhone: @NotBlank @Pattern("[0-9]{10}") en vez de @NotBlank @Size
└── service/impl/
    └── RequestServiceImpl.java         # toResponse carga el timeline y llama a originOf (D2); originOf devuelve RequestOrigin

src/test/java/com/uniremington/api/tramita/
├── controller/
│   ├── RequestControllerIT.java        # origen y contacto en las tres acciones; :251 se invierte (D4);
│   │                                   # guarda §III sobre búsqueda y bandeja (precedente :1098); 400 interno; SC-006
│   ├── PublicRequestControllerIT.java  # 422/201 por formato del teléfono; fixture :392 → número válido
│   └── WorkflowGenericityIT.java       # SC-005: DEMO en estado final expone los hechos
└── shared/exception/
    └── PublicCaptureExceptionHandlerTest.java   # fixture :156 → número válido; los cuatro casos siguen igual

specs/004-public-request-capture/contracts/openapi.yaml   # nota «ENMENDADO POR LA 008» en studentPhone
```

**Structure Decision**: se conserva la estructura *package-by-layer* del §II sin excepciones.
No se crea ningún paquete: `RequestOrigin` cae en `dto/`, junto a los dos records que lo usan.
`contracts/` de esta feature es un **delta** sobre los contratos previos, igual que en la 006 y la
007. `controller/` y `repo/` no aparecen porque no cambian: los tres endpoints que devuelven el
detalle ya existen y ya delegan en `toResponse`, y la consulta del timeline es la de `getTimeline`.

## Complexity Tracking

> Sin entradas: el Constitution Check pasó sin violaciones que justificar.

## Reparto con el frontend

Esta entrega deja el contrato listo. Lo que sigue va al repositorio del front
(`Villanueva-dev/tramita-frontend`), que lleva Codex, y se le pasa junto con la spec y el
contrato de esta feature. En orden de despliegue:

1. **Primero, el filtro de dígitos del formulario público** (`app/solicitud/creditos-adicionales/`):
   `type="tel"`, `inputMode="numeric"`, `autoComplete="tel-national"`, y un filtro en `onChange`
   que deje solo dígitos y quite el `57` inicial si quedan doce. **Sin `maxLength`**, para que lo
   pegado no se trunque antes de filtrar. Tiene que estar en `main` **antes** del `@Pattern` del
   back, o entrar en la misma ventana (research D9): hoy ese formulario manda el teléfono crudo.
2. **Mapear los tres campos nuevos del detalle**: `origin`, `studentPhone` y `studentEmail`. El
   del correo **ya está mapeado** y hoy cae siempre en `''` porque el back no lo devuelve
   (`lib/store.tsx`, `studentEmail: apiRequest.studentEmail ?? ''`, `front#10` 2.4); se llena solo.
3. **Los dos botones en el detalle**, solo cuando `origin === 'PUBLIC_LINK' && currentState.isFinal`:
   - **Correo** (P1), siempre que haya `studentEmail`: `mailto:<correo>?subject=…&body=…`, con los
     saltos de línea codificados como `%0D%0A` (RFC 6068 §5) y el texto percent-encoded en UTF-8.
   - **WhatsApp** (P2), solo si `/^3\d{9}$/.test(studentPhone)`:
     `https://wa.me/57<tel>?text=<encodeURIComponent(texto)>`.
   - **El mismo texto en los dos**, y solo con nombre, trámite y estado, tal como vienen en
     `studentName`, `definition.name` y `currentState.name` (FR-005). Un rechazo dice «rechazado»
     y no promete pasos siguientes (FR-005a). Los tests de FR-005 y SC-003 viven allá (D5).
4. **El test que `front#52` ya pide**: un mock de `transition` que devuelva el estado posterior,
   porque el botón aparece **en la respuesta de esa acción** (US1, escenario 2), y hoy ningún test
   del detalle ejerce lo que pasa después de registrar una transición.
5. **Sin registro de «avisado»** en el cliente tampoco: nada de banderas locales que afirmen lo
   que el sistema no sabe (FR-007).

Y al abrir la PR del back, con `Closes #13` en texto plano: enmendar los dos criterios de cierre
del issue («puerto como interfaz» y «solo en FINALIZADO»), decidir el destino de `#38` y `#39` —que
describen el puerto de `router-ia`, que no se construye—, y enmendar el árbol de problemas
(`:134`, `:164`, `:216`) como hizo la PR #44. Todo esto ya está declarado en la spec; acá solo se
recuerda dónde toca.

## Lo que este plan deja explícitamente fuera

- **Enviar correo o mensajes** — decidido en la spec (FR-006). Ni SMTP, ni proveedor, ni
  integración con WhatsApp: el sistema prepara, la Coordinación envía.
- **Registrar «avisado»** — decidido en la spec (FR-007, SC-006). Limitación aceptada.
- **Normalizar el teléfono en el servidor** — decidido en la spec (FR-011) y en D3: se conserva
  tal como llega, validado.
- **Exigir móvil en la captura** — decidido en la spec (FR-012): un fijo es un contacto válido.
- **Números extranjeros** — fuera por decisión del usuario.
- **Reescribir las solicitudes ya radicadas** — decidido en la spec (FR-011): las que tienen un
  teléfono fuera de formato no tendrán WhatsApp y conservan el correo.
- **El frontend** — otro repositorio. Su parte está en «Reparto con el frontend».
- **El puerto de notificación de `router-ia`** — no se cosecha (issues #38 y #39). Su cierre se
  decide en la PR.
