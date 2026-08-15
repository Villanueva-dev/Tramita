---
name: "auditar-vs-entrevistas"
description: "Audita un artefacto del proyecto (constitución, spec, plan, doc de requisitos, decisión arquitectónica) contra las fuentes primarias: transcripciones de entrevistas a la Coordinación de la Sede Cali y el árbol de problemas. Reporta divergencias clasificadas por severidad con cita textual de respaldo."
argument-hint: "[ruta-del-artefacto-a-auditar] (opcional — si se omite, se pregunta al usuario)"
compatibility: "Diseñado para el proyecto de trabajo de grado Trámita (raíz del repositorio). Las rutas de la sección 'Fuentes canónicas' son relativas a la raíz del proyecto (cwd de Claude Code)."
metadata:
  author: "equipo de trabajo de grado — Ing. Sistemas, Universidad Remington"
  created: "2026-05-19"
  version: "1.0.0"
user-invocable: true
disable-model-invocation: false
---

## Propósito

Este skill existe para evitar **drift entre la intención y la implementación** del MVP. A medida que el proyecto avanza por las fases del Spec Kit (constitución → specify → plan → tasks → implement), es fácil que las decisiones se aparten silenciosamente de:

- Lo que la Coordinación Académica de la Sede Cali efectivamente dijo en las dos entrevistas.
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

| Tipo de fuente | Ruta | Autoridad |
|----------------|------|-----------|
| Entrevista 1 (transcripción) | `material-coord/transcript-entrevista-coordi.md` | Fuente primaria — autoridad máxima |
| Entrevista 2 (transcripción) | `material-coord/transcript-entrevista-coordi-2.md` | Fuente primaria — autoridad máxima |
| Árbol de problemas | `docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md` | Derivada de las entrevistas. Si contradice una entrevista, prevalece la entrevista. |
| Guía de entrevista N.º 3 (si existe) | `docs/nuevo-proyecto/01-planteamiento/guia-entrevista-3.md` | Documento de preguntas abiertas. Útil para identificar lo que **todavía no se sabe** y no debe afirmarse en el artefacto. |

Si en el futuro el equipo realiza una tercera entrevista y la transcribe, agregar su ruta a esta tabla y a las lecturas del skill.

## Procedimiento

Seguir esta secuencia de forma estricta. No saltar pasos ni anticipar conclusiones.

### Paso 1 — Resolver el artefacto a auditar

Si el usuario pasó argumento, validar que la ruta exista. Si no, preguntar:

> *"¿Qué artefacto necesitas que audite? Puede ser una ruta de archivo (por ejemplo, `docs/nuevo-proyecto/02-constitucion/draft-principios.md`), una decisión que acabamos de tomar en esta conversación, o la última versión escrita de un documento Spec Kit."*

### Paso 2 — Cargar las fuentes canónicas

Leer en este orden y en paralelo cuando sea técnicamente posible:

1. Las dos transcripciones de entrevista.
2. El árbol de problemas.
3. La guía de entrevista N.º 3 (si existe).
4. El artefacto a auditar.

Si alguna fuente canónica no existe o no se puede leer, detener el procedimiento y notificar al usuario antes de continuar.

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

Detectar cuándo el artefacto ha **cerrado una decisión** sobre un tema que la guía de entrevista N.º 3 marca como pregunta abierta (bloqueante o importante). Si un tema está pendiente de la próxima entrevista, no debería estar cerrado en el artefacto sin marcarse como provisional.

Tratamiento: listar la decisión, citar la pregunta abierta correspondiente, recomendar marcarla como "pendiente de validación con coordinación" o "pendiente de validación con tutor".

### Paso 4 — Clasificar cada hallazgo por severidad

Cada hallazgo debe llevar una etiqueta de severidad. Aplicar criterios estrictos:

| Severidad | Criterio |
|-----------|----------|
| **CRÍTICO** | Contradicción directa con entrevista; o decisión arquitectónica grande basada en información sin respaldo; o omisión de un sub-problema completo del árbol. Bloquea el avance hasta resolverse. |
| **IMPORTANTE** | Afirmación sin respaldo que el equipo está dando por hecho; información omitida del árbol sin justificación; decisión cerrada sobre tema marcado como pregunta abierta IMPORTANTE en la guía 3. No bloquea pero requiere revisión consciente. |
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
3. Posponer las correcciones (registrar como TODO en `MEMORY.md` o en el archivo de estado de setup).
4. Discutir un hallazgo específico antes de decidir.

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

- Este skill audita **alineamiento con fuentes**, no calidad técnica del código ni cumplimiento de SOLID/Clean Code. Para eso existirán skills separados en fases futuras.
- Las entrevistas son orales transcritas y contienen redundancias, idas y vueltas y pausas. Al citar, conviene elegir el fragmento más claro y completo, sin distorsionar el sentido.
- Si una afirmación del artefacto se respalda en una conversación posterior a las entrevistas (por ejemplo, una decisión tomada en una sesión de chat con el usuario), no aparecerá como respaldada en las fuentes canónicas. Si es relevante preservarla, debe documentarse explícitamente en el artefacto o promoverse a una memoria semántica antes de auditar.
