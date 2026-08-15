<!--
Sync Impact Report — Constitución de Trámita
============================================
Cambio de versión: 2.1.0 → 2.2.0
Ratificada: 2026-07-02 | Última enmienda: 2026-08-14
Bump: MINOR (guía materialmente ampliada — §III y §IV)

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

## Restricciones tecnológicas

- Stack fijo, chasis heredado de Convenia: **Spring Boot 4 / Java 21 / PostgreSQL**, Maven.
- **Flyway gestiona el schema; Hibernate solo valida** (`ddl-auto: validate`). Todo cambio
  de schema se hace con una migración nueva, nunca a mano.
- Los errores se devuelven según **RFC 7807** (`application/problem+json`).
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

**Versión**: 2.2.0 | **Ratificada**: 2026-07-02 | **Última enmienda**: 2026-08-14
