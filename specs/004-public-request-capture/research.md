# Research — Captura pública del formato DO-FR-100

**Feature**: `004-public-request-capture` | **Fecha**: 2026-09-15

Cada decisión sigue el formato que exige la constitución §IV: qué se eligió, por qué, y qué
se descartó sabiendo cuál es el costo.

---

## D1 — El trámite se habilita por configuración, no por código

**Decisión**: el enlace público lleva el código del trámite en su ruta, y el servidor acepta
el envío solo si la definición de ese trámite tiene el parámetro `PUBLIC_CAPTURE_ENABLED`
en `true`. Se siembra únicamente para adición de créditos.

**Rationale**: el §VI exige que incorporar un trámite ya cubierto no requiera desplegar
código. Novedad de notas tiene la misma estructura; habilitarle captura pública debe ser un
`INSERT`, no un despliegue. Reusa `workflow_parameter`, la tabla que la `003` creó, y el
patrón exacto de `CAPTURES_CREDITS` en `RequestBusinessRulesImpl`: parámetro booleano por
definición, cuya ausencia significa «no» y cuyo valor no interpretable es configuración rota
(error del servidor, nunca aceptación silenciosa).

**Alternativas consideradas**:
- *Literal `ADICION_CREDITOS` en el servidor*: más simple hoy. Rechazada porque dejaría el
  motor configurable con su entrada más nueva cableada a un trámite — justo lo que la
  pregunta de investigación dice evitar, y lo primero que un jurado señalaría.
- *El trámite viaja en el cuerpo del envío*: rechazada por seguridad. Un cuerpo manipulado
  podría radicar en un trámite no habilitado. En la ruta, el enlace determina el trámite y
  el contenido no puede cambiarlo (FR-002a).

**Costo aceptado**: los códigos de trámite quedan visibles en la URL pública. No son
secretos —son nombres de procesos institucionales— y la habilitación sigue siendo del
servidor: conocer el código de un trámite no habilitado no permite radicar en él.

---

## D2 — CSRF se desactiva en el endpoint público

**Decisión**: el endpoint de recepción queda excluido de la protección CSRF; el resto del
sistema la conserva sin cambios.

**Rationale**: CSRF protege contra que un sitio de terceros provoque una acción **usando la
sesión de la víctima**. En un endpoint sin sesión no hay identidad que suplantar: quien
quiera enviar lo hace directamente con cualquier cliente HTTP, con o sin token. Mantenerla
solo obligaría al formulario a pedir una cookie previa, agregando un paso que puede fallar
sin cerrar ningún ataque.

**Alternativas consideradas**:
- *Dejar CSRF activo*: rechazada. Produce una sensación de protección que no corresponde a
  una amenaza real en este endpoint, y el `LoginThrottlingFilter` ya dejó escrito el mismo
  razonamiento: *«el CSRF double-submit no frena a un atacante directo, que fabrica su
  propio par cookie+header»*.

**Costo aceptado**: el endpoint queda abierto a envíos automatizados. Lo contiene D3, que es
la defensa que sí corresponde a esta amenaza.

> ⚠️ **Nota para quien lea esto después**: este proyecto ya perdió un mes persiguiendo un
> 403 que se atribuyó a CSRF y era una allowlist de CORS mal configurada. Esta desactivación
> es deliberada, está acotada a una ruta, y no debe usarse como precedente para desactivarla
> en ninguna otra.

---

## D3 — El límite de envíos reusa la ventana deslizante del login

**Decisión**: se extrae de `LoginAttemptService` la mecánica de ventana deslizante a una
clase propia, y el filtro del canal público la usa con la dirección de origen como clave.
`LoginAttemptService` conserva su API pública intacta y delega.

**Rationale**: la lógica ya existe, probada y con reloj inyectable para tests deterministas.
Duplicarla sería violar DRY; reusarla tal cual obligaría a llamar `recordFailure` para
contar envíos exitosos, y un método cuyo nombre miente es peor que el duplicado. Extraer la
mecánica deja ambos usos con nombres honestos.

