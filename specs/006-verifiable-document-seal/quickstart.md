# Quickstart — Feature 006: sello verificable

Cómo levantar, ejercitar y comprobar la feature a mano. El contrato formal está en
[contracts/openapi.yaml](./contracts/openapi.yaml).

## Levantar el entorno

```bash
docker start tramita-postgres && docker ps          # publica en el 5433 del host, no el 5432
set -a; source .env; set +a
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Flyway corre en modo `validate`, así que la migración `V4.1.0` se aplica al arrancar. **Si
el arranque falla en la restricción de precisión**, es el saneamiento: hay calificaciones
con más de un decimal en la base. Se comprueba con:

```bash
docker exec tramita-postgres psql -U postgres -d tramita-db -c \
  "SELECT id, code, current_grade, proposed_grade FROM request_subject
   WHERE (current_grade  IS NOT NULL AND current_grade  <> round(current_grade,  1))
      OR (proposed_grade IS NOT NULL AND proposed_grade <> round(proposed_grade, 1));"
```

Al escribir este plan devolvía **una fila** (`proposed_grade = 3.46`), que la propia
migración redondea antes de declarar la restricción.

## El recorrido completo, a mano

### 0. Sesión y token CSRF

⚠️ **Sin esto, todo `POST` de abajo responde `403`, no `401`.** La protección CSRF está activa
para todo salvo la captura pública (`SecurityConfig:129-130`), así que cada mutación necesita
la cabecera `X-XSRF-TOKEN`. Los `GET` no la necesitan.

```bash
# 1) GET inicial: emite la cookie XSRF-TOKEN
curl -c cookies.txt http://localhost:8080/api/requests/inbox -o /dev/null -s

# 2) login
XSRF=$(grep XSRF-TOKEN cookies.txt | awk '{print $7}')
curl -b cookies.txt -c cookies.txt -H 'Content-Type: application/json' \
     -H "X-XSRF-TOKEN: $XSRF" \
     -d '{"email":"...","password":"..."}' \
     http://localhost:8080/api/auth/login

# 3) el token para las mutaciones siguientes
XSRF=$(grep XSRF-TOKEN cookies.txt | awk '{print $7}')
```

### 1. Emitir el documento y ver que queda sellado

```bash
# el PDF se guarda y se conserva para verificarlo después. Es un GET: sin token
curl -b cookies.txt -o documento.pdf http://localhost:8080/api/requests/{id}/document
```

Comprobaciones:

```bash
# el pie lleva el código impreso
pdftotext documento.pdf - | tail -5

# quedó exactamente un sello nuevo
docker exec tramita-postgres psql -U postgres -d tramita-db -c \
  "SELECT verification_code, format_version, request_version, state_code, issued_at
   FROM request_document_seal ORDER BY issued_at DESC LIMIT 1;"
```

### 2. Comprobar que el documento es reproducible

⚠️ **Dos emisiones NO dan el mismo archivo, y está bien que así sea.** Cada una imprime su
propio código de verificación en el pie, así que sus bytes difieren a propósito: son dos
papeles distinguibles, cada uno con su sello. Comparar dos descargas y esperar huellas
iguales mide la propiedad equivocada.

Lo que hay que comprobar es **FR-004: que reconstruir una emisión dé exactamente sus bytes**.
Ya no es lo que hace la verificación: el paso 4 compara la huella recibida contra la huella
que el sello **guardó** al emitir (`research.md` D2/D6), sin regenerar nada. Un `INTACT`
confirma que el archivo descargado es, bit a bit, el que el sistema entregó — pero no
ejercita el determinismo del render.

Para verlo aislado, sin pasar por la API, está el test de determinismo (tramo 1), que
reconstruye dos veces con el mismo código fijo y compara los bytes:

```bash
docker start tramita-postgres && ./mvnw clean test -Dtest=PdfDeterminismProbeTest
```

Si ahí las huellas difieren, el determinismo se rompió y ningún sello verifica: es el defecto
que esta feature existe para cerrar. La sonda imprime el primer byte divergente y su contexto.

### 3. Verificar sin cuenta, como lo haría la decanatura

```bash
# sin cookie: este canal es abierto a propósito
curl http://localhost:8080/api/public/seals/{código-impreso}
```

Debe responder `ISSUED` con la fecha, el estado y la revisión — y **ningún dato personal**.
Conviene mirar la respuesta entera y confirmar que no aparece ni el nombre ni la cédula:
es el FR-014c, y es lo que un revisor va a comprobar primero.

```bash
# un código inventado responde 404, igual que uno mal escrito
curl -i http://localhost:8080/api/public/seals/ZZZZZZZZZZZZZ
```

### 4. Verificación exacta, con sesión

```bash
HUELLA=$(sha256sum documento.pdf | cut -d' ' -f1)
curl -b cookies.txt -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
     -d "{\"code\":\"{código}\",\"sha256\":\"$HUELLA\"}" \
     http://localhost:8080/api/seals/verify
