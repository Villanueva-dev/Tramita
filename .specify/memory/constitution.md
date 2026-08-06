<!--
Sync Impact Report — Constitución de Trámita
============================================
Cambio de versión: 2.0.0 → 2.1.0
Ratificada: 2026-07-02 | Última enmienda: 2026-08-06
Bump: MINOR (guía materialmente ampliada — §IV)

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
- III. Seguridad por defecto
- IV.  Decisiones defendibles y trazables ← enmendado en 2.1.0
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

**Rationale**: elegir la opción segura más simple que cumple el requisito, respaldada por
OWASP e IETF, en lugar de tecnología de moda que resuelve problemas que este sistema no tiene.

### IV. Decisiones defendibles y trazables

Toda decisión arquitectónica DEBE poder justificarse con un trade-off explícito
("elegí X frente a Y, sabiendo que el costo es Z"). La especificación precede al código.
Se privilegia la trazabilidad requisito → código: el documento de requisitos se estructura
según **ISO/IEC/IEEE 29148:2018** (cláusula 9.6) y la arquitectura se documenta con **C4 y
4+1**. Las afirmaciones técnicas y normativas que sustentan decisiones DEBEN verificarse
contra documentación oficial vigente (vía Context7) y citarse con su URL en la documentación
del proyecto.

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

**Versión**: 2.1.0 | **Ratificada**: 2026-07-02 | **Última enmienda**: 2026-08-06