**Blast radius medido**: `LoginAttemptService` tiene 14 llamadas en 5 archivos, cubiertas
por 3 suites de test. Ninguna se toca: solo cambia el interior de la clase.

**Alternativas consideradas**:
- *Contador propio para el canal público*: ~30 líneas duplicadas de lógica con
  concurrencia y purga. Rechazada.
- *Renombrar los métodos de `LoginAttemptService`*: tocaría los 14 puntos de uso sin
  beneficio adicional sobre la extracción.
- *Librería de rate limiting*: rechazada por el §I y por la restricción de no agregar
  dependencias para un problema que 40 líneas ya resuelven.

---

## D4 — La identidad del canal público es una fila inactiva en `users`

**Decisión**: se siembra `portal-publico@tramita.local` con la marca de activo en falso, y
el tramo inicial de las solicitudes públicas la nombra como responsable.

**Rationale**: `request_transition_log.actor_id` es `NOT NULL` por el §VII, y el histórico
debe nombrar un responsable en todo tramo. La fila sintética preserva esa garantía y además
es **honesta**: el responsable del tramo inicial *es* el portal público. En el histórico se
lee como tal, que es más informativo que un valor vacío.

**Seguridad**: `AppUserDetailsService` marca como deshabilitado a todo usuario inactivo, de
modo que esa cuenta no puede iniciar sesión. Se le asigna además un valor de contraseña sin
prefijo de algoritmo reconocible, que ningún codificador puede verificar — defensa en
profundidad, no la defensa principal.

**Alternativas consideradas**:
- *Permitir `actor_id` nulo*: rechazada. Aflojar una garantía estructural del §VII para
  cubrir un caso equivale a perderla para todos.
- *Una columna nueva que marque el origen*: agrega estado para responder algo que el
  responsable del tramo ya responde. Violación del §I.

**Costo aceptado**: la tabla de usuarios contiene una fila que no es una persona. Se
documenta en la migración para que nadie la confunda con una cuenta real ni la borre.

---

## D5 — El recibo no devuelve identificador ni estado

**Decisión**: la confirmación al estudiante no incluye el identificador de la solicitud, su
estado ni ningún medio de consulta posterior.

**Rationale**: devolver el identificador crearía de hecho una ventana de consulta al motor
—bastaría con probarlo— y la Coordinación rechazó explícitamente que el estudiante consulte
el estado. Menos superficie expuesta a cambio de que el estudiante pregunte por correo, que
es lo que hace hoy.

**Alternativas consideradas**:
- *Devolver un número de radicado*: rechazada. Es la puerta de entrada a la consulta de
  estado, que está fuera de alcance por decisión de la Coordinación, no por falta de tiempo.

---

## D6 — La firma se persiste como texto junto a la solicitud

**Decisión**: el trazo se guarda como una URL de datos en una columna de texto, en la misma
fila de la solicitud.

**Rationale**: un trazo del tamaño del recuadro pesa 20–30 KB en base64. Guardarlo como
texto evita abrir el frente de almacenamiento de archivos, que está fuera de alcance y
arrastraría decisiones de custodia, respaldo y limpieza que este MVP no necesita.

**Alternativas consideradas**:
- *Almacenamiento de archivos*: rechazada por el §I. Ninguna otra parte del sistema recibe
  archivos todavía.
- *Reusar el modelo de aprobaciones con sello verificable*: rechazada por ser otra cosa. Ese
  vocabulario (`SignatureType`, `AttachmentApproval`, huella del documento) describe las
  firmas de los **aprobadores** sobre el PDF, que es el SP4. La firma del estudiante es un
  dato de captura del formato. Mezclarlas metería la captura dentro de un modelo de sellado
  que todavía no está construido.

