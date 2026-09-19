---
name: "auditar-vs-entrevistas"
description: "Audita el alineamiento del proyecto Trámita con sus fuentes de autoridad: entrevistas a la Coordinación de la Sede Cali, árbol de problemas, constitución y normativa institucional. Funciona en dos modos: un artefacto puntual (constitución, spec, plan, decisión) o el estado completo del MVP —incluida la brecha entre lo especificado y lo implementado—. Reporta divergencias clasificadas por severidad con cita textual de respaldo."
argument-hint: "[ruta-del-artefacto | 'sistema'] (opcional — si se omite, se pregunta al usuario cuál de los dos modos)"
compatibility: "Diseñado para el proyecto de trabajo de grado Trámita (raíz del repositorio). Las rutas de la sección 'Fuentes canónicas' son relativas a la raíz del proyecto (cwd de Claude Code)."
metadata:
  author: "equipo de trabajo de grado — Ing. Sistemas, Universidad Remington"
  created: "2026-05-19"
  updated: "2026-09-18"
  version: "3.0.0"
user-invocable: true
disable-model-invocation: false
---

## Propósito

Este skill existe para evitar **drift entre la intención y la implementación** del MVP. A medida que el proyecto avanza por las fases del Spec Kit (constitución → specify → plan → tasks → implement), es fácil que las decisiones se aparten silenciosamente de:

- Lo que la Coordinación Académica de la Sede Cali efectivamente dijo en las **dos sesiones de entrevista** (repartidas en cuatro archivos de transcripción).
- Lo que el árbol de problemas (Marco Lógico) declaró como problema central, causas, efectos y sub-problemas.

Cuando esa desviación ocurre y no se detecta, el equipo termina construyendo algo que no responde al problema real, o que el tutor o la coordinadora pueden rechazar en validación. Este skill es la **red de seguridad** que valida el alineamiento antes de avanzar a la siguiente fase.

## Cuándo usar

Invocar este skill en cualquiera de estas situaciones:

1. **Antes de cerrar una fase del Spec Kit** (después de redactar la constitución, la spec, el plan, o las tasks; antes de aprobarlos).
2. **Al cierre de una sesión de trabajo nocturna**, especialmente si se tomaron decisiones de scope o de arquitectura.
3. **Antes de implementar** una decisión arquitectónica grande (elección de librería, patrón de diseño, modelo de datos).
4. **Cuando el usuario tenga la duda explícita** de si una decisión está alineada con las fuentes.
5. **De forma proactiva por parte del agente principal** cuando detecte que se está documentando algo no respaldado por entrevistas, sin que el usuario lo pida.

## Inputs

El skill admite un argumento opcional:

- **Ruta del artefacto a auditar** (relativa al directorio del proyecto o absoluta). Si se omite, el skill debe preguntar al usuario qué artefacto auditar antes de proceder.

Si el usuario pasa una indicación informal (por ejemplo, "audita el draft de la constitución"), el agente debe resolverla a una ruta concreta antes de continuar.

## Fuentes canónicas

El skill **siempre** debe leer estas fuentes antes de emitir un diagnóstico. Si alguna no existe, abortar la auditoría y avisar al usuario explícitamente:

> Las rutas de la tabla son **relativas a la raíz del proyecto** (el directorio donde Claude Code abre la sesión). Resolverlas desde ahí; no usar rutas absolutas para no romperlas ante un renombrado del proyecto.

### Nivel 1 — Fuentes primarias (verbatim, autoridad máxima)

Son las cuatro transcripciones de las **dos sesiones** de entrevista. Se leen **todas**: ninguna auditoría es válida con una parte del corpus.

| Fuente | Ruta | Contenido |
|--------|------|-----------|
| Sesión 1 · parte A | `material-coord/transcript-entrevista-coordi.md` | Primer tramo de la primera reunión. Adición de créditos. |
| Sesión 1 · parte B | `material-coord/transcript-entrevista-coordi-2.md` | Continuación de **la misma** reunión (la grabación se había cortado). Novedad de notas, Dirección de Sede, Área Financiera. |
| Sesión 2 · parte A | `material-coord/entrevista-Coordi-3.txt` | Segunda reunión, **guionada**: 35 preguntas preparadas. La fuente más rica del corpus. |
| Sesión 2 · parte B | `material-coord/parte2-entrevista3.md` | Tramo final de la segunda reunión. |

