# Implementation Plan: Conteos de solicitudes por trámite y estado

**Branch**: `010-request-counts` | **Date**: 2026-09-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-request-counts/spec.md`

## Summary

La Coordinación pidió que vuelvan las tarjetas del tablero —cuántas solicitudes terminaron, cuántas
siguen en curso, cuántas se rechazaron— contando **toda la base**. Hoy ningún endpoint lo permite: la
búsqueda exige un término y la bandeja un responsable y corta en 200
(`RequestController.java:72-76`, `:105-110`), y el front retiró sus tarjetas porque contaban solo lo
que el navegador tenía cargado (`tramita-frontend#56`, PR #93).

El enfoque técnico cabe en tres frases. **Un endpoint de solo lectura, `GET /api/requests/counts`,
sin parámetros**, que devuelve por trámite y por estado cuántas solicitudes hay hoy en él; no clasifica
nada como «terminado» o «rechazado», expone hechos y el cliente decide (research D1). **Una sola
consulta parte de los estados y rellena el 0 en la base** con un `left join` a la solicitud, de modo
que un estado sin solicitudes aparece con 0 sin código adicional (D5). **Un plegado corto en el
servicio** suma las versiones de un mismo trámite por código de estado y toma el nombre y las marcas
de la versión más alta (D2, D7).

Consecuencias directas: **ninguna migración** y ninguna tabla nueva (todo está persistido desde
`V2.0.0`); **cero endpoints abiertos nuevos** (el endpoint queda tras la misma sesión que protege
`anyRequest().authenticated()`, `SecurityConfig.java:171`); **un contrato puramente aditivo**
(FR-011); y la **primera proyección** del código (un `record` con el resultado de la consulta, D6).

### Alcance de la entrega de hoy