> ⚠️ **Provisional y no auditada** (§IV, régimen de normativa institucional): no se ha
> obtenido pronunciamiento institucional sobre el valor probatorio de una firma trazada, y
> la pregunta correspondiente de la guía de entrevista nunca llegó a formularse. Además,
> **no está determinado si una firma manuscrita digitalizada constituye dato biométrico**
> bajo la Ley 1581 de 2012, lo que cambiaría su régimen de tratamiento. Hasta obtener ambos
> documentos, el sistema conserva el trazo y **ningún texto afirma que tenga validez legal**
> (FR-021).

---

## D7 — El tope de tamaño del envío es de 256 KB

**Decisión**: el filtro del canal público rechaza envíos cuyo contenido supere 256 KB.

**Rationale**: el login se acota a 8 KB porque son dos campos. Aquí el envío carga el
formato completo más un trazo en base64: 256 KB deja margen amplio para una firma densa sin
permitir que el canal abierto agote la memoria del proceso. El mecanismo es el mismo que ya
usa `LoginThrottlingFilter`, incluida la lectura de un byte de más para no confiar en la
longitud que declara el cliente.

**Alternativas consideradas**:
- *Sin tope*: rechazada. Es el modo de fallo que la auditoría de julio ya encontró en el
  único endpoint abierto del sistema.
- *Confiar en el límite del contenedor de servlets*: rechazada por la misma razón que está
  documentada en `LoginThrottlingFilter`: solo acota formularios codificados, no cuerpos
  JSON.

---

## D8 — La vista de recientes es un contrato distinto, no la búsqueda relajada

**Decisión**: se agrega una consulta propia con su propio DTO, sin documento de identidad,
en vez de hacer opcional el criterio de la búsqueda existente.

**Rationale**: el repositorio documenta que un volcado sin filtro devolvería *«nombre y
cédula de cada estudiante»*, y que eso *«no es solo un bug de búsqueda»* sino un problema de
minimización bajo la Ley 1581 de 2012. La objeción es a exponer documentos en masa, no a
listar. Un DTO sin documento de identidad resuelve la objeción conservando el listado.

**Alternativas consideradas**:
- *Hacer opcional el criterio de búsqueda*: rechazada. El mismo endpoint devolvería dos
  formas distintas del mismo objeto según si se pasó el parámetro — un contrato que no se
  puede leer sin ejecutarlo.
- *Reusar el DTO existente omitiendo el campo*: rechazada. Un campo presente-pero-vacío
  invita a que alguien lo llene después sin advertir por qué estaba vacío.

**Costo aceptado**: dos DTO parecidos. No es duplicación de conocimiento sino dos contratos
con reglas de exposición distintas, y la diferencia es exactamente el punto.

---

## D9 — Los envíos duplicados se registran por separado

**Decisión**: el sistema no intenta detectar repeticiones.

**Rationale**: detectarlas exigiría una ventana de tiempo que ninguna fuente respalda, y
produciría un fallo silencioso — quien corrigió un dato y reenvió creería haber enviado la
corrección. Un duplicado visible se descarta; un envío perdido no lo nota nadie.

**Alternativas consideradas**: fusionar por documento y trámite dentro de una ventana, y
bloquear el doble clic en el formulario. La segunda sigue disponible como mitigación de
interfaz si el piloto muestra que molesta; no requiere cambiar el servidor.

---

## Preguntas abiertas que esta feature NO resuelve

Ninguna bloquea la implementación; las tres se arrastran de fases anteriores y afectan lo
que el sistema puede **afirmar**, no lo que hace.

| Pregunta | Estado | Efecto |
|---|---|---|
| Validez legal de la firma trazada | Nunca formulada a la Coordinación (P29 de la guía) | El sistema guarda el trazo y no afirma nada sobre su valor (FR-021) |
| ¿Es dato biométrico una firma digitalizada bajo la Ley 1581? | Sin verificación documental | Cambiaría el régimen de tratamiento del dato, no su almacenamiento |
| ¿Los 13 motivos del formato siguen vigentes? | Pendiente con la Coordinación | Afecta al formulario del frontend, no al backend |
