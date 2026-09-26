# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Trabajo de grado en curso — Ingeniería de Sistemas, Universidad Remington (modalidad Distancia, SNIES 53112, Resolución 015939 del 1 de septiembre de 2023). Equipo de dos personas, plazo ≈ 2,5 meses. **Estado**: **seis features cerradas y mergeadas a `main`** (`001-auth-login` … `006-verifiable-document-seal`): autenticación, motor de workflow, formularios y reglas configurables, captura pública del formato, PDF formal y sello verificable (panorama técnico y arranque en `README.md`). El estado vigente NO se lleva en un documento: vive en los **milestones e issues de GitHub**, donde cada sub-problema SP1–SP7 es un issue y cada sprint un milestone cuyo avance calcula GitHub. El chasis Spring Boot 4 / Java 21 se hereda de `../convenia/`.

## Qué se está construyendo

MVP de **motor de workflow configurable** para dos trámites académicos con estructura idéntica (formato Word + firmas escaneadas + cadena de correos):

1. **Adición de créditos** — autorizar matrícula por encima del tope de créditos del semestre.
2. **Novedad de notas** — corregir/registrar nota luego de cerrado el periodo oficial.

**Alcance**: Sede Cali únicamente. **Class** y **QF** son cajas negras (no se integra técnicamente — el sistema entrega el PDF formal y un humano lo asienta donde corresponda). Documento canónico de scope, métricas y supuestos: `docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md` (pregunta de investigación, SP1–SP7, ordenamiento por sprint, supuestos y riesgos).

## Fuentes primarias

| Recurso | Por qué importa |
|---------|-----------------|
| `material-coord/transcript-entrevista-coordi.md`, `material-coord/transcript-entrevista-coordi-2.md` | Entrevistas semi-estructuradas a la Coordinación Académica de la Sede Cali. **Insumo único** del que salen los procesos, los tiempos (1 semana – 2 meses por trámite) y los actores. Cítalas explícitamente cuando justifiques una decisión de scope. |
| `docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md` | Árbol de problemas según Marco Lógico (CEPAL/ILPES 2005 — Ortegón, Pacheco y Prieto). Estado: **borrador inicial, pendiente de validación con tutor y coordinación**. Si cambias el scope o las métricas, edita aquí — es la fuente de verdad del planteamiento. |
| `docs/realone-doc-proyecto-july30.docx` | **El documento de grado real**, el que audita la tutora. Copia local del 2026-07-30; **el original vive en OneDrive** — si cambia allá, hay que bajar una copia nueva, esta no se sincroniza sola. Ya tiene redactados el planteamiento, la justificación, el objetivo general y los **5 objetivos específicos** (la tutora fijó un máximo de 5). Siguen en blanco: resumen, palabras clave, marco teórico, metodología, resultados y conclusiones. Leerlo con `libreoffice --headless --convert-to txt:Text --outdir <destino> <archivo>`. |
| `docs/BASE_DOCUMENTO_TRAMITA.md` | Borrador markdown del documento de grado. **No es un duplicado del `.docx`: es su complemento** — tiene redactadas varias secciones que en el `.docx` siguen como placeholders (resumen, palabras clave, introducción, dedicatoria, agradecimientos). Es la cantera para llenarlo, no un archivo muerto. |

## Flujo Spec Kit

Repo inicializado con **Spec Kit v0.8.12** (integración `claude`, script `sh`, branching secuencial — ver `.specify/init-options.json`). Los skills `speckit-*` están bajo `.claude/skills/` y se invocan vía Skill cuando el usuario tipea `/speckit-*`.

Ciclo SDD canónico (definido en `.specify/workflows/speckit/workflow.yml`):

```
specify → review-spec (gate) → plan → review-plan (gate) → tasks → implement
```

Comandos complementarios: `speckit-constitution`, `speckit-clarify`, `speckit-checklist`, `speckit-analyze`, `speckit-taskstoissues`.

