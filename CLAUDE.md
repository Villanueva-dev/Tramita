# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Trabajo de grado en curso — Ingeniería de Sistemas, Universidad Remington (modalidad Distancia, SNIES 53112, Resolución 015939 del 1 de septiembre de 2023). Equipo de dos personas, plazo ≈ 2,5 meses. **Estado**: el backend ya arrancó — el **Sprint 1 de autenticación (`001-auth-login`) está cerrado y mergeado a `main`** (panorama técnico y arranque en `README.md`); el resto del dominio (trámites) sigue en fase de planteamiento. El chasis Spring Boot 4 / Java 21 se hereda de `../convenia/`.

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

- `.specify/memory/constitution.md` **está ratificada (v1.0.0, 2026-07-02) y va por la v2.1.0 (última enmienda: 2026-08-06)** vía `/speckit-constitution`: 5 principios (KISS+YAGNI · **arquitectura por capas** · seguridad por defecto · decisiones trazables +Context7 · testing pragmático) + secciones de restricciones tecnológicas, idioma y proceso (Scrum, sprints de 2 semanas). Dos enmiendas hasta hoy:
  - **v2.0.0 (MAJOR, 2026-08-02)** — §II pasa de package-by-feature a package-by-layer, para alinear el proyecto con el material de formación del equipo; el trade-off (el árbol deja de "gritar" el dominio, la correspondencia C4 pasa a los diagramas) está documentado en el propio principio.
  - **v2.1.0 (MINOR, 2026-08-06)** — §IV sustituye **IEEE 830** por **ISO/IEC/IEEE 29148:2018** (cláusula 9.6) y fija C4 + 4+1 para la arquitectura. Es MINOR y no PATCH porque cambia la norma que rige la estructura del entregable de requisitos, no solo su redacción; no obliga a rehacer trabajo porque el SRS todavía no está redactado.
- **Sprint 1 completo**: la feature `001-auth-login` recorrió el ciclo entero (`specify → plan → tasks → implement`). El backend de autenticación está **implementado, testeado y mergeado a `main` (PR #2, 2026-07-15)**; sus artefactos viven en `specs/001-auth-login/` y el arranque/uso está en `README.md`.
- `arbol-de-problemas.md` sigue siendo la fuente del planteamiento; según su §11, lo que resta es **bajar SP1–SP7 a backlog Scrum** y producir los entregables formales de tesis (requisitos ISO/IEC/IEEE 29148:2018 + arquitectura C4 / 4+1).

## Reutilización arquitectónica de Convenia

El árbol de problemas (§11.4) declara que la arquitectura inicial reusa el chasis de `../convenia/`. Para razonar arquitectura del MVP:

- Leer `../convenia/CLAUDE.md` (capas, multi-tenancy por `university_id` filtrado manualmente en cada query, Flyway-valida-Hibernate, errores RFC 7807, auditoría por listener, orden Lombok-antes-de-MapStruct).
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

## Metodología (citar al usarla)

- **Planteamiento**: Marco Lógico — Ortegón, Pacheco y Prieto (2005), *Metodología del Marco Lógico*, CEPAL/ILPES Serie Manuales N.º 42.
- **Verbos de objetivos**: taxonomía de Bloom revisada — Anderson & Krathwohl (2001), priorizando *aplicar / analizar / crear*.
- **Gestión**: Scrum, 3 sprints (S1: SP1+SP2+SP6 → S2: SP3+SP4 → S3: SP5+SP7).
- **Requisitos** (pendiente): **ISO/IEC/IEEE 29148:2018**, estructura del SRS según su cláusula 9.6. Sustituye a IEEE 830-1998, que figura como *superseded* en el catálogo del IEEE Standards Association. Se descartó IEEE 1016-2009 (*inactive-reserved*): el diseño se documenta con C4 + 4+1.
- **Arquitectura** (pendiente): C4 + 4+1.
- **Ciclo de vida**: ISO/IEC/IEEE 12207:2017 (edición vigente).

Al citar literatura o normativa institucional, **incluir la referencia exacta** en cada afirmación — alineado con la regla general #4 del CLAUDE.md global.

<!-- SPECKIT START -->
Feature activa: `002-workflow-engine` (motor de workflow configurable + timeline de auditoría,
SP1+SP6). Plan e insumos técnicos: `specs/002-workflow-engine/plan.md` (+ `research.md`,
`data-model.md`, `contracts/openapi.yaml`, `quickstart.md`).
Diseño: configuración en BD, definiciones versionadas (`UNIQUE(code, version)`), **5 tablas**
(`workflow_parameter`/`guard_key` diferidos a `003` — research D3), timeline solo-INSERT con
trigger, locking optimista `@Version`. Sin dependencias nuevas.
Stack: Java 21 · Spring Boot 4.0.7 (Security 7, Data JPA, Validation, WebMVC) · PostgreSQL + Flyway
(validate) · BCrypt · Lombok · Testcontainers (test).
Paquete `com.uniremington.api.tramita`, estructura **package-by-layer**: `controller/`,
`dto/`, `model/`, `repo/`, `security/`, `service/` (contratos) + `service/impl/`, `util/`
y `shared/` (`config/`, `exception/`, `seed/`). Interfaces con prefijo `I`.
Para más contexto de tecnologías, estructura y comandos, leer el plan actual.
<!-- SPECKIT END -->
