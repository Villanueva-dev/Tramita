# Quickstart — Captura pública del formato DO-FR-100

**Feature**: `004-public-request-capture` | **Recorrido y corregido contra la instancia local el 2026-09-16**

Cómo ejercitar el canal a mano, sin el frontend. Todos los comandos asumen el backend en
`localhost:8080` con el perfil `dev`.

> Cada resultado de este documento está **medido**, no previsto. La versión anterior afirmaba
> un `201` en el paso 1 y un `404` en el paso 2 que la instancia real no devolvía: se había
> escrito antes de que D10 sumara cuatro campos obligatorios al formato. Un comando citado
> como prueba debe poder re-ejecutarse y dar el mismo resultado.

> ⚠️ **ENMENDADO POR LA 008** (2026-09-24): `studentPhone` exige exactamente diez dígitos
> (`[0-9]{10}`, FR-009 de la 008), así que los cuerpos de abajo pasaron de `"000 000 0000"` y
> `"000"` a `"3000000001"`. Con el teléfono viejo el paso 1 daba `422` con
> `invalidFields: ["studentPhone"]` y el paso 2 daba `422` en vez del `404` que existe para
> mostrar; con el teléfono válido vuelven a dar lo que este documento mide. Lo encontró el
> review con agente limpio de la 008 (B3).

## Levantar

```bash
docker start tramita-postgres        # publica en el 5433 del host, no el 5432
set -a; source .env; set +a
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

---

## 1. Enviar un formato sin sesión y sin token CSRF

Lo esencial de este comando es lo que **no** tiene: ni cookie de sesión, ni cabecera
`X-XSRF-TOKEN`. Si alguna vez deja de funcionar sin ellas, la apertura del canal se rompió.

Lleva **los once campos del formato**. Ninguno es opcional salvo `studentCode`: el DO-FR-100
los pide todos y el canal los exige todos (FR-003, D10).

```bash
curl -i -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
  -H 'Content-Type: application/json' \
  -d '{
        "studentName": "Ana Ejemplo Prueba",
        "studentDocument": "SIN-DATO-REAL-001",
        "studentEmail": "ana.ejemplo@correo.test",
        "studentPhone": "3000000001",
        "program": "Ingeniería de Sistemas",
        "campus": "Cali",
        "faculty": "Facultad de Ingeniería",
        "modality": "Distancia",
        "semester": "Noveno",
        "reason": "Con las homologaciones de mi plan quedo un crédito por encima del tope de mi semestre. Me comprometo a sostener el promedio y la asistencia.",
        "signature": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg=="
      }'
```

**Medido**:

```
HTTP/1.1 201
{"message":"Tu solicitud llegó a la Coordinación."}
```

Sin `id`, sin estado y **sin cabecera `Location`**. Si aparece cualquiera de los tres, se abrió
la ventana de consulta que el diseño decidió no dar (FR-008).

Si falta alguno de los once, la respuesta nombra cuáles — en prosa para quien diligencia y en
un arreglo para el cliente que la consume:

```
HTTP/1.1 422
{
  "status": 422,
  "title": "Formato incompleto",
  "detail": "El formato está incompleto. Revise estos campos: campus, faculty, modality, studentPhone",
  "instance": "/api/public/requests/ADICION_CREDITOS",
  "missingFields": ["campus", "faculty", "modality", "studentPhone"],
  "invalidFields": []
}
```

Y si el campo **llegó lleno** pero su valor no se puede procesar, la respuesta es distinta a
propósito (issue #27): decirle «el formato está incompleto» a quien escribió el correo —solo que
mal— le pide rellenar una casilla que ve llena.

```bash
# el mismo cuerpo del paso 1, cambiando solo el correo
curl -s -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
  -H 'Content-Type: application/json' \
  -d '{ ..., "studentEmail": "ana.ejemplo-arroba-correo.test", ... }'
```

```
HTTP/1.1 422
{
  "status": 422,
  "title": "Formato inválido",
  "detail": "El formato tiene campos con un valor que no se puede procesar. Revise estos campos: studentEmail",
  "instance": "/api/public/requests/ADICION_CREDITOS",
  "missingFields": [],
  "invalidFields": ["studentEmail"]
}
```

Los dos arreglos llegan **siempre**, aunque vengan vacíos. Un campo que incumple las dos cosas a
la vez —`studentEmail` con solo espacios viola `@NotBlank` y `@Email`— aparece **solo** en
`missingFields`: la ausencia domina, para no obligar al cliente a decidir cuál mostrar.

Y un `Content-Type` que no sea JSON devuelve **415**, no 400:

```bash
curl -s -i -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
  -H 'Content-Type: text/plain' -d 'no es un formato'
