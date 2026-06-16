# Auditoría: docs/DOCUMENTACION_PROYECTO_TRAMITA.md

**Fecha**: 2026-06-15
**Método**: skill `auditar-vs-entrevistas` — auditoría de alineamiento del artefacto contra las fuentes primarias (entrevistas), las derivadas (árbol, constitución, PRD) y la guía de preguntas abiertas. Ejecutada por un agente auditor independiente con contexto fresco.
**Fuentes consultadas**:
- [x] `material-coord/transcript-entrevista-coordi.md` (Entrevista 1)
- [x] `material-coord/transcript-entrevista-coordi-2.md` (Entrevista 2)
- [x] `material-coord/2026-06-04-entrevista3-sintesis-analitica.md` (síntesis E3)
- [x] `material-coord/parte2-entrevista3.md` (parte 2 E3, Q21–Q23)
- [x] `docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md`
- [x] `docs/nuevo-proyecto/02-constitucion/draft-principios.md`
- [x] `docs/nuevo-proyecto/03-prd/prd.md`
- [x] `docs/nuevo-proyecto/01-planteamiento/guia-entrevista-3.md`

## Veredicto
**REQUIERE REWORK** — el documento contradice tres decisiones cerradas y estructurales (CLASS como caja negra, no-multi-tenancy, y el estado de validación), se autodeclara "Aprobado / línea base" sin validación de ninguna fuente, e inventa líneas base numéricas que ninguna entrevista respalda. No puede servir de línea base de Sprint 1 en su estado actual.

> **Nota del orquestador sobre [C2] (React vs Angular)**: el auditor, correctamente, marcó "React" como contradicción contra las fuentes (PRD §8.1 dice Angular). Sin embargo, el usuario (líder del equipo) **decidió React en sesión del 2026-06-15** (el compañero desarrollará el frontend en React con tooling de IA de Vercel). Por lo tanto [C2] se resuelve **a favor de React**: la corrección NO es cambiar el doc a Angular, sino **enmendar el PRD §8.1 (Angular → React)**. La constitución (`draft-principios.md`) ya dice React; el PRD quedó desactualizado. El doc de DOCUMENTACION es correcto en este punto.

## Hallazgos

### CRÍTICOS

**[C1] Integración técnica con CLASS vía API — contradice el Principio VI (caja negra)**
- **Ubicación**: §Arquitectura Técnica → "Integración con externos" (l. 481-482); §RF1.1 (l. 257-261); §Solución 4.2; §Validación en Origen (l. 174-177); §Notificación (l. 234).
- **Fuente**: `draft-principios.md` Principio VI: *"Class … y QF … son cajas negras … no consume sus APIs, no escribe en sus bases de datos, no asume contratos."* `arbol` §1 y §8 (exclusión: *"Integración técnica con Class o QF"*). E3 Q5: *"la validación de elegibilidad de materia se delega a CLASS (caja negra). El MVP valida el tope de 21 créditos y la integridad del formulario, no la malla curricular."*
- **Naturaleza**: Contradicción.
- **Recomendación**: Eliminar toda consulta/validación contra CLASS (existencia de asignatura, prerrequisitos, "API expuesta por Remington asumimos disponible", auto-relleno desde CLASS). Reescribir validaciones a lo que el sistema controla: tope 21 créditos, integridad del formulario, firma. La elegibilidad es verificación externa (humana/CLASS), no capacidad del sistema.

**[C2] Frontend declarado React — (RESUELTO a favor de React por decisión del 2026-06-15; ver nota del orquestador arriba)**
- **Ubicación**: §RNF4 (l. 445); §Arquitectura → Frontend (l. 473-476); diagrama (l. 489); Sprint 1 (l. 624).
- **Fuente**: `prd.md` §8.1 punto 3: *"Frontend en Angular."* (desactualizado)
- **Naturaleza**: Contradicción documental — se resuelve enmendando el PRD a React, no el doc.
- **Recomendación**: Enmendar `prd.md` §8.1 (Angular → React). El doc de DOCUMENTACION y la constitución ya están en React.

