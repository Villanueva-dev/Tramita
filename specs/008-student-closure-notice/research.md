# Research — Aviso de cierre al estudiante (008)

Decisiones técnicas previas al diseño. Cada una lleva su trade-off explícito, como exige el
Principio IV: *«elegí X frente a Y, sabiendo que el costo es Z»*.

Todas las mediciones son del 2026-09-24 sobre la rama `008-student-closure-notice` en `4ae8626`
—que es `main` (`412a5e0`) más el commit de la spec— y cada una trae el comando que la reproduce.
Las decisiones de producto (dos acciones manuales, correo como principal, sin envío automático,
teléfono colombiano validado en el API) ya están tomadas en la spec y **no se reabren acá**: esto
resuelve cómo se construye lo que la spec pide.

---

## D1 — El origen de la solicitud pasa a un enum de primer nivel, `dto/RequestOrigin`

**Decisión**: el enum `Origin {COORDINATION, PUBLIC_LINK}`, hoy anidado en
`InboxEntryResponse`, se extrae a `dto/RequestOrigin.java` y lo usan los dos DTO que lo
necesitan: `InboxEntryResponse` (desde la 007) y `RequestResponse` (desde esta feature).

**Rationale**: con un solo consumidor, un enum anidado era la forma más corta. Con dos deja de
serlo: `RequestResponse` tendría que importar `InboxEntryResponse` —el DTO de la bandeja— solo
para nombrar un tipo, y eso acopla dos contratos que no tienen relación entre sí. Dos
consumidores es el umbral a partir del cual DRY pide un tipo propio. **El JSON no cambia**:
Jackson serializa el enum por su nombre (`"PUBLIC_LINK"`, `"COORDINATION"`), así que para el
contrato de la 007 el movimiento es invisible; es un rename solo en Java.

**Medido** — el enum anidado tiene tres referencias fuera de su propio archivo, todas en la
implementación del servicio:

```bash
grep -rn 'InboxEntryResponse.Origin' src/
# src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java:439
# src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java:444
# src/main/java/com/uniremington/api/tramita/service/impl/RequestServiceImpl.java:445
```

Ningún test lo nombra: los IT de la bandeja afirman sobre el JSON (`"PUBLIC_LINK"`), no sobre
el tipo Java.

**Alternativas consideradas**:

- *Reusar `InboxEntryResponse.Origin` desde `RequestResponse`*, como hizo el spike
  `spike/008-wa` (`a58561d`): cero líneas nuevas, pero el DTO del detalle queda importando al de
  la bandeja por un enum. Es lo más corto hoy y lo que peor se lee dentro de seis meses.
- *`String`*: rechazada. Pierde el tipo cerrado que el contrato ya declara como `enum`, y el
  compilador dejaría de detectar un literal mal escrito.

**Costo aceptado**: un archivo más que el spike (`RequestOrigin.java`) y tres líneas cambiadas
en `RequestServiceImpl`. A cambio, ningún DTO importa a otro por un tipo compartido.

---

## D2 — `toResponse` carga el timeline y reusa `originOf` de la 007

**Decisión**: el mapeo a `RequestResponse` carga el timeline de la solicitud con
`logRepo.findTimelinesOf(Collections.singletonList(id))` —la consulta en lote de la bandeja, con `join fetch
l.actor`, `IRequestTransitionLogRepo:34-38`— y deriva el origen con el `originOf(List<RequestTransitionLog>)`
que la 007 dejó en `RequestServiceImpl:439`, cambiando solo su tipo de retorno a `RequestOrigin`
(D1). Vale para las tres acciones que devuelven el detalle: `register`, `advance` y `getById`
(FR-008).

**Rationale**: el origen se deriva del actor de la entrada de nacimiento (007, FR-007), y esa
regla ya está escrita y probada. Reusarla es la única forma de que «origen» signifique lo mismo
en la bandeja y en el detalle; duplicarla en una segunda derivación garantizaría que un día
divergen. Que la lista completa viaje en vez de una sola fila no pesa: una solicitud tiene del
orden de cinco a diez entradas (la cadena más larga sembrada, la de novedad de notas, tiene
siete estados —`V2.1.0:68-73`— y cada devolución suma dos).