⏱️ **Hoy se entrega solo la Fase A: spec, plan y contrato.** La implementación (Fase B) y el cierre
(Fase C) están **documentados aquí para que `/speckit-tasks` los baje, pero hoy no se implementan ni se
mergean** (research D12). El tablero actual funciona sin las tarjetas —el front las quitó en
`tramita-frontend#56` (PR #93)—, así que no mergear no rompe nada. Lo que sí desbloquea la Fase A es el
**contrato fijo**: `tramita-frontend#51` deja escrito que el front no puede empezar hasta que el
contrato de `Tramita#58` quede fijado (spec, *Input*).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.7 (Web MVC, Data JPA, Security 7), Lombok, Hibernate
**7.2.19.Final** (la fija `spring-boot-dependencies-4.0.7.pom`, propiedad `hibernate.version`, `:69`).
**Ninguna dependencia nueva.**

**Storage**: PostgreSQL con Flyway en modo `validate`. **Ninguna migración nueva**: la última sigue
siendo `V5.2.0`. La feature **lee** `workflow_definition`, `workflow_state` y `request`; no escribe
ninguna. No hay índice sobre `request.current_state_id` (medido, research D11): a 30–40 solicitudes por
semestre no hace falta.

**Testing**: JUnit 5 + AssertJ + Mockito para el unitario del plegado; Testcontainers
(`@ServiceConnection`, `postgres:16`) + MockMvc para el IT, con `@TramitaIntegrationTest`. Runner:
`./mvnw clean verify`, **siempre con `clean`** (un RED incremental puede salir falso). **TDD estricto**:
RED observado → GREEN → REFACTOR. Línea base de la suite en `main`: **172 unitarios + 158 IT**, medida
en el log del CI de `main` sobre `09c7b6f` (run `36529191834`: `Tests run: 172` de Surefire y `Tests run:
158` de Failsafe, 0 fallos). Esta fase no ejecuta Maven: al arrancar la Fase B se corre
`./mvnw clean verify` en local y se confirma que da lo mismo. Cuatro mutantes de diseño
(research D10).

**Target Platform**: servicio HTTP, despliegue en Linux.

**Project Type**: servicio web (backend). El front vive en otro repositorio y recibe su parte como
contrato; la clasificación de estados es suya (D1).

**Performance Goals**: ninguno. Volumen documentado: **30–40 solicitudes por semestre** para el trámite
más frecuente (`specs/007-coordination-inbox/spec.md:134`). La consulta recorre toda la base en cada
llamada: a esa escala es barato (D11).

**Constraints**: una consulta por llamada, sin caché (FR-007); sin filtros ni parámetros (FR-006: uno que
llegue se ignora); respuesta sin datos personales (FR-009). El `git grep` que prueba la tesis del §VI
debe seguir dando exactamente lo que da hoy: **una línea**, `DoFr100Renderer.java:166` (medido, abajo).

**Scale/Scope**: Sede Cali, 2 trámites, **15 estados en la versión vigente** (8 de la adición de
créditos y 7 de la novedad de notas v2; la v1 de la novedad conserva sus 6, todos con código repetido en
la v2). **Un endpoint nuevo**, protegido: `GET /api/requests/counts`.

## Constitution Check

*GATE: debe pasar antes de la Fase 0. Re-evaluado después de la Fase 1.*

| Principio | Antes del diseño | Después del diseño | Evidencia |
|---|---|---|---|
| **I — KISS + YAGNI** | ✅ | ✅ | Ninguna tabla, columna ni migración (`constitution.md:211-217`: crecer con migraciones «cuando el requisito exista»). Un controller, un servicio y un repositorio-método; el plegado son ~20–30 líneas. Se descartaron con evidencia el total (el cliente suma), los filtros, la caché, el orden de estados y la marca de rechazo en la base (D1, D4, D7). |
| **II — Arquitectura por capas** | ✅ | ✅ | Cada pieza cae en su capa: `controller/`, `dto/`, `repo/`, `service/` + `service/impl/`. Interfaz con prefijo `I` y el controller la inyecta (`constitution.md:223-226`). **No se engorda `RequestServiceImpl`** y no se toca `RequestController`, donde entrará la feature del #57. |
| **III — Seguridad y minimización** | ✅, con una tensión declarada | ✅ | Solo con sesión (FR-001). La respuesta lleva **códigos, nombres, marcas y conteos**: ningún dato personal (FR-009), y un IT fija el **conjunto exacto de claves**. Sin filtros a propósito: un conteo filtrado puede identificar a una persona («Ingeniería de Sistemas, rechazada: 1», FR-006). DTOs en la frontera, nunca entidades (`constitution.md:245-250`). Tensión: ver abajo. |
| **IV — Decisiones trazables** | ✅ | ✅ | Doce decisiones en `research.md`, cada una con su alternativa descartada, su costo y su comando. Las fuentes técnicas (Hibernate, Spring Data JPA) se citan con URL y se leyeron el 2026-09-29. No hay normativa institucional en juego. |
| **V — Testing del comportamiento sensible** | ✅ | ✅ | Lo sensible es **no mezclar trámites que comparten un código de estado** y **no perder estados en 0 ni solicitudes de versiones viejas**. Por eso: un unitario del plegado, un IT sobre el JSON servido y cuatro mutantes con su test asignado (D10). |
| **VI — Workflow configurable por dato** | ✅, con una deuda declarada del cliente | ✅ | El endpoint **no nombra ningún código de trámite ni de estado** (FR-010): un trámite cargado por SQL aparece sin desplegar. Prueba viva: `git grep -nE '"(FINALIZADA\|DEVUELTA\|RECHAZADA\|ADICION_CREDITOS\|NOVEDAD_NOTAS)"' -- 'src/main/java/*.java'` → `DoFr100Renderer.java:166`, y debe seguir así. Un IT carga una definición propia por SQL y comprueba que aparece con todos sus estados en 0. Deuda: ver abajo. |
| **VII — Trazabilidad inmutable** | ✅ | ✅ | **Nada escribe el timeline**: la operación es de solo lectura y los conteos no se almacenan (spec, *Key Entities*). Los dos triggers de inmutabilidad (`constitution.md:342-349`, §VII: `trg_timeline_immutable`, `trg_document_seal_immutable`) no se tocan. |

**Resultado del gate: pasa sin violaciones.** *Complexity Tracking* queda vacío.

### Dos tensiones que no son violación, pero se declaran

**1. Deuda con el Principio VI, en el cliente y no en el motor (spec, *Assumptions*).** El motor no
reconoce estados, pero la tabla del cliente que clasifica «devuelta» y «rechazada» sí, por código
(`tramita-frontend`, `lib/request-state.ts:56-70`, sobre `origin/main` = `059458b`, sin `git fetch`). Si
mañana se incorpora por configuración un trámite con un estado de rechazo, el cliente lo contaría como
«terminado» hasta actualizar y desplegar esa tabla: la promesa de no desplegar código se cumple en el
motor y no en la pantalla. La deuda **no nace aquí** —la misma tabla ya decide hoy las insignias del
detalle— y la corrección de fondo, marcar en `workflow_state` qué finales son rechazo o devolución
(opción 2 del #58), es la de `tramita-frontend#51`: una migración y un cambio en el contrato de `State`,
que usa toda la API. **Se descarta hoy por costo, a horas de la entrega** (research D1).

**2. Sin filtros, un conteo pequeño sigue siendo un dato.** «Rechazadas: 1» no identifica a nadie **mientras
el conteo sea global**; por programa, fecha o responsable sí podría. Por eso FR-006 prohíbe filtros y la
implementación no declara ningún `@RequestParam`: un parámetro que llegue se ignora, y un IT lo fija.
Agregar uno exige revisarlo contra el §III antes.

## Project Structure

### Documentation (this feature)

```text
specs/010-request-counts/
├── spec.md              # Qué y por qué — APROBADA (gate review-spec superado el 2026-09-29)
├── plan.md              # Este archivo
├── research.md          # D1–D12, con trade-offs, fuentes, comandos y líneas verificadas
├── data-model.md        # Sin tablas nuevas: lo que se lee, la fila de la consulta, el plegado y la respuesta
├── quickstart.md        # Verificación contra el servidor dev, con la línea base del §VI
├── contracts/
│   └── openapi.yaml     # Delta: solo GET /requests/counts (aditivo)
├── checklists/
│   └── requirements.md  # Calidad de la spec — completo
└── tasks.md             # Lo genera /speckit-tasks, no este comando (Fase B)
```

### Source Code (repository root)

```text
src/main/java/com/uniremington/api/tramita/
├── repo/
│   └── IRequestRepo.java                    # + findStateCounts() y el record anidado StateCountRow (D5, D6)
├── dto/
│   ├── RequestCountsResponse.java           # NUEVO: (definitionCode, definitionName, states)
│   └── StateCountResponse.java              # NUEVO: (StateResponse state, long count)
├── controller/
│   └── RequestCountController.java          # NUEVO (D3): GET /api/requests/counts, sin parámetros
└── service/
    ├── IRequestCountService.java            # NUEVO (D3): el contrato
    └── impl/
        ├── RequestCountServiceImpl.java     # NUEVO (D7): la consulta + el plegado, solo lectura
        └── StateResponseMapper.java         # + sobrecarga toResponse(code, name, initial, finalState) (D8)

src/test/java/com/uniremington/api/tramita/
├── service/impl/
│   └── RequestCountServiceImplTest.java     # NUEVO: el plegado, con el repositorio simulado (D10)
└── controller/
    └── RequestCountControllerIT.java        # NUEVO: sesión, diferencias antes/después, definición propia por SQL, claves exactas
```

**Ningún archivo existente cambia salvo dos**: `IRequestRepo` (un método y un record, sin tocar los
existentes) y `StateResponseMapper` (una sobrecarga; la de entidad delega en ella).

**Structure Decision**: se conserva *package-by-layer* (§II) sin excepciones y sin paquetes nuevos. El
controller es aparte de `RequestController` aunque cuelgue de la misma ruta base: otro recurso
(estadística, no una solicitud), y para no chocar con la feature del #57. Una ruta literal y una con
variable compiten en el mismo mapeo y **gana el segmento literal** (D9).

## Fases

### Fase A — Spec, plan y contrato (HOY)

Entrega: `spec.md` (aprobada), este plan, `research.md`, `data-model.md`, `contracts/openapi.yaml` y
`quickstart.md`. Con el contrato fijo, el front puede arrancar en paralelo. Después: gate `review-plan`
y comentario en `tramita-frontend#51` con el enlace permanente al contrato (publicarlo requiere
aprobación del propietario en ese momento).

### Fase B — Implementación (DESPUÉS; `/speckit-tasks` la baja a tareas)

⛔ **Hoy no se implementa.** Orden y forma, TDD estricto, con línea `Verificado:` en cada commit de
comportamiento (`.gitmessage`):

1. **RED del unitario** de `RequestCountServiceImplTest` (falla por compilación: la clase no existe) y
   **RED del IT** (con sesión, `GET /api/requests/counts` responde **400**, porque entra por
   `GET /api/requests/{id}` y falla al convertir «counts» a UUID: el mismo síntoma que tenía `/inbox`
   antes de existir, `RequestController.java:95-101`).
2. **GREEN**: el record y la consulta en `IRequestRepo`; el plegado en el servicio; los dos DTOs; la
   sobrecarga del mapeador; el controller.
3. **REFACTOR** y **mutantes** (D10): cada uno debe morir por el test asignado.
4. Suite completa `./mvnw clean verify` por encima de la línea base re-medida.

Los IT **miden diferencias antes/después y no totales**: la base es compartida entre clases de test y
nadie la limpia (D10). Los tres escenarios de solicitudes se mueven con
`AdvanceRequestSupport.advanceFromCurrentState(mockMvc, id, target, note)`
(`src/test/java/com/uniremington/api/tramita/controller/AdvanceRequestSupport.java:41`), que envía el
`fromStateCode` que exige H-10 (PR #61).

### Fase C — Cierre (DESPUÉS)

- Review con agente limpio sobre el rango (`docs/workflow/code-review-agente-limpio.md`), **con tope de
  tiempo**; su informe se confirma con comandos propios antes de aplicarse.
- PR con `Closes #58` en **texto plano**, sin `--milestone`. Merge con CI verde (`./mvnw clean verify`),
  nunca en la última hora antes de una entrega.
- Después del merge: PR de docs (tabla de endpoints del `README`, bloque SPECKIT de `CLAUDE.md`) y
  `tramita-frontend#51` actualizado como contraparte.

## Plan de pruebas

**Unitario del plegado** — `RequestCountServiceImplTest`, con el repositorio simulado como única
frontera de I/O (patrón de `AuthServiceImplTest`):

| Caso | Afirma |
|---|---|
| Dos trámites que comparten `EN_FACULTAD` | Dos entradas, cada una con su conteo; nunca se mezclan (FR-004) |
| Estados de la versión mayor con 0 | Se conservan con 0 (FR-003) |
| v1 + v2 del mismo código de trámite | Un solo trámite; cada código de estado una vez, con la suma y el nombre y las marcas de la v2 (FR-005) |
| Código solo de la v1 con `count > 0` | Se conserva |
| Código solo de la v1 con `count = 0` | Se descarta |
| Orden | Los trámites salen ordenados por nombre |

**IT** — `RequestCountControllerIT` (`@TramitaIntegrationTest` + MockMvc, sesión como los demás):

| Caso | Afirma |
|---|---|
| Sin sesión | **401** `application/problem+json` (US1, escenario 6) |
| Con sesión | 200 y los dos trámites de la semilla |
| Tres adiciones nuevas: una a `FINALIZADA`, una a `RECHAZADA`, una en el estado inicial | **+1** en cada uno de esos tres estados y **+0** en el resto (escenarios 2 y 4) |
| Una adición a `EN_FACULTAD` | +1 en la adición y **+0 en `EN_FACULTAD` de la novedad** (escenario 3, SC-003) |
| Definición propia sin solicitudes, insertada por SQL | Aparece con **todos** sus estados en 0 (escenario 5, FR-010, SC-006) |
| `?responsible=X` u otro parámetro | La respuesta **no cambia** (FR-006) |
| Claves JSON | Conjunto **exacto**: `definitionCode`, `definitionName`, `states`; `state`, `count`; `code`, `name`, `isInitial`, `isFinal` (FR-009, SC-005) |
| La novedad de notas | Sus 7 estados vigentes, **cada código una sola vez**: la v1 y la v2 están plegadas (FR-005) |

La definición propia usa el patrón de `WorkflowGenericityIT.insertDefinition` (`:667`, javadoc
`:661-666`); su nombre empieza por «Trámite …» para ordenar **después** de «Novedad de notas» y no romper
`WorkflowDefinitionControllerIT.java:36-49`, que afirma `$[0]` = adición y `$[1]` = novedad v2 por orden
de nombre; y cumple el invariante de configuración de `WorkflowGenericityIT` (javadoc `:198-206`, test
`:207-210`): ningún estado final con salidas y todo no final con al menos una.

**Mutantes**, cada uno debe morir y se dice por qué test:

| Mutante | Lo mata |
|---|---|
| `inner join` en vez de `left join` | El IT de la definición sin solicitudes (sus estados desaparecen) |
| Plegar solo por código de estado, sin separar trámites | El unitario de `EN_FACULTAD` compartido y el IT de la adición a `EN_FACULTAD` (+0 en la novedad) |
| Descartar las filas viejas aunque tengan solicitudes | El unitario «código solo de la v1 con `count > 0`» |
| No sumar entre versiones (quedarse con una fila por código) | El unitario v1 + v2 |

⚠️ **Honestidad sobre el alcance de cada test.** Los dos últimos mutantes viven en el plegado y solo el
unitario puede matarlos con certeza: en el IT, que cuente solicitudes de la v1 de la novedad depende de
datos que la base compartida no garantiza. El IT de la novedad (siete códigos, ninguno repetido) sí mata
«no plegar» a secas: sin plegar, los códigos de la v1 y de la v2 saldrían repetidos.

## Verificación end-to-end

Detalle en [`quickstart.md`](./quickstart.md). En resumen:

1. `./mvnw clean verify` en verde, por encima de la línea base re-medida.
2. Contra el servidor `dev` (puerto 8080, perfil `dev`, base en el 5433): sin sesión **401**; con
   sesión, los dos trámites con todos sus estados; radicar y avanzar una adición **cambia exactamente un
   conteo**.
3. §VI intacto: `git grep -nE '"(FINALIZADA|DEVUELTA|RECHAZADA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' --
   'src/main/java/*.java'` debe dar **exactamente las mismas líneas que hoy**. Línea base medida el
   2026-09-29 sobre `5f6936c`: **una sola línea**, `DoFr100Renderer.java:166`.
4. Un solo sitio para la consulta: `git grep -n 'count(' -- 'src/main/java/*.java'` da hoy **0 líneas** y,
   tras la Fase B, exactamente la de `IRequestRepo`.

## Supuestos sin verificar

- **La resolución del nombre de una clase anidada** en la reescritura a expresión constructora de Spring
  Data (D6). Lo confirma el arranque del contexto en el primer IT: Spring Data valida la consulta al
  crear el repositorio. Si fallara, el `record` pasa a ser de primer nivel en `repo/`.
- **Que Hibernate 7.2.19 empareje `int`/`boolean`/`Long` del record con lo que devuelve la selección**
  (D6). Mismo mecanismo de confirmación.
- La estimación de horas de las Fases B y C es analogía con la 007 y la 009, no una medición de esta
  feature.
- Cuántas horas quedan para la entrega: lo sabe el propietario.

## Lo que este plan deja explícitamente fuera

- **Clasificar estados** (terminado, rechazado, devuelto, pendiente) — decidido en la spec; es del cliente
  (D1). La marca en `workflow_state` es la corrección de fondo de `tramita-frontend#51`.
- **Un total, filtros, tiempos de ciclo (#37), prioridad, corte por periodo** — spec, *Assumptions* (D4).
- **Contar devoluciones de la novedad de notas** — no son un estado, son un retorno a `EN_PREPARACION`
  (spec, *Edge Cases*); limitación declarada del #58.
- **Caché de los conteos** — FR-007 (D4).
- **La feature del #57 (hoja de vida con retención)** — otra feature; por eso el controller es aparte.
- **El front** — otro repositorio; recibe el contrato.
