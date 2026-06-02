# material-coord/

Carpeta local para guardar **material aportado por la Coordinación Académica de la Sede Cali** (formatos Word vigentes, plantillas PDF institucionales, hilos de correo anonimizados, pénsum con códigos, calendario académico, capturas autorizadas).

## Por qué NO se versiona

Esta carpeta está agregada al `.gitignore` del proyecto. El material que aterriza acá puede contener:

- Datos parcialmente sensibles aunque estén anonimizados.
- Codificación interna institucional.
- Material que la coordinación haya marcado como reservado.

**El repositorio versionado solo conserva referencias** (ruta + fecha de recepción) y **citas mínimas** indispensables para fundamentar decisiones de diseño. La política completa está en:

- `docs/nuevo-proyecto/01-planteamiento/guia-entrevista-3.md` Anexo B (reglas de confidencialidad y nivel de prioridad por ítem).
- Memoria engram `proyecto-grado/coord-material-policy` (convención reusable, justificación, pendientes).

## Qué SÍ va acá

- Formatos Word vigentes (`.docx`).
- Plantillas PDF institucionales (la que aterriza en QF al cierre del trámite).
- Pénsum del programa de Ingeniería de Sistemas con códigos de asignatura.
- Hilos de correo anonimizados (con `███` o `[Estudiante]` sustituyendo datos personales).
- Calendario académico institucional del semestre vigente.
- Normativa institucional compartida (resoluciones, comunicados, manuales) que no sea de acceso público.
- Capturas de pantalla de QF — **solo si la coordinación lo autoriza explícitamente**.

## Qué NO va acá (ni siquiera local)

Aunque la carpeta sea local, no acumular material innecesario:

- Cualquier dato personal identificable (PII): cédulas, listas de estudiantes matriculados, nombres docentes no anonimizados.
- Copias de Class (sistema académico) o de QF (gestor documental) como tales.
- Material que la coordinación haya marcado como NO compartible. En la duda, no se solicita y se conversa en la entrevista.

## Convención de nombrado de archivos

Cada archivo recibido se renombra al guardarse usando el patrón:

```
YYYY-MM-DD-<origen>-<descripcion-corta>.<ext>
```

Ejemplos:

- `2026-06-15-coord-formato-adicion-creditos-v2025.docx`
- `2026-06-20-coord-hilo-correo-adicion-anonimizado.pdf`
- `2026-07-01-coord-pensum-ingenieria-sistemas.pdf`
- `2026-07-10-coord-calendario-academico-2026-2.pdf`

## Cómo referenciar este material desde el repositorio versionado

Al citar un artefacto de esta carpeta desde el PRD, las especificaciones o cualquier otro documento del repo, usar la ruta relativa:

```
Ver `material-coord/2026-06-15-coord-formato-adicion-creditos-v2025.docx` (no versionado, solicitar al equipo).
```

Quien clone el repositorio verá la cita pero **no** el archivo. Si necesita acceso al material original, debe solicitarlo al equipo del trabajo de grado.

## Trazabilidad mínima (opcional)

Cuando llegue el primer paquete de material, mantener un archivo `_inventario.md` dentro de esta carpeta (también no versionado) con una fila por archivo: fecha de recepción, canal (correo, Teams), estado de anonimización, si fue citado en algún artefacto del repo. Útil cuando lleguen más de cinco archivos y haga falta saber qué se tiene a mano.
