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
| `docs/auditoria.md` | ⚠️ **Documenta el proyecto Convenia v0.10.0 hermano, NO este MVP.** Útil como referencia retrospectiva del proyecto paralelo, no como spec de este. |

## Flujo Spec Kit

Repo inicializado con **Spec Kit v0.8.12** (integración `claude`, script `sh`, branching secuencial — ver `.specify/init-options.json`). Los skills `speckit-*` están bajo `.claude/skills/` y se invocan vía Skill cuando el usuario tipea `/speckit-*`.

Ciclo SDD canónico (definido en `.specify/workflows/speckit/workflow.yml`):

```
specify → review-spec (gate) → plan → review-plan (gate) → tasks → implement
```

Comandos complementarios: `speckit-constitution`, `speckit-clarify`, `speckit-checklist`, `speckit-analyze`, `speckit-taskstoissues`.

**Auto-commit de git** está habilitado en `.specify/extensions.yml` para casi todas las transiciones (`after_specify`, `after_plan`, `after_tasks`, `after_implement`, etc.). Los commits saldrán solos al final de cada fase salvo que se rechace el prompt — útil saberlo si esperabas controlar manualmente la historia git.

### Estado actual del Spec Kit

- `.specify/memory/constitution.md` **está ratificada (v1.0.0, 2026-07-02)** vía `/speckit-constitution`: 5 principios (KISS+YAGNI · arquitectura por feature · seguridad por defecto · decisiones trazables +Context7 · testing pragmático) + secciones de restricciones tecnológicas, idioma y proceso (Scrum, sprints de 2 semanas).
- **Sprint 1 completo**: la feature `001-auth-login` recorrió el ciclo entero (`specify → plan → tasks → implement`). El backend de autenticación está **implementado, testeado y mergeado a `main` (PR #2, 2026-07-15)**; sus artefactos viven en `specs/001-auth-login/` y el arranque/uso está en `README.md`.
- `arbol-de-problemas.md` sigue siendo la fuente del planteamiento; según su §11, lo que resta es **bajar SP1–SP7 a backlog Scrum** y producir los entregables formales de tesis (requisitos IEEE 830 + arquitectura C4 / 4+1).

## Reutilización arquitectónica de Convenia

El árbol de problemas (§11.4) declara que la arquitectura inicial reusa el chasis de `../convenia/`. Para razonar arquitectura del MVP:

- Leer `../convenia/CLAUDE.md` (capas, multi-tenancy por `university_id` filtrado manualmente en cada query, Flyway-valida-Hibernate, errores RFC 7807, auditoría por listener, orden Lombok-antes-de-MapStruct).
- `../convenia/MER.mermaid` ilustra convenciones del modelo de datos del proyecto hermano (no es el modelo de este MVP).
- **El dominio es distinto**: aquí no hay `Agreement` ni máquina de estados de práctica; aquí hay `Trámite` (o equivalente) con workflow **configurable por dato**, no por código. Copiar el **patrón de capas y plumbing**; no copiar las entidades de Convenia.

## Metodología (citar al usarla)

- **Planteamiento**: Marco Lógico — Ortegón, Pacheco y Prieto (2005), *Metodología del Marco Lógico*, CEPAL/ILPES Serie Manuales N.º 42.
- **Verbos de objetivos**: taxonomía de Bloom revisada — Anderson & Krathwohl (2001), priorizando *aplicar / analizar / crear*.
- **Gestión**: Scrum, 3 sprints (S1: SP1+SP2+SP6 → S2: SP3+SP4 → S3: SP5+SP7).
- **Requisitos** (pendiente): IEEE 830.
- **Arquitectura** (pendiente): C4 + 4+1.

Al citar literatura o normativa institucional, **incluir la referencia exacta** en cada afirmación — alineado con la regla general #4 del CLAUDE.md global.

<!-- SPECKIT START -->
Feature activa: `001-auth-login` (autenticación de la Coordinación por sesión + cookie, sin JWT).
Plan e insumos técnicos: `specs/001-auth-login/plan.md` (+ `research.md`, `data-model.md`,
`contracts/openapi.yaml`, `quickstart.md`).
Stack: Java 21 · Spring Boot 4.0.7 (Security 7, Data JPA, Validation, WebMVC) · PostgreSQL + Flyway
(validate) · BCrypt · Lombok (sin MapStruct en auth) · Testcontainers (test).
Paquete `com.uniremington.api.tramita`, estructura package-by-feature (`auth/`, `shared/`).
Para más contexto de tecnologías, estructura y comandos, leer el plan actual.
<!-- SPECKIT END -->