```

```
HTTP/1.1 415
```

> Los datos son sintéticos a propósito. **Nunca** usar una cédula real en un comando que
> queda escrito en el repositorio: es público (§III, minimización).

## 2. Un trámite sin captura pública habilitada

⚠️ **El cuerpo tiene que estar COMPLETO para ver el `404`.** La validación del formato corre
antes de que el servicio resuelva el trámite, así que un cuerpo incompleto devuelve `422`
aunque el trámite no exista. No es un defecto: quien sondea qué trámites hay manda un cuerpo
válido, y ahí el canal sí responde lo mismo para «no existe» que para «no habilitado».

```bash
curl -i -X POST http://localhost:8080/api/public/requests/NOVEDAD_NOTAS \
  -H 'Content-Type: application/json' \
  -d '{"studentName":"Ana Ejemplo","studentDocument":"SIN-DATO-REAL-002","studentEmail":"a@b.test","studentPhone":"3000000001","program":"x","campus":"Cali","faculty":"x","modality":"x","semester":"x","reason":"x","signature":"data:image/png;base64,AA=="}'
```

**Medido**:

```
HTTP/1.1 404
{"detail":"No hay captura pública disponible para ese trámite", ...}
```

Y con un código inventado, **exactamente la misma respuesta** — que es el punto:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  http://localhost:8080/api/public/requests/TRAMITE_QUE_NO_EXISTE \
  -H 'Content-Type: application/json' -d '<el mismo cuerpo completo>'
# 404, con el mismo detail
```

Para habilitar un trámite (sin desplegar código, que es el punto del §VI):

```sql
INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), d.id, 'PUBLIC_CAPTURE_ENABLED', 'true'
FROM workflow_definition d
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 1;
```

> La columna es `parameter_value`, no `value`. La versión anterior de este documento decía
> `value` y el `INSERT` habría fallado.

## 3. Envío sin firma

Mismo cuerpo del paso 1, quitándole `signature`.

**Medido**: `422` en `application/problem+json`, con `"title": "Formato incompleto"`,
`"detail": "El formato está incompleto. Revise estos campos: signature"`,
`"missingFields": ["signature"]`, `"invalidFields": []`, y **ninguna fila nueva** en `request`.

## 4. Tope de tamaño

```bash
python3 -c "
import json
print(json.dumps({
  'studentName':'Ana Ejemplo','studentDocument':'SIN-DATO-REAL-004',
  'studentEmail':'a@b.test','studentPhone':'3000000001','program':'x','campus':'Cali',
  'faculty':'x','modality':'x','semester':'x','reason':'x',
  'signature':'data:image/png;base64,' + 'A'*300000
}))" > /tmp/envio-grande.json

curl -i -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
  -H 'Content-Type: application/json' --data-binary @/tmp/envio-grande.json
```

**Medido** (cuerpo de 294 KB contra el tope de 256 KB):

```
HTTP/1.1 413
{"status":413,"title":"El envío excede el tamaño admitido"}
```

El envío rechazado por tamaño **sí consume cupo** del límite del paso 5 (research.md D7-bis):
ocupó el canal aunque no se procesara. Con `Content-Length` declarado —como en el `curl` de
arriba— el servidor corta sin abrir el stream; sin él, lo lee hasta el tope para poder medirlo.
Cobra en los dos casos. Y si un origen ya agotó su cupo, el corte ocurre antes todavía: recibe el
`429` del paso 5, no este `413`.

## 5. Límite de envíos

El umbral es **20 envíos por origen cada 15 minutos** (`app.public-capture` en
`application.yml`, research.md D3-bis). Cuentan TODOS los envíos: los que terminan en `422` y
también los que el tope de tamaño rechaza con `413`. El recurso que se protege es el
procesamiento, y quien manda basura o envíos desmesurados lo consume igual.

```bash
for i in $(seq 1 25); do
  curl -s -o /dev/null -w "%{http_code} " -X POST \
    http://localhost:8080/api/public/requests/ADICION_CREDITOS \
    -H 'Content-Type: application/json' -d '{}'
done; echo
```

**Medido**: a partir del envío 21 desde ese origen, `429`.