> **Nomenclatura**: los nombres de archivo dicen «entrevista 1 / 2 / 3» por razones históricas — el rótulo «N.º 3» nació de contar las dos partes de la primera reunión como reuniones distintas. **Hubo dos sesiones, no tres.** Al citar en el reporte se usa siempre `Sesión 1 parte A/B` y `Sesión 2 parte A/B` más la ruta, nunca el rótulo del nombre de archivo.

### Nivel 1-bis — Vara normativa interna (autoridad sobre decisiones técnicas)

La constitución no es una fuente derivada: es la norma que el equipo se dio, ratificada y enmendada
con proceso. Un artefacto puede estar perfectamente alineado con las entrevistas y aun así violar
un principio. Ese hallazgo es tan válido como una contradicción con el verbatim.

| Fuente | Ruta | Cómo leerla |
|--------|------|-------------|
| Constitución del proyecto | `.specify/memory/constitution.md` | **7 principios.** ⚠️ La versión vigente se lee de una línea, nunca de memoria ni de este archivo: `grep -n '^\*\*Versión\*\*' .specify/memory/constitution.md`. Al 2026-09-18 es la **v2.3.0** (enmienda del 2026-09-13), pero eso es una medición con fecha. |

Los principios, con la línea donde empieza cada uno, para citar con precisión:

| § | Principio |
|---|-----------|
| I | Simplicidad primero (KISS + YAGNI) |
| II | Arquitectura por capas |
| III | Seguridad por defecto **+ minimización de datos personales** |
| IV | Decisiones defendibles y trazables |
| V | Testing del comportamiento sensible |
| VI | **Workflow configurable por dato, no por código** |
| VII | **Trazabilidad inmutable del trámite** |

> §VI y §VII se **apendizaron** en la v2.3.0, no se insertaron en el medio: había más de veinte citas
> a los principios I, III, IV y V en las specs 001–003, y numerar en medio las habría roto todas.
> Cualquier principio futuro se apendiza por la misma razón.

### Nivel 1-ter — Normativa institucional (autoridad externa, CONDICIONAL)

⚠️ **Estas fuentes viven FUERA del repositorio**, en la memoria persistente del agente
(`~/.claude/projects/-home-villa-Escritorio-proyectos-Tramita/memory/`). No se versionan, así que
un checkout limpio no las tiene.

**Tratamiento**: son fuentes **condicionales**, no obligatorias. Si no están disponibles, la
auditoría **NO aborta** — continúa y declara en el reporte que la dimensión normativa quedó sin
cubrir. Lo contrario haría que la skill dejara de funcionar para cualquiera que clone el repo.

| Fuente | Archivo de memoria | Qué fija |
|--------|--------------------|----------|
| Reglamento Estudiantil de Pregrado | `reglamento-estudiantil-pregrado-2023.md` | Acuerdo n.º 13 (1-ago-2023), vigente desde el 1-ene-2024. Créditos adicionales = **40 % del nivel**, autoriza la **decanatura** (art. 24 §1); notas **0.0–5.0 con un decimal** (art. 32); corrección de notas **15 días hábiles** (art. 35 §3). |
| Reglamento de Homologaciones | `reglamento-homologaciones-2023.md` | Acuerdo n.º 17 (3-oct-2023). Tercer trámite con la misma estructura y reglas que cambian por facultad: el argumento normativo del motor configurable. |
| Reglamento de 2013 | `reglamento-estudiantil-remington-2013.md` | **DEROGADO.** Valor solo histórico. Citarlo como vigente es hallazgo CRÍTICO. |

> **§IV de la constitución es explícito**: Context7 sirve para fuentes técnicas, **no para normativa
> institucional**. Esta solo se verifica contra el documento obtenido de la fuente. Mientras no se
> obtenga, lo que dependa de ella se marca **provisional y no auditado**.

