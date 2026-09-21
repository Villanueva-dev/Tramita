# Research — Bandeja de trabajo de la coordinación (007)

Decisiones técnicas previas al diseño. Cada una lleva su trade-off explícito, como exige el
Principio IV: *«elegí X frente a Y, sabiendo que el costo es Z»*.

Todas las mediciones son del 2026-09-21 contra `origin/main` (`29acf33`), y cada una trae el
comando que la reproduce.

---

## D1 — «Espera acción de X» se deriva de las transiciones, no se almacena

**Decisión**: una solicitud espera acción de un responsable X cuando existe, en la definición
con la que nació, una transición **desde su estado actual** cuyo `responsible` sea X. No se
persiste ningún campo nuevo en `request`.

**Rationale**: el dato ya está completo en la configuración. `workflow_transition` declara
`from_state`, `to_state` y `responsible`, y `RequestServiceImpl.toResponse` (`:447-451`) ya
recorre esa misma relación para exponer las transiciones disponibles — la bandeja la recorre
en sentido inverso: dado un responsable, qué solicitudes lo esperan. Derivarlo mantiene una
sola fuente de verdad y respeta el Principio VI: el motor no aprende nada nuevo sobre ningún
trámite.

**De dónde sale el significado de `responsible`, y tres consecuencias a la vista.** El seed de
la 002 lo definió como el área cuya firma o aprobación espera el paso — no como quién ejecuta
la transición en el sistema, que en el MVP es siempre la Coordinación (FR-003b).

1. En adición de créditos, `DEVUELTA → EN_COORDINACION` lleva `COORDINACION` (`V2.1.0:52`,
   retocada por `V3.2.0`): una solicitud **devuelta aparece en la bandeja de la Coordinación**
   aunque el formato esté en manos del estudiante, porque el reingreso lo registra ella. Es
   deseable — es el peor retraso que describe la entrevista — y la spec lo fija en el
   escenario 6 de US1.
2. En novedad de notas, `EN_PREPARACION → EN_FACULTAD` lleva `SEDE` (`V2.1.0:86`): la carpeta
   que la Coordinación arma **no** está en su bandeja mientras espera la firma de la Dirección
   de Sede. Es una decisión del seed de la 002, no de esta feature.
3. Si un estado tuviera salidas con responsables distintos, la solicitud aparece en la bandeja
   de cada uno (caso borde de la spec, cerrado así). Hoy ninguna definición sembrada lo tiene.

**Alternativas consideradas**:

- *Columna desnormalizada `pending_responsible` en `request`*: rechazada. Duplica un dato que
  la configuración ya tiene, hay que mantenerla sincronizada en cada transición, y quedaría
  desfasada ante un cambio de configuración — que es exactamente lo que el Principio VI
  permite hacer sin desplegar.
- *Vista materializada*: rechazada por el Principio I. Con 30–40 solicitudes por semestre no
  hay problema de rendimiento que justificarla.

**Costo aceptado**: cada consulta de la bandeja recorre las transiciones de las definiciones
involucradas. A este volumen es irrelevante; si algún día deja de serlo, la solución es un
índice, no desnormalizar.

---

## D2 — El responsable viaja como parámetro, no como literal en el código

**Decisión**: el endpoint de la bandeja recibe el responsable a consultar. El código **no
contiene** el literal `COORDINACION` ni ningún otro.

**Rationale**: es lo que hace la diferencia entre un motor genérico y uno con un trámite
cableado. La tesis del proyecto se prueba con un comando que exige que ningún rótulo de
dominio viva en el código Java:

```bash
git grep -nE '"(FINALIZADA|DEVUELTA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' origin/main -- 'src/main/java/*.java'
# → una sola línea, y es el rótulo impreso del papel
```

Hardcodear `COORDINACION` agregaría una segunda, y sería el mismo error en su versión de
responsables. Con el responsable como parámetro, incorporar un área nueva por configuración
—o una bandeja para la facultad el día que exista— no toca código.

**Alternativas consideradas**:

- *Literal `COORDINACION` en el servicio*: rechazada por lo anterior. Es más corta hoy y más
  cara de defender.
- *Resolverlo del rol del usuario autenticado*: rechazada porque **no hay roles**, y la spec
  decidió no introducirlos (FR-003).
- *Parámetro de configuración del workflow*: rechazada por el Principio I. Con un solo usuario
  y una sola sede, es configurar una variabilidad que nadie pidió.

**Costo aceptado, y declarado**: cualquier sesión válida puede consultar la bandeja de
cualquier área. La spec ya lo fija en FR-003a — **la bandeja filtra, no impide** — y el
control de acceso queda como trabajo futuro explícito. Documentarlo es lo que evita que se
lea como un descuido.

