# Quickstart — Captura pública del formato DO-FR-100

**Feature**: `004-public-request-capture` | **Fecha**: 2026-09-15

Cómo ejercitar el canal a mano, sin el frontend. Todos los comandos asumen el backend en
`localhost:8080` con el perfil `dev`.

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

```bash
curl -i -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
  -H 'Content-Type: application/json' \
  -d '{
        "studentName": "Ana Ejemplo Prueba",
        "studentDocument": "SIN-DATO-REAL-001",
        "studentEmail": "ana.ejemplo@correo.test",
        "program": "Ingeniería de Sistemas",
        "semester": "Noveno",
        "reason": "Con las homologaciones de mi plan quedo un crédito por encima del tope de mi semestre. Me comprometo a sostener el promedio y la asistencia.",
        "signature": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg=="
      }'
```

**Esperado**: `201` con un cuerpo que contiene **solo** un mensaje. Sin `id`, sin estado, sin
cabecera `Location`. Si aparece cualquiera de los tres, se abrió la ventana de consulta que
el diseño decidió no dar (FR-008).

> Los datos son sintéticos a propósito. **Nunca** usar una cédula real en un comando que
> queda escrito en el repositorio: es público (§III, minimización).

## 2. Un trámite sin captura pública habilitada

```bash
curl -i -X POST http://localhost:8080/api/public/requests/NOVEDAD_NOTAS \
  -H 'Content-Type: application/json' \
  -d '{"studentName":"Ana Ejemplo","studentDocument":"SIN-DATO-REAL-002","studentEmail":"a@b.test","reason":"x","signature":"data:image/png;base64,AA=="}'
```

**Esperado**: `404`. No distingue entre «no existe» y «no habilitado» — esa distinción solo
le sirve a quien sondea.

Para habilitarlo (sin desplegar código, que es el punto del §VI):

```sql
INSERT INTO workflow_parameter (id, definition_id, parameter_key, value)
SELECT gen_random_uuid(), d.id, 'PUBLIC_CAPTURE_ENABLED', 'true'
FROM workflow_definition d
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 1;
```

## 3. Envío sin firma

```bash
curl -i -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
  -H 'Content-Type: application/json' \
  -d '{"studentName":"Ana Ejemplo","studentDocument":"SIN-DATO-REAL-003","studentEmail":"a@b.test","reason":"Necesito adicionar una asignatura."}'
```

**Esperado**: `422` en `application/problem+json`, y **ninguna fila nueva** en `request`.

## 4. Tope de tamaño

```bash
python3 -c "
import json
print(json.dumps({
  'studentName':'Ana Ejemplo','studentDocument':'SIN-DATO-REAL-004',
  'studentEmail':'a@b.test','reason':'x',
  'signature':'data:image/png;base64,' + 'A'*300000
}))" > /tmp/envio-grande.json

curl -i -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
  -H 'Content-Type: application/json' --data-binary @/tmp/envio-grande.json
```

**Esperado**: `413`.

## 5. Límite de envíos

Enviar el comando del paso 1 repetidamente desde el mismo origen hasta superar el umbral.

**Esperado**: a partir del envío que lo supera, `429` con cabecera `Retry-After`. Pasado ese
tiempo, sin reiniciar nada, el mismo origen vuelve a poder enviar.

## 6. Verla en la vista de recientes

Con sesión de la Coordinación:

```bash
# Obtener la cookie XSRF y la sesión
curl -s -c /tmp/cookies.txt http://localhost:8080/api/auth/me > /dev/null
XSRF=$(grep XSRF-TOKEN /tmp/cookies.txt | awk '{print $7}')

curl -s -b /tmp/cookies.txt -c /tmp/cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d "{\"email\":\"$SEED_COORD_EMAIL\",\"password\":\"$SEED_COORD_PASSWORD\"}"

curl -s -b /tmp/cookies.txt http://localhost:8080/api/requests/inbox | jq
```

**Esperado**: la solicitud del paso 1 aparece primera, y **ningún objeto de la respuesta
tiene `studentDocument`**. Comprobarlo explícitamente:

```bash
curl -s -b /tmp/cookies.txt http://localhost:8080/api/requests/inbox \
  | jq 'map(has("studentDocument")) | any'
```

**Esperado**: `false`. Si devuelve `true`, se filtró el documento de identidad al listado.

## 7. El histórico nombra al portal

```bash
ID=$(curl -s -b /tmp/cookies.txt http://localhost:8080/api/requests/inbox | jq -r '.[0].id')
curl -s -b /tmp/cookies.txt http://localhost:8080/api/requests/$ID/timeline | jq '.[0]'
```

**Esperado**: el primer tramo tiene `fromState: null` y
`actorEmail: "portal-publico@tramita.local"`. Ningún tramo sin responsable (§VII).

## 8. Esa cuenta no puede iniciar sesión

```bash
curl -s -c /tmp/c2.txt http://localhost:8080/api/auth/me > /dev/null
X2=$(grep XSRF-TOKEN /tmp/c2.txt | awk '{print $7}')
curl -i -b /tmp/c2.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $X2" \
  -d '{"email":"portal-publico@tramita.local","password":"NO_LOGIN"}'
```

**Esperado**: `401`. La cuenta está inactiva y ningún codificador reconoce su valor de
contraseña.

---

## Apagar

```bash
docker stop tramita-postgres
```