**El auto-commit de git NO corre: los commits de fase son manuales.** Hay dos archivos de configuración y mandan en distinto nivel. `.specify/extensions.yml` registra los hooks (`after_specify`, `after_plan`, `after_tasks`, `after_implement`, etc.) y habilita su ejecución, pero **`.specify/extensions/git/git-config.yml` los apaga uno por uno**: `auto_commit.default: false` y cada evento con `enabled: false`, así que `auto-commit.sh` sale en silencio sin commitear. El único hook git que sí ejecuta es `before_specify`, que crea la rama de la feature. Cada fase se commitea a mano con la convención de `.gitmessage`. *(Verificado el 2026-08-06: los 12 commits de la rama `002-workflow-engine` son todos manuales.)*

### Estado actual del Spec Kit

- `.specify/memory/constitution.md` **está ratificada (v1.0.0, 2026-07-02) y va por la v2.3.1 (última enmienda: 2026-09-19)** vía `/speckit-constitution`: **7 principios** (KISS+YAGNI · **arquitectura por capas** · seguridad por defecto **+ minimización de datos personales** · decisiones trazables · testing pragmático · **workflow configurable por dato** · **trazabilidad inmutable**) + secciones de restricciones tecnológicas, idioma y proceso (Scrum, sprints de 2 semanas). La versión vigente se lee de una línea, sin creerle a este archivo: `grep -n '^\*\*Versión\*\*' .specify/memory/constitution.md`. **Siete enmiendas** hasta hoy:
  - **v2.0.0 (MAJOR, 2026-08-02)** — §II pasa de package-by-feature a package-by-layer, para alinear el proyecto con el material de formación del equipo; el trade-off (el árbol deja de "gritar" el dominio, la correspondencia C4 pasa a los diagramas) está documentado en el propio principio.
  - **v2.1.0 (MINOR, 2026-08-06)** — §IV sustituye **IEEE 830** por **ISO/IEC/IEEE 29148:2018** (cláusula 9.6) y fija C4 + 4+1 para la arquitectura. Es MINOR y no PATCH porque cambia la norma que rige la estructura del entregable de requisitos, no solo su redacción; no obliga a rehacer trabajo porque el SRS todavía no está redactado.
  - **v2.2.0 (MINOR, 2026-08-14)** — §III suma la **minimización de datos personales** (qué se almacena, qué nunca se persiste, anonimización por rol en fixtures) y §IV **separa el medio de verificación por clase de fuente**: Context7 queda acotado a fuentes técnicas, y la normativa institucional solo se verifica contra el documento obtenido de la fuente — mientras no se obtenga, lo que dependa de ella se marca como provisional y no auditada. Ambos huecos salieron de auditar la constitución con `auditar-vs-entrevistas` v2.0.0.
  - **v2.2.1 (PATCH, 2026-08-16)** — «Restricciones tecnológicas» pasa de citar **RFC 7807** a **RFC 9457**, que la obsoleta según el propio documento. Es PATCH y no MINOR porque la obligación de devolver los errores en `application/problem+json` es idéntica antes y después: solo se corrige la identificación de la norma que la respalda.
  - **v2.3.0 (MINOR, 2026-09-13)** — ratifica **dos principios nuevos** que vivían en el borrador y nunca habían entrado: **§VI workflow configurable por dato** (la tesis arquitectónica del proyecto: incorporar un trámite ya cubierto NO debe requerir desplegar código) y **§VII trazabilidad inmutable** (la garantía vive en la base de datos, no en la disciplina del código). Van **APENDIZADOS y no insertados** en el orden del borrador: había más de veinte citas a los Principios I, III, IV y V en las specs de las features 001–003, y numerar en medio las habría roto todas. ⛔ **Cualquier principio futuro se apendiza, por la misma razón.**
  - **v2.2.2 (PATCH, 2026-08-16)** — **errata de la anterior**. La v2.2.1 justificó su alcance citando `rg -n --hidden '7807'` → «una sola ocurrencia», pero ese comando se había ejecutado acotado a tres rutas y se consignó como si fuera global; sobre el repositorio completo devuelve **18 líneas en 9 archivos**. La norma no cambia: se rectifica la evidencia con que se justificó el alcance. Lección incorporada al §IV: **un comando citado como prueba debe poder re-ejecutarse y dar el mismo resultado**.
  - **v2.3.1 (PATCH, 2026-09-19)** — segunda errata de evidencia, y por la misma lección que la v2.2.2. La v2.3.0 justificó no ratificar el invariante «sin PDF no hay trámite cerrado» con dos pruebas que hoy dan otro resultado: `grep -ric pdf src/main/java` devolvía **0** y hoy devuelve **46** (entraron PDFBox y el renderer con las features 005 y 006), y la validación que citaba en `RequestServiceImpl:144` vive hoy en la **:260**. ⚠️ **La decisión NO cambia y se re-midió**: el motor sigue impidiendo solo avanzar DESDE un estado final y sigue sin impedir llegar a uno sin PDF. Se rectifica la evidencia, no la conclusión, y el texto de la v2.3.0 **no se reescribe**. Además el §VII pasa a nombrar **los dos** triggers que hoy sostienen la garantía: `trg_timeline_immutable` y `trg_document_seal_immutable`.
