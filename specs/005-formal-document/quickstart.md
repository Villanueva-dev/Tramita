# Quickstart — El documento formal del trámite (DO-FR-100)

**Feature**: `005-formal-document` | **Recorrido contra la instancia local el 2026-09-17**

Cómo obtener el formato oficial a mano, sin el frontend. Todo asume el backend en
`localhost:8080` con el perfil `dev`.

> Cada resultado de este documento está **medido**, no previsto. En este proyecto ya hubo un
> quickstart con tres afirmaciones falsas por escribirse sin ejecutar: un comando citado como
> prueba debe poder re-ejecutarse y dar el mismo resultado (§IV).

## Levantar

```bash
docker start tramita-postgres        # publica en el 5433 del host, no el 5432
set -a; source .env; set +a
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

---

## 1. Abrir sesión y registrar una solicitud

El documento **no es un recurso del estudiante**: exige sesión de la Coordinación.

```bash
C=/tmp/cookies.txt; rm -f $C
curl -s -c $C http://localhost:8080/api/auth/me > /dev/null
X=$(grep XSRF-TOKEN $C | awk '{print $7}')

curl -s -b $C -c $C -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $X" \
  -d "{\"email\":\"$SEED_COORD_EMAIL\",\"password\":\"$SEED_COORD_PASSWORD\"}"

X=$(grep XSRF-TOKEN $C | awk '{print $7}')   # el token rota al autenticarse
ID=$(curl -s -b $C -c $C -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $X" \
  -d '{
        "definitionCode": "ADICION_CREDITOS",
        "studentName": "Ana Ejemplo Prueba",
        "studentDocument": "SIN-DATO-REAL-010",
        "studentCode": "EST-010",
        "program": "Ingeniería de Sistemas",
        "semester": "Noveno",
        "reason": "Con las homologaciones de mi plan quedo un crédito por encima del tope de mi semestre."
      }' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo $ID
```

**Medido**: `login: 204` y un identificador de solicitud.

> Los datos son sintéticos a propósito. **Nunca** usar una cédula real en un comando que
> queda escrito en el repositorio: es público (§III, minimización).

## 2. Descargar el formato

```bash
curl -s -b $C -D - -o /tmp/formato.pdf \
  "http://localhost:8080/api/requests/$ID/document"
```

**Medido**:

```
HTTP/1.1 200
Content-Disposition: attachment; filename="DO-FR-100-7cceac01-8030-4234-b5b8-87239d5b2d9c.pdf"
X-Content-Type-Options: nosniff
Content-Type: application/pdf
Content-Length: 51590
```

Se descarga en vez de abrirse en el navegador: es un documento para imprimir y firmar. El
nombre del archivo lleva el **identificador de la solicitud** y nunca el nombre ni la cédula
del estudiante — el archivo se reenvía y queda en carpetas compartidas, y el nombre viaja con
él (§III).

### Verificar el contenido sin abrir un visor

```bash
pdftotext /tmp/formato.pdf - | sed -n '/Ciudad/,/2026/p' | tr '\n' ' ' | tr -s ' '
```

**Medido**: `Ciudad Día Mes Año Cali 17 09 2026`

⚠️ **Esa línea vale más de lo que parece.** La ciudad va **preimpresa en el formato**, no es
un dato del solicitante. La primera versión la tomaba del campo `campus`, y las solicitudes
del formulario interno no lo traen —lo agregó el canal público en la 004—, así que la celda
salía vacía donde el papel dice «Cali». **Se descubrió mirando un documento generado desde la
base**, no con un test: hasta ese momento la suite estaba verde.

### Ver el documento

```bash
xdg-open /tmp/formato.pdf
# o, para inspeccionarlo sin visor:
pdftoppm -png -r 110 /tmp/formato.pdf /tmp/formato && xdg-open /tmp/formato-1.png
```

Son **dos páginas**: los datos en la primera y el campo de firmas en la segunda, como el
papel. Una solicitud registrada por el formulario interno **no trae firma ni correo ni sede**
—esos campos los captura el canal público—, así que esas celdas salen vacías. Es correcto:
un formato a medio diligenciar se ve a medio diligenciar.

## 3. Un trámite que no emite documento formal

```bash
X=$(grep XSRF-TOKEN $C | awk '{print $7}')
ID2=$(curl -s -b $C -c $C -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $X" \
  -d '{"definitionCode":"NOVEDAD_NOTAS","studentName":"Sin Formato Prueba",
       "studentDocument":"SIN-DATO-REAL-011","program":"Ingeniería de Sistemas",
       "semester":"Noveno","reason":"Corrección de nota del periodo anterior."}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -s -b $C -D - "http://localhost:8080/api/requests/$ID2/document"
```

**Medido**:

```
HTTP/1.1 404
Content-Type: application/problem+json
{
  "status": 404,
  "title": "Recurso no encontrado",
  "detail": "El trámite de esta solicitud no emite documento formal",
  "instance": "/api/requests/3d734ba7-6cb0-4341-b959-34a1768fbb1a/document"
}
```

`NOVEDAD_NOTAS` no declara `DOCUMENT_TEMPLATE`, y eso **no es una configuración a medio
cargar**: es el caso por defecto. No todo trámite emite documento formal, y su papel todavía
no se puede modelar —es por asignatura con varios estudiantes, con cuatro notas parciales—.

## 4. Sin sesión

```bash
curl -s -o /dev/null -w '%{http_code}\n' "http://localhost:8080/api/requests/$ID/document"
```

**Medido**: `401`.

Es la segunda barrera, no la única: el recibo del canal público **no devuelve identificador**
(FR-008 de la 004), así que quien envía el formato no tiene con qué construir esta URL.

## 5. Habilitar el formato para otro trámite, sin desplegar

Es el punto del §VI. Basta declarar el parámetro:

```sql
INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), d.id, 'DOCUMENT_TEMPLATE', 'DO_FR_100'
FROM workflow_definition d
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 1;
```

> ⚠️ **No hacerlo de verdad hoy**: `NOVEDAD_NOTAS` emitiría el formato equivocado, porque su
> papel oficial es otro. El ejemplo muestra el mecanismo, no una acción recomendada.

Y si se declara un formato que ningún renderer implementa, la respuesta es **500**, no 404:
es configuración rota, y devolver «no tiene documento» la escondería.

```sql
-- para ver el 500, y revertir después
UPDATE workflow_parameter SET parameter_value = 'FORMATO_QUE_NO_EXISTE'
WHERE parameter_key = 'DOCUMENT_TEMPLATE';
```

## Apagar

```bash
kill %1          # o cerrar el proceso de fondo
docker stop tramita-postgres
```
