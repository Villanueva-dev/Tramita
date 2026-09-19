# Investigación — Feature 006: sello verificable y registro de emisiones

**Fecha**: 2026-09-17 · **Rama**: `006-verifiable-document-seal` · **Spec**: [spec.md](./spec.md)

Todas las mediciones de este documento se hicieron contra el código de la rama, y los
comandos citados son re-ejecutables (§IV de la constitución).

---

## D1 — Cómo se vuelve reproducible el PDF

**Decisión**: fijar el `/ID` del trailer derivándolo de la solicitud, con
`PDDocument.getDocument().setDocumentID(COSArray)` y dos `COSString`.

**Medición que lo sustenta** (sonda `PdfDeterminismProbeTest`, ejecutada sobre `main`):

| Observación | Valor | ¿Se reproduce? |
|---|---|---|
| Tamaño de dos renders del mismo trámite | 52 348 bytes, idéntico | Sí |
| Primer byte que difiere | **52 022 de 52 348** | Sí |
| Dónde caen TODAS las diferencias | dentro del `/ID` del trailer | Sí |
| `CreationDate` / `ModDate` / `Producer` | **ausentes en ambos** | Sí |
| ¿Los dos renders dan los mismos bytes? | **no** | Sí |
| SHA-256 de cada render | distinto en cada corrida | **No, y es el punto** |

⚠️ **Los SHA-256 concretos de esta medición NO son reproducibles, y esa es justamente la
conclusión.** El `/ID` se sortea en cada `save()`, así que el hash del documento completo
cambia entre ejecuciones: la primera corrida dio `0deee6bb…` y `5d886990…`, y una posterior
dio `a0b836e5…` y `d18f8e6b…`. Lo que se reproduce —y lo que sostiene el diseño— es el
tamaño, el offset del primer byte divergente y el hecho de que las diferencias caen todas
dentro del `/ID`. Quien re-ejecute la sonda y obtenga hashes distintos está viendo el
defecto, no una discrepancia con este documento.

El contraste de la segunda sonda **sí** da un valor estable, porque ahí el `/ID` está
fijado: `e9a35481830a653a823c2cf3b49f6978b7aa04d3deee454c29213b09583a97ba`, idéntico en
corridas separadas.

🔑 **La hipótesis previa era falsa y la medición la corrigió.** Se había anticipado que el
problema serían las fechas de creación que PDFBox escribe; no las escribe. Los primeros
52 021 bytes ya son idénticos: **el contenido del documento hoy es determinista**, y lo
único que varía es el identificador aleatorio de 32 bytes que PDFBox genera en cada
`save()`. La segunda sonda lo confirmó por contraste: con el `/ID` fijado, dos guardados
dan el mismo SHA-256; sin fijarlo, dan distinto.

**De qué se deriva**: el `/ID` de PDF es un arreglo de dos cadenas cuya semántica está
definida por el formato — la primera identifica al documento de forma permanente, la
segunda cambia cuando el documento se modifica. Se respeta esa semántica:

- **Primera cadena**: derivada del identificador de la solicitud. Permanente: todas las
  emisiones del documento de un mismo trámite la comparten.
- **Segunda cadena**: derivada del identificador de la solicitud **y de su revisión**.
  Cambia cuando los datos cambian, que es exactamente cuando el documento deja de ser el
  mismo.

**Alternativa rechazada**: un `/ID` constante literal. Haría que **todos** los documentos
del sistema compartieran identificador, lo que contradice la razón de ser del campo.
Costaba lo mismo y era incorrecto.

**Alternativa rechazada**: persistir el PDF emitido para no depender del determinismo.
Obligaría a montar almacenamiento de archivos, que no es objetivo del sistema —la custodia
documental es de la institución en sus propios sistemas (§III, minimización)—. Su costo
está declarado como riesgo aceptado en el spec.

---

## D2 — Qué se hashea, y en qué orden

**Decisión**: SHA-256 sobre los bytes completos del documento emitido, **con el código de
verificación ya impreso dentro**.

El orden importa porque parece circular y no lo es:

1. Se genera el código de verificación (no depende del contenido).
2. Se renderiza el documento con ese código impreso en el pie.
3. Se calcula la huella del resultado.
4. Se guarda el sello con el código y la huella.

Hashear el documento *sin* el código y luego imprimirlo encima produciría una huella que no
describe al archivo que se entrega — se estaría sellando un documento que nadie recibe.

🔑 **Consecuencia que hay que tener presente: dos emisiones de la misma solicitud NO producen
el mismo archivo.** Cada una imprime su propio código (D3), así que sus bytes difieren a
propósito: son dos papeles distinguibles, cada uno verificable contra su propio sello. La
reproducibilidad que el sistema garantiza —y la única que la verificación necesita— es **a
código fijo**: reconstruir una emisión concreta, con el código que su sello guarda, devuelve
sus bytes exactos. Es lo que dice el FR-004, y es distinto de «emitir dos veces da lo mismo»,
que sería falso.

Esa distinción tiene un efecto directo sobre el test de determinismo: debe comparar
**reconstrucciones a código fijo**, no huellas de emisiones sucesivas ni un hash dorado del
documento «tal como sale hoy». Un hash dorado se rompería al imprimir el código en el pie, y
actualizarlo sin pensar desactivaría la barrera que D5 describe.

**Algoritmo**: SHA-256, disponible en `java.security.MessageDigest` sin dependencias. Es el
mismo que usa el prototipo de `origin/router-ia` para los adjuntos, así que no introduce un
criterio nuevo en el proyecto.

---

## D3 — Forma del código de verificación

**Decisión**: 64 bits de `SecureRandom` representados en base 36 con
`Long.toUnsignedString(valor, 36)` — hasta 13 caracteres alfanuméricos, impresos en
mayúsculas y agrupados.

**Por qué no un UUID**: es el tipo que el proyecto usa en todas partes y habría sido lo
natural, pero son 36 caracteres para transcribir desde un papel. El **SC-004** del spec
exige contrastar el documento en menos de un minuto y sin transcribir códigos largos: un
UUID lo incumple.

**Por qué no Base32 con alfabeto sin ambigüedades**: daría 16 caracteres y evitaría
confundir `0`/`O` y `1`/`l`, pero Java no trae Base32 en su biblioteca estándar y habría
que escribir el codificador. Para un MVP no compensa: `Long.toUnsignedString(v, 36)` es una
llamada de biblioteca estándar y no hay nada que mantener.

**Trade-off declarado**: base 36 incluye caracteres que se confunden al leer a mano. Se
mitiga imprimiendo el código **junto a la dirección de verificación**, de modo que el camino
normal sea copiar desde el archivo y no transcribir desde el papel.

**Fuerza suficiente**: 64 bits son ~1,8 × 10¹⁹ combinaciones. Recorrer el espacio no es
viable, y por eso **el canal público no necesita un límite de tasa propio**: la protección
es el tamaño del espacio, no un contador. Añadir un tercer contador sería resolver con
infraestructura un problema que la aritmética ya resuelve.

---

## D4 — Dónde vive el sello y cómo se garantiza que no cambie

**Decisión**: tabla nueva `request_document_seal`, de solo anexado, con un trigger
`BEFORE UPDATE OR DELETE` que lanza excepción.

🔑 **El patrón NO se cosecha de `origin/router-ia`: ya está en `main`.** El mecanismo
vigente es `trg_timeline_immutable` sobre `request_transition_log`
(`V2.0.0__Create_workflow_tables.sql:80-88`), y el §VII de la constitución lo nombra
explícitamente como la implementación de la garantía. Imitar el patrón local es preferible
a traer uno de una rama que no se mergea: mismo resultado, y la consistencia interna del
repositorio se mantiene.

El prototipo de `router-ia` sigue siendo útil como confirmación de que el patrón se sostiene
en otra tabla, pero su `document_sha256` se calcula **sobre adjuntos que alguien sube**, no
sobre el documento que el sistema genera, que es justo lo que este sub-problema pide.

---

## D5 — Qué es «la versión del formato», y quién la mantiene

**Decisión**: una constante de versión declarada por cada renderer, combinada con la huella
del logo institucional cargado del classpath.