**Alternativas consideradas**:

- *Caso especial en `register`*, porque ahí el actor ya se conoce y no hace falta consultar:
  rechazada por el Principio I. Serían dos formas de calcular el mismo dato, una para el
  nacimiento y otra para el resto; el ahorro es un SELECT en la acción menos frecuente.
- *Consulta dirigida `findFirstByRequestIdAndFromStateIsNull`*, que traiga solo la entrada de
  nacimiento: rechazada por ahora. Exige un método de repositorio nuevo para ahorrar filas que
  a este volumen no cuestan nada. Queda anotada como **la** optimización si algún día una
  medición muestra que el detalle pesa; no se construye antes.

**Costo aceptado**: **una consulta más por cada respuesta de detalle**, acotada por la longitud
del timeline, con el actor traído en el mismo JOIN. El spike lo midió así (`git diff --stat
main..spike/008-wa`: +20/−4 líneas de producción, suite en verde con 163 unitarios + 114 IT).
El cliente que hoy hace las dos llamadas deja de necesitar la segunda para saber el origen.

⚠️ **Corregido el 2026-09-24 tras el review con agente limpio (B1).** La primera
implementación usó `findByRequestIdOrderByOccurredAtAscIdAsc`, la consulta de `getTimeline`,
y esta decisión decía «la misma consulta que ya paga `getTimeline`». Esa consulta no trae al
actor, que es perezoso (`RequestTransitionLog.actor`), y `originOf` lo lee: el review midió
con `Statistics` de Hibernate que en `getById` costaba **dos** consultas (timeline + actor) y
una en `register` y `advance`, donde el usuario ya estaba en la sesión. Se reusa
`findTimelinesOf`, que ya existía con `join fetch l.actor`, en vez de crear una consulta
nueva (§I); la medición es del review, no se repitió acá. La decisión no cambia: una
consulta más, ahora de verdad.

---

## D3 — El teléfono se valida con `@Pattern(regexp = "[0-9]{10}")`, y nada más

**Decisión**: `PublicRequestBody.studentPhone` pasa de `@NotBlank @Size(max = 30)` a
`@NotBlank @Pattern(regexp = "[0-9]{10}")`; `CreateRequestBody.studentPhone` pasa de
`@Size(max = 30)` a `@Pattern(regexp = "[0-9]{10}")`. El `@Size` se **quita** de los dos: el
patrón ya fija la longitud, y dos anotaciones que dicen lo mismo son dos verdades que
mantener. La columna `student_phone` sigue en `VARCHAR(30)`: no hay migración.

**Rationale, con las fuentes**:

- `[0-9]` y `\d` son equivalentes en Java mientras no se active `UNICODE_CHARACTER_CLASS`; la
  documentación de `Pattern` define `\d` como «A digit: `[0-9]`»
  (<https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html>).
  Se elige la forma explícita para que quien lea la anotación no tenga que saber eso.
