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

> **El umbral, la ventana y dónde se configuran los fija D3-bis**, más abajo. Esta decisión
> resolvió el mecanismo y la clave, pero dejó sin decidir los números — y con ellos, el modo
> de fallo por IP compartida que D3-bis documenta.

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

## D3-bis — El umbral vive en `application.yml`, y es holgado a propósito

**Decidido el 2026-09-16**, al detectar que D3 fijaba la clave del contador («la dirección de
origen») pero no el umbral, la ventana ni el lugar donde se configuran.

**Decisión**: `20 envíos por IP cada 15 minutos`, declarados en `application.yml` bajo
`app.public-capture`, leídos por un record `@ConfigurationProperties` con validación
fail-fast, igual que `CorsProperties`.

```yaml
app:
  public-capture:
    max-submissions: 20
    window: 15m
    max-body-size: 256KB
```

### Por qué el umbral es alto y no bajo

El objetivo declarado de este límite **no es la confidencialidad: es la disponibilidad**. El
canal no guarda nada que valga la pena robar, y si el enlace deja de responder, el trámite no
arranca y el resto del flujo no ocurre. Bajo ese objetivo, **el propio rate limit es la
principal amenaza**: un falso positivo produce exactamente el fallo que se quiere evitar, y a
diferencia de un ataque, es un fallo que sí va a ocurrir.

El argumento que decide el número: **un script de abuso hace más de 100 peticiones por
segundo, de modo que un límite de 5 y uno de 50 lo cortan igual, en el primer segundo**. Entre
esos dos valores la protección es idéntica y lo único que cambia es la probabilidad de
bloquear a un estudiante legítimo. Por eso no se optimiza hacia abajo.

Calibración contra el volumen de la fuente —**30 a 40 solicitudes por semestre**, repartidas
en los primeros meses (`material-coord/2026-06-04-entrevista3-sintesis-analitica.md:213`)—:

| Medida | Valor |
|---|---|
| Solicitudes por día hábil, toda la sede | ≈ 0,6 |
| Con reintentos (×5, pesimista) | ≈ 3 envíos/día |
| Umbral fijado | 20 / 15 min = **80/hora** |

**Por qué la ventana es de 15 minutos y no de una hora**: la ventana no gradúa cuán estricto
es el límite, gradúa **cuánto dura el bloqueo cuando el límite se equivoca**. Con el objetivo
puesto en la disponibilidad, conviene ventana corta con umbral alto: el peor falso positivo
cuesta quince minutos, no sesenta. Además reusa la `WINDOW` que `LoginAttemptService` ya
define, de modo que el sistema mantiene un concepto de ventana y no dos.

### Por qué en `application.yml` y NO en `workflow_parameter`

Se evaluó ponerlo en `workflow_parameter`, por coherencia con `MAX_CREDITS` y
`PUBLIC_CAPTURE_ENABLED`. **Rechazado: mezcla capas.** El tope de créditos es regla de
negocio —cambia por trámite y por facultad, y esa variabilidad es justamente la tesis del
motor configurable (§VI)—. «Cuántas peticiones por hora tolera un endpoint» es una propiedad
del **canal HTTP**, no del trámite: no cambia entre adición de créditos y novedad de notas, y
declararla por trámite obligaría a replicarla en cada definición nueva sin que ninguna la
necesite distinta. Contaminaría el vocabulario de negocio con una tuerca de infraestructura.

**Costo aceptado**: recalibrar el umbral exige redesplegar, no un `UPDATE`. Es el precio de
mantener la separación, y es bajo: es un valor que se toca una vez tras el piloto, no una
regla que la Coordinación administre.

### ⚠️ Precondición de despliegue — el modo de fallo que este diseño introduce

`LoginThrottlingFilter` usa `request.getRemoteAddr()` directo, documentando que asume **sin
proxy delante**. Al momento de escribir esto no hay proveedor de despliegue elegido, pero la
intención es desplegar, y prácticamente cualquier despliegue real pone un proxy delante
(Cloudflare, nginx, el del PaaS).