El formato de un documento depende de tres cosas, y las tres tienen que coincidir para que
una reimpresión sea idéntica:

1. Los datos del trámite — cubiertos por la revisión de la solicitud (D7).
2. **El logo institucional** — `DoFr100Renderer.java:61`, cargado del classpath. Cambia si
   alguien reemplaza el archivo en un despliegue.
3. **El código del renderer** — constantes de maquetación, tipografías, posiciones, la
   ciudad impresa.

El punto 2 se detecta solo: se calcula la huella del logo al construir el renderer. El punto
3 **no puede detectarse automáticamente sin sobreingeniería**, así que lo declara una
constante que el desarrollador bumpea al cambiar la maquetación.

🔑 **Lo que evita que alguien olvide bumpearla no es un proceso, es el test de
determinismo**: el test de FR-004 compara contra una huella conocida; cualquier cambio de
maquetación lo pone en rojo y obliga a mirar. El recordatorio es mecánico y ya estaba en el
alcance.

**Riesgo residual declarado**: un cambio de formato deja «no verificables» los sellos
anteriores, y no pueden repararse. Es el riesgo que el spec acepta explícitamente.

---

## D6 — Cómo se distingue «íntegro» de «alterado» y de «no verificable»

**Decisión**: comparar la huella recibida contra la **huella guardada** en el sello
(`documentSha256`, D2) y, solo si no coinciden, preguntar por qué antes de acusar.

```
¿existe un sello con ese código?              no → 404
¿la huella recibida == la huella GUARDADA?    sí → ÍNTEGRO  (definitivo)
no coincide → ¿el formato del sello sigue vigente?   no → NO VERIFICABLE (motivo: formato)
              ¿la revisión coincide con la actual?   no → NO VERIFICABLE (motivo: datos)
              ninguna explicación legítima              → ALTERADO
```

Reemplaza al diseño anterior, que regeneraba el documento completo antes de comparar y solo
llegaba a comparar huellas si el sello sobrevivía dos guardas previas. Las razones del
cambio:

1. **Regenerar no aportaba nada a la comparación que importa.** El sello ya guarda
   `documentSha256`, la huella del documento *tal como se emitió* (D2). Y `POST
   /api/seals/verify` recibe la huella, no el archivo (D10): el veredicto sale de comparar
   dos cadenas que el sistema ya tiene, una guardada y otra recibida. Reconstruir el
   documento para producir una tercera cadena y compararla contra la primera era trabajo que
   no cambiaba el resultado de esa comparación.

2. **El argumento que descartaba «comparar primero» atacaba un diseño distinto.** El D6
   original rechazaba *comparar y concluir*: comparás huellas, no coinciden, decís
   «alterado» — eso sí produce la acusación falsa que FR-007 prohíbe, porque un formato
   vencido o una revisión vieja hacen que la comparación falle sin que el papel esté
   falsificado. Pero el diseño nuevo no es «comparar y concluir»: es **comparar, y si falla,
   explicar**. Solo dice ALTERADO cuando la comparación falla y ninguna de las dos guardas de
   FR-007 explica por qué. FR-007 queda intacto: sigue siendo imposible acusar de alteración
   a un documento que dejó de coincidir por una causa legítima.

3. **El propio caso decisivo de `quickstart.md` refutaba al diseño viejo.** Su paso 5 emite
   un documento, hace avanzar el trámite un paso, y verifica **el mismo documento legítimo**
   recién emitido. Con el diseño viejo eso daba «no verificable / datos» —el `@Version` ya
   había cambiado— aunque el papel fuera perfecto. Con el diseño nuevo da **ÍNTEGRO**, porque
   la huella guardada en el sello es la del documento emitido, no la de una reconstrucción
   contra el estado actual de la solicitud. Es un resultado más verdadero (el papel es el que
   salió del sistema) y más fuerte (no depende de que nada haya avanzado desde entonces).

4. **La debilidad que el diseño viejo no declaraba**: bajo D6 original, apenas el trámite
   avanzaba un paso —lo normal, no la excepción— ningún documento volvía a poder verificarse
   como íntegro nunca más, aunque nadie lo hubiera tocado. La cláusula final de SC-001
   («mientras los datos y el formato no hayan cambiado») era la admisión de ese problema, no
   una acotación cosmética.