---

## D3 — La espera se cuenta desde la última transición, no desde la radicación

**Decisión**: el dato que la bandeja expone es **el instante desde el cual la solicitud está
esperando**: el `occurred_at` de su última entrada de timeline, o su `created_at` si todavía
no tiene ninguna.

**Rationale**: FR-004 pide «cuánto tiempo lleva esperando», que no es lo mismo que «qué tan
vieja es». Una solicitud radicada hace dos meses, devuelta y corregida ayer, lleva **un día**
esperando, no dos meses. Medir desde `created_at` la pondría primera en la bandeja y
desplazaría a otra que de verdad lleva semanas detenida — es decir, invertiría justo la
priorización que la feature viene a dar.

El dato existe: `request_transition_log` registra cada movimiento con su `occurred_at`
(`IRequestTransitionLogRepo:16`) y es inmutable por trigger (§VII), de modo que la medición
no puede falsearse.

**Alternativas consideradas**:

- *Medir desde `created_at`*: rechazada por lo anterior. Es la que el frontend usa hoy, y es
  parte de por qué su indicador no sirve.
- *Columna `state_since` en `request`*: rechazada por el Principio I y porque duplicaría un
  dato que el timeline ya guarda de forma inmutable. Una copia mutable de un dato inmutable
  es una regresión del §VII.

**Costo aceptado**: obtener ese instante exige mirar el timeline, no solo la fila de la
solicitud. Se resuelve en una consulta por lote —no una por solicitud— para no caer en N+1.

---

## D4 — Se expone el instante, no la duración

**Decisión**: la respuesta lleva `waitingSince` (un instante con offset), y **no** un número
de días. La duración la calcula quien presenta.

**Rationale**: resuelve por construcción el problema de zona horaria que la spec marca en
SC-006 y en sus casos borde. Una duración calculada en el servidor obliga a fijar el momento
«ahora» y la zona en que se redondea a días; un instante es el mismo hecho mirado desde
cualquier parte. Es además lo que ya hace el resto del API: `createdAt` viaja como instante y
el cliente lo presenta.

**Alternativas consideradas**:

- *Devolver `daysWaiting` calculado en el servidor*: rechazada. Introduce el redondeo, la zona
  horaria y un «ahora» congelado en la respuesta; y el número envejece en cuanto se cachea.
- *Devolver ambos*: rechazada por el Principio I. Dos representaciones del mismo hecho se
  desincronizan, y la derivada es trivial.

**Costo aceptado**: el cliente hace una resta. A cambio, no hay ninguna decisión de calendario
en el backend — que es justo lo que la spec decidió evitar al descartar el umbral de SLA.

**Tipo y offset, decidido el 2026-09-21.** `waitingSince` viaja como `OffsetDateTime` construido
con `CampusTime.toCampus(...)` — la utilidad que dejó la 006 en `util/` — que fija el offset de
la sede: es lo que hace verdadero «instante con offset» y resuelve FR-006 sin cálculo propio.
Las entidades guardan `LocalDateTime` en UTC, sin marcador; sin esa conversión el cliente
recibiría `2026-09-21T15:30:00` y lo leería como hora local. `createdAt` **se deja como está**
(`LocalDateTime` UTC, contrato de la 004): cambiarlo no es de esta feature. El costo, y se
declara en el contrato, es un DTO con dos instantes en dos formatos.

---

## D5 — El orden lo fija el servidor: primero lo que más espera

**Decisión**: la bandeja se devuelve ordenada por `waitingSince` ascendente — lo más antiguo
primero — y ese orden es parte del contrato.

**Rationale**: FR-009 pide un criterio «estable y explicable ante quien lo use», y este se
explica en una frase. Dejarlo al cliente haría que dos clientes mostraran órdenes distintos
sobre el mismo dato, y que el orden dependiera de cuál se esté mirando.

**Alternativa considerada**: *ordenar por fecha de radicación*, que es lo que hace hoy el
endpoint. Rechazada por la misma razón que D3.

---

## D6 — El catálogo gana los estados; el endpoint de la bandeja no los repite

**Decisión**: `GET /api/workflow-definitions` expone, por definición, la lista de sus estados
con `code`, `name`, `isInitial` e `isFinal`. Se hace con un DTO propio —
`WorkflowDefinitionDetailResponse` — y **no** ampliando `WorkflowDefinitionResponse`.

**Rationale**: resuelve el issue #22 sacando del cliente el reconocimiento de códigos, que es
conocimiento de trámites concretos viviendo fuera del motor. Ya se rompió una vez en silencio:
`V3.2.0` renombró el estado inicial **solo** para adición de créditos, y el cliente que usaba
una constante global dejó de acertar sin que nada fallara.

