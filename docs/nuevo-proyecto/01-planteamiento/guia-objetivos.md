# Guía para la redacción de los objetivos del trabajo de grado

> **Propósito.** Instrucciones para redactar y validar el objetivo general y los
> objetivos específicos del documento de grado, garantizando trazabilidad
> metodológica hacia las fuentes del planteamiento. No sustituye a la fuente
> canónica: los objetivos se **derivan** del árbol de problemas, no se inventan.
>
> **Fuentes de referencia obligatorias:**
> - Pregunta de investigación y descomposición en sub-problemas:
>   [`arbol-de-problemas.md`](./arbol-de-problemas.md) §6 y §7.
> - Métricas de éxito: `arbol-de-problemas.md` §9.
> - Borrador actual de objetivos: `docs/BASE_DOCUMENTO_TRAMITA.md` §Objetivos.
> - Método: Marco Lógico (Ortegón, Pacheco y Prieto, 2005, CEPAL/ILPES Serie
>   Manuales N.º 42). Verbos: taxonomía de Bloom revisada (Anderson y
>   Krathwohl, 2001).

---

## 1. Principio rector: trazabilidad en una sola cadena

Los objetivos no se redactan de forma aislada. Deben poder recorrerse en una
única cadena de derivación:

```
Pregunta de investigación (§6)        →  OBJETIVO GENERAL
Cada causa raíz / sub-problema (§7)    →  UN objetivo específico
Cada objetivo específico              →  Una métrica o artefacto verificable (§9)
```

**Regla de oro.** El objetivo general expresa el *qué*; los objetivos
específicos son los pasos que, sumados, producen ese *qué*.

- Si se elimina un objetivo específico y el general sigue siendo alcanzable, ese
  específico sobra.
- Si se cumplen todos los específicos pero el general no se alcanza, falta un
  específico.

Esta propiedad se denomina **MECE** (mutuamente excluyente, colectivamente
exhaustivo): los específicos no se solapan entre sí y, en conjunto, cubren todo
el objetivo general.

---

## 2. Objetivo general

**Cantidad:** exactamente uno. **Verbo:** uno principal, de nivel alto de Bloom
(*diseñar, desarrollar, implementar, crear*).

**Fórmula de redacción:**

> **[Verbo en infinitivo]** + **[el qué: el sistema / motor de workflow]** +
> **[para qué: propósito ligado a las tres variables — tiempo de ciclo,
> re-trabajo, opacidad]** + **[alcance: adición de créditos y novedad de notas,
> Sede Cali]**

**Criterio de validación:** el "para qué" debe enganchar explícitamente con la
pregunta de investigación (§6). Ese es el hilo que conecta el problema con el
objetivo. Un objetivo general que no menciona el propósito medible queda
desconectado del planteamiento.

---

## 3. Objetivos específicos

**Reglas de redacción:**

1. **Uno por sub-problema, salvo restricción de cantidad.** El árbol de
   problemas §7 asigna a cada sub-problema (SP1–SP7) un objetivo específico. Esa
   tabla es el insumo directo. Cuando el número de objetivos está acotado (la
   tutora fijó **máximo 5**), se **agrupan sub-problemas afines por artefacto o
   capacidad** en un mismo objetivo; nunca se parte un mismo artefacto en dos
   objetivos separados por verbo.
2. **Exactamente un verbo en infinitivo por objetivo**, de nivel Bloom
   (*aplicar / analizar / crear*). Las demás acciones no se expresan con un
   segundo infinitivo ni con gerundios: se **nominalizan** (pasan a sustantivo).
   Ejemplo: en vez de «Generar el PDF *y registrar* la traza» → «Generar el
   PDF… *con el registro auditable* de la traza». Razón: un objetivo con dos
   verbos son dos objetivos disfrazados y deja de ser evaluable de forma
   unívoca. Verbos ya empleados en el árbol: *Diseñar, Construir, Generar,
   Registrar, Desarrollar, Integrar*.