5. **Deja de depender del determinismo del render a lo largo del tiempo**, que es frágil:
   T022a y T022b fueron dos defectos de reproducibilidad encontrados en una sola sesión
   (fuentes compartidas entre documentos; una versión de PDFBox que cambiaba el resultado).
   Una huella guardada en una tabla de solo anexado (D4) no depende de que el render siga
   produciendo los mismos bytes meses después — depende de que la tabla no se pueda
   modificar, que es una garantía más fuerte y ya construida.

🔑 **Lo que no cambia**: los tres veredictos (ÍNTEGRO / ALTERADO / NO VERIFICABLE) y los dos
motivos («formato», «datos») siguen siendo los mismos. Fijar el `/ID` del trailer (D1) sigue
siendo correcto y la reproducibilidad del render sigue siendo una propiedad real — pero deja
de ser el cimiento de la verificación: pasa a sostener la reconstrucción del documento para
mostrarlo, no el veredicto de integridad.

---

## D7 — De dónde sale «la revisión de los datos»

**Decisión**: el campo `@Version` que la solicitud ya lleva (`Request.java:129-131`).

Existe desde la feature 002 para locking optimista, y su semántica coincide exactamente con
lo que hace falta: se incrementa cada vez que la solicitud se modifica. **Cero migración y
cero concepto nuevo.**

**Alternativa rechazada**: una huella de los datos del trámite en forma canónica. Sobreviviría
a cambios de formato y respondería «¿estos datos son los que el trámite registró?», pero
obliga a definir y mantener una serialización canónica, que es la única pieza del diseño con
decisiones abiertas. Queda fuera del MVP.

---

## D7-bis — Qué se congela del estado del trámite

**Decisión**: el sello guarda el **código y el nombre** del estado, no solo el código.

El pie impreso muestra el nombre legible —«En facultad»—, no el código interno.

**Por qué ya no es un mecanismo de defensa.** Con el D6 original —que regeneraba el
documento y comparaba—, guardar solo el código habría obligado a resolver el nombre contra
`workflow_state` **en tiempo de verificación**, y un **renombre** —cambio de pura
configuración, que es lo que el §VI habilita— habría hecho que el pie regenerado difiriera
del impreso. Ninguna de las dos guardas del FR-007 lo detectaría: renombrar un estado no
incrementa el `@Version` de la solicitud ni cambia la versión del formato. El resultado
habría sido un **`TAMPERED` sobre un documento legítimo**. Con el D6 vigente, el veredicto ya
no sale de regenerar y comparar, sino de comparar la huella recibida contra la huella
guardada: un renombre posterior no toca esa comparación, así que el riesgo que motivó la
columna desapareció.

**Por qué la columna se conserva de todos modos**: sigue siendo la única fuente para mostrar
en qué estado estaba el trámite al emitir, sin depender de que `workflow_state` no haya
cambiado el nombre desde entonces. Es información de contexto del sello, no una guarda contra
un falso positivo que ya no puede ocurrir por esta vía.

**Costo**: una columna. **Alternativa rechazada**: resolver el nombre al verificar — seguiría
siendo incorrecta para quien quiera reconstruir el documento y mostrarlo tal como era, aunque
ya no afecte al veredicto de integridad.

---

## D8 — Precisión de las calificaciones (FR-013, deuda M2)

**Decisión**: validar en la entrada que la calificación tenga a lo sumo un decimal, **y**
declarar la misma regla como restricción en la base.

**Norma que lo respalda**: Reglamento Estudiantil de Pregrado, **Acuerdo n.º 13 del 1 de
agosto de 2023, artículo 32** (p. 17): «Todas las evaluaciones practicadas se califican con
**un número entero y un número decimal** e irán de cero punto cero (0.0) hasta cinco punto
cero (5.0)». Documento obtenido de la fuente institucional el 2026-09-10 (§IV: la normativa
institucional se verifica contra el documento, nunca contra Context7).