- `@Pattern` exige coincidencia **completa** del valor y considera válido `null`
  (<https://jakarta.ee/specifications/bean-validation/3.0/apidocs/jakarta/validation/constraints/pattern>).
  Es lo que hace que la misma anotación sirva para los dos canales: en el público el
  `@NotBlank` sigue exigiendo el dato; en el interno, omitirlo sigue siendo válido (FR-010).
- Por qué no se acorta la columna a `VARCHAR(10)`: se midió y se descartó (Engram #2412, la
  spec lo recoge en *Assumptions*). `VARCHAR(10)` cuenta caracteres, no dígitos —acepta
  `abcdefghij` y rechaza `300 123 4567`—, y chocaría con las filas ya radicadas, que no se
  reescriben (FR-011).

**Consecuencias medidas sobre el manejo de errores.** La partición ausente/inválido vive en
`shared/exception/ValidationFields.java`, y su regla es que **la ausencia domina**: un campo
que viola `NotBlank` y otra regla a la vez se lista solo en `missingFields`. Aplicado al
teléfono:

| Canal | Valor recibido | Resultado |
|---|---|---|
| Público | `"3001234567"`, `"6025551234"` | 201 |
| Público | `"300 123 4567"`, `"+57 3001234567"`, 9 u 11 dígitos | 422 «Formato inválido», `invalidFields: ["studentPhone"]`, sin eco del valor (el detalle solo nombra campos) |
| Público | `""` o solo espacios | 422 «Formato incompleto», `missingFields: ["studentPhone"]` —una sola lista, no las dos— |
| Interno | ausente o `null` | 201: `@Pattern` no se evalúa sobre `null` |
| Interno | `""` | 400 «Petición inválida», `invalidFields: ["studentPhone"]`: llegó, y no cumple |
| Interno | `"3001234567"` | 201 |

El caso `""` del canal interno es «vino e inválido», que es lo que FR-010 dice literalmente
(*«si viene, MUST aplicar la misma regla»*). Hoy ningún cliente lo manda —el `POST` del front
omite `studentPhone` (`Villanueva-dev/tramita-frontend#10`, 2.3)— y el contrato lo documenta:
para no declarar teléfono, se omite el campo.

**Fixtures que rompen, y los que NO hay que tocar** (medido):

- `PublicRequestControllerIT.java:392` manda `"000 000 0000"` por el canal público real →
  pasa a `"3001234567"`. Es el RED natural de FR-009.
- `PublicCaptureExceptionHandlerTest.java:156` también rompe: ese test **sí valida el record
  real** (`VALIDATOR.validate(body, errors)`, `:117`, con `LocalValidatorFactoryBean`), y sus
  cuatro casos afirman listas exactas (`containsExactly`, `isEmpty`) que ganarían
  `studentPhone` como inválido. Su `FormBuilder` pasa a un número válido.
- Los cuatro fixtures del renderer (`DoFr100LayoutCanaryTest:89`, `DoFr100RendererTest:549`,
  `PdfDeterminismProbeTest:144`, `DoFr100FontIsolationTest:87`) construyen la **entidad**
  directamente, sin pasar por Bean Validation, y **no rompen**. ⛔ Y no se tocan «por
  prolijidad»: `DoFr100LayoutCanaryTest:63-68` compara el SHA-256 del documento contra un
  `KNOWN_DIGEST` literal, así que cambiarle el teléfono al fixture **rompería el canario** sin
  que la 008 haya cambiado nada del PDF. Los otros tres comparan dos renders entre sí, no contra
  un literal, pero se dejan igual: no hay razón para tocarlos.

```bash
grep -rn '000 000 0000' src/test/java --include='*.java'
# → 6 archivos; rompen solo los dos que pasan por el validador
grep -n 'KNOWN_DIGEST' src/test/java/com/uniremington/api/tramita/service/impl/DoFr100LayoutCanaryTest.java
```

**Alternativas consideradas**:

- *Normalizar en el backend* (quitar espacios, guiones y el `57` inicial antes de validar):
  rechazada por la spec (FR-011: se conserva tal como llega) y porque el filtro ya vive en el
  cliente, donde el estudiante ve el resultado antes de enviar. Normalizar en dos sitios es
  tener dos reglas.
- *Exigir móvil (`3[0-9]{9}`) en el API*: rechazada por la spec (FR-012): un fijo es un contacto
  válido para el PDF y para el correo; solo no habilita WhatsApp. Un falso rechazo acá bloquea
  el formulario entero del estudiante.
- *Conservar `@Size(max = 30)` junto al patrón*: rechazada. Es redundante, y una lectura rápida
  creería que se aceptan hasta 30 caracteres.

**Costo aceptado, y declarado**: es una **enmienda no aditiva** de dos contratos —el de
captura pública (004) y el interno—, y una solicitud del formulario público del front, que hoy
manda el teléfono tal como se escribe, recibiría 422 hasta que el filtro del cliente se
despliegue (D9). Se declara en `plan.md` con el mismo procedimiento que la 007 usó para
`GET /requests/inbox`.

---

## D4 — `RequestResponse` gana `origin`, `studentEmail` y `studentPhone`, de forma aditiva

**Decisión**: el record suma los tres campos al final, con el `@JsonInclude(NON_NULL)` que ya
tiene: una solicitud anterior a `V3.3.0`, o registrada por la Coordinación sin contacto, no los
devuelve. `origin` viaja siempre que haya entrada de nacimiento, que `register` escribe siempre.

**Rationale**: FR-008 exige que cada acción que devuelve el detalle traiga lo necesario para
ofrecer el aviso **sin una segunda consulta** (escenario 2 de US1: el botón aparece en la
respuesta de la transición). Los tres campos son lo mínimo: el origen decide si se ofrece, el
correo es el destinatario del canal principal y el teléfono el del alternativo. Un cliente que
hoy lee los doce campos sigue funcionando (FR-013).

**El javadoc que hay que corregir** (FR-008a). `RequestResponse.java:16` dice *«NO expone ningún
dato de contacto del estudiante (FR-020, constitución §III)»*. Esa cita sobrevivió a su base:
FR-020 de la 003 (`specs/003-request-form-rules/spec.md:247`) prohibía **almacenar** el correo
*«en esta feature»* y anticipaba a SP7 como su único consumidor; la 004 lo reemplazó por FR-005
(`specs/004-public-request-capture/spec.md:155`), que manda conservarlo *«porque es el canal por
el que la Coordinación responde»*. La razón vigente es la minimización del §III leída como la
lee `Request.java:37-40`: un dato de contacto se expone cuando tiene quien lo use, y esta
feature es ese consumidor.

**Un test existente pasa a rojo por diseño, y se invierte a conciencia.**
`RequestControllerIT.java:251-283` —`registerPersistsStudentEmailButNeverReturnsIt`, hoy `registerPersistsAndReturnsStudentEmail` (T010), «el correo del
estudiante se conserva pero NUNCA sale en la respuesta (FR-005a)»— registra una solicitud con correo y
afirma que la respuesta **no** lo trae (`jsonPath("$.studentEmail").doesNotExist()` y que el cuerpo no contenga el
valor). Ese test defendía exactamente el invariante que esta feature retira. No se «adapta»
en silencio ni se borra: se reescribe para afirmar lo contrario —el correo sale bajo su clave—
y se conserva su segunda mitad, que verifica que la fila lo guarda. Es la misma lección que
dejó `Villanueva-dev/tramita-frontend#11`: invertir una aserción verde cambia una conducta
entregada y se decide con la spec delante, no con la suite en rojo.

**`RequestSummaryResponse` e `InboxEntryResponse` NO los ganan.** La búsqueda y la bandeja
listan; el contacto solo tiene consumidor en el detalle de una solicitud concreta (§III). Se
fija con una guarda en el JSON servido, con el precedente exacto de la 007
(`RequestControllerIT.java:1098`, `jsonPath("$[*].studentDocument").doesNotExist()`): se
verifica sobre lo que sale, no sobre los componentes del record, *«porque quien lo verifica del
lado del DTO no vería un campo agregado por otra vía»* (comentario del propio test).

**Alternativa considerada**: *exponer solo `origin` y `studentPhone`*, como el spike (que decía
*«el correo sigue SIN exponerse»*). Rechazada: el spike es anterior a la decisión del correo como
canal principal (2026-09-23), y sin el correo en el detalle el canal P1 no existe.

---

## D5 — El backend expone hechos; el cliente decide si ofrece el aviso y compone el texto

**Decisión**: el backend no sabe nada de «aviso». Expone cuatro hechos —`origin`,
`currentState.isFinal`, `studentEmail`, `studentPhone`— y es el cliente quien aplica la regla
(`origin == PUBLIC_LINK && currentState.isFinal`, y para WhatsApp además `^3[0-9]{9}$`), arma
el `mailto:` (RFC 6068, saltos como `%0D%0A`, §5) y el `wa.me/57<tel>?text=` (FAQ de WhatsApp),
y redacta el texto con los tres datos que FR-005 permite.

**Rationale**: la spec ya repartió así el trabajo (*Assumptions*, «Reparto») y ya fijó que la
regla del móvil vive **solo en el cliente** (FR-002, FR-012). Con esa decisión tomada, cualquier
pieza de «aviso» en el backend quedaría a medias: decidiría el correo pero no el WhatsApp. Y un
sistema que **no envía nada** (FR-006) ni **registra nada** (FR-007) no necesita un concepto de
notificación en su modelo: necesita dejar los datos a la vista.

**Alternativas consideradas**:

- *(B) Una bandera derivada en la respuesta*, tipo `closureNoticeOffered: true`. Parece «la
  regla en un solo sitio», pero no lo es: la mitad de la regla (el móvil) vive en el cliente por
  decisión de la spec, así que la bandera partiría la regla en dos sitios en vez de uno. Y una
  bandera de presentación duplica lo que ya se deriva de dos campos que viajan igual.
- *(C) Un endpoint `GET /requests/{id}/closure-notice`* que devuelva `{to, subject, body}` ya
  codificados. Rechazado por el Principio I: mete la RFC 6068 y el formato de `wa.me` en el
  servidor, crea un recurso «notificación» que insinúa un acto del sistema —justo lo que FR-006 y
  FR-007 niegan— y obliga a versionar un texto que es presentación.
- *Componer el texto en el servidor y exponerlo como campo del detalle*: misma objeción, en
  chico.

**Costo aceptado, y declarado**: FR-005 y SC-003 —que el mensaje lleve solo nombre, trámite y
estado— se verifican con tests del **repositorio del front**, no de este. Lo que este
repositorio garantiza es lo suyo: que el detalle no filtra más de lo que la spec enumera (D4) y
que la búsqueda y la bandeja no llevan contacto.

---

## D6 — Sin migración, sin tabla, sin evento, sin puerto

**Decisión**: esta feature **no escribe nada** al ofrecer el aviso y no agrega mecanismo alguno
de notificación. La última migración sigue siendo `V4.1.0`.

**Rationale**: es la consecuencia de FR-006 (el sistema no envía), FR-007 (no registra
«avisado») y SC-006 (avisar no deja registro). Todo lo que la feature necesita ya está
persistido: el correo y el teléfono desde `V3.3.0`, el actor de nacimiento desde `V2.0.0` y la
marca de estado final desde `V2.0.0`.

**Lo que se descartó antes de esta spec, resumido para que no se reabra** (el detalle está en la
spec, *Assumptions*, y en Engram #2421):

| Descartado | Por qué |
|---|---|
| **B′** — evento al entrar a un estado final + listener `AFTER_COMMIT` + puerto + tabla `V5.0.0` | Sin correo automático, el enrutador y la tabla no tienen razón de ser (§I). Los spikes `008-a/-b/-b2/-c` son de esta opción y quedaron obsoletos; sus gotchas de Spring siguen valiendo como conocimiento. |
| **Correo automático** (SMTP institucional o proveedor) | Sin SMTP no hay correo oficial; un proveedor manda correos que no son de la universidad y entrega datos a un tercero (§III). Decisión del usuario, 2026-09-23. |
| **`is_success` en `workflow_state`** | El rechazo también es final, y el aviso **nombra el estado** (FR-005a). Una marca de éxito modelaría una distinción que el mensaje no necesita. |
| **Reintentos, `@Async`, cola, outbox** | No hay nada que reintentar: no se envía nada (§I). |
| **Migración a `VARCHAR(10)`** | Ver D3. |

**Costo aceptado**: no queda registro de «avisado». Es una limitación que la spec declara (caso
borde «Doble aviso»): el sistema no sabe si el estudiante fue avisado, y no lo afirma.

---

## D7 — «Estado final» se lee de la configuración, y ya viaja en la respuesta

**Decisión**: no se agrega nada para saber si un estado es final. `StateResponse.isFinal` existe
desde la 002 y desde la 007 se construye en un único sitio (`StateResponseMapper`, que lee
`WorkflowState.isFinalState()`), así que `currentState.isFinal` ya está en cada `RequestResponse`.

**Rationale**: FR-003 exige decidir «final» leyendo la configuración, sin reconocer códigos
concretos (§VI). El dato ya está donde debe estar y ya sale. Cualquier estado marcado
`is_final = TRUE` —`FINALIZADA` y `RECHAZADA` en adición de créditos, `FINALIZADA` en novedad
de notas (`V2.1.0:32-34,73`)— activa el aviso por igual, y uno que se marque mañana por SQL
también.

**La tesis del §VI sigue en una línea** — medido, y debe seguir así al cerrar:

```bash
git grep -nE '"(FINALIZADA|DEVUELTA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' HEAD -- 'src/main/java/*.java'
# → HEAD:src/main/java/com/uniremington/api/tramita/service/impl/DoFr100Renderer.java:166
#   (el rótulo impreso del papel, único desde la 005)
```

**SC-005 se prueba como en la 007**: con el trámite `DEMO` que `WorkflowGenericityIT` carga por
SQL en runtime (`:112-130`). Una solicitud DEMO llevada a su estado final debe devolver
`currentState.isFinal = true` y, si nació por el canal público, `origin = PUBLIC_LINK`, sin que
el código sepa que DEMO existe. ✅ Al implementar (T015) se afirmó `origin = COORDINATION`, porque
`insertDefinition` no siembra `PUBLIC_CAPTURE_ENABLED` y sembrarlo sumaría un camino que SC-005 no
necesita; y el test avanza «por la única transición disponible hasta que la configuración diga
final», sin nombrar `CERRADO`: tampoco el test conoce el camino, que es más fuerte todavía.

**Alternativa considerada**: ninguna que valga la pena escribir. Reconocer `FINALIZADA` por
código es el literal que el issue #13 ya advirtió no cosechar de `router-ia`.

---

## D8 — Estrategia de tests: RED observado, mutantes sobre lo sensible

**Decisión**: TDD con el RED visto antes de cada cambio, siempre con `./mvnw clean verify` (el
incremental de Maven produce rojos falsos; ver `CLAUDE.md`). Lo sensible acá (§V) es **qué sale
y qué no** —exponer de más es el fallo caro— y **qué teléfono entra**. El detalle por tarea lo
fija `/speckit-tasks`; esto es el mapa.

| Qué se prueba | Dónde | Por qué ahí |
|---|---|---|
| `origin`, `studentEmail`, `studentPhone` en `POST /requests`, `POST …/transitions` y `GET /requests/{id}`; `COORDINATION` sin contacto no los devuelve (`NON_NULL`) | `RequestControllerIT` | El mapeo y la derivación del origen atraviesan el servicio y la base; un unitario con el repo mockeado no ve el JSON servido |
| El test que hoy afirma que el correo NO sale se invierte (D4) | `RequestControllerIT:251` (`registerPersistsStudentEmailButNeverReturnsIt` → `registerPersistsAndReturnsStudentEmail`) | Es el RED de FR-008 |
| 422 público por formato, con el campo nombrado y sin eco del valor; 201 con fijo y móvil; blanco → `missingFields` | `PublicRequestControllerIT` | Es el contrato del canal (FR-009) |
| 400 interno con teléfono inválido; 201 sin teléfono | `RequestControllerIT` | FR-010 |
| La búsqueda y la bandeja no llevan correo ni teléfono | `RequestControllerIT`, precedente `:1098` | Guarda de §III sobre el JSON servido |
| Consultar el detalle no escribe en el timeline (SC-006) | `RequestControllerIT` | La única garantía del back sobre «avisar no deja registro» |
| DEMO en estado final ofrece los hechos (SC-005) | `WorkflowGenericityIT` | Precedente de la 007 |
| Los cuatro casos del handler siguen partiendo ausente/inválido con el teléfono válido en el fixture | `PublicCaptureExceptionHandlerTest` | Rompe por el fixture (D3) |

**Mutantes previstos**, cada uno con el test que debe ponerlo en rojo:

1. Invertir la condición de `originOf` (`PUBLIC_LINK` ↔ `COORDINATION`) → el IT de origen.
2. `[0-9]{10}` → `[0-9]{9,10}` → el 422 del público con 9 dígitos.
3. Quitar el `@Pattern` del canal interno → el 400 interno.
4. Quitar `@JsonInclude(NON_NULL)` de `RequestResponse` → el caso `COORDINATION` sin contacto.
5. Agregar `studentEmail` a `RequestSummaryResponse` → la guarda de §III.

**Suite base**: 163 unitarios + 112 IT, medidos en `cb85fd6` (la punta de la 007 que mergeó como
`412a5e0`). ⚠️ Se **re-mide** al arrancar la implementación, no se cita de memoria: es la regla
del repo desde que un conteo de la 007 se citó mal dos veces. ✅ Re-medida en T001 (163 + 112,
idéntica) y al cerrar en T036 (**163 + 125** sobre `813113d`): +7 IT de US1, +1 de US2, +5 de US3.
Los seis mutantes mordieron; el de `NON_NULL` (T020a) confirmó que `jsonPath(...).doesNotExist()`
pasa con la clave presente en `null`, y que la aserción sobre el cuerpo crudo era necesaria.

**Alternativa considerada**: *un test unitario de `RequestServiceImpl` con el repo mockeado para
el origen*. Rechazado como prueba principal: el origen depende de qué actor escribió la entrada
de nacimiento, y eso lo produce el flujo real (`register` con `PORTAL_ACTOR_EMAIL`); un mock lo
afirmaría por construcción.

---

## D9 — Reparto con el frontend, y el orden de despliegue

**Decisión**: este repositorio entrega los hechos (D4) y la validación (D3). Todo lo demás es del
repositorio del front, que lleva otro agente (Codex), y se le entrega como brief desde
`plan.md`, sección «Reparto con el frontend». **El filtro de dígitos del formulario público
debe desplegarse antes que el `@Pattern`, o a la vez.**

**Rationale**: el formulario público del front manda hoy el teléfono tal como se escribe
(`Villanueva-dev/tramita-frontend#2`, comentario del 2026-09-16: once obligatorios, todos
persistidos). Si el backend llega primero, un estudiante que escriba `300 123 4567` recibe un 422
que nombra el campo: recuperable, pero degrada el único canal sin nadie que lo explique. La spec
lo declara en *Assumptions* («Orden de despliegue»); acá se convierte en condición de la PR.

**Lo que ya está del lado del front, medido el 2026-09-24 sobre su `origin/main`** (`7b9e2bf`):

```bash
git -C ../tramita-frontend show origin/main:lib/store.tsx | grep -n "studentEmail: apiRequest"
# → studentEmail: apiRequest.studentEmail ?? ''
```

El mapeo del correo **ya existe** y hoy cae siempre en `''` porque el detalle no lo devuelve
(`front#10`, 2.4, y su comentario del 2026-09-23). En cuanto el back lo exponga, ese campo se
llena sin tocar el front. Lo que falta allá: `origin` y `studentPhone` en el modelo, los dos
botones, el filtro del input y el test que `front#52` ya pide.

**Alternativas consideradas**:

- *Aceptar el 422 transitorio y desplegar el back primero*: rechazada. El costo lo paga un
  estudiante en el canal público, y evitarlo cuesta coordinar dos PR.
- *Desplegar el `@Pattern` en una PR aparte, después*: rechazada. Partiría la 008 en dos
  entregas para esquivar una coordinación que igual hay que hacer, y dejaría la P2 (WhatsApp)
  dependiendo de números sin validar entre una PR y otra.

**Costo aceptado**: la PR del back no se mergea sola. Lleva como condición explícita que el
filtro del front esté en `main` o entre en la misma ventana, y así se escribe en su cuerpo.

---

## Lo que esta feature NO investiga, y por qué

- **Cómo enviar correo**: la spec lo descartó (FR-006). No se investiga SMTP, proveedores ni
  `spring-boot-starter-mail`.
- **Si el `mailto:` abre en el equipo de la Coordinación**: es un supuesto marcado en la spec
  para validar en la prueba real; si no abre, la mitigación (copiar el texto) se construye
  después de observarlo (Principio I), no antes.
- **Si el estudiante prefiere WhatsApp**: supuesto de la spec, se valida con la Coordinación,
  no con código.
- **Números extranjeros**: fuera por decisión del usuario.
- **Registro de «avisado»**: fuera por FR-007. Se acepta no saberlo.
