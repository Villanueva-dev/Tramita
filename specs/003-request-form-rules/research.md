# Research — Formularios validados y reglas de negocio por trámite (003)

**Phase 0** del plan. Cada decisión lleva su racional, sus alternativas y su fuente
(constitución §IV).

Contexto particular de esta feature: existe un **prototipo de referencia funcionando** —
`origin/router-ia`, commit `82ece40`, rama del compañero de equipo que **no se mergea**
(escenario C). Sus 321 líneas relevantes fueron revisadas pieza por pieza antes de este plan
(review con 0 críticos, 4 altos y 4 medios). Varias decisiones de abajo **confirman** lo que
el prototipo ya resolvió bien; otras **corrigen** lo que resolvió mal. Ambas cosas se
declaran, porque diseñar mirando código que corre no es lo mismo que diseñar en frío, y el
jurado debe poder ver la diferencia.

---

## D1 — Las reglas de negocio se configuran como parámetros en base, no como motor de reglas

**Decisión**: cada definición de trámite puede declarar parámetros `(parameter_key,
parameter_value)` en la tabla `workflow_parameter`. Las validaciones los leen en tiempo de
ejecución. No se incorpora un motor de reglas ni un lenguaje de expresiones.

**Racional**: FR-007 y SC-005 exigen que ajustar el tope de créditos sea un cambio de
configuración. Una tabla clave-valor por definición lo cumple con una tabla y un `UNIQUE`.
Es además la continuación natural de D1/D2 de la `002`: la configuración ya vive en base;
esto extiende el mismo mecanismo de los estados a los límites numéricos.

**Alternativas descartadas**:
- *Motor de reglas embebido (Drools, MVEL, SpEL sobre expresiones almacenadas)*: resuelve un
  problema que este MVP no tiene —dos trámites, dos reglas numéricas— y trae un lenguaje de
  expresiones que hay que aprender, testear y defender. Principio I.
- *Parámetros en `application.yml`*: cambiar un tope exigiría redesplegar, que es justo lo que
  SC-005 prohíbe. Además no permite valores distintos por trámite (FR-014).

**Confirma el prototipo**: `V3.0.0` con `UNIQUE(definition_id, parameter_key)` y el seed atado
a `code + version`. Se porta tal cual.

## D2 — Configuración ausente o inválida es error del servidor, no rechazo al usuario

**Decisión**: cuando una validación necesita un parámetro que no está cargado, o que está
cargado con un valor no interpretable, la operación falla con **500** y un `ProblemDetail` de
título fijo («Configuración del trámite incompleta»), sin detalle interno. Se introduce
`IncompleteConfigurationException`. La solicitud **no** se registra.

**Racional**: es la corrección del hallazgo alto del prototipo. Su `validateCredits` usa
`ifPresent`, así que un trámite sin parámetro cargado **acepta cualquier cantidad de créditos
y responde 201**: una regla de negocio que falla abierto y en silencio. FR-010 y FR-011 lo
prohíben explícitamente, y US2-4 y US2-6 lo ejercitan.

La elección de 500 sobre 422 es deliberada: 422 comunicaría al usuario que su solicitud está
mal, cuando quien está mal es la configuración del sistema. Atribuir al usuario una falla del
operador manda a la Coordinación a corregir un formulario que no tiene ningún error.

**Trade-off**: un trámite mal configurado queda inoperante en vez de operar sin límite. Se
acepta: para un sistema cuya tesis es la trazabilidad, es preferible no aceptar una solicitud
a aceptarla sin haber aplicado la regla que dice haber aplicado.

**Alternativa descartada**: tratar el parámetro ausente como «sin límite». Es el
comportamiento actual del prototipo y es indefendible ante el jurado: la validación aparece
en el código pero no ocurre.