```
HTTP/1.1 429
Retry-After: 874
Content-Type: application/problem+json;charset=UTF-8
{"status":429,"title":"Demasiados envíos desde este origen"}
```

El `Retry-After` cuenta lo que le falta al envío más viejo para salir de la ventana, así que
baja solo. Pasado ese tiempo, **sin reiniciar nada**, el mismo origen vuelve a poder enviar
(FR-019).

> ⚠️ Este paso deja el origen bloqueado un cuarto de hora. Conviene recorrerlo **al final**:
> los pasos 6 a 8 salen del mismo origen.
>
> 🔑 **El origen que el sistema contó en esta corrida NO fue `127.0.0.1` sino
> `0:0:0:0:0:0:0:1`** —el loopback IPv6—, porque `curl` resolvió `localhost` por IPv6. Se supo
> por el WARN que el filtro registra:
>
> ```
> WARN ... PublicSubmissionThrottlingFilter : Envío público bloqueado por límite de tasa.
> Origen contado: 0:0:0:0:0:0:0:1. Reintento en 874 s.
> ```
>
> **Consecuencia real**: un cliente con doble pila tiene dos claves distintas y, alternando,
> el doble de cupo. No se normaliza a propósito: el objetivo del límite es la disponibilidad
> (D3-bis), y duplicar el cupo de un cliente legítimo va en esa dirección, mientras que contra
> un script el corte ocurre igual. Pero conviene saberlo antes de depurar por qué un origen
> «no se bloqueó».

## 6. Verla en la vista de recientes

```bash
curl -s -c /tmp/cookies.txt http://localhost:8080/api/auth/me > /dev/null
XSRF=$(grep XSRF-TOKEN /tmp/cookies.txt | awk '{print $7}')

curl -s -b /tmp/cookies.txt -c /tmp/cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d "{\"email\":\"$SEED_COORD_EMAIL\",\"password\":\"$SEED_COORD_PASSWORD\"}"

curl -s -b /tmp/cookies.txt http://localhost:8080/api/requests/inbox | jq '.[0]'
```

**Medido**: el login responde `204` y la solicitud del paso 1 aparece primera, con
`definition.code` = `ADICION_CREDITOS` y `currentState.code` = `EN_COORDINACION`.

La comprobación que de verdad importa:

```bash
curl -s -b /tmp/cookies.txt http://localhost:8080/api/requests/inbox \
  | jq 'map(has("studentDocument")) | any'
```

**Medido**: `false`. Si devuelve `true`, se filtró el documento de identidad al listado.

## 7. El histórico nombra al portal

```bash
ID=$(curl -s -b /tmp/cookies.txt http://localhost:8080/api/requests/inbox | jq -r '.[0].id')
curl -s -b /tmp/cookies.txt http://localhost:8080/api/requests/$ID/timeline | jq '.[0]'
```

**Medido**: el primer tramo tiene `fromState: null`, `responsible: null` —un nacimiento no
tiene paso de la definición que lo respalde— y `actorEmail: "portal-publico@tramita.local"`.
Ningún tramo queda sin responsable (§VII).

## 8. Esa cuenta no puede iniciar sesión

```bash
curl -s -c /tmp/c2.txt http://localhost:8080/api/auth/me > /dev/null
X2=$(grep XSRF-TOKEN /tmp/c2.txt | awk '{print $7}')
curl -i -b /tmp/c2.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $X2" \
  -d '{"email":"portal-publico@tramita.local","password":"NO_LOGIN_ESTA_CUENTA"}'
```

**Medido**: `401` con `{"status":401,"title":"Credenciales inválidas"}` — el mismo cuerpo
genérico que cualquier credencial equivocada (anti-enumeración, FR-002).

> La versión anterior explicaba este `401` diciendo que «ningún codificador reconoce su valor
> de contraseña». **Es inexacto y esconde una trampa**: el hash sí declara su algoritmo con el
> prefijo `{bcrypt}`, y es justamente eso lo que evita el `500`. Un valor SIN prefijo hace que
> el `DelegatingPasswordEncoder` no tenga a quién delegar y lance `IllegalArgumentException`.
> Lo que sí ocurre es que el contenido no es un hash BCrypt válido, de modo que `matches()`
> devuelve `false` y el intento termina en este `401`. Ver research.md D4.

---

## Apagar

```bash
kill %1               # o cerrar el proceso de spring-boot:run
docker stop tramita-postgres
```