El DTO aparte no es preferencia: `WorkflowDefinitionResponse` se construye en **cuatro**
sitios —`toSummary:392`, `toInboxEntry:409`, `toResponse:454` y el catálogo— porque se anida
en cada respuesta de solicitud. Ampliarlo metería el recorrido completo en todas y dispararía
la carga perezosa de `states` en cada una.

**Medido con un spike descartable** antes de decidir: 5 archivos tocados y uno nuevo,
`+16/−9` líneas, suite completa en verde **sin modificar un solo test**, y
`WorkflowDefinitionServiceImpl` ya es `@Transactional(readOnly = true)`, de modo que la carga
perezosa no rompe.

**Es aditivo**: `WorkflowDefinitionControllerIT` afirma campos puntuales (`$[0].code`,
`$[0].version`), así que un cliente que lee los tres campos actuales sigue funcionando
(FR-011c).

**Alternativa rechazada — exponer también un orden**: la configuración declara transiciones de
retorno (`EN_FACULTAD → DEVUELTA → EN_FACULTAD`) y de rechazo (`EN_FACULTAD → RECHAZADA`), así
que el conjunto de estados **no es una secuencia**. Un «paso N de M» exigiría inventar un
orden, sembrarlo por definición y mantenerlo. Se descarta (FR-011b).

---

## D7 — Se reusa `GET /api/requests/inbox`, y eso enmienda un contrato de la 004

**Decisión**: la bandeja **no estrena endpoint**. Se le da criterio al que ya existe.

**Rationale**: el endpoint está construido, su DTO ya omite deliberadamente el documento de
identidad —que es la garantía de minimización que su javadoc declara (§III)— y nadie lo
consume todavía. Estrenar otro dejaría dos listados con el mismo propósito, que es la clase de
duplicación que se desincroniza.

⚠️ **Esto cambia la semántica de un contrato existente, y hay que declararlo.** Hoy el
endpoint responde «las 50 solicitudes más recientes, sin criterio», y así lo documenta la 004
(FR-012/FR-013) y el javadoc de `IRequestRepo:33-44`. A partir de esta feature responde «las
que esperan acción de un responsable». **No es aditivo**: quien esperara el listado completo
recibiría un subconjunto.

Se enmienda el contrato de la 004 en su `openapi.yaml`, con el mismo procedimiento que usó la
PR #45 al enmendar el de la 002. **Nadie lo consume** —medido: cero llamadas a `/requests/inbox`
en el frontend— así que el cambio no rompe a ningún cliente real.

**Alternativa considerada**: *endpoint nuevo `/requests/pending`, dejando `/inbox` intacto*.
Rechazada por el Principio I: dos endpoints que listan solicitudes, uno de ellos sin ningún
consumidor y sin propósito claro una vez que existe el otro.

---

## D8 — El límite sigue siendo explícito y de quien llama

**Decisión**: se conserva el `Limit` obligatorio en la firma del repositorio.

**Rationale**: no es una formalidad heredada. El javadoc de `IRequestRepo:40-43` lo argumenta:
*«una consulta sin cota podría convertirse en un volcado el día que el volumen crezca, y quien
la llame debe decidir explícitamente cuánto pide»*. Esta feature no tiene por qué relajarlo, y
menos siendo un listado de datos personales (§III).

**Costo aceptado**: si algún día hay más solicitudes pendientes que el tope, la bandeja
muestra un subconjunto. A 30–40 por semestre no ocurre; cuando ocurra, la respuesta es
paginación, no quitar la cota.

**Cuál subconjunto, y por qué se declara en vez de corregirse.** La consulta que filtra por
responsable corta por **radicación ascendente**, y el orden por `waitingSince` (D5) se aplica
después, en memoria, sobre lo que sobrevivió. Con más pendientes que la cota, el resultado es
«las N radicadas hace más tiempo, ordenadas por espera», no «las N que más esperan». Exige más
de 50 pendientes simultáneas de un mismo responsable con un volumen de 30–40 **por semestre**:
no ocurre. Si algún día importa, la solución es una sola consulta que calcule el instante de
espera en una subconsulta y ordene por él antes de la cota — no quitar la cota ni traer todo a
memoria.

---

## Lo que esta feature NO investiga, y por qué

- **Roles y control de acceso**: la spec lo descartó (FR-003). No se investiga cómo se harían.
- **Umbral de SLA y días hábiles**: la spec decidió medir sin dictaminar. No hay que resolver
  el calendario de festivos de Colombia, que habría sido el trabajo más caro de la feature.
- **Notificaciones**: son SP7 (issue #13), otra feature. La bandeja no avisa: se consulta.
- **El recorrido como secuencia**: descartado en D6.