### Nivel 2 — Instrumento aplicado

| Fuente | Ruta | Qué es y qué NO es |
|--------|------|--------------------|
| Guion de la Sesión 2 | `docs/nuevo-proyecto/01-planteamiento/guia-entrevista-3.md` | El cuestionario que **ya se aplicó** en la Sesión 2: registro histórico del instrumento. **NO es una lista de temas pendientes** — 33 de sus 35 preguntas fueron respondidas. Ver «Lo que sigue abierto» más abajo. |

### Nivel 3 — Fuentes derivadas (consulta rápida, nunca autoridad sobre el verbatim)

Sirven para orientarse rápido, jamás para respaldar un hallazgo. Ante cualquier discrepancia, **prevalece la transcripción**.

| Fuente | Ruta | Advertencia |
|--------|------|-------------|
| Documento de evidencia consolidado | `material-coord/evidencia-entrevistas-coordinacion.md` | Auditado contra el verbatim el 2026-08-08. Incluye la tabla de trazabilidad decisión-de-diseño → cita. El mejor punto de entrada al corpus. |
| Árbol de problemas | `docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md` | Derivada de las entrevistas. Si contradice una entrevista, prevalece la entrevista. |
| Síntesis analíticas (2) | `material-coord/2026-06-03-entrevista1-sintesis-analitica.md`, `material-coord/2026-06-04-entrevista3-sintesis-analitica.md` | **Ya demostraron heredar errores al verbatim**: de ellas venían los dos hallazgos críticos de la auditoría del 2026-08-08. Verificar contra la transcripción antes de citar. |

## Procedimiento

Seguir esta secuencia de forma estricta. No saltar pasos ni anticipar conclusiones.

### Paso 1 — Resolver el MODO y el objeto de la auditoría

La skill tiene **dos modos**. Resolver cuál antes de cargar nada.

#### Modo A — Artefacto puntual (el modo histórico)

Audita **un** documento: la constitución, una spec, un plan, un doc de requisitos, una decisión
arquitectónica. Es el modo de siempre y no cambió.

Si el usuario pasó una ruta, validar que exista. Si pasó una indicación informal («audita el draft
de la constitución»), resolverla a ruta concreta antes de continuar.

#### Modo B — Estado del sistema (nuevo en v3.0.0)

Audita **el MVP completo**: qué se especificó, qué se implementó, y si la brecha entre ambos está
declarada o es silenciosa. Se invoca con el argumento `sistema`.

Este modo existe porque el proyecto creció más allá de lo que un artefacto único puede responder:
al 2026-09-18 hay **seis features** con ciclo Spec Kit, un backend con endpoints en producción, y un
tablero de issues que es el estado vigente. Auditar solo un documento ya no dice dónde está parado
el proyecto.

En modo B, el objeto de la auditoría **no es un archivo**: son las cuatro superficies siguientes,
y hay que cargarlas todas.

| Superficie | Cómo se obtiene |
|------------|-----------------|
| Lo **especificado** | `specs/*/spec.md`, `plan.md`, `tasks.md` |
| Lo **implementado** | Los controllers, servicios, modelos y migraciones del árbol de trabajo |
| El **estado declarado** | `gh issue list --state open` y `gh api repos/:owner/:repo/milestones` |
| Lo **prometido** | Las fuentes de Nivel 1, 1-bis y 1-ter |

⚠️ **El tablero es fuente de estado, no de verdad.** Un issue cerrado afirma que algo se hizo; no
lo prueba. En modo B, toda afirmación del tablero se contrasta contra el código antes de darla por
buena — y la divergencia entre ambos es, ella misma, un hallazgo.

Si no se pasó argumento, preguntar:

> *"¿Qué necesitas que audite? Puede ser **un artefacto puntual** (dame la ruta: una spec, un plan,
> la constitución) o **el estado del sistema** completo — lo especificado contra lo implementado
> contra lo prometido."*

### Paso 2 — Cargar las fuentes canónicas

Leer en este orden, y en paralelo cuando sea técnicamente posible:

1. **Las cuatro transcripciones de Nivel 1.** Cuatro, no dos: auditar sin la Sesión 2 deja fuera las 35 preguntas guionadas y produce falsos «sin respaldo» en masa.
2. El artefacto a auditar.
3. El documento de evidencia consolidado y el árbol de problemas (Nivel 3), para ubicarse rápido en el corpus.
4. El guion de la Sesión 2 (Nivel 2), únicamente para verificar **qué se preguntó** — nunca para inferir qué queda pendiente.
5. **La constitución (Nivel 1-bis).** Leer primero su versión vigente con
   `grep -n '^\*\*Versión\*\*' .specify/memory/constitution.md`, nunca darla por sabida.
6. **La normativa institucional (Nivel 1-ter), si está disponible.** Si no lo está, anotarlo y
   continuar: la dimensión normativa se declara no cubierta en el reporte.
7. **Solo en modo B**: las cuatro superficies del sistema (especificado, implementado, tablero,
   prometido) según la tabla del Paso 1.

Si alguna fuente de Nivel 1 no existe o no se puede leer, **detener el procedimiento** y notificar al usuario antes de continuar. Una auditoría con el corpus incompleto es peor que ninguna: devuelve hallazgos falsos con apariencia de rigor.

### Paso 3 — Análisis estructurado por dimensiones

Examinar el objeto de la auditoría contra las fuentes desde estos ángulos. **A–E aplican en los dos
modos. F aplica solo en modo B.**

#### A. Afirmaciones sin respaldo

Detectar afirmaciones del artefacto que no se respaldan en ninguna fuente canónica. Ejemplos: "los estudiantes prefieren el canal X", "la institución exige Y", "el proceso siempre toma Z días" — cuando ninguna entrevista ni el árbol lo dicen.

Tratamiento: marcar como hallazgo, indicar que parece **sesgo del equipo o suposición no validada**, y proponer convertirlo en pregunta abierta para la próxima entrevista o marcarlo explícitamente como "supuesto a validar con tutor".

#### B. Información faltante

Detectar hechos relevantes de las entrevistas o del árbol que el artefacto **no refleja** u **omite indebidamente**. Ejemplos: el árbol declara siete causas raíz (C1–C7) pero el artefacto solo cubre tres; la Sesión 1 parte B menciona el rol de **Dirección de Sede** (en el verbatim, «dirección de CD»: la sigla no se expande) pero el artefacto solo modela coordinadora, decano y registro.

Tratamiento: listar lo omitido, citar la fuente, recomendar incorporación o justificar la exclusión explícitamente.

#### C. Contradicciones

Detectar afirmaciones del artefacto que **contradicen** lo que dicen las entrevistas o el árbol. Ejemplos: el artefacto afirma que el flujo tiene tres pasos cuando la Sesión 1 parte A describe cinco; el artefacto excluye al **Área Financiera** del flujo de novedad de notas cuando la Sesión 1 parte B la incluye explícitamente (verifica el recibo de pago; es paso de cadena, **no** firmante).

Tratamiento: este es el hallazgo más grave. Citar literal la fuente que contradice, marcar como CRÍTICO, y recomendar resolución antes de avanzar.

#### D. Decisiones tomadas sobre información todavía pendiente

Detectar cuándo el artefacto ha **cerrado una decisión** sobre un tema que la coordinación todavía no resolvió, sin marcarla como provisional.

> ⚠️ **No usar el guion de la Sesión 2 como lista de pendientes.** Ese cuestionario **ya se aplicó**: 33 de sus 35 preguntas están respondidas. Tratarlo como agenda abierta marca como «pendiente» todo lo que la coordinación ya contestó — el falso positivo más caro de esta dimensión.

Lo que sigue realmente abierto es esta lista corta, y solo esta:

| Tema | Estado |
|------|--------|
| **P29** — validez legal / institucional de la firma escaneada | `NO FORMULADA`: la pregunta figura en el guion pero **nunca llegó a hacerse**. |
| **P34** — infraestructura (dirigida al docente asesor) | `NO FORMULADA`: ídem. |
| Versión vigente del formato DO-FR-100 | Sin resolver: circulan `0.1`, `v2024` y `00001-2025`. |
| Qué instancia concreta es la Dirección de Sede | Sin resolver: ¿Cali o Medellín? El verbatim ubica a la auxiliar en Medellín. |
| Volumen de novedades de notas (P25) y población activa (P26) | Respondidas de forma parcial o aproximada. |
| Fechas reales de ambas sesiones | Sin registrar en ningún archivo. |

**`NO FORMULADA` no es lo mismo que «sin responder».** Una pregunta que nunca se hizo no tiene respuesta que citar, y atribuirle una es un hallazgo CRÍTICO de fabricación de evidencia — ya ocurrió una vez en este proyecto.

Tratamiento: listar la decisión, citar el tema abierto correspondiente de la tabla, y recomendar marcarla como "pendiente de validación con coordinación" o "pendiente de validación con tutor".

Si el artefacto cierra una decisión sobre un tema **que no está en esta tabla**, no es hallazgo: la Sesión 2 probablemente ya lo respondió. Buscar la respuesta en el verbatim antes de elevar nada.

#### E. Desalineación con la constitución

Detectar decisiones que el artefacto o el código toman y que **violan un principio ratificado**, aunque
estén perfectamente alineadas con las entrevistas. Una cosa no cubre a la otra: las entrevistas dicen
qué problema resolver; la constitución dice cómo se nos permite resolverlo.

Los dos principios que más se violan en silencio, por ser los más recientes y los más fáciles de
romper sin darse cuenta:

- **§VI — workflow configurable por dato, no por código.** El síntoma es un **literal de dominio
  dentro del motor**: un `"FINALIZADA"`, un `"DEVUELTA"`, un `MAX_CREDITS` quemado. La prueba es
  la pregunta: *¿incorporar un trámite ya cubierto obligaría a desplegar código?* Si la respuesta
  es sí, hay hallazgo. Ojo con `contains()` en vez de `equals()` sobre códigos de estado: es la
  variante más difícil de ver y la más frágil.
- **§VII — trazabilidad inmutable.** El síntoma es una operación que **muta estado sin registrar
  actor ni entrada de timeline**, o una tabla de auditoría sin trigger que impida `UPDATE`/`DELETE`.
  La garantía tiene que vivir en la base, no en la disciplina del código.

Tratamiento: citar el principio por su número y el texto exacto de la constitución, más la
`ruta:línea` del código o documento que lo incumple. Severidad según el Paso 4 — una violación de
§VI o §VII en código de producción es **CRÍTICA**, porque §VI es la tesis arquitectónica del
proyecto y §VII su garantía de auditoría.

#### F. Brecha entre lo especificado y lo implementado *(solo modo B)*

Detectar divergencias entre las cuatro superficies. No toda brecha es hallazgo: una brecha
**declarada** es gestión de alcance; una brecha **silenciosa** es el hallazgo.

| Patrón | Cómo se ve | ¿Es hallazgo? |
|--------|-----------|---------------|
| Especificado y no implementado, **con tarea abierta** | La spec lo pide, el `tasks.md` lo tiene sin marcar, el issue está abierto | **No.** Es trabajo pendiente y está declarado |
| Especificado y no implementado, **sin rastro** | La spec lo pide y no hay tarea, ni issue, ni código | **Sí — IMPORTANTE.** Se perdió en el camino |
| Implementado y **no especificado** | Existe código que ninguna spec pide | **Sí.** CRÍTICO si toca el dominio o los datos personales; MENOR si es plumbing |
| Declarado cerrado y **no implementado** | El issue está cerrado o la tarea marcada, pero el código no está | **Sí — CRÍTICO.** El tablero está mintiendo, y un tablero desactualizado es peor que ninguno porque parece autoridad |
| Implementado **distinto** de lo especificado | El código hace algo que la spec no describe | **Sí.** Severidad según cuánto se aparte |

