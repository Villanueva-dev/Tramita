---
name: "auditar-vs-entrevistas"
description: "Audita un artefacto del proyecto (constitución, spec, plan, doc de requisitos, decisión arquitectónica) contra las fuentes primarias: transcripciones de entrevistas a la Coordinación de la Sede Cali y el árbol de problemas. Reporta divergencias clasificadas por severidad con cita textual de respaldo."
argument-hint: "[ruta-del-artefacto-a-auditar] (opcional — si se omite, se pregunta al usuario)"
compatibility: "Diseñado para el proyecto de trabajo de grado Trámita (raíz del repositorio). Las rutas de la sección 'Fuentes canónicas' son relativas a la raíz del proyecto (cwd de Claude Code)."
metadata:
  author: "equipo de trabajo de grado — Ing. Sistemas, Universidad Remington"
  created: "2026-05-19"
  updated: "2026-08-14"
  version: "2.0.0"
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

### Nivel 2 — Instrumento aplicado

| Fuente | Ruta | Qué es y qué NO es |
|--------|------|--------------------|
| Guion de la Sesión 2 | `docs/nuevo-proyecto/01-planteamiento/guia-entrevista-3.md` | El cuestionario que **ya se aplicó** en la Sesión 2: registro histórico del instrumento. **NO es una lista de temas pendientes** — 33 de sus 35 preguntas fueron respondidas. Ver «Lo que sigue abierto» más abajo. |

### Nivel 3 — Fuentes derivadas (consulta rápida, nunca autoridad sobre el verbatim)

Sirven para orientarse rápido, jamás para respaldar un hallazgo. Ante cualquier discrepancia, **prevalece la transcripción**.

| Fuente | Ruta | Advertencia |
|--------|------|-------------|
| Documento de evidencia consolidado | `docs/nuevo-proyecto/01-planteamiento/evidencia-entrevistas-coordinacion.md` | Auditado contra el verbatim el 2026-08-08. Incluye la tabla de trazabilidad decisión-de-diseño → cita. El mejor punto de entrada al corpus. |
| Árbol de problemas | `docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md` | Derivada de las entrevistas. Si contradice una entrevista, prevalece la entrevista. |
| Síntesis analíticas (2) | `material-coord/2026-06-03-entrevista1-sintesis-analitica.md`, `material-coord/2026-06-04-entrevista3-sintesis-analitica.md` | **Ya demostraron heredar errores al verbatim**: de ellas venían los dos hallazgos críticos de la auditoría del 2026-08-08. Verificar contra la transcripción antes de citar. |

## Procedimiento

Seguir esta secuencia de forma estricta. No saltar pasos ni anticipar conclusiones.

### Paso 1 — Resolver el artefacto a auditar

Si el usuario pasó argumento, validar que la ruta exista. Si no, preguntar:

> *"¿Qué artefacto necesitas que audite? Puede ser una ruta de archivo (por ejemplo, `docs/nuevo-proyecto/02-constitucion/draft-principios.md`), una decisión que acabamos de tomar en esta conversación, o la última versión escrita de un documento Spec Kit."*

### Paso 2 — Cargar las fuentes canónicas

Leer en este orden, y en paralelo cuando sea técnicamente posible:

1. **Las cuatro transcripciones de Nivel 1.** Cuatro, no dos: auditar sin la Sesión 2 deja fuera las 35 preguntas guionadas y produce falsos «sin respaldo» en masa.
2. El artefacto a auditar.
3. El documento de evidencia consolidado y el árbol de problemas (Nivel 3), para ubicarse rápido en el corpus.
4. El guion de la Sesión 2 (Nivel 2), únicamente para verificar **qué se preguntó** — nunca para inferir qué queda pendiente.

Si alguna fuente de Nivel 1 no existe o no se puede leer, **detener el procedimiento** y notificar al usuario antes de continuar. Una auditoría con el corpus incompleto es peor que ninguna: devuelve hallazgos falsos con apariencia de rigor.

### Paso 3 — Análisis estructurado en cuatro dimensiones

Examinar el artefacto contra las fuentes desde estos cuatro ángulos:

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

### Paso 4 — Clasificar cada hallazgo por severidad

Cada hallazgo debe llevar una etiqueta de severidad. Aplicar criterios estrictos:

| Severidad | Criterio |
|-----------|----------|
| **CRÍTICO** | Contradicción directa con entrevista; o decisión arquitectónica grande basada en información sin respaldo; o omisión de un sub-problema completo del árbol. Bloquea el avance hasta resolverse. |
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

## Tono y estilo del reporte

- Formal, en español.
- Tono auditor: descriptivo, no acusatorio. El hallazgo señala el hecho, no juzga al equipo.
- Sin redundancia: cada hallazgo único, sin repetir el mismo problema en categorías distintas.
- Sin emojis salvo los del veredicto si el equipo los pide explícitamente.

## Limitaciones conocidas

- Este skill audita **alineamiento con fuentes**, no calidad técnica del código ni cumplimiento de SOLID/Clean Code. Para eso está la skill `revisar-backend-java`.
- Las entrevistas son orales transcritas y contienen redundancias, idas y vueltas y pausas. Al citar, conviene elegir el fragmento más claro y completo, sin distorsionar el sentido.
- Si una afirmación del artefacto se respalda en una conversación posterior a las entrevistas (por ejemplo, una decisión tomada en una sesión de chat con el usuario), no aparecerá como respaldada en las fuentes canónicas. Si es relevante preservarla, debe documentarse explícitamente en el artefacto o promoverse a una memoria semántica antes de auditar.