```

→ `INTACT`.

Ahora la prueba que importa: **alterar el archivo y confirmar que se detecta**.

```bash
cp documento.pdf alterado.pdf
printf 'x' >> alterado.pdf        # un byte basta
HUELLA=$(sha256sum alterado.pdf | cut -d' ' -f1)
curl -b cookies.txt -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
     -d "{\"code\":\"{código}\",\"sha256\":\"$HUELLA\"}" \
     http://localhost:8080/api/seals/verify
```

→ `TAMPERED`.

### 5. La prueba que distingue esta feature de una ingenua

Hacer avanzar el trámite (o editarlo), de modo que cambie la revisión de la solicitud, y
volver a verificar **el mismo documento de antes**:

```bash
curl -b cookies.txt -H "X-XSRF-TOKEN: $XSRF" -X POST \
     http://localhost:8080/api/requests/{id}/transitions ...
HUELLA=$(sha256sum documento.pdf | cut -d' ' -f1)   # el documento LEGÍTIMO de antes
curl -b cookies.txt -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
     -d "{\"code\":\"{código}\",\"sha256\":\"$HUELLA\"}" \
     http://localhost:8080/api/seals/verify
```

→ **`INTACT`**, no `NOT_VERIFIABLE` y nunca `TAMPERED`.

🔑 Ese documento es legítimo, y la verificación lo dice sin rodeos: su huella sigue siendo la
que el sello guardó al emitir, y esa comparación no depende de la revisión actual de la
solicitud. Con el diseño anterior —que regeneraba el documento con los datos de hoy antes de
comparar— este mismo caso daba `NOT_VERIFIABLE / DATA_CHANGED`: un papel perfecto quedaba sin
poder verificarse íntegro solo porque el trámite había avanzado, lo cual es un motivo
distinto pero igual de injusto que el `TAMPERED` que el FR-007 prohíbe. **Si hay una sola
comprobación manual que hacer en esta feature, es esta.**

### 6. Comprobar que el sello no se puede tocar

```bash
docker exec tramita-postgres psql -U postgres -d tramita-db -c \
  "UPDATE request_document_seal SET document_sha256 = 'x';"
docker exec tramita-postgres psql -U postgres -d tramita-db -c \
  "DELETE FROM request_document_seal;"
```

Las dos deben fallar con `request_document_seal es inmutable`. Se ejecutan por acceso
directo al motor, que es el punto: la garantía del §VII es de la base, no de la disciplina
del código.

### 7. Comprobar que la precisión se rechaza en la entrada

Registrar una solicitud con una calificación de más de un decimal debe responder `400` y
**no** guardarla redondeada. Es `400` y no `422` porque las calificaciones entran por el
formulario interno, y el `422` pertenece al canal público de captura por un advice acotado.

```bash
# proposed_grade: 3.456  → debe ser rechazado
```

## Qué debe pasar si la base no puede escribir

Con la base en solo lectura, **la emisión falla y no se entrega documento** (FR-012,
fail-closed). Se simula así:

```bash
docker exec tramita-postgres psql -U postgres -d tramita-db -c \
  "ALTER DATABASE \"tramita-db\" SET default_transaction_read_only = on;"
# reiniciar la conexión de la app y pedir el documento → error, sin PDF
docker exec tramita-postgres psql -U postgres -d tramita-db -c \
  "ALTER DATABASE \"tramita-db\" SET default_transaction_read_only = off;"
```

No es un escenario que haya que soportar: es la comprobación de que **no existe un documento
emitido sin sello registrado**. Con el código impreso en el papel, entregar sin registrar
produciría documentos legítimos que el propio sistema declararía desconocidos.

## Pruebas automatizadas

```bash
docker start tramita-postgres && ./mvnw clean verify
```

⚠️ **`clean` no es opcional cuando se trabaja en rojo**: la compilación incremental de Maven
puede dar un rojo falso o esconder uno verdadero.

Los de integración levantan su propio Postgres con Testcontainers y no usan el contenedor de
desarrollo.
