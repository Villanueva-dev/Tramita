# Research — Conteos de solicitudes por trámite y estado (010)

Decisiones técnicas previas al diseño. Cada una lleva su trade-off explícito, como exige el
Principio IV: *«elegí X frente a Y, sabiendo que el costo es Z»*.

Todas las mediciones son del 2026-09-29 sobre la rama `010-request-counts` en `5f6936c` —que es `main`
(`09c7b6f`) más los commits de la spec— y cada una trae el comando que la reproduce o la línea exacta que
la respalda. Las del front se tomaron sobre la referencia local `origin/main` de `tramita-frontend`, que
apunta a **`059458b`**; no se hizo `git fetch`, así que si el remoto avanzó, esas líneas se re-verifican
antes de usarlas. Las fuentes técnicas (Hibernate y Spring Data JPA) se leyeron el 2026-09-29.

Las decisiones de producto (una consulta global, el cliente clasifica, sin filtros, sin total) ya están
tomadas en la spec y por el propietario al aprobar el plan del 2026-09-28, y **no se reabren acá**: esto
resuelve cómo se construye lo que la spec pide y deja por escrito el porqué.

---

## D1 — El cliente clasifica «terminado / rechazado / devuelto»; el backend expone hechos

**Decisión**: el endpoint devuelve, por estado, su código, su nombre, sus dos marcas (`isInitial`,
`isFinal`) y el conteo. **No** agrupa en «terminadas», «rechazadas» ni «en curso»: esa clasificación la
hace el cliente. Es la **opción 1** del issue #58, que el propietario eligió al aprobar el plan el
2026-09-28.

**Justificación**:

- **Sin migración ni cambio de modelo.** Con el plazo de la entrega, la opción 1 no toca el esquema ni el
  contrato de `State` (FR-011: el cambio es aditivo).
- **Es el criterio de la 008**: *«el backend no sabe nada de "aviso": expone hechos y el cliente aplica la
  regla»* (`specs/008-student-closure-notice/research.md:224`, D5). Lo repite el javadoc de
  `RequestResponse` (`RequestResponse.java:34-35`, «el backend expone hechos; el cliente decide»).
- **Las marcas ya existen y no alcanzan a distinguir un rechazo**: `isFinal` no significa exitoso, un
  rechazo también es final (`StateResponse.java:10-11`). Distinguirlo exige una tabla, y la tabla ya
  existe en el cliente: `lib/request-state.ts:56-70` en `tramita-frontend` (`origin/main` = `059458b`)
  marca `DEVUELTA` como devolución y `RECHAZADA` como rechazo de la adición, y deja la novedad de notas
  vacía a propósito.

**Alternativas descartadas**:

- *Opción 2 del #58: una marca en `workflow_state`* (qué finales son un rechazo y cuáles una devolución).
  Es la corrección de fondo, la que sí cumpliría el §VI también en la pantalla y la que
  `tramita-frontend#51` reclama. Cuesta una migración, un campo nuevo en `StateResponse` —que **usa toda
  la API**: el detalle, el catálogo y la bandeja— y coordinar el despliegue con el front. **Fuera hoy, por
  costo, a horas de la entrega.** Sigue disponible después y es **aditiva** sobre este endpoint: el
  conteo ya trae `state`, así que el día que `StateResponse` gane la marca, las tarjetas la reciben sin
  cambiar este contrato.
- *Que el backend devuelva ya los tres grupos.* Obliga a que el motor reconozca códigos como `RECHAZADA` y
  `DEVUELTA`: viola el §VI de raíz y el `git grep` de la tesis dejaría de dar una línea.

**Costo aceptado — una deuda con el §VI, declarada**: el motor no reconoce estados, pero la tabla del
cliente sí, por código. Si se incorpora por configuración un trámite con un estado de rechazo, el
cliente lo contaría como «terminado» hasta actualizar y desplegar esa tabla. La promesa de no desplegar
código se cumple en el motor y **no en la pantalla**. La deuda no nace acá: la misma tabla ya decide hoy
las insignias del detalle de una solicitud.

**Evidencia**:

```bash
git -C ../tramita-frontend show origin/main:lib/request-state.ts | sed -n '56,70p'
# const STATE_SEMANTICS: Record<RequestType, Record<string, StateSemantics>> = {
#   adicion_creditos: { DEVUELTA: { returned: true }, RECHAZADA: { rejection: true } },
#   novedad_notas: { /* vacío a propósito */ }, }
sed -n '10,11p' src/main/java/com/uniremington/api/tramita/dto/StateResponse.java
# {@code isFinal} no significa exitoso: un rechazo también es final.
```

---

## D2 — Las versiones de un mismo trámite se suman por código

**Decisión**: los conteos se **agrupan por el código del trámite y por el código del estado**, sumando
versiones. El nombre del trámite y el nombre y las marcas de cada estado salen de la **versión más alta
que tiene ese estado**. Todo estado de la versión vigente aparece, con 0 si no tiene solicitudes; un
estado que existe solo en versiones anteriores aparece **solo si todavía tiene solicitudes**.

**Justificación**:

- **El motor admite varias versiones vivas y una solicitud queda atada a la con que nació**:
  `Request.definition` es `updatable = false` (`Request.java:63-64`) y `Request.currentState` apunta a un
  estado de esa versión (`:66-68`). Agrupar por código y no por versión es lo que da una cifra que la
  Coordinación entiende («cuántas novedades están en facultad»), no «cuántas de la v2».
- **El caso ya es real, no hipotético.** La novedad de notas tiene dos versiones desde `V5.2.0` (PR #60,
  H-11): la v1 declara seis estados (`V2.1.0__Seed_workflow_definitions.sql:68-73`) y la v2 siete
  (`V5.2.0__Split_novedad_preparation_and_sede_signature.sql:62-68`), sumando `EN_FIRMA_SEDE` (`:64`).
  Los seis de la v1 existen con el mismo código en la v2, así que **hoy ningún estado vive solo en una
  versión anterior**; pero sí hay dos filas por cada uno de esos seis códigos, y sin plegar saldrían
  duplicados.
- **Es coherente con el catálogo de trámites**, que muestra solo la versión vigente
  (`IWorkflowDefinitionRepo.java:21-26`: la de mayor `version` por `code`).

**Alternativas descartadas**:

- *Mostrar una entrada por versión* (`ADICION_CREDITOS v1`, `NOVEDAD_NOTAS v1`, `v2`). El cliente tendría
  que sumar las versiones para armar una tarjeta, y cada v3 obligaría a tocarlo.
- *Solo la versión vigente, descartando las solicitudes de las viejas.* Esconde solicitudes vivas: la suma
  por trámite dejaría de igualar el total de la base (SC-002).
- *Mostrar todos los estados de todas las versiones.* Llena la lista de estados que ya no se usan.

**Costo aceptado — el código debe conservar su sentido entre versiones (FR-005).** La suma supone que un
mismo código significa lo mismo. **La v2 de la novedad lo cumple solo a medias**: separa la espera de la
firma de la sede en `EN_FIRMA_SEDE`, así que `EN_PREPARACION` de la v1 **incluye** esa espera y el de la
v2 no; ambos se suman bajo el mismo nombre. Se acepta porque la convención del motor para un cambio de
sentido es usar otro código, y porque los conteos son un recordatorio para llevar el control a ojo, no un
registro histórico.

**Evidencia**:

```bash
sed -n '68,73p' src/main/resources/db/migration/V2.1.0__Seed_workflow_definitions.sql   # los 6 de la v1
sed -n '62,68p' src/main/resources/db/migration/V5.2.0__Split_novedad_preparation_and_sede_signature.sql  # los 7 de la v2
sed -n '64p'   src/main/resources/db/migration/V5.2.0__Split_novedad_preparation_and_sede_signature.sql  # EN_FIRMA_SEDE
```

---

## D3 — Un controller y un servicio nuevos, en `GET /api/requests/counts`

**Decisión**: `controller/RequestCountController` expone `GET /api/requests/counts` y delega en
`service/IRequestCountService`, implementada por `service/impl/RequestCountServiceImpl`. Interfaz con
prefijo `I`, package-by-layer.

**Justificación**:

- **No engordar `RequestServiceImpl`**, que ya concentra el registro, el avance, la búsqueda y la bandeja.
  La feature del #57 (hoja de vida con retención) tocará `RequestController`; un controller aparte evita
  que las dos PR compitan por el mismo archivo.
