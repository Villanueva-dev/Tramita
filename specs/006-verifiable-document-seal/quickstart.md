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

### 1. Emitir el documento y ver que queda sellado

```bash
# con sesión iniciada; el PDF se guarda y se conserva para verificarlo después
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

```bash
curl -b cookies.txt -o segunda.pdf http://localhost:8080/api/requests/{id}/document
sha256sum documento.pdf segunda.pdf
```

⚠️ **Las dos huellas deben ser idénticas.** Si difieren, el determinismo se rompió y ningún
sello verifica: es el defecto que esta feature existe para cerrar. Antes de arreglarlo,
comparar dónde difieren — la sonda de `PdfDeterminismProbeTest` imprime el primer byte
divergente y su contexto.

Nota: son dos emisiones, así que habrá **dos** sellos con la misma huella y códigos
distintos. Es lo esperado (FR-009): el historial cuenta emisiones, no documentos.

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
curl -b cookies.txt -H 'Content-Type: application/json' \
     -d "{\"code\":\"{código}\",\"sha256\":\"$HUELLA\"}" \
     http://localhost:8080/api/seals/verify
```

→ `INTACT`.

Ahora la prueba que importa: **alterar el archivo y confirmar que se detecta**.

```bash
cp documento.pdf alterado.pdf
printf 'x' >> alterado.pdf        # un byte basta
HUELLA=$(sha256sum alterado.pdf | cut -d' ' -f1)
curl -b cookies.txt -H 'Content-Type: application/json' \
     -d "{\"code\":\"{código}\",\"sha256\":\"$HUELLA\"}" \
     http://localhost:8080/api/seals/verify
```

→ `TAMPERED`.

### 5. La prueba que distingue esta feature de una ingenua

Hacer avanzar el trámite (o editarlo), de modo que cambie la revisión de la solicitud, y
volver a verificar **el mismo documento de antes**:

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/requests/{id}/transitions ...
HUELLA=$(sha256sum documento.pdf | cut -d' ' -f1)   # el documento LEGÍTIMO de antes
curl -b cookies.txt -H 'Content-Type: application/json' \
     -d "{\"code\":\"{código}\",\"sha256\":\"$HUELLA\"}" \
     http://localhost:8080/api/seals/verify
```

→ **`NOT_VERIFIABLE` con `reason: DATA_CHANGED`**, nunca `TAMPERED`.

🔑 Ese documento es legítimo: simplemente es viejo. Si el sistema respondiera «alterado»
estaría acusando de falsificación a un papel perfecto, que es exactamente lo que el FR-007
prohíbe. **Si hay una sola comprobación manual que hacer en esta feature, es esta.**

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

Registrar una solicitud con una calificación de más de un decimal debe responder `422` y
**no** guardarla redondeada.

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
