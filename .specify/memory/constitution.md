<!--
Sync Impact Report — Constitución de Trámita
============================================
Cambio de versión: 2.3.0 → 2.3.1
Ratificada: 2026-07-02 | Última enmienda: 2026-09-19
Bump: PATCH (rectificación de evidencia y aclaración de un mecanismo; ningún principio
      se crea, se elimina ni se redefine)

Enmienda 2026-09-19 — v2.3.1
----------------------------
Dos cambios, ninguno de fondo.

1. ERRATA DE LA EVIDENCIA DE LA v2.3.0 — no de su decisión
---------------------------------------------------------
La entrada de la v2.3.0, más abajo, explica por qué NO se ratificó el invariante «sin PDF
no hay trámite cerrado» y lo sustenta con dos pruebas. Ninguna de las dos se re-ejecuta hoy
con el resultado que allí se consigna:

- Dice «Hoy `grep -ric pdf src/main/java` → 0». Hoy ese comando devuelve **46**
  coincidencias: PDFBox y el renderer del DO-FR-100 entraron con las features
  `005-formal-document` y `006-verifiable-document-seal`, posteriores a aquella enmienda.
- Dice «RequestServiceImpl:144 solo impide avanzar DESDE uno». Esa línea hoy es la
  persistencia de asignaturas. La validación vive en **RequestServiceImpl:260**
  (`if (current.isFinalState())`).

**La decisión de la v2.3.0 se mantiene, y por la misma razón.** Medido el 2026-09-19 sobre
`789faea`: el motor sigue impidiendo únicamente avanzar DESDE un estado final, y sigue sin
impedir llegar a uno sin PDF —`DocumentServiceImpl.generateFor` ni siquiera consulta el
estado de la solicitud—. Ratificar aquel invariante seguiría poniendo aquí una regla que el
código no cumple.

Lo que caduca, entonces, es la evidencia, no la conclusión. Se rectifica porque el §IV
exige que una afirmación se verifique contra la fuente vigente, y porque la v2.2.2 ya fijó
la lección de que **un comando citado como prueba debe poder re-ejecutarse y dar el mismo
resultado**. Una prueba cuyo resultado cambió deja de sostener nada, aunque lo que afirmaba
siga siendo cierto por otras razones.

Siguiendo el precedente de aquella v2.2.2, la rectificación se registra acá y **el texto de
la enmienda v2.3.0 no se reescribe**: un registro documenta lo que se midió entonces.

2. ACLARACIÓN DEL «Estado» DEL §VII
-----------------------------------
El §VII declaraba como mecanismo vigente un solo trigger, `trg_timeline_immutable`. Desde
la feature 006 el mismo patrón protege también el registro de emisiones del documento, con
`trg_document_seal_immutable` sobre `request_document_seal`
(`V4.1.0__Register_document_seals.sql`). Se nombran los dos.

No cambia lo que el principio exige: la garantía sigue siendo de la base de datos y el §VII
sigue sin atarse a una implementación. Por eso es PATCH y no MINOR — se describe con más
precisión un estado, no se amplía la guía.

Enmienda 2026-09-13 — v2.3.0
----------------------------
Se ratifican DOS principios que vivían en `docs/nuevo-proyecto/02-constitucion/
draft-principios.md` y que nunca entraron a esta constitución: la configurabilidad del
motor (§III del borrador) y la trazabilidad inmutable (§IV del borrador).

Origen: auditoría de la cita «Principio III», que nombra cosas distintas según qué
documento se abra — «Workflow configurable por dato» en el borrador, «Seguridad por
defecto» aquí. La tesis arquitectónica del proyecto no estaba en su propio documento de
gobernanza.

Evidencia de que no estaban, re-ejecutable (§IV):
  grep -ic "configurab" .specify/memory/constitution.md          → 0 antes de esta enmienda
  grep -ic "inmutab"    .specify/memory/constitution.md          → 0 antes de esta enmienda
  git log --oneline -S 'configurab' -- .specify/memory/constitution.md → cero commits