**Hay precedente directo en el repositorio** para poner la regla en los dos lados:
`ck_request_subject_credits_positive` (`V2.3.0:38-41`) lo hace y deja escrito el porqué —
una restricción de negocio que solo vive en el código deja la base a merced de cualquier
otra vía de escritura.

**Por qué NO se cambia el tipo de la columna**: `V2.3.0:43-45` declara explícitamente que el
rango efectivo de las notas lo fija la configuración del trámite y **no** la columna, que es
solo una cota de sanidad. Reescribir `NUMERIC(3,2)` a `NUMERIC(2,1)` movería la regla
institucional al esquema y contradiría esa decisión de diseño. Una restricción de precisión
consigue lo mismo sin reabrirla.

⚠️ **Corrección de un dato que circulaba**: el argumento original de la deuda M2 hablaba de
`MAX_GRADE = 100` haciendo reventar un `INSERT`. Ese valor era **un supuesto hipotético del
argumento, no un valor real**: `V3.0.0__Configure_business_rules.sql:51` siembra `5.0`, y
`:50` siembra `MIN_GRADE = 0.0`. Ese modo de fallo no existe. Lo único vivo de M2 es el
redondeo silencioso de `3.456` a `3.46`.

🔴 **Riesgo de la migración: MEDIDO, y es real.** Una restricción nueva se valida contra
las filas existentes, así que se comprobó contra la instancia local antes de diseñarla:

```sql
SELECT count(*) FROM request_subject
WHERE (current_grade  IS NOT NULL AND current_grade  <> round(current_grade, 1))
   OR (proposed_grade IS NOT NULL AND proposed_grade <> round(proposed_grade, 1));
-- → 1, sobre 16 filas totales
```

La fila es `04f93f4d-a718-470e-9e0c-f8fde6f9e33f`, asignatura `A-1`, con
`proposed_grade = 3.46`. **Es el argumento de M2 materializado**: alguien envió una
calificación con más de un decimal y la base la guardó redondeada, sin avisar. El defecto
no era hipotético, dejó rastro.

**Qué hace la migración con esa fila**: la redondea a un decimal antes de declarar la
restricción. No hay alternativa —el valor original no se puede recuperar, precisamente
porque nunca se guardó— y dejar la migración fallando sería peor. La diferencia con el
defecto que esta feature corrige es que acá el redondeo es **explícito, único y
documentado**, no silencioso y repetido en cada envío.

En producción no hay datos: el sistema todavía no está desplegado.

**Alcance honesto**: hoy el DO-FR-100 **no dibuja ninguna calificación**
(`DoFr100Renderer:169-192` dibuja datos del solicitante y el motivo libre; no hay asignaturas
ni notas). Es decir, **esta corrección es preventiva, no correctiva**: cierra el hueco antes
de que el formato de novedad de notas lo abra. Se declara así para que no se defienda como
otra cosa.

---

## D9 — El canal público de verificación

**Decisión**: `GET /api/public/seals/{code}`, abierto, sin CSRF y **sin límite de tasa
propio**.

- **Abierto**: lo exige FR-014. Es el tercer endpoint sin sesión del sistema, después del
  login y de la captura pública.
- **Sin CSRF, y sin tener que excluir nada**: CSRF protege operaciones que cambian estado
  usando la sesión de un navegante. Un `GET` no cambia estado y Spring Security no lo
  protege, así que **no hace falta tocar la configuración de CSRF** — a diferencia de la
  captura pública, que sí necesitó una exclusión explícita por ser un `POST`.
- **Sin límite de tasa**: ver D3. Con 64 bits de espacio, recorrerlo no es viable.
- **Sin datos personales**: devuelve existencia, fecha de emisión, estado del trámite y
  revisión. Nada que identifique al solicitante (FR-014c, §III).
- **Sin veredicto de integridad**: no recibe huella (D10), así que no compara nada y no puede
  decir ni «íntegro» ni «no verificable»; afirma emisión (FR-014b). El veredicto es exclusivo
  de `POST /api/seals/verify` (D6).

---

## D10 — La verificación exacta, sin subir archivos

**Decisión**: `POST /api/seals/verify` con sesión, recibiendo el **código y la huella**, no
el archivo.