3. **Medible.** Debe existir evidencia de cumplimiento: un artefacto entregable,
   una prueba o una métrica de §9.
4. **Ordenados por lógica de construcción:** primero analizar/diseñar, luego
   construir, por último integrar.

**Verbos prohibidos** (no son medibles): *gestionar, mejorar, optimizar,
facilitar, apoyar, potenciar*. Si no se puede enunciar cómo se mide el
cumplimiento, el verbo está mal elegido.

> **Trade-off de la restricción de cantidad.** «Un verbo por objetivo» +
> «máximo 5 objetivos» implica que no se pueden separar el *diseño* y la
> *implementación* de un mismo artefacto en objetivos distintos (se gastarían
> dos cupos en uno solo). Por eso se elige un verbo que englobe el ciclo
> (*Desarrollar*, *Implementar*): el diseño no se pierde, queda como actividad
> interna del objetivo y se documenta en la fase de diseño (C4 / 4+1).

---

## 4. Reconciliación aplicada: objetivos del documento alineados al árbol

> **Estado: resuelto (2026-07-17).** La sección Objetivos de
> `docs/BASE_DOCUMENTO_TRAMITA.md` se reescribió para alinearse con el árbol de
> problemas §7 y respetar el límite de 5 objetivos de la tutora.

Los 5 objetivos específicos del documento cubren el diagnóstico y los 7
sub-problemas, agrupados por afinidad de artefacto, con un verbo en infinitivo
cada uno:

| # | Verbo       | Objetivo (resumen)                                 | Cubre             |
|---|-------------|----------------------------------------------------|-------------------|
| 1 | Analizar    | Procesos actuales de ambos trámites                | Diagnóstico       |
| 2 | Desarrollar | Motor de workflow configurable                     | SP1               |
| 3 | Construir   | Módulo de formularios validados                    | SP2               |
| 4 | Generar     | PDF formal con sello y registro de aprobaciones    | SP3 + SP4         |
| 5 | Implementar | Capa operativa (bandeja + auditoría + notificación)| SP5 + SP6 + SP7   |

**Criterio de agrupación:** cada objetivo corresponde a un artefacto o capacidad
con frontera clara; entre los cinco cubren los siete sub-problemas sin
solaparse (MECE). El sello electrónico (SP4) se ubica junto al PDF (SP3) porque
sella el documento que el sistema genera; la bandeja (SP5), el timeline (SP6) y
la notificación (SP7) se agrupan como la capa de visibilidad y operación.

> **Pendiente menor.** El **objetivo general** del documento aún emplea un verbo
> compuesto («Diseñar e implementar»). En el objetivo general la tolerancia
> suele ser mayor, pero conviene confirmar con la tutora si aplica la regla de
> verbo único también al general.

---

## 5. Checklist de validación

Antes de dar por cerrada la sección de objetivos:

- [ ] ¿El objetivo general responde la pregunta de investigación (§6)?
- [ ] ¿Cada objetivo específico traza a un sub-problema del árbol §7 (y este a
      una causa)?
- [ ] ¿Los objetivos específicos, sumados, alcanzan el general (MECE)?
- [ ] ¿Cada uno inicia con un verbo de Bloom, no con un verbo no medible?
- [ ] ¿Cada uno es verificable contra un artefacto o una métrica de §9?
- [ ] ¿El alcance es consistente en todos (dos trámites, Sede Cali)?

---

## Referencias

- Anderson, L. W. y Krathwohl, D. R. (eds.) (2001). *A Taxonomy for Learning,
  Teaching, and Assessing: A Revision of Bloom's Taxonomy of Educational
  Objectives*. Longman.
- Ortegón, E., Pacheco, J. F. y Prieto, A. (2005). *Metodología del Marco
  Lógico para la planificación, el seguimiento y la evaluación de proyectos y
  programas*. CEPAL/ILPES, Serie Manuales N.º 42.