**Cuando eso ocurra, `getRemoteAddr()` dejará de devolver la IP del estudiante y devolverá la
del proxy.** Con la clave solo-IP de D3, eso significa **una única clave para todo el mundo**:
el envío número 21 de cualquier persona bloquearía el canal para la sede entera. Es
precisamente el fallo de disponibilidad que este límite existe para prevenir, causado por el
límite mismo.

La corrección es configuración, no código
([Spring Boot — Running Behind a Front-end Proxy Server](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server)):

```yaml
server:
  forward-headers-strategy: NATIVE
  tomcat:
    remoteip:
      internal-proxies: "<regex de la IP del proxy>"
```

⛔ **`internal-proxies` NUNCA vacío**: los propios docs advierten que dejarlo vacío confía en
cualquier proxy, y entonces el cliente puede inventarse el `X-Forwarded-For` y evadir el
límite a voluntad — *«Setting internal-proxies to empty trusts all proxies, but should not be
done in production»*.

**Diagnóstico exigido al filtro**: debe registrar en WARN cada bloqueo **con la IP que usó
como clave**. Si en producción aparece siempre la misma IP, o una del rango privado
(`10.x`, `172.16-31.x`, `192.168.x`), ese es el síntoma inequívoco de que falta la
configuración de arriba. Sin ese log el defecto se manifiesta como «a los estudiantes les sale
un error raro» y es muy caro de rastrear.

### Qué se descartó

- *Agregar el documento del estudiante a la clave* (análogo al `email + IP` del login):
  protegería al compañero que comparte IP, pero con 80 envíos/hora ese caso es tan improbable
  que no justifica leer y parsear el cuerpo antes de validarlo.
- *CAPTCHA*: agrega fricción a un estudiante que ya está haciendo un trámite a contrarreloj y
  mete una dependencia de un servicio externo. Contra bots de formulario, un honeypot y un
  tiempo mínimo de diligenciado logran casi lo mismo sin ninguna de las dos desventajas.
- *Confiar únicamente en el rate limit del proveedor*: es donde este control pertenece de
  verdad, y conviene activarlo, pero no se puede depender de él mientras el proveedor no esté
  elegido. El filtro es la red de seguridad, no la defensa principal.

---

## D4 — La identidad del canal público es una fila inactiva en `users`

**Decisión**: se siembra `portal-publico@tramita.local` con la marca de activo en falso, y
el tramo inicial de las solicitudes públicas la nombra como responsable.

**Rationale**: `request_transition_log.actor_id` es `NOT NULL` por el §VII, y el histórico
debe nombrar un responsable en todo tramo. La fila sintética preserva esa garantía y además
es **honesta**: el responsable del tramo inicial *es* el portal público. En el histórico se
lee como tal, que es más informativo que un valor vacío.

**Seguridad**: la cuenta no puede iniciar sesión por dos vías independientes. La que se
ejecuta primero es el **hash**: `password_hash` declara el algoritmo con el prefijo
`{bcrypt}` pero su contenido no es un hash BCrypt válido, de modo que
`BCryptPasswordEncoder.matches()` no reconoce el patrón, registra una advertencia y devuelve
`false` — el intento termina en el 401 genérico, indistinguible de cualquier otra credencial
equivocada (FR-002). La segunda es **`active = FALSE`**: `AppUserDetailsService` mapea a
deshabilitado todo usuario inactivo, así que aunque existiera una clave que abriera la fila,
la cuenta seguiría rechazada.