⚠️ **Verificar en el código, no en el checkbox.** Una tarea marcada `[x]` es una afirmación del
equipo, no una prueba. En modo B, toda tarea marcada que se cite como evidencia se confirma contra
el archivo que debería haberla implementado.

### Paso 4 — Clasificar cada hallazgo por severidad

Cada hallazgo debe llevar una etiqueta de severidad. Aplicar criterios estrictos:

| Severidad | Criterio |
|-----------|----------|
| **CRÍTICO** | Contradicción directa con entrevista; o decisión arquitectónica grande basada en información sin respaldo; o omisión de un sub-problema completo del árbol; **o violación de §VI o §VII en código de producción; o una tarea/issue declarado cerrado cuyo código no existe; o normativa derogada citada como vigente**. Bloquea el avance hasta resolverse. |
| **IMPORTANTE** | Afirmación sin respaldo que el equipo está dando por hecho; información omitida del árbol sin justificación; decisión cerrada sobre uno de los temas de la tabla «lo que sigue abierto» (dimensión D) sin marcarla como provisional. No bloquea pero requiere revisión consciente. |
| **MENOR** | Falta de cita textual donde habría valor agregado; redacción ambigua que podría inducir interpretación errónea; oportunidad de mejora documental. Anotación de mejora continua. |

### Paso 5 — Emitir reporte estructurado

Producir un reporte en Markdown con la siguiente estructura:

```markdown
# Auditoría: <ruta-del-artefacto>

**Fecha**: <YYYY-MM-DD>
**Fuentes consultadas**: <listado con marcas de verificación>

## Veredicto

<uno de los tres siguientes>:
- ALINEADO — sin hallazgos críticos ni importantes; puede avanzarse.
- REQUIERE AJUSTES — sin críticos pero con hallazgos importantes; revisar antes de avanzar.
- REQUIERE REWORK — uno o más hallazgos críticos; no avanzar hasta resolver.

## Hallazgos

### CRÍTICOS

(uno por hallazgo, con esta estructura)

**[C1] <título corto del hallazgo>**

- **Ubicación en el artefacto**: <archivo:línea o sección>
- **Fuente que contradice o demanda**: <archivo + cita textual entre comillas>
- **Naturaleza**: <Afirmación sin respaldo | Información faltante | Contradicción | Decisión sobre pendiente>
- **Recomendación**: <acción concreta>

### IMPORTANTES

(misma estructura)

### MENORES

(misma estructura, puede ser más breve)

## Resumen de acciones recomendadas

(lista priorizada de cambios a realizar antes de avanzar)
```

#### Añadidos obligatorios del modo B

Un reporte de modo B lleva, además de lo anterior:

- Un encabezado **«Dónde estamos parados»** con el estado medido: tablero (abiertos/cerrados por
  milestone), endpoints que existen, features con ciclo Spec Kit completo y features sin él.
- Una **matriz de brecha** por feature: especificado · implementado · declarado en el tablero, con
  la divergencia marcada.
- Un **apéndice de comandos** que reproduzca cada afirmación del reporte. Sin él, el reporte
  envejece sin que nadie lo note y se vuelve autoridad falsa.
- La lista explícita de **dimensiones no cubiertas** y por qué (por ejemplo, la normativa
  institucional si sus fuentes condicionales no estaban disponibles).

### Paso 6 — Cierre

Tras emitir el reporte, el agente debe ofrecer al usuario las siguientes opciones de continuación:

1. Aplicar las correcciones recomendadas en el artefacto.
2. Justificar explícitamente por qué una recomendación no se aplica (y registrarlo en el artefacto como nota de auditoría).
3. Posponer las correcciones, **registrándolas en la memoria persistente** con `mem_save` (tipo `project`) para que no se pierdan al cerrar la sesión. El repositorio no tiene ningún `MEMORY.md` propio: la memoria del proyecto vive en engram.
4. Discutir un hallazgo específico antes de decidir.

## Reglas de lectura del corpus

Estas reglas salieron de auditar el corpus contra sí mismo el 2026-08-08. **Aplicarlas antes de citar cualquier cosa**: sin ellas, la lectura ingenua del verbatim produce hallazgos falsos que parecen sólidos porque traen cita textual.