🔑 Calcular el SHA-256 de un archivo es trivial en cualquier entorno —`sha256sum` en la
terminal, `crypto.subtle.digest` en el navegador— y enviarlo en lugar del archivo elimina de
un plumazo el manejo de cargas: sin `multipart`, sin tope de tamaño, sin materializar el
cuerpo en memoria, sin heredar el defecto abierto del `413` (issue #25).

Además hace literal el FR-010: el sistema nunca recibe el archivo, así que no puede
almacenarlo ni por accidente.

**Trade-off**: el cliente tiene que calcular la huella. Para el frontend son cinco líneas;
para un humano con terminal, un comando.

**El cuerpo mal formado responde `400`, no `422`.** El `422` del canal público no es la norma
del sistema: lo produce un manejador acotado con
`@RestControllerAdvice(assignableTypes = PublicRequestController.class)`, y existe porque allí
quien diligencia es un estudiante y el envío es sintácticamente impecable —lo que falla es el
formato—. Este endpoint lo consume el frontend de la Coordinación, donde un campo ausente es
un defecto del contrato de entrada, y el resto del API ya responde `400` por el manejador
heredado de `ResponseEntityExceptionHandler`. El mismo criterio aplica a la validación de
precisión de FR-013a, que entra solo por el formulario interno.

---

## D11 — Fail-closed, y por qué no hace falta escribir nada para lograrlo

**Decisión**: registrar el sello dentro de la misma transacción que emite el documento,
quitando `readOnly = true` de `DocumentServiceImpl.generateFor()` (`:69`).

**Lo que hay que escribir para el caso de fallo: nada.** Si el guardado lanza, Spring
deshace la transacción y propaga la excepción; el método nunca retorna y el controlador
nunca construye la respuesta. El comportamiento seguro es el que se obtiene al **no**
escribir manejo de error.

**Verificado en documentación oficial**: el rollback automático ocurre con excepciones **no
chequeadas**; las chequeadas confirman la transacción salvo que se declare `rollbackFor`
([Spring Framework Reference — Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)).
Aquí **no hace falta `rollbackFor`**: `DoFr100Renderer:135-137` ya reenvía la `IOException`
como `IllegalStateException`, y las excepciones propias del servicio también son no
chequeadas.

**No hay ventana de «entregado pero abortado»**: `RequestController:120` devuelve
`ResponseEntity<byte[]>`, de modo que el commit ocurre antes de que se escriba el primer
byte de la respuesta.

⚠️ **Efecto secundario a vigilar**: quitar `readOnly` reactiva el dirty checking, así que
una modificación accidental de `Request` durante el renderizado se persistiría. Hoy el
renderer solo lee. Se deja anotado en el código para quien venga después.

**Modos de fallo enumerados antes de aceptar el bloqueo**: de diez, cuatro ya bloquean la
emisión hoy porque `generateFor()` ya lee la base dos veces (`:71` y `:79`). De las seis
ventanas nuevas, las de infraestructura —base en solo lectura, disco lleno— **bloquean por
igual crear y avanzar solicitudes**, que también escriben, así que emitir sin sello no
destrabaría ningún trámite; las transitorias se resuelven volviendo a pedir el documento; y
las que quedan son defectos de programación, que conviene que rompan en las pruebas en vez
de producir documentos sin sello en silencio.

**Sin política de reintento**: reintentar es volver a pedir el documento. La interfaz ya lo
resuelve sin escribir código.

---

## Lo que deliberadamente NO se investigó

- **Serialización canónica de los datos del trámite** (la alternativa de D7): fuera del MVP.
- **Código QR en el documento**: mejoraría el SC-004, pero exige una dependencia nueva y el
  proyecto viene sosteniendo que no las agrega si puede evitarlo.
- **Guarda de workflow que condicione el avance a que el documento esté sellado**: sería la
  primera implementación de producción de `IWorkflowGuard` y arrastraría la deuda M5. Está
  descartado en las asunciones del spec por KISS+YAGNI (§I).
- **Validez legal del sello**: el sistema aporta evidencia de integridad, no valor
  probatorio. Sigue abierta en §10 del árbol de problemas.