**Fuente**: RFC 9457 §3 para la forma del error
(<https://www.rfc-editor.org/rfc/rfc9457.html>); constitución, restricciones tecnológicas.

## D3 — Las notas se almacenan como numérico, no como texto

**Decisión**: `current_grade` y `proposed_grade` son `NUMERIC(3,2)` con `CHECK` de rango
amplio en base, y viajan como número en el contrato.

**Racional**: una nota es un número. El prototipo las guarda como `VARCHAR(20)`, y con eso la
base acepta `'abc'` en la columna: la única garantía de que una nota sea numérica vive en el
código Java, y desaparece ante cualquier carga por SQL. Es exactamente el modo de falla que
la `002` ya sufrió en su review (SC-005 sancionó cargar trámites por SQL crudo y descubrir que
lo «garantizado por convención de la semilla» no estaba garantizado).

**Trade-off aceptado**: el contrato del formulario cambia de string a number para esos dos
campos, y `NUMERIC(3,2)` fija el número de decimales. El costo es bajo hoy —el frontend aún no
consume este formulario, su Fase B no lo incluye— y sería alto después de que lo consuma.

**Precisión elegida**: `NUMERIC(3,2)` cubre `0.00`–`9.99`, holgado para la escala 0.0–5.0 sin
comprometerse con ella en el esquema: el rango efectivo lo fija la configuración (D4), no la
columna. El `CHECK` de base es una cota de sanidad, no la regla institucional.

## D4 — El rango de notas son dos parámetros, no un valor compuesto

**Decisión**: `MIN_GRADE` y `MAX_GRADE` como dos filas de `workflow_parameter`, del mismo modo
que `MAX_CREDITS`.

**Racional**: FR-012 exige que el rango sea configuración y no constante en código. Dos
parámetros usan el mecanismo que ya existe, sin inventar un formato de cadena (`"0.0-5.0"`)
que habría que parsear, validar y documentar — y cuyo parseo fallido sería un tercer modo de
error. Principio I.

**Alternativas descartadas**:
- *Un parámetro `GRADE_RANGE` con formato compuesto*: introduce una mini-gramática propia.
- *Rango fijo en código*: es el estado del prototipo (`0.0`–`5.0` literal en
  `RequestBusinessRules`), incoherente con que el tope de créditos sí sea configurable.

## D5 — Las guardas se resuelven por nombre contra un registro de implementaciones

**Decisión**: se define `IWorkflowGuard` con un método que declara la clave que atiende. Las
implementaciones son beans; el motor recibe la colección inyectada por Spring y resuelve por
clave. `guard_key` guarda **solo el nombre**. Una clave sin implementación registrada bloquea
la transición con error de configuración (FR-019).

**Racional**: FR-018 exige que el motor conozca el nombre de la regla, no el trámite. Un
registro de beans mantiene la genericidad: agregar una guarda es agregar una clase que se
auto-registra, sin tocar el motor ni un `switch`.

**Alternativas descartadas**:
- *Enum de guardas*: cada guarda nueva recompila el motor, y reintroduce en el código el
  conocimiento de dominio que D1 de la `002` sacó de ahí.
- *Resolver la clase por reflection desde el nombre almacenado*: cualquier rename rompe la
  configuración en runtime sin aviso del compilador. Prohibido por el checklist del proyecto.
- *`switch` sobre la clave en el motor*: idéntico problema que el enum, con menos type-safety.

**Deuda declarada**: en esta feature se implementa el mecanismo y **ninguna guarda concreta de
producción**, porque ningún trámite del alcance necesita todavía una transición condicionada.
Se entrega el mecanismo porque D3 de la `002` lo comprometió a esta feature y porque SP3/SP4
lo van a consumir; se entrega con una guarda de prueba en tests, no con una inventada en el
seed. Es la lectura más estricta de YAGNI compatible con el compromiso ya adquirido.

## D6 — `guard_key` es opcional; sin guarda, el comportamiento es el de la 002

**Decisión**: `workflow_transition.guard_key` es `NULL`-able. Una transición sin clave no
ejecuta validación adicional.

**Racional**: FR-017 lo exige y es lo que preserva las cinco definiciones ya sembradas por la
`002` sin migración de datos. Todas las transiciones existentes quedan con `NULL`.

## D7 — El contrato se amplía con un constructor de compatibilidad

**Decisión**: `CreateRequestBody` pasa a llevar los campos del formulario y la lista de
asignaturas; se conserva un constructor con los tres argumentos originales que completa el
resto con valores por defecto.

**Racional**: FR-006 y SC-007 exigen no romper a los clientes ni a los tests de la `002`.

**Confirma el prototipo**: es su mejor decisión — amplió el record de 3 a 9 componentes sin
tocar un solo test de la `002`. Se porta el patrón.

## D8 — Créditos: la cota inferior es normativa; la superior es de sanidad y se declara

**Decisión**: los créditos de una asignatura se validan `>= 1` en el DTO y con
`CHECK (credits > 0)` en base. Se agrega además una cota superior por asignatura como defensa
de entrada.

**Racional**: FR-009 exige que ningún valor pueda restar del total. La validación va en los
dos lados por el mismo argumento de D3: la del DTO protege el caso de uso, la de base protege
el dato de cualquier otra vía de escritura.

**Declaración de respaldo**: la cota inferior tiene respaldo conceptual —una asignatura no
puede aportar créditos negativos—. La **cota superior no tiene respaldo normativo**: es un
límite de sanidad para rechazar entrada absurda, no una regla institucional, y se documenta
como tal en el código. No se convierte en parámetro configurable porque no hay ningún
requisito que pida ajustarla (Principio I).

## D9 — Dos migraciones: `V2.3.0` para el formulario, `V3.0.0` para las reglas

**Decisión**:
- `V2.3.0__Persist_request_form_data.sql`: columnas del formulario en `request` y tabla
  `request_subject`.
- `V3.0.0__Configure_business_rules.sql`: tabla `workflow_parameter`, columna
  `workflow_transition.guard_key`, y el seed de los parámetros iniciales.

**Racional**: separar por naturaleza del cambio. La primera solo agrega datos de captura; la
segunda introduce la configurabilidad de reglas, que es un cambio conceptual mayor y por eso
sube de *minor*. Ninguna migración existente se edita: `main` está en `V2.2.0` y estas dos se
apilan encima (restricciones tecnológicas: Flyway posee el schema).

**Qué NO se porta del prototipo en `V2.3.0`**: la columna del correo del estudiante (FR-020,
constitución §III — su único consumidor era SP7, fuera de alcance) y la columna de prioridad
(fuera del alcance por decisión de producto: sin respaldo en las entrevistas, diferida a SP5).

## D10 — El total de créditos no se persiste

**Decisión**: la suma de créditos de una solicitud se calcula al validar; no se guarda como
columna.

**Racional**: es un dato derivable de las asignaturas, y persistirlo abre la posibilidad de
que quede desincronizado del detalle que lo origina. Ningún requisito pide consultarlo por
separado. Principio I.

## D11 — Los datos de prueba pasan a ser sintéticos evidentes

**Decisión**: los ejemplos y fixtures de esta feature usan un identificador de estudiante con
forma inequívocamente sintética. Se corrigen además las 6 ocurrencias heredadas en `main` del
número con forma de cédula real.

**Racional**: FR-022 y constitución §III («todo dato personal en documentos de ejemplo,
fixtures o material de prueba DEBE estar anonimizado por rol»). El valor actual es inventado
—no aparece en `material-coord/`— pero tiene forma de documento de identidad y el repositorio
es público.

**Alcance declarado**: se corrige el valor hacia adelante. **No** se reescribe el historial de
`main`: exigiría desactivar el ruleset de protección y forzar un push, y el remedio es peor
que el problema para un dato inventado.