> ⚠️ **RECTIFICADO el 2026-09-16, al implementar la US1.** Hasta esa fecha este párrafo
> decía: *«`AppUserDetailsService` marca como deshabilitado a todo usuario inactivo, de modo
> que esa cuenta no puede iniciar sesión. Se le asigna además un valor de contraseña sin
> prefijo de algoritmo reconocible, que ningún codificador puede verificar — defensa en
> profundidad, no la defensa principal»*. **Las dos afirmaciones que contiene son falsas, y
> juntas producían un 500.**
>
> **1. El orden de las defensas está invertido.** El texto suponía que la cuenta inactiva
> cortaba antes de evaluar contraseña alguna. En Spring Security 7 el orden es
> `performPreCheck → additionalAuthenticationChecks`, es decir que **la contraseña se evalúa
> ANTES que el estado de la cuenta** (`AbstractUserDetailsAuthenticationProvider:159→191`).
> Ese orden es deliberado: mitiga el ataque de tiempo que permitiría distinguir una cuenta
> deshabilitada de una inexistente. La consecuencia acá es que el hash no es la defensa
> secundaria sino la que de verdad se ejecuta primero.
>
> **2. Un hash sin prefijo de algoritmo no «no se puede verificar»: hace explotar el login.**
> `SecurityConfig` usa `DelegatingPasswordEncoder`, que elige el codificador por ese prefijo;
> sin él no tiene a quién delegar y lanza `IllegalArgumentException` en lugar de devolver
> `false`. Medido: la primera versión de `V3.3.0` sembró la fila sin prefijo y
> `portalAccountCannotAuthenticate` falló con *«Given that there is no default password
> encoder configured, each password must have a password encoding prefix»* — un **500**, no
> el 401 esperado. Además de ser un defecto en sí, ese 500 habría hecho **distinguible** esa
> cuenta de cualquier otra, rompiendo el anti-enumeración que el FR-002 protege.
>
> **Cómo se detectó**: por el test `portalAccountCannotAuthenticate` (T009), que se escribió
> en la fase RED precisamente para afirmar esta garantía. Es el caso de un test que pasa en
> verde por ausencia —mientras la fila no existía, el email desconocido ya daba 401— y cuyo
> valor real aparece cuando la fila empieza a existir.

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

## D10 — Los once campos del formato se exigen y se conservan

**Decisión** (2026-09-16): el canal público exige los once campos del formato —nombre,
documento, correo, contacto, programa, sede, facultad, modalidad, semestre, compromisos y
firma— y los persiste. **Ningún campo del formulario puede quedar vacío.**

**Rationale**: la alternativa que se venía siguiendo era pedir algunos campos y descartarlos
—el formulario los mostraba «por fidelidad al papel» y el backend los ignoraba—. Esa es la
peor de las tres salidas posibles: le cuesta trabajo a quien diligencia, en un celular, y
además **exige un dato personal sin conservarlo**, lo que contraviene §III con más fuerza
que persistirlo, porque lo vuelve obligatorio sin finalidad.

El consumidor que justifica conservarlos es **la generación del PDF formal del trámite**
(SP3, `Villanueva-dev/Tramita#10`): un documento que no reproduce el formato oficial no
sirve para lo que el trámite necesita, y el DO-FR-100 pide esos campos en su tabla de datos
del solicitante. `faculty` tiene además un consumidor dentro del propio motor, cuyo flujo
pasa por un estado `EN_FACULTAD`.

**Consecuencia**: `V3.3.0` pasa de dos columnas a seis. Las seis quedan **nullable** — la
obligatoriedad es del contrato de entrada, no del modelo, porque la migración corre sobre
filas existentes que no tienen estos datos y el formulario interno sigue aceptando el cuerpo
mínimo de la `002`.

**Alternativas consideradas**:
- *Exigirlos en pantalla y seguir descartándolos en el servidor*: rechazada por lo anterior.
  Es la que menos código cuesta y la que peor se defiende.
- *Quitarlos del formulario*: rechazada por el responsable del proyecto. Habría dejado el
  PDF del SP3 sin datos para reproducir el formato.
- *Dejar sede, ciudad y fecha como constantes del sistema*: **aceptada solo para ciudad y
  fecha**, que el servidor conoce. La sede se conserva como dato declarado porque el alcance
  del MVP —Sede Cali— es una restricción del proyecto, no del modelo.