**[C3] Multi-tenancy reintroducida (`sede_id`) — contradice single-tenant explícito**
- **Ubicación**: §RNF6 (l. 457): *"Multi-tenancy: Diseño preparado (columna sede_id), no implementado en MVP."*
- **Fuente**: `draft-principios.md` §Stack: *"~~Multi-tenancy~~ — el MVP es single-tenant explícito … Sin respaldo en las entrevistas. Eliminado por [C1]."* `prd.md` §8.2 punto 4: *"Multi-tenancy eliminada del stack."*
- **Naturaleza**: Contradicción (reintroduce decisión eliminada).
- **Recomendación**: Eliminar la fila de multi-tenancy / columna `sede_id`. Es la herencia mecánica de Convenia que la constitución descartó.

**[C4] Autodeclaración "Aprobado / Línea base" sin validación de ninguna fuente**
- **Ubicación**: encabezado (l. 12-15); cierre (l. 866).
- **Fuente**: `arbol`: *"borrador inicial. Pendiente de validación con tutor (a asignar) y con la coordinadora."* `prd`: *"Pendiente de validación con tutor … y con la coordinadora."* El propio doc marca *"Tutor asignado [BLOQUEANTE]"* (l. 727), incompatible con "aprobado".
- **Naturaleza**: Afirmación sin respaldo.
- **Recomendación**: Degradar a "borrador, pendiente de validación con tutor y coordinación".

**[C5] Notificación indica asentar el PDF "en CLASS" — confusión CLASS/QF**
- **Ubicación**: §Visibilidad Mediada (l. 234); §RF4.2 (l. 366).
- **Fuente**: `transcript-entrevista-coordi.md`: *"Ese formato hay que subirse en un portal que se llama QF … solo almacenamiento."* E3: *"el PDF que se sube a QF es el mismo formato firmado … Es el que Registro y Control mira para matricular."* (El propio doc se contradice: l. 55, 195, 355 dicen QF.)
- **Naturaleza**: Contradicción / inconsistencia interna.
- **Recomendación**: Unificar a "asentar en QF". CLASS es donde Registro ejecuta la matrícula; QF es el banco documental.

**[C6] Líneas base numéricas inventadas en métricas**
- **Ubicación**: §Resumen → Métricas (l. 71-75: "~2-3", "~5-10 min", "1-2 semanas"); §Criterios de Éxito (l. 781: *"Recortó el trabajo manual en un 70%"*).
- **Fuente**: `arbol` §9: re-trabajo *"No medido formalmente"*; opacidad *"Imposible sin leer hilos de correo"*; tiempo de ciclo: *"El sistema no controla el tiempo total … variable contextual."* El "70%" no aparece en ninguna fuente.
- **Naturaleza**: Afirmación sin respaldo.
- **Recomendación**: Sustituir por "no medido formalmente (línea base a establecer con el propio sistema)". Eliminar el "70%". Distinguir tiempo total (no controlable) de porción controlable.

### IMPORTANTES

**[I1] Nomenclatura del formato: "DFR100" en vez de "DO-FR-100"**
- **Ubicación**: l. 164, 194.
- **Fuente**: E3 Q14: *"Codificación institucional DO-FR-100; versionado: cambiar una casilla genera nueva versión (0.1 → 0.2)."* Archivo `2026-06-03-coord-DO-FR-100-…docx`.
- **Recomendación**: Corregir a "DO-FR-100" (es el formato de adición). No inventar el código del formato de novedad (no consta en fuentes).

**[I2] "Nota en escala 0.0-5.0" presentada como regla sin respaldo**
- **Ubicación**: §RF1.2 (l. 291).
- **Fuente**: ninguna entrevista menciona la escala. E3 deja la validación de novedad manual y *"NO hay rechazo definitivo… siempre termina si hay soporte."*
- **Recomendación**: Eliminar o marcar como supuesto del equipo a validar.

**[I3] Cadena de aprobadores incompleta — omite Dirección de CD y Área Financiera en novedad**
- **Ubicación**: §Características Clave (l. 162); §RF2.
- **Fuente**: `arbol` §1: *"para novedad de notas, la cadena suma además a la Dirección de CD y al Área Financiera … Las cuatro casillas de firma de la novedad son docente, Dirección de CD, Decano y Registro y Control."*
- **Recomendación**: Completar la cadena. Aunque el sistema no orqueste externos, la asimetría es el argumento central de la genericidad.