- La feature `001-auth-login` recorrió el ciclo entero (`specify → plan → tasks → implement`) y fue la primera en hacerlo: el backend de autenticación está **mergeado a `main` (PR #2, 2026-07-15)**, sus artefactos viven en `specs/001-auth-login/` y el arranque está en `README.md`. Pertenece al milestone **Sprint 0 — Fundaciones**, no al Sprint 1: el login es anterior al árbol de problemas y no corresponde a ningún SP.
  - ⚠️ **Qué sprints están completos NO se escribe acá.** Esta línea decía «Sprint 1 completo» atribuyéndoselo a la 001, y era falso en dos sentidos a la vez. El avance lo calcula GitHub: `gh api repos/:owner/:repo/milestones --jq '.[] | "\(.title) — abiertos:\(.open_issues) cerrados:\(.closed_issues)"'`.
- `arbol-de-problemas.md` sigue siendo la fuente del planteamiento. ✅ Su §11 pedía **bajar SP1–SP7 a backlog Scrum**, y eso **ya está hecho**: los siete son issues de GitHub (#7 a #13), cada uno con su milestone. ⛔ No re-agendarlo. Lo que sigue pendiente son los **entregables formales de tesis**: requisitos según ISO/IEC/IEEE 29148:2018 y arquitectura C4 / 4+1.

## Reutilización arquitectónica de Convenia

El árbol de problemas (§11.4) declara que la arquitectura inicial reusa el chasis de `../convenia/`. Para razonar arquitectura del MVP:

- Leer `../convenia/CLAUDE.md` (capas, multi-tenancy por `university_id` filtrado manualmente en cada query, Flyway-valida-Hibernate, errores RFC 9457, auditoría por listener, orden Lombok-antes-de-MapStruct).
- `../convenia/MER.mermaid` ilustra convenciones del modelo de datos del proyecto hermano (no es el modelo de este MVP).
- **El dominio es distinto**: aquí no hay `Agreement` ni máquina de estados de práctica; aquí hay `Trámite` (o equivalente) con workflow **configurable por dato**, no por código. Copiar el **patrón de capas y plumbing**; no copiar las entidades de Convenia.

## Convención de commits

Conventional Commits en español. La estructura y las reglas están en **`.gitmessage`**
(raíz del repo). Al clonar hay que activarla: `git config commit.template .gitmessage` —
esa configuración vive en `.git/config` y **no se versiona**, así que cada persona la
activa en su copia.

Lo innegociable: el cuerpo explica el **porqué**, no repite el diff; un commit tiene un
solo propósito; y todo cambio de comportamiento lleva una línea `Verificado:` con el
comando ejecutado y su resultado.

## CI y reglas de `main`

`.github/workflows/ci.yml` corre `./mvnw clean verify` en cada push a `main` y en cada PR. **No
necesita secretos**: los IT traen su Postgres vía `@ServiceConnection`.

El ruleset `branch-protect` tiene `bypass_actors` vacío, así que **nadie puede pushear directo a
`main`, ni el administrador**. Todo va por PR con el check `build` en verde — documentación
incluida. `squash` está deshabilitado a propósito: aplastaría los cuerpos de commit.

El estado del proyecto vive en los **milestones e issues de GitHub** (un milestone por sprint, un
issue por SP1–SP7), no en un archivo. Se cierra con `Closes #N` en el cuerpo de la PR.

## Metodología (citar al usarla)

- **Planteamiento**: Marco Lógico — Ortegón, Pacheco y Prieto (2005), *Metodología del Marco Lógico*, CEPAL/ILPES Serie Manuales N.º 42.
- **Verbos de objetivos**: taxonomía de Bloom revisada — Anderson & Krathwohl (2001), priorizando *aplicar / analizar / crear*.
- **Gestión**: Scrum, 3 sprints (S1: SP1+SP2+SP6 → S2: SP3+SP4 → S3: SP5+SP7).
- **Requisitos** (pendiente): **ISO/IEC/IEEE 29148:2018**, estructura del SRS según su cláusula 9.6. Sustituye a IEEE 830-1998, que figura como *superseded* en el catálogo del IEEE Standards Association. Se descartó IEEE 1016-2009 (*inactive-reserved*): el diseño se documenta con C4 + 4+1.
- **Arquitectura** (pendiente): C4 + 4+1.
- **Ciclo de vida**: ISO/IEC/IEEE 12207:2017 (edición vigente).

Al citar literatura o normativa institucional, **incluir la referencia exacta** en cada afirmación — alineado con la regla general #4 del CLAUDE.md global.

<!-- SPECKIT START -->
**Feature ACTIVA: `009-program-catalog-annex`** — Sprint 3, issues `Tramita#40` (la hoja de vida académica que
exige Ingeniería de Sistemas al reenviar) y `Tramita#50` (el programa entra como texto libre). Fase: **IMPLEMENTADA
el 2026-09-26, sin push y sin PR** (spec `202d594` → gate `review-spec` `ef4d61a` → plan `a57b136` → tareas `d11482f`
→ Fase 2 `f350a3c` → US1 `c3e53a2` (con `BREAKING CHANGE:`) → US2 `5849039` → review `188f093` + `8c03742`
→ docs, el commit de cierre). Las 48 tareas de `tasks.md` están marcadas con lo observado (RED, verde, mutante, commit);
`tasks.md` se edita A MANO y ⛔ `/speckit-tasks` NO se vuelve a correr (regenera desde plantilla,
`.claude/skills/speckit-tasks/SKILL.md:77`). Suite final: **171 unitarios + 151 IT** (línea base `163 + 127` en `d11482f`);
**19 mutantes** muertos (13 de `tasks.md` + 6 del review), cada uno por el test que su tarea nombra.
Si la PR ya se abrió o se mergeó no se escribe acá: `gh pr list --head 009-program-catalog-annex --state all`.
Artefactos en `specs/009-program-catalog-annex/`: `spec.md` («Implementada»), `plan.md`, `research.md` (D1–D11),
`data-model.md`, `contracts/openapi.yaml`, `quickstart.md` (**recorrido contra el servidor `dev` el 2026-09-26**: doce
bloques, una corrección) y `tasks.md`. ⚠️ Este bloque lo reescribe **solo `/speckit-plan`** cuando corre
(`.claude/skills/speckit-plan/SKILL.md`); fuera de eso se corrige a mano, y así se hizo en el commit de cierre de la 009.
🔑 **Qué ES la 009**: el programa se ELIGE de un catálogo de 13 programas de la Sede Cali (dato: tabla
`academic_program`, siembra `V5.1.0`; lista PROVISIONAL dictada el 25-sep sin confirmación escrita de la Coordinación,
§IV) por los dos canales, con coincidencia EXACTA (FR-004) validada en `RequestBusinessRulesImpl` ANTES que los
créditos; un programa fuera del catálogo es 422 «Formato inválido» (público) / 400 «Petición inválida» (interno) con
`invalidFields:["program"]`, sin eco (`InvalidFieldValueException`, un `@ExceptionHandler` por advice). La lista se
publica en **`GET /api/public/programs`** (cuarto endpoint abierto, solo `name`, sin caché, orden de la intercalación
`en_US.utf8`). El detalle expone `annexRequirement {documentName, sourceHint}` (aditivo, `NON_NULL`) cuando
`workflow_annex_rule` lo declara para la VERSIÓN con que nació la solicitud × su programa; hoy solo ADICION_CREDITOS v1
× Ingeniería de Sistemas → hoja de vida académica (evidencia `:184`, `:553`, `:762`). Se deriva al leer, NO se guarda,
NO mira el estado (FR-009/FR-010, §VI); bandeja y resumen NO lo llevan (§III). Lo radicado no se toca (FR-005).
⛔ **NO reabrir** (spec, gates, plan e implementación): la regla por facultad (es por programa); derivar la facultad del
programa; los tres anexos universales de la novedad de notas (FUERA; una regla «para todos» es aditiva después);
`program_id` en `request` (D3: se guarda el NOMBRE); normalizar mayúsculas, tildes o espacios (D8: tres mutantes lo
vigilan); administrar el catálogo por pantalla (§I); Bean Validation con BD (D4); recibir archivos (FR-012, 006);
guardar el requisito en vez de derivarlo (D6: un mutante lo vigila).
🔑 **Condición de despliegue (D11), re-medida el 2026-09-26 sobre `origin/main` del front = `0650548`**: el formulario
público sigue mandando texto libre (`components/do-fr-100/sections.tsx:161`, `TextField`) y nada consume
`/api/public/programs`; el interno usa la constante `PROGRAMS` (`lib/ui-constants.ts:33`) y preselecciona
`PROGRAMS[0]`. **Sin el desplegable del front, cualquier programa escrito a mano recibe 422.** El selector entra ANTES o
A LA VEZ que la PR del back; el brief al front va como issue (precedente front#59). El front avanza en paralelo:
re-medir su punta antes de escribir cualquier número.
📎 Al abrir la PR (solo con OK del usuario): `Closes #40` y `Closes #50` en texto plano, sin `--milestone`; el
`BREAKING CHANGE` de `c3e53a2`; la lista provisional; el enlace al brief. Los contratos históricos de la 003 (`:159`) y la
004 (`:27-35`, `:283`) ya llevan la nota «ENMENDADO POR LA 009». Review con agente limpio hecho (T045): sin críticos ni
altos; sus 5 hallazgos se confirmaron con comandos propios antes de aplicarse (`188f093`, `8c03742`).
Última feature cerrada: `008-student-closure-notice` — SP7 (`#13`, CERRADO), PR #49 (`0cf3fa3`, 25-sep).
⛔ Nada de la 008 se re-agenda. Sus decisiones vivas: DOS acciones manuales (`mailto:` P1 y `wa.me` P2, esta solo
con móvil `^3\d{9}$`); el sistema no envía nada ni registra «avisado»; el back expone `origin`, `studentEmail` y
`studentPhone` y el front decide; teléfono `[0-9]{10}` validado en el API, sin normalizar; sin migración.
Antes: `007-coordination-inbox` (SP5 + `#22`, PR #47), `006-verifiable-document-seal` (SP4, PR #41),
`005-formal-document` (SP3, PR #31), `004-public-request-capture` y `003-request-form-rules`.
Stack: Java 21 · Spring Boot 4.0.7 (Security 7, Data JPA, Validation, WebMVC) · PostgreSQL + Flyway
(validate) · PDFBox 3 · BCrypt · Lombok · Testcontainers (test).
Paquete `com.uniremington.api.tramita`, estructura **package-by-layer**: `controller/`,
`dto/`, `model/`, `repo/`, `security/`, `service/` (contratos) + `service/impl/`, `util/`
y `shared/` (`config/`, `exception/`, `seed/`). Interfaces con prefijo `I`.
Para más contexto de tecnologías, estructura y comandos, leer `specs/009-program-catalog-annex/plan.md`.
<!-- SPECKIT END -->