Probado además con nueve patrones (por dato, parametriz, parámetro, maquinaria,
genericidad, timeline, PDF) y con `grep -F` literal: todos en 0. No se habían quitado:
nunca se incorporaron.

Por qué MINOR: «Gobernanza» fija MINOR para principio nuevo. No se redefine ni se elimina
ninguno de los cinco vigentes, de modo que no es MAJOR.

Por qué APENDIZADOS y no insertados en el orden del borrador: existen más de veinte citas
a los Principios I, III, IV y V en specs/001-auth-login, specs/002-workflow-engine y
specs/003-request-form-rules, todas posteriores a la ratificación y correctas contra esta
numeración. Insertar en medio las habría roto todas.

Corrección respecto del borrador: su §IV describía el mecanismo como «UPDATE y DELETE
revocados a nivel SQL» sobre una tabla `solicitud_event`. El código NO hace eso: usa el
trigger `trg_timeline_immutable` BEFORE UPDATE OR DELETE sobre `request_transition_log`
(V2.0.0__Create_workflow_tables.sql:80-88). El §VII se redactó contra el código y fija la
garantía sin fijar el mecanismo.

Lo que deliberadamente NO se ratifica:
- El invariante «sin PDF no hay trámite cerrado», que el borrador colgaba de su §III. Hoy
  `grep -ric pdf src/main/java` → 0 y el motor no impide llegar a un estado final sin PDF
  (RequestServiceImpl:144 solo impide avanzar DESDE uno). Ratificarlo pondría aquí una
  regla que el código incumple. Se registra en la spec de SP3 (issue #10).
- «Class y QF son cajas negras» y «chasis heredado de Convenia»: la ratificación los
  degradó a «Restricciones tecnológicas» de forma deliberada y ahí se quedan.

Enmienda 2026-08-16 (b) — ERRATA de la v2.2.1
---------------------------------------------
La entrada de la v2.2.1, más abajo, afirmaba que la constitución era «la última
referencia desactualizada del repo, verificado con `rg -n --hidden '7807'` → una sola
ocurrencia». **Esa afirmación es FALSA y queda rectificada aquí.**

El comando citado se ejecutó **acotado a `.specify/`, `README.md` y `CLAUDE.md`**, y su
resultado se consignó como si hubiera sido una búsqueda global. Ejecutado sobre el
repositorio completo, `rg -n --hidden --glob '!.git' '7807'` devuelve **18 líneas en 9
archivos**. Verificado además con `git show --stat 2e036e2`: aquel commit **no toca ningún
archivo bajo `specs/001-auth-login/`**, de modo que la carpeta entera de la feature 001
quedó fuera de la migración.

Citas de RFC 7807 pendientes al 2026-08-16, detectadas por auditoría independiente:
`specs/001-auth-login/contracts/openapi.yaml` (líneas 7 y 252) — el contrato que la propia
sesión había declarado «la autoridad» —, `specs/001-auth-login/plan.md`,
`specs/001-auth-login/research.md`, `specs/001-auth-login/tasks.md`,
`specs/002-workflow-engine/research.md`, `docs/auditoria-seguridad-2026-07-18.md` y
`docs/nuevo-proyecto/02-constitucion/draft-principios.md`.

**La norma no cambia**: «Restricciones tecnológicas» sigue exigiendo RFC 9457, que es
correcto. Lo que se rectifica es la evidencia con que se justificó el alcance. Es PATCH
porque no altera ningún principio ni regla.

Lección incorporada al §IV en la práctica: **un comando citado como prueba debe poder
re-ejecutarse y dar el mismo resultado**. Una salida transcrita sin su invocación exacta
no es evidencia verificable — es justamente lo que el §IV exige evitar.

Enmienda 2026-08-16 (a) — v2.2.1
--------------------------------
«Restricciones tecnológicas» pasa de citar **RFC 7807** a **RFC 9457** para
`application/problem+json`. La 9457 obsoleta a la 7807 según el propio documento
(https://www.rfc-editor.org/rfc/rfc9457.html).

Motivo: el commit `2e036e2` (2026-08-15) había migrado la mayor parte de las citas del
repositorio y la constitución seguía desactualizada. ~~Verificado con `rg -n --hidden
'7807'` → una sola ocurrencia.~~ **← afirmación errónea; ver la errata de arriba.**

Es PATCH y no MINOR porque no cambia ninguna regla ni principio: la obligación de devolver
los errores en `application/problem+json` es idéntica antes y después. Solo se corrige la
identificación de la norma que la respalda. Ningún artefacto existente requiere rehacerse.

Enmienda 2026-08-14
-------------------
§III incorpora la **minimización de datos personales** como regla explícita: qué se
almacena, qué no se persiste nunca, y la obligación de anonimizar por rol en fixtures y
material de ejemplo.

§IV separa el medio de verificación según la clase de fuente. Context7 queda acotado a
fuentes técnicas; la normativa institucional se verifica solo contra el documento obtenido
de la fuente, y mientras no se obtenga, toda afirmación que dependa de él se marca como
provisional y no auditada.

Motivo: ambos huecos salieron de auditar la constitución contra las entrevistas con la
skill `auditar-vs-entrevistas` v2.0.0. El de §III era una omisión: el equipo asumió ante
la Coordinación el compromiso de no retener datos sensibles y ningún principio lo recogía.
El de §IV era un mecanismo inaplicable: Context7 no puede verificar el reglamento
estudiantil ni el PEI, que son justamente las fuentes que el proyecto necesita para
defender la legalidad del trámite — y que siguen sin obtenerse.

Es MINOR y no MAJOR porque ningún principio existente se invierte ni se vuelve
incompatible: §III suma una regla que antes no estaba escrita y §IV precisa el alcance de
un mecanismo que ya exigía. No obliga a rehacer trabajo: la 001 y la 002 no persisten
datos personales fuera de lo que el trámite necesita.

Enmienda 2026-08-06
-------------------
§IV sustituye la referencia normativa **IEEE 830** por **ISO/IEC/IEEE 29148:2018**, y
precisa que el documento de requisitos se estructura según su cláusula 9.6 mientras la
arquitectura se documenta con C4 y 4+1.

Motivo: IEEE 830-1998 figura como *superseded* en el catálogo del IEEE Standards
Association; 29148:2018 es la edición vigente de la norma que la reemplaza. Se descartó
además IEEE 1016-2009, cuyo estado en ese catálogo es *inactive-reserved*.

Es MINOR y no PATCH porque cambia la norma concreta que rige la estructura del entregable
de requisitos, no solo su redacción. No se rehace trabajo existente: el SRS todavía no
está redactado.

Enmienda 2026-08-02
-------------------
§II pasa de "Arquitectura por feature (Screaming Architecture)" a "Arquitectura por
capas". Motivo: el equipo cursa formación en Spring Boot con material organizado
package-by-layer; alinear el proyecto con su referencia de estudio elimina el costo de
traducción y reduce el riesgo de error al trasladar patrones.

Trade-off aceptado y documentado en el propio principio: el árbol de paquetes deja de
"gritar" el dominio y se pierde la correspondencia 1:1 carpeta ↔ componente C4; esa
correspondencia pasa a documentarse en los diagramas de arquitectura.

El principio incorpora además dos reglas que antes eran implícitas: el prefijo `I` en
las interfaces, y la excepción de los filtros del chain de seguridad, que no llevan
estereotipo porque Spring Boot los auto-registraría por duplicado.

Principios vigentes:
- I.   Simplicidad primero (KISS + YAGNI)
- II.  Arquitectura por capas            ← enmendado en 2.0.0
- III. Seguridad por defecto              ← enmendado en 2.2.0
- IV.  Decisiones defendibles y trazables ← enmendado en 2.1.0 y 2.2.0
- V.   Testing del comportamiento sensible
- VI.  Workflow configurable por dato     ← nuevo en 2.3.0
- VII. Trazabilidad inmutable del trámite ← nuevo en 2.3.0

Secciones: Restricciones tecnológicas · Idioma y convenciones · Proceso y gestión
(Scrum, sprints de 2 semanas) · Gobernanza

Plantillas dependientes (verificadas, alineadas, sin cambios):
- OK .specify/templates/plan-template.md  (el "Constitution Check" se resuelve en runtime)
- OK .specify/templates/spec-template.md  (genérico, sin principios hardcodeados)
- OK .specify/templates/tasks-template.md (tests OPTIONAL: coherente con el Principio V)

TODOs pendientes: ninguno
-->

# Constitución del proyecto Trámita

## Principios rectores

### I. Simplicidad primero (KISS + YAGNI)

Se construye lo mínimo que cumple el requisito. No se agregan columnas, capas ni
abstracciones especulativas ("por si acaso"). El sistema crece con migraciones Flyway
cuando el requisito **exista**, no cuando se anticipa.

**Rationale**: en un equipo de dos personas con plazo acotado, cada pieza de más es
deuda que hay que mantener y defender. La extensibilidad la da el proceso de migraciones
versionadas, no las estructuras pre-construidas.

### II. Arquitectura por capas

El código se organiza *package-by-layer*: `controller/`, `dto/`, `model/`, `repo/`,
`security/`, `service/` (contratos) con `service/impl/` (implementaciones), `util/`, y
`shared/` para lo transversal (`config/`, `exception/`, `seed/`). Las interfaces se
nombran con prefijo `I`. Los servicios se exponen siempre por interface.

**Rationale**: el equipo está en formación activa en Spring Boot con material didáctico
organizado de esta forma. Alinear el proyecto con su referencia de estudio elimina el
costo de traducción en cada consulta y reduce el riesgo de error al trasladar patrones —
un factor material en un equipo de dos personas que aprende mientras construye, con un
plazo acotado. Es además la organización mayoritaria del ecosistema Spring, lo que
facilita que un tercero se incorpore al proyecto.

**Trade-off aceptado**: el árbol de paquetes deja de "gritar" el dominio y se pierde la
correspondencia 1:1 entre carpeta y componente del diagrama C4. Esa correspondencia se
documenta explícitamente en los diagramas de arquitectura, no en la estructura de
carpetas. Se acepta el costo a cambio de la coherencia con la formación del equipo.

**Excepción documentada**: los filtros que `SecurityConfig` construye e inserta a mano en
el filter chain (`LoginThrottlingFilter`, `CsrfCookieFilter`) NO llevan estereotipo. Un
filtro anotado con `@Component` es auto-registrado por Spring Boot en la cadena del
servlet container además de en el chain de seguridad, ejecutándose dos veces por request.

### III. Seguridad por defecto

La autenticación usa sesión del lado del servidor con cookie `HttpOnly; Secure;
SameSite=Strict` (patrón BFF); NO se usa JWT. Las contraseñas se almacenan con BCrypt.
Los DTOs en la frontera de la API son obligatorios — NUNCA se exponen entities. La
validación autoritativa DEBE ocurrir en el backend; la validación del frontend es solo UX.

**Datos personales — minimización obligatoria**. El sistema almacena únicamente los datos
personales que el trámite necesita para existir (identificación del solicitante, datos
académicos de la solicitud y trazabilidad de quién actuó). NO se persisten documentos de
identidad, recibos de pago ni anexos con datos de terceros: el documento formal se entrega
y es la institución quien lo custodia en sus propios sistemas. Todo dato personal en
documentos de ejemplo, fixtures o material de prueba DEBE estar anonimizado por rol.

**Rationale**: elegir la opción segura más simple que cumple el requisito, respaldada por
OWASP e IETF, en lugar de tecnología de moda que resuelve problemas que este sistema no tiene.
La minimización, además, no es solo higiene técnica: es un compromiso que el equipo asumió
explícitamente ante la Coordinación durante la Sesión 2 de entrevistas, y opera en un país
donde el tratamiento de datos personales tiene marco legal propio — **Ley 1581 de 2012** y
su decreto reglamentario **1377 de 2013**. La Coordinación confirmó que la institución
recoge autorización de tratamiento tanto de estudiantes al matricularse como de empleados
al vincularse; el sistema no puede ofrecer menos garantías que el proceso que reemplaza.

**Pendiente de verificación documental**: la referencia legal anterior está citada por su
identificación oficial pero **no se ha contrastado contra el texto publicado**, ni se ha
obtenido la política de tratamiento de datos de la propia universidad. Hasta que ocurra,
se aplica el régimen del §IV para normativa institucional.

### IV. Decisiones defendibles y trazables

Toda decisión arquitectónica DEBE poder justificarse con un trade-off explícito
("elegí X frente a Y, sabiendo que el costo es Z"). La especificación precede al código.
Se privilegia la trazabilidad requisito → código: el documento de requisitos se estructura
según **ISO/IEC/IEEE 29148:2018** (cláusula 9.6) y la arquitectura se documenta con **C4 y
4+1**. Las afirmaciones que sustentan decisiones DEBEN verificarse contra documentación
oficial vigente y citarse en la documentación del proyecto, con el medio de verificación
que corresponda a cada clase de fuente:

- **Fuentes técnicas** (librerías, frameworks, estándares publicados): se verifican vía
  Context7 o contra el catálogo del organismo emisor, y se citan con su URL.
- **Normativa institucional** (reglamento estudiantil, PEI, resoluciones y comunicados
  internos de la universidad): NO está en Context7 y puede no estar publicada. Se verifica
  únicamente contra el documento institucional obtenido de la fuente, y se cita por su
  identificación oficial y fecha de obtención. **Mientras el documento no se obtenga, toda
  afirmación que dependa de él se marca explícitamente como provisional y no auditada**, en
  el artefacto donde aparezca. Un dato de este tipo NUNCA se presenta como hecho establecido
  por el solo respaldo de una entrevista.

**Rationale**: es un trabajo de grado que se defiende ante un jurado. Una decisión sin
trade-off explícito ni fuente verificable no debería estar en el código ni en el documento.

**Nota normativa**: la vigencia de una norma la fija el catálogo de su organismo emisor, no
su antigüedad. IEEE 830-1998 figura allí como *superseded* y fue reemplazada por la familia
29148, cuya edición vigente es la de 2018; IEEE 1016-2009 figura como *inactive-reserved*,
por lo que el diseño se documenta con C4 y 4+1 en su lugar.

### V. Testing del comportamiento sensible

El comportamiento crítico, no obvio o de alto costo de regresión DEBE tener tests. No se
testea lo trivial por dogma. Los tests se priorizan por valor, no por cobertura nominal.

**Rationale**: con un primer sprint de dos semanas, el esfuerzo de testing se invierte
donde el riesgo lo justifica, no en inflar una métrica de cobertura.

### VI. Workflow configurable por dato, no por código

El motor DEBE modelar los trámites del alcance —adición de créditos y novedad de notas— con
la misma maquinaria, parametrizada por configuración persistida en base de datos. Dos
code-paths casi idénticos, uno por trámite, son una violación de este principio y no una
optimización. Incorporar un trámite nuevo cuya estructura ya está cubierta NO DEBE requerir
desplegar código.

**Rationale**: es la pregunta de investigación misma del proyecto — *«¿puede un motor de
workflow configurable reducir tiempo, re-trabajo y opacidad en la tramitación de adición de
créditos y novedad de notas?»* (`docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md`,
§6). La genericidad es el aporte académico: sin ella el sistema colapsa a «dos formularios
con flujo cableado», que es exactamente el statu quo de Word + correo que viene a reemplazar.

**Tensión declarada con el §I (KISS + YAGNI)**: el §I prohíbe abstracciones especulativas.
Aquí la configurabilidad NO es especulación: es el requisito. Existen dos trámites reales con
estructura idéntica y un tercero documentado —el Reglamento de Homologaciones, Acuerdo n.º 17
del 3 de octubre de 2023— cuyas reglas cambian por facultad. El principio es falsable: un
trámite de esa misma familia que exigiera tocar código lo refutaría.

### VII. Trazabilidad inmutable del trámite

Toda transición de estado DEBE generar una entrada de auditoría inmutable, de modo que el
histórico completo de una solicitud pueda reconstruirse en cualquier momento. La inmutabilidad
se garantiza **en la base de datos**, no por disciplina del código de aplicación: la tabla del
timeline solo admite INSERT y rechaza UPDATE y DELETE aunque se intenten por acceso directo al
motor de datos.

**Rationale**: el sistema combate tres variables —tiempo de ciclo, re-trabajo y opacidad
(árbol §6)— y ninguna es medible sin un histórico en el que se pueda confiar. Sin trazabilidad
inmutable el sistema pierde su justificación frente al proceso manual, cuyo problema central
es precisamente que el estado vive en correos y en la memoria de una persona.

**Estado**: implementado en la feature `002-workflow-engine` y extendido en la
`006-verifiable-document-seal`. Hoy la garantía la sostienen dos triggers, ambos
`BEFORE UPDATE OR DELETE` y ninguno sobre `INSERT`: `trg_timeline_immutable` sobre
`request_transition_log` (`V2.0.0__Create_workflow_tables.sql`), que protege el histórico de
transiciones, y `trg_document_seal_immutable` sobre `request_document_seal`
(`V4.1.0__Register_document_seals.sql`), que protege el registro de emisiones del documento
formal. El principio exige la **garantía** a nivel de base de datos; no fija el mecanismo,
que puede cambiar mientras la garantía se conserve.

## Restricciones tecnológicas

- Stack fijo, chasis heredado de Convenia: **Spring Boot 4 / Java 21 / PostgreSQL**, Maven.
- **Flyway gestiona el schema; Hibernate solo valida** (`ddl-auto: validate`). Todo cambio
  de schema se hace con una migración nueva, nunca a mano.
- Los errores se devuelven según **RFC 9457** (`application/problem+json`), que obsoleta a la
  RFC 7807 (<https://www.rfc-editor.org/rfc/rfc9457.html>).
- Los servicios se exponen siempre por interface; los controllers inyectan la interface.
- **Class** y **QF** son cajas negras: no se integran técnicamente. El sistema entrega el
  documento formal y un humano lo asienta donde corresponde.

## Idioma y convenciones

- Documentación, commits y comentarios en **español** (neutral/profesional).
- Identificadores de código (clases, métodos, variables) en **inglés**.

## Proceso y gestión

- El proyecto se gestiona con **Scrum**, en sprints de **2 semanas** (al menos para la
  primera entrega).
- Cada sprint cierra con objetivos verificables y demostrables.
- El flujo de trabajo sigue Spec-Driven Development (Spec Kit): la especificación, el plan
  y las tareas preceden a la implementación. El código se genera en la fase de implementación,
  no antes.

## Gobernanza

La constitución prevalece sobre cualquier otra práctica del proyecto. Las enmiendas se
documentan y versionan según SemVer (MAJOR: cambios incompatibles de principios; MINOR:
nuevo principio o guía materialmente ampliada; PATCH: aclaraciones y refinamientos). Cada
especificación y plan verifica su alineación con estos principios; toda complejidad
introducida debe justificarse explícitamente. La guía operativa del día a día vive en
`CLAUDE.md`.

**Versión**: 2.3.1 | **Ratificada**: 2026-07-02 | **Última enmienda**: 2026-09-19