- **§II**: cada pieza cae en su capa y el controller inyecta la interfaz (`constitution.md:223-226`).
- **La ruta es un recurso propio** —una estadística, no una solicitud— y por eso cuelga de `/api/requests`
  solo como prefijo, con el segmento literal `counts` (D9).
- **Mismo patrón que el catálogo**: `WorkflowDefinitionController` (`@RequestMapping` propio, un
  `@GetMapping` sin parámetros, delega en una interfaz) y `WorkflowDefinitionServiceImpl` (lectura con
  `@Transactional(readOnly = true)`, `WorkflowDefinitionServiceImpl.java:28`).

**Alternativas descartadas**:

- *Otro método en `RequestController`.* Cabe, pero fusiona con el #57 y mete un recurso distinto en un
  archivo que ya trae búsqueda, bandeja, detalle, timeline y emisiones.
- *`/api/metrics/requests`*, el nombre del prototipo de `router-ia`. Ese prototipo queda solo como
  referencia (spec, *Assumptions*): agrupa por código de estado sin trámite, cuenta los rechazos como
  completados (#51) y carga cada solicitud en memoria (#58). Heredar su ruta heredaría sus expectativas.
- *Colgarlo de `/api/workflow-definitions`* (el catálogo). Mezclaría configuración con datos vivos, y el
  catálogo trae solo la versión vigente.

**Costo aceptado**: tres archivos de producción para una consulta, y una ruta que **compite** con
`GET /api/requests/{id}` (D9).

---

## D4 — Qué NO trae: total, filtros, tiempos, prioridad, caché

**Decisión**: la respuesta no lleva un total; la operación no declara parámetros (uno que llegue se
**ignora**, FR-006); no lleva tiempos de ciclo ni prioridad; y no se cachea (FR-007).

**Justificación**:

- **Sin total**: el cliente suma lo que necesite, y SC-002 (la suma por trámite iguala el número de
  solicitudes) es comprobable con los datos que ya vienen. Un total sería un campo que agregar y
  mantener sin que nadie lo pida.
- **Sin filtros — §III**: un conteo filtrado puede identificar a una persona: «Ingeniería de Sistemas,
  rechazada: 1» señala al único estudiante de ese programa al que le negaron la adición (spec, FR-006).
  Con el conteo global y sin dimensiones, esa inferencia no existe. **Ignorar** el parámetro y no
  rechazarlo mantiene compatible a un cliente que mande uno de más y garantiza que la respuesta es
  siempre la de toda la base. Técnicamente basta con **no declarar `@RequestParam`**: Spring ignora los
  que no declara.
- **Sin tiempos ni prioridad**: fuera de alcance (#37; el sistema no modela urgencia).
- **Sin caché**: cada llamada lee la base, con el mismo criterio que el catálogo de definiciones —*«no hay
  caché de definiciones»*, `WorkflowGenericityIT.java:157`— y que hace que una solicitud radicada o un
  trámite cargado por SQL se vean en la consulta siguiente.

**Alternativas descartadas**:

- *Filtro opcional por programa, fecha o responsable*: es el riesgo que FR-006 prohíbe. Un corte por
  semestre sería un filtro (por fecha el riesgo es bajo; por programa, alto) y queda fuera.
- *Rechazar con 400 un parámetro desconocido*: convertiría un cliente descuidado en un error, para
  proteger algo que ya está protegido por no leerlo.

**Costo aceptado**: si un cliente manda `?program=X` creyendo que filtra, recibe la respuesta global sin
aviso. Se declara en el contrato. Y el conteo **acumula desde la primera solicitud**, sin corte por
periodo (spec, *Assumptions*).

---

## D5 — Una sola consulta, que parte de los estados y rellena el 0 en la base

**Decisión**: **una** consulta en `IRequestRepo`, que parte de los **estados** y trae las solicitudes con
un `left join`:

```java
@Query("""
        select d.code, d.version, d.name, s.code, s.name, s.initial, s.finalState, count(r)
        from WorkflowState s
        join s.definition d
        left join Request r on r.currentState = s
        group by d.code, d.version, d.name, s.code, s.name, s.initial, s.finalState
        """)
List<StateCountRow> findStateCounts();
```

`count(r)` cuenta las solicitudes que casan y da **0** en un estado sin ninguna: el relleno de FR-003 lo
hace la base, no un bucle en Java.

**Nombre del método**: `findStateCounts` y **no** `countBy…`. En Spring Data el prefijo `countBy` es el
de una consulta *derivada* que devuelve un número: con el `@Query` presente la anotación manda, pero el
nombre le afirmaría al lector otra cosa, y si alguien quitara la anotación el método cambiaría de
significado sin error de compilación.

**Justificación**:

- **Parte de los estados, no de las solicitudes.** Es lo que garantiza que un estado sin solicitudes
  aparece (FR-003) y que un trámite recién cargado por SQL aparece con todo en 0 (FR-010, SC-006). Contar
  desde `request` y agrupar solo devolvería los estados que tienen solicitudes: en la base de demo, **5 de
  los 14 estados tenían 0** (spec, *Contexto medido*).
- **`left join` a una entidad sin asociación**: `Request` sí tiene la asociación `currentState`
  (`Request.java:66-68`), pero `WorkflowState` no tiene la inversa —solo `Request → WorkflowState`—, así que
  desde el estado hay que unir con `on`. Hibernate lo soporta (D6, R1).
- **Una consulta y no N+1**: agrupar por `code` y `version` de la definición y por el estado da todo lo que
  el plegado necesita en una ida a la base.
- **`count(r)` y no `count(*)`**: con el `left join`, `count(*)` contaría 1 en un estado sin solicitudes;
  `count(r)` cuenta solo filas donde `r` no es nulo. Es la razón por la que el mutante `inner join` y el
  cambio a `count(*)` **se ven** en el IT (D10).

**Alternativas descartadas**:

- *`select r.currentState, count(r) from Request r group by …`.* No rellena el 0, y el relleno tendría que
  hacerse en Java consultando además el catálogo de estados: dos consultas y un cruce a mano, para lo que
  la base ya sabe hacer.
- *Traer las solicitudes y contarlas en memoria.* Es lo que hacía el prototipo de `router-ia` (#58): carga
  cada solicitud, con sus datos personales, para producir un número.
- *Un `select new` con el nombre completamente calificado.* Ver D6.

**Costo aceptado**: no hay índice sobre `request.current_state_id`, así que el `left join` recorre la
tabla de solicitudes entera en cada llamada. A 30–40 solicitudes por semestre es irrelevante (D11).

**Evidencia**:

```bash
git grep -n 'current_state_id' -- 'src/main/resources/db/migration/*.sql'
# → V2.0.0__Create_workflow_tables.sql:53  (la columna, con su FK)
# → V2.2.0__Harden_workflow_constraints.sql:21 (un comentario)   — ningún CREATE INDEX
git grep -n "CREATE .*INDEX" -- "src/main/resources/db/migration/*.sql"
# → V1.0.0, V2.0.0 (student_document, student_name_lower, timeline), V2.2.0, V2.3.0 (request_subject), V4.1.0 (sello): ninguno sobre request.current_state_id
```

---

## D6 — La proyección: un `record` anidado en el repositorio, sobre la reescritura de Spring Data

**Decisión**: el resultado de la consulta se proyecta a un `record` **anidado en `IRequestRepo`**,
`StateCountRow`, con un componente por columna seleccionada y **`Long`** (no `long`) para el conteo. La
selección es **múltiple y sin alias**: se apoya en que Spring Data JPA reescribe una consulta `@Query` de
selección múltiple a una expresión constructora cuando el método devuelve un DTO. Es la **primera
proyección del código** (`git grep 'select new'` y las interfaces de proyección dan 0 hoy).

```java
record StateCountRow(String definitionCode, int definitionVersion, String definitionName,
        String stateCode, String stateName, boolean initial, boolean finalState, Long count) { }
```

**Justificación (fuentes técnicas, leídas el 2026-09-29)**:

- **R1 — Hibernate soporta el `left join` a una entidad no asociada con `on`.** Hibernate ORM **7.2.19.Final**
  (la fija `spring-boot-dependencies-4.0.7.pom`, propiedad `hibernate.version`) lo cubre con su prueba
  `EntityJoinTest`, que une una entidad sin asociación con `left join User u on r.lastUpdateBy =
  u.username`:
  <https://github.com/hibernate/hibernate-orm/blob/main/hibernate-core/src/test/java/org/hibernate/orm/test/query/hql/EntityJoinTest.java>.
  ⚠️ El árbol es `main` de Hibernate, no la etiqueta 7.2.19: es evidencia de que la característica existe y
  está probada, no de que el comportamiento sea idéntico byte a byte en esa versión (**confianza media**;
  la confirma el arranque del contexto en el primer IT).
- **R2 — Spring Data JPA reescribe la selección múltiple** a una expresión constructora cuando el método
  devuelve un DTO o `record`. La documentación dice que *«Spring Data JPA can support you with your JPQL
  queries by introducing constructor expressions»* y que `SELECT u.firstname, u.lastname FROM User u …` se
  reescribe a `SELECT new UserDto(u.firstname, u.lastname) FROM User u …`. Dos condiciones, ambas
  cumplidas acá: *«JPQL constructor expressions must not contain aliases for selected columns»*, y *«if an
  `@Query`-annotated query already uses constructor expressions, then Spring Data backs off»* (con
  `select new`, la clase tendría que llevar el nombre **completamente calificado**):
  <https://github.com/spring-projects/spring-data-jpa/blob/main/src/main/antora/modules/ROOT/pages/repositories/projections.adoc>
  (BOM de Spring Data 2025.1.6 según el `spring-boot-dependencies` de Boot 4.0.7; **confianza media**: la
  página es `main`).
- **`Long` y no `long`**: `count(...)` devuelve `Long`; con un componente primitivo la resolución del
  constructor puede no casar. Es un riesgo conocido de las expresiones constructoras y se evita con el
  tipo envoltorio.
- **Un `record` y no una interfaz de proyección**: inmutable, sin proxies y con tipos explícitos; se
  serializa a mano en el servicio, nunca sale por la API (la frontera es `RequestCountsResponse`, §III).

**Alternativas descartadas**:

- *`select new com.uniremington…IRequestRepo$StateCountRow(...)`.* Es válido y explícito, pero ata la
  consulta al nombre de un paquete y de una clase anidada (`$`), y al mover el record se rompe en silencio
  hasta el arranque. La reescritura, sin nombres en el texto, evita esa fragilidad.
- *El `record` como tipo de primer nivel en `repo/`.* Es la **alternativa de respaldo** si la resolución
  del nombre de la clase anidada fallara. Se prefiere el anidado (lo aprobó el propietario) porque el tipo
  solo existe para esta consulta y no debe ofrecerse al resto del código.
- *Devolver `List<Object[]>`.* Sin tipos, y un cambio de orden en la selección rompe a quien lo lea.

**Costo aceptado — un riesgo declarado como supuesto**: la resolución del nombre de una clase **anidada**
por la reescritura de Spring Data **no está verificada** por la documentación. Tampoco lo está que
Hibernate empareje `int` y `boolean` primitivos del record con `Integer` y `Boolean` de la selección.
Ambas cosas las confirma **el arranque del contexto en el primer IT**: Spring Data valida la consulta al
crear el repositorio, así que un fallo sería inmediato y ruidoso, no silencioso. Si fallara: el record
pasa a primer nivel y, si hiciera falta, los tipos primitivos a `Integer` y `Boolean`.

---

## D7 — El plegado va en el servicio, y la respuesta no ordena estados

**Decisión**: `RequestCountServiceImpl` (`@Transactional(readOnly = true)`) llama a la consulta y pliega
las filas (~20–30 líneas):

1. agrupa las filas por `definitionCode` (los trámites **nunca** se mezclan, FR-004);
2. en cada grupo, calcula la versión mayor y **conserva** las filas de esa versión **o** con `count > 0`
   (D2);
3. agrupa las conservadas por `stateCode` y **suma** los conteos (FR-005);
4. toma el nombre y las marcas de la fila de **mayor versión** de cada código, y el nombre del trámite de
   la de mayor versión del grupo;
5. ordena los trámites por nombre y, ante un empate, por código.

Los **estados no llevan orden significativo**, con el mismo criterio que
`WorkflowDefinitionDetailResponse.java:15-17`: el flujo admite devoluciones y rechazos, no es una
secuencia, y un orden afirmaría una linealidad que la configuración no tiene.

**Justificación**:

- **La regla de versiones no cabe en la consulta** sin subconsultas correlacionadas por código de trámite
  y por código de estado; en Java son unas pocas líneas y se prueban con **un unitario sin base de datos**.
  La consulta hace lo que la base hace bien (unir, agrupar, rellenar el 0); el servicio, la regla de
  negocio (qué versión manda).
- **Pura y testeable**: el repositorio es la única frontera de I/O; el plegado se prueba entregándole
  filas armadas a mano, incluidas v1 + v2, que en la base de demo dependen de datos que no controlo.
- **Por qué ordenar en Java y no en SQL**: el orden por nombre depende del **nombre final** del trámite (el
  de la versión más alta), que se conoce después de plegar. Se ordena por `String` natural de Java.
  **Diferencia declarada** con el catálogo, cuyo `order by d.name` usa la intercalación de la base
  (`IWorkflowDefinitionRepo.java:24`): pueden discrepar solo entre nombres que difieran en mayúsculas o
  tildes tras un prefijo común. Con los dos trámites y `Trámite …` del IT no ocurre. El cliente no debe
  depender de una posición concreta más allá de «por nombre».

**Alternativas descartadas**:

- *Filtrar y sumar por versión en la consulta.* Consulta más larga, sin test unitario posible, y la regla
  de D2 quedaría escrita en JPQL.
- *`order by d.name` en la consulta y conservar el orden con un `LinkedHashMap`.* Reproduce la intercalación
  del catálogo, pero la posición de un trámite dependería de la primera fila que aparezca, y si dos
  versiones tuvieran nombres distintos el resultado sería difícil de razonar.
- *Ordenar los estados* (inicial primero, finales al final). Inventa un orden que la configuración no
  declara (FR-011b de la 007).

**Costo aceptado**: un servicio con lógica propia, en un proyecto donde varios servicios solo delegan. Se
justifica porque es lo único sensible de la feature (§V).

---

## D8 — `StateResponseMapper` gana una sobrecarga, y sigue siendo el único sitio

**Decisión**: `StateResponseMapper` agrega `toResponse(String code, String name, boolean initial,
boolean finalState)`; la sobrecarga que recibe la entidad **delega** en ella. Así el mapeador sigue siendo
el «único sitio donde se construye `StateResponse`» que afirma su javadoc
(`StateResponseMapper.java:7`).

**Justificación**:

- La respuesta necesita construir un `StateResponse` **sin** una entidad `WorkflowState` a mano: los
  valores vienen del `record` de la consulta (D6). Duplicar `new StateResponse(...)` en el servicio nuevo
  rompería la garantía de un solo sitio que la 007 fijó, con la consecuencia que el propio javadoc explica:
  un mutante sobre `isInitial` solo alcanzaría a uno de los caminos (`StateResponseMapper.java:14-17`).
- La sobrecarga que ya existe (`toResponse(WorkflowState)`, `:29-32`) se conserva y pasa a delegar: cero
  cambio de comportamiento para el detalle y el catálogo.

**Alternativas descartadas**:

- *Construir `StateResponse` directamente en el servicio nuevo.* Duplica el constructor, exactamente lo
  que el mapeador vino a evitar.
- *Una factoría estática en el record.* Ya descartada por la 007: metería una dependencia de `dto/` hacia
  `model/` (`StateResponseMapper.java:12-13`).

**Costo aceptado**: tocar un archivo existente (una línea nueva y una delegación).

---

## D9 — Sin sesión responde 401, y la ruta compite con `/{id}`

**Decisión**: no se agrega ninguna regla de seguridad. `GET /api/requests/counts` queda cubierto por
`anyRequest().authenticated()` (`SecurityConfig.java:171`) y el punto de entrada sirve `application/problem+json`
(`SecurityConfig.java:174`).

**R3 — sin sesión, un `GET` protegido responde 401 y no 403.** El 403 de este backend es de las peticiones
**mutantes sin CSRF**; un `GET` no lo necesita. Ya lo prueban `RequestControllerIT.java:817-829` (localizar,
detalle y timeline sin sesión → 401) y `WorkflowDefinitionControllerIT.java:134-139` (el catálogo sin
sesión → 401); el `:111` del mismo IT prueba el 401 de un `POST` **con** token CSRF. El encargo de esta
feature pide 401, y es 401.

**R4 — colisión de ruta.** `/api/requests/counts` compite con `GET /api/requests/{id}`. Un segmento
literal gana por especificidad del patrón: no lo decide el orden del archivo, y ya se midió con `/inbox`
(`RequestController.java:95-101`: antes de que el método existiera, `GET /api/requests/inbox` entraba por
`/{id}`, fallaba al convertir «inbox» a UUID y devolvía 400). Como el controller nuevo es una clase aparte,
la competencia es entre dos clases en el **mismo** mapeo, y la regla de especificidad es la misma.

**Consecuencia para el RED**: hoy, **con sesión**, `GET /api/requests/counts` responde **400** (convierte
«counts» a UUID). Ese es el RED esperado del primer IT, y se observa antes de escribir código (Fase B).

**Alternativas descartadas**:

- *Otra ruta sin colisión* (`/api/request-counts`, `/api/stats/requests`). Evita el 400 de hoy, pero se
  aparta de la convención REST del resto (`/api/requests/inbox`) para esquivar un problema que la
  especificidad del patrón ya resuelve y que un IT verifica.
- *Un `permitAll` para el tablero.* Viola FR-001 y el §III.

**Costo aceptado**: una colisión que hay que **probar**, no suponer. El IT con sesión lo hace (200 y no
400); si alguien registrara un patrón más específico que gane, el IT lo detecta.

---

## D10 — Estrategia de tests: diferencias, no totales, y cuatro mutantes

**Decisión**: un unitario del plegado, un IT sobre el JSON servido y cuatro mutantes (plan, *Plan de
pruebas*). Los IT miden **diferencias antes/después y no totales**.

**R5 — la base de los IT es compartida entre clases.** El contexto de Spring se cachea por clave y cada
contexto levanta su contenedor (`TestcontainersConfiguration` declara un `PostgreSQLContainer` como bean,
`TestcontainersConfiguration.java:14-18`); todas las clases que usan `@TramitaIntegrationTest` con las
mismas propiedades **comparten contexto y, por tanto, base**. `TramitaIntegrationTest.java:13-16` explica el
lado del **caché de contexto** —*«un literal distinto en un solo archivo le cuesta a esa clase un contexto
entero»*—, pero **no** dice que la base sea compartida ni que nadie la limpie: esa parte es **inferencia**
(confianza media). Lo que sí lo dicen, en prosa, los propios IT: *«la base es compartida entre ITs y acumula
solicitudes»* (`RequestControllerIT.java:988`), *«la base es compartida entre IT»*
(`RequestControllerIT.java:1251`, `WorkflowGenericityIT.java:600`) y *«el catálogo puede traer también la
definición DEMO de `WorkflowGenericityIT`, así que `$[0]`/`$[1]` no bastan para afirmar sobre un trámite»*
(`WorkflowDefinitionControllerIT.java:52-54`). Un IT que afirmara «`FINALIZADA` = 1» fallaría según el orden
en que corran las demás clases.

**Justificación de cada mutante** (quién lo mata):

| Mutante | Lo mata | Por qué |
|---|---|---|
| `inner join` en vez de `left join` | IT de la definición sin solicitudes | Sus estados no tienen filas en `request`: con `inner join` desaparecen |
| Plegar solo por código de estado | Unitario `EN_FACULTAD` compartido + IT de la adición a `EN_FACULTAD` | Dos trámites con el mismo código se sumarían |
| Descartar filas viejas aunque tengan solicitudes | Unitario «solo v1 con `count > 0`» | El filtro sería «solo la versión mayor» |
| No sumar entre versiones | Unitario v1 + v2 | Quedaría una fila por código en vez de la suma |

⚠️ **Los dos últimos solo los mata el unitario** (D2, D7): en el IT, que la v1 de la novedad tenga
solicitudes depende de la base compartida. El IT de la novedad (siete códigos, ninguno repetido) mata la
variante «no plegar» a secas, porque sin plegar los seis códigos comunes saldrían dos veces.

**La definición propia del IT** se inserta por SQL con el patrón `WorkflowGenericityIT.insertDefinition`
(`WorkflowGenericityIT.java:667`, javadoc `:661-666`). Dos condiciones que no se negocian:

- **Su nombre empieza por «Trámite …»**, para ordenar después de «Novedad de notas» y no romper
  `WorkflowDefinitionControllerIT.java:36-49`, que afirma `$[0]` = `ADICION_CREDITOS` y `$[1]` =
  `NOVEDAD_NOTAS` v2 **por orden de nombre**. (La `DEMO` de genericidad ya se llama «Trámite de
  demostración», `WorkflowGenericityIT.java:616`.)
- **Cumple el invariante de configuración** de `WorkflowGenericityIT` (javadoc `:198-206`, test `:207-210`):
  ningún estado final con salidas y todo no final con al menos una. El helper ya lo respeta: solo siembra los
  estados que las transiciones conectan (`ABIERTO` inicial, `CERRADO` final).

**Los movimientos de estado** usan `AdvanceRequestSupport.advanceFromCurrentState(mockMvc, id, target,
note)` (`AdvanceRequestSupport.java:41`), que lee el estado vigente y envía el `fromStateCode` que exige
H-10 (PR #61).

**Alternativas descartadas**:

- *Afirmar totales exactos.* Frágil por lo anterior.
- *Limpiar la base entre clases.* Cambia la infraestructura de toda la suite por una feature.
- *Mockear el repositorio en el IT.* No probaría la consulta, que es el riesgo real (el `left join`, el
  `group by`, la reescritura de D6).

**Costo aceptado**: un IT algo más largo, porque calcula una línea base antes de actuar.

---

## D11 — Volumen e índice: contar toda la base en cada llamada es barato

**Decisión**: no se agrega un índice sobre `request.current_state_id` ni se cachea.

**Justificación**: el volumen documentado es de **30–40 solicitudes por semestre** para el trámite más
frecuente (`specs/007-coordination-inbox/spec.md:134`). A esa escala, recorrer la tabla es sub-milisegundo;
un índice sería una columna de infraestructura especulativa (§I) y una migración que esta feature evita.
PostgreSQL **no** crea un índice por una clave foránea, así que `current_state_id` no lo tiene
(evidencia en D5).

**Alternativa descartada**: *un índice y una vista materializada de conteos.* Resuelve un problema de
volumen que el proyecto no tiene y obliga a mantener la coherencia con el timeline.

**Costo aceptado**: si el volumen creciera órdenes de magnitud, la consulta habría que revisarla. Un
supuesto de la spec, no una garantía.

---

## D12 — Hoy solo se entrega la Fase A

**Decisión**: la entrega de la tesis de hoy incluye **spec, plan y contrato** (Fase A). La
implementación (Fase B) y el cierre (Fase C) **no se hacen hoy** y quedan documentados para que
`/speckit-tasks` los baje.

**Justificación**:

- **Duración real de features parecidas**, del primer commit al merge: 007 ≈ 21 h y 009 ≈ 15,5 h (medido en
  el historial, según el plan de trabajo del 2026-09-28). Esta es más chica que la 007, pero el ciclo con
  TDD, mutantes y review sigue siendo del orden de **4,5 a 6 horas de trabajo enfocado**. ⚠️ Es una
  **analogía**, no una medición de esta feature.
- **Fijar el contrato desbloquea al front** sin poner código nuevo en `main` a horas de la entrega.
- **No mergear no rompe nada**: el tablero actual funciona sin las tarjetas porque el front las quitó en
  `tramita-frontend#56` (PR #93). Lo que se pierde por no implementar es la vuelta de las tarjetas, no una
  función existente.

**Alternativa descartada**: *implementar hoy con TDD completo.* Solo con más de ~6 horas y sin mergear en
la última hora. Es decisión del propietario, que sabe cuánto tiempo queda.

**Costo aceptado**: el contrato queda fijado **sin haberse ejercido contra un servidor**. Los riesgos de
D6 —el nombre de la clase anidada, los tipos primitivos— **no estarán confirmados** hasta la Fase B.

---

## Lo que esta feature NO investiga, y por qué

- **La marca de rechazo en `workflow_state`** — es la opción 2 del #58 y la corrección de fondo de
  `tramita-frontend#51`; D1 explica por qué queda fuera y qué costaría.
- **Conteos por programa, fecha o responsable** — filtros prohibidos por FR-006 y el §III.
- **Tiempos de ciclo (#37) y prioridad** — fuera de alcance (spec).
- **Contar devoluciones de la novedad de notas** — no son un estado, son un retorno a `EN_PREPARACION`.
- **Normativa institucional** — la feature no depende de ninguna.