### 1. Atribución de turnos — el modo de fallo dominante

Las transcripciones vienen de TurboScribe, que **no hace diarización**: el texto no distingue quién habla. Una frase del *entrevistador* leyendo su guion parece, en el archivo, una respuesta de la coordinación.

**Regla operativa**: antes de escribir «la coordinación dijo X», leer el **giro dialógico completo** alrededor de la frase — lo anterior y lo posterior — y confirmar que la voz es la de la entrevistada. Nunca atribuir a partir de una frase aislada.

De este fallo salieron **los dos hallazgos críticos** de la auditoría del 2026-08-08: dos intervenciones del entrevistador se habían consignado como respuestas de la coordinación.

### 2. Prevalencia entre sesiones

Ante divergencia entre lo dicho en la Sesión 1 y en la Sesión 2, **prevalece la Sesión 2**: es posterior, fue guionada y varias de sus preguntas existían justamente para confrontar afirmaciones de la primera.

### 3. Datos quemados — no valen como respaldo

Afirmaciones presentes en el verbatim que la **propia fuente rectificó después**. Citarlas es un hallazgo inválido aunque la cita sea literal:

| Dato | Dónde aparece | Por qué no vale |
|------|---------------|-----------------|
| «Nos pasa con el 80 %, 70 % de los estudiantes» | Sesión 1 parte A, literal | La entrevistada lo **rectificó en la Sesión 2**: *«Pasa mucho. No es tan así»*. Además es aritméticamente incoherente con las 30–40 solicitudes por semestre que ella misma reporta. **No usar como métrica.** |

Si aparece un dato nuevo con este patrón — cifra fuerte en una sesión, matizada en la otra — agregarlo a esta tabla en vez de discutirlo dos veces.

### 4. `NO FORMULADA` ≠ sin responder

Una pregunta del guion que nunca llegó a hacerse no tiene respuesta que citar. El documento de evidencia ya distingue ambos estados; respetar esa distinción y no rellenar el hueco con la frase más cercana del transcript.

## Reglas para evitar falsos positivos

Aplicar estos filtros antes de elevar un hallazgo:

1. **No marcar como "sin respaldo" lo que está respaldado por una regla técnica universal**. Por ejemplo, "el sistema usará HTTPS" no requiere cita de entrevista.

2. **No marcar como "contradicción" una expansión legítima**. Si la entrevista dice "la firma puede ser escaneada o digital" y el artefacto dice "el MVP soporta firma escaneada en su primera versión, firma digital queda como hipótesis para v2", eso es priorización, no contradicción.

3. **No marcar como "información faltante" lo que el árbol explícitamente excluye**. La sección 8 del árbol de problemas declara qué está fuera del alcance del MVP. Una omisión coherente con esa exclusión no es hallazgo.

4. **No inflar severidad para ganar visibilidad**. Si todo es CRÍTICO, nada es crítico. Usar CRÍTICO solo para lo que verdaderamente bloquea.

5. **Citar textualmente**. Cada hallazgo de tipo "afirmación sin respaldo", "contradicción" o "información faltante" debe traer cita literal de la fuente entre comillas. Si no se puede citar, el hallazgo no procede.

6. **No citar la constitución de memoria.** Antes de elevar un hallazgo de dimensión E, leer el
   principio en el archivo y verificar la versión vigente. La constitución lleva **seis enmiendas**:
   un principio pudo cambiar de número, de alcance o de redacción. Citar una versión que ya no rige
   es el mismo error que citar el reglamento derogado de 2013.

7. **Una brecha declarada no es hallazgo.** En dimensión F, antes de reportar «especificado y no
   implementado», buscar la tarea en el `tasks.md` y el issue en el tablero. Si está declarado
   pendiente, es backlog, no defecto. El hallazgo es el **silencio**, no la falta.

8. **Un `[x]` no es evidencia.** Ni un issue cerrado. Son afirmaciones del equipo sobre sí mismo.
   Confirmar contra el código antes de darlos por buenos — y si divergen, **esa** divergencia es el
   hallazgo, más grave que lo que se estaba buscando.