**[I4] Profundidad de automatización invertida / "transiciones automáticas en segundo plano"**
- **Ubicación**: §Características Clave (l. 162-163); §RF2.2 (l. 320).
- **Fuente**: `arbol` §7 nota: *"Los pasos a cargo de aprobadores externos los avanza la coordinación (el sistema registra que ocurrieron, no los ejecuta)."*
- **Recomendación**: Ninguna transición a "aprobado por externo" es automática; la coordinación marca los hitos. La diferencia adición/novedad es de configuración (flujo completo vs seguimiento), no de automatización en segundo plano.

**[I5] Ventana "2-3 meses" y tope "21" como datos cerrados, no provisionales**
- **Ubicación**: l. 161, 176, 260.
- **Fuente**: `arbol` §1: tope 21 *"dato provisional y no auditado … documento normativo pendiente"* + *"ambigüedad: si 21 es el total matriculable o los créditos adicionales"*. Ventana *"aproximadamente los dos primeros meses"* (E3 Q27: *"los tres primeros meses"*).
- **Recomendación**: Marcar como provisionales y modelar el tope como parámetro de configuración del motor, no constante.

**[I6] SLA "rojo si > X días" sin línea base institucional confirmada**
- **Ubicación**: §RF5.1 (l. 386-387).
- **Fuente**: E3 Q4: *"sí existe una normativa de tiempos de respuesta, pero no sabe dónde está … 3 a 5 días … hasta 15 días hábiles"* (documento pendiente).
- **Recomendación**: SLA configurable; anotar que el umbral depende de normativa aún no obtenida.

### MENORES

**[M1] Referencia rota a `entrevista3-limpia.md`** (l. 812) — no existe; las fuentes E3 viven en `material-coord/` (confidencial, gitignored). Eliminar o reapuntar.

**[M2] Inconsistencia de nombres de estados** — el esqueleto usa `APROBADO_POR_COORD`; el enum (l. 511) usa `APROBADO_COORD` + `RECHAZADO`; RF2.1 introduce `RECHAZADO_FINAL`; "ENVIADO" se usa con dos sentidos. Definir un set canónico único y usarlo en esqueleto, enum, RF y diagrama.

**[M3] Errata que invierte el sentido**: "timeline inmutable e **impopular**" (l. 219) → debería ser "inviolable". Corregir.

**[M4] SQL de auditoría incoherente**: `ALTER TABLE solicitud_event DISABLE TRIGGER ALL` (l. 215) no aporta a la inmutabilidad. Dejar solo el `REVOKE UPDATE, DELETE` alineado con el Principio IV.

**[M5] Glosario define QF como "gestor de firmas"** — las fuentes dicen que QF *"solo almacena; no valida firmas ni contenido"* (E3 Q8). Redefinir.

**[M6] Volumetría/FIFO no recogidos** — `parte2-entrevista3.md` confirma 9 docentes firmantes, 30-40 adiciones/sem, ~100% aprobación, orden FIFO. Incorporar; refuerza el realismo y la defensa.

## Resumen de acciones recomendadas

**Prioridad 1 (bloquean Sprint 1):**
1. [C1] Eliminar integración/consulta a CLASS; reescribir validaciones a lo controlable.
2. [C2] Enmendar PRD §8.1 (Angular → React). *(No tocar el doc, que ya dice React.)*
3. [C3] Eliminar multi-tenancy / `sede_id` (single-tenant).
4. [C4] Degradar estado a "borrador pendiente de validación".
5. [C5] Unificar "asentar en QF" (no CLASS).
6. [C6] Reemplazar líneas base inventadas por "no medido formalmente"; eliminar "70%".

**Prioridad 2 (antes de IEEE 830):**
7. [I1] "DFR100" → "DO-FR-100".
8. [I3] Completar cadena de aprobadores de novedad.
9. [I4] Corregir "automatización en segundo plano".
10. [I2]/[I5]/[I6] Marcar provisionales: escala 0.0-5.0, tope 21, ventana, umbral SLA.

**Prioridad 3 (saneamiento documental):**
11. [M1] Referencia rota. 12. [M2] Máquina de estados única. 13. [M3]/[M4]/[M5] Errata, SQL, glosario QF. 14. [M6] Volumetría + FIFO.

**Nota de método**: no se marcaron como hallazgo HTTPS con certificados autofirmados, la priorización v1/v2 de escalabilidad, ni la ausencia de procesos excluidos del árbol §8 (filtros anti-falso-positivo 1-3).