> ✅ **CONFIRMADO el 2026-09-16 — ya no es provisional.** Este bloque advertía que la frase
> citada de `Tramita#10` se escribió analizando el formato de **novedad de notas** y no el
> DO-FR-100, y que sostener D10 sobre ella exigía confirmarlo contra la plantilla v2024 (§IV:
> la normativa institucional se verifica contra el documento obtenido de la fuente, no contra
> una fuente técnica ni una inferencia).
>
> Se hizo. La plantilla oficial
> (`material-coord/2026-06-03-coord-DO-FR-100-formato-solicitud-excepcion-de-matricula-v2024.docx`,
> **DO-FR-100 · Versión. 01 · Fecha. 19/11/2024** según su encabezado) pide en su tabla del
> solicitante, con estas palabras: «Correo electrónico», «Número de contacto», «Sede»,
> «Facultad» y «Modalidad». **D10 ya no descansa en un principio trasladado desde otro
> formato: descansa en el documento que el trámite usa.**
>
> Detalle de método, porque el primer intento dio el resultado contrario:
> `libreoffice --headless --convert-to txt` **no sirve para este archivo** — descarta tablas
> y encabezado, que es donde viven los once campos. Hay que leer `word/document.xml` y
> `word/header1.xml` del `.docx` descomprimido.

---

## Preguntas abiertas que esta feature NO resuelve

Ninguna bloquea la implementación; se arrastran de fases anteriores y afectan lo que el
sistema puede **afirmar**, no lo que hace.

| Pregunta | Estado | Efecto |
|---|---|---|
| Validez legal de la firma trazada | Nunca formulada a la Coordinación (P29 de la guía) | El sistema guarda el trazo y no afirma nada sobre su valor (FR-021) |
| ¿Es dato biométrico una firma digitalizada bajo la Ley 1581? | Sin verificación documental | Cambiaría el régimen de tratamiento del dato, no su almacenamiento |

> **Resuelta el 2026-09-16 y retirada de esta tabla**: *«¿El DO-FR-100 exige los campos que
> D10 asume?»*. Se verificó la plantilla oficial
> (`material-coord/2026-06-03-coord-DO-FR-100-formato-solicitud-excepcion-de-matricula-v2024.docx`,
> identificada en su encabezado como **DO-FR-100 · Versión. 01 · Fecha. 19/11/2024**) y **los
> once campos del FR-003 están en ella, uno a uno**: «Nombres completos del solicitante»,
> «Número de identificación», «Correo electrónico», «Número de contacto», «Programa académico
> en el que se encuentra», «Sede», «Facultad», «Modalidad», «Semestre cursado y aprobado»,
> «Compromisos adquiridos» y «Firma del estudiante». D10 y las seis columnas de `V3.3.0`
> quedan sostenidas por la fuente, no por inferencia.
>
> Lo que la plantilla pide y no se captura tiene razón: «Ciudad» viene impresa con `Cali`,
> «Tipo de solicitud» viene con `Matrícula créditos adicionales` marcada —y viaja en la ruta,
> D2—, la fecha la pone el servidor, y la «Firma de la Facultad» pertenece al tramo posterior
> del trámite.
>
> ⚠️ **Cómo leer ese `.docx`**: `libreoffice --headless --convert-to txt` **no sirve** —
> descarta el contenido de las tablas y del encabezado, que es donde viven los once campos, y
> devuelve un texto plausible al que le faltan justamente. Hay que extraer el texto de
> `word/document.xml` y `word/header1.xml` del `.docx` descomprimido.

> **Resuelta el 2026-09-16 y retirada de esta tabla**: *«¿Los 13 motivos del formato siguen
> vigentes?»*. La Coordinación confirmó que esas casillas pertenecen a **otros tipos de
> solicitud** del formato, no a la adición de créditos, y que casi no se diligencian. El
> motivo se captura como texto libre en `reason`; el formulario no las muestra.