9. **No confundir «no está en el repo» con «no existe».** Antes de escribir que falta un archivo:
   `git check-ignore -v <ruta>` y `find`. Buena parte del material de la Coordinación está
   gitignorado por política de habeas data, y un `rg` sin `--no-ignore` sobre `material-coord/`
   devuelve cero siempre. Ese falso negativo ya fabricó tres certezas falsas en este proyecto, y una
   llegó a un issue público.

10. **Toda afirmación de la auditoría debe traer el comando que la reproduce**, y ese comando debe
    dar el mismo resultado al re-ejecutarse. Un comando citado como prueba pero ejecutado con un
    alcance distinto del que se declara es un hallazgo inválido — ya pasó, y obligó a una errata en
    la constitución (v2.2.2).

## Tono y estilo del reporte

- Formal, en español.
- Tono auditor: descriptivo, no acusatorio. El hallazgo señala el hecho, no juzga al equipo.
- Sin redundancia: cada hallazgo único, sin repetir el mismo problema en categorías distintas.
- Sin emojis salvo los del veredicto si el equipo los pide explícitamente.

## Limitaciones conocidas

- Este skill audita **alineamiento con fuentes y con la constitución**, no calidad técnica del código
  línea por línea. No busca bugs, no evalúa SOLID ni Clean Code, no revisa el estilo. Para eso está
  `revisar-backend-java`, y para revisar un slice ya cerrado está `review-agente-limpio`. La frontera:
  este skill pregunta *¿construimos lo correcto, y nos lo permitimos?*; los otros dos preguntan
  *¿lo construimos bien?*.
- **El modo B no reemplaza al modo A.** Auditar el sistema da panorama; auditar un artefacto da
  profundidad sobre ese artefacto. Un hallazgo de modo B sobre una spec puede justificar una
  auditoría de modo A sobre ella.
- **La normativa institucional es condicional** y vive fuera del repositorio. Un checkout limpio no
  la tiene, y en ese caso la dimensión normativa se declara no cubierta en vez de inventarse.
- Las entrevistas son orales transcritas y contienen redundancias, idas y vueltas y pausas. Al citar, conviene elegir el fragmento más claro y completo, sin distorsionar el sentido.
- Si una afirmación del artefacto se respalda en una conversación posterior a las entrevistas (por ejemplo, una decisión tomada en una sesión de chat con el usuario), no aparecerá como respaldada en las fuentes canónicas. Si es relevante preservarla, debe documentarse explícitamente en el artefacto o promoverse a una memoria semántica antes de auditar.

---

## Historial de versiones

| Versión | Fecha | Qué cambió y por qué |
|---------|-------|----------------------|
| **3.0.0** | 2026-09-18 | **MAJOR — cambia el alcance de la skill.** Se agregó el **modo B (estado del sistema)** porque el proyecto creció más allá de lo que un artefacto único puede responder: seis features con ciclo Spec Kit, backend con endpoints en producción y un tablero que es el estado vigente. Se incorporaron la **constitución** (Nivel 1-bis) y la **normativa institucional** (Nivel 1-ter, condicional) como fuentes de autoridad — la primera nunca lo había sido, y la segunda se incorporó al proyecto el 2026-09-10, un mes después de la v2.0.0. Se agregaron las dimensiones **E (desalineación con la constitución)** y **F (brecha especificado ↔ implementado)**, cinco reglas anti-falso-positivo nuevas, y se **reparó una ruta rota**: el documento de evidencia había salido del repositorio versionado en `13fc02b` y la skill seguía apuntando a `docs/nuevo-proyecto/01-planteamiento/`. |
| **2.0.0** | 2026-08-14 | Reparó una auditoría que corría con medio corpus: las fuentes de Nivel 1 pasaron de 2 a 4 transcripciones. Incorporó las reglas de lectura del corpus (atribución de turnos, datos quemados, `NO FORMULADA` ≠ sin responder) salidas de auditar el corpus contra sí mismo el 2026-08-08. |
| 1.0.0 | 2026-05-19 | Versión inicial. |
