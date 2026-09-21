# Quickstart — Bandeja de trabajo de la coordinación (007)

Cómo comprobar la feature contra un servidor real. No reemplaza la suite: comprueba el
cableado, que es lo que los tests con mocks no ven.

## 0. Levantar

```bash
docker start tramita-postgres && docker ps          # publica en el 5433 del host, no en el 5432
set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Iniciar sesión y guardar la cookie:

```bash
curl -s -c /tmp/tramita.jar -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"coordinacion.cali@uniremington.edu.co","password":"<la del entorno>"}'
```

---

## 1. El catálogo trae los estados

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/workflow-definitions | jq
```

Cada definición debe traer ahora su lista de `states`, y cada estado sus marcas:

```json
[ { "code": "ADICION_CREDITOS", "name": "Adición de créditos", "version": 1,
    "states": [
      { "code": "EN_COORDINACION", "name": "En coordinación", "isInitial": true,  "isFinal": false },
      { "code": "FINALIZADA",      "name": "Finalizada",      "isInitial": false, "isFinal": true  } ] } ]
```

**Lo que hay que mirar, y es el punto de la decisión del issue #22**: que `isInitial` sea
`true` en un estado **distinto** para cada trámite. `ADICION_CREDITOS` arranca en
`EN_COORDINACION` y `NOVEDAD_NOTAS` en `REGISTRADA`:

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/workflow-definitions \
  | jq -r '.[] | "\(.code): inicial = \(.states[] | select(.isInitial) | .code)"'
# ADICION_CREDITOS: inicial = EN_COORDINACION
# NOVEDAD_NOTAS: inicial = REGISTRADA
```

Si los dos dieran el mismo código, una constante global en el cliente habría funcionado y la
feature no haría falta. Dan distinto: por eso el cliente no puede adivinarlo.

**Y que el cambio es aditivo**: `code`, `name` y `version` siguen ahí, con el mismo
significado (FR-011c).

---

## 2. La bandeja lista lo que espera a un responsable

```bash
curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests/inbox?responsible=COORDINACION' | jq
```

Cada entrada trae `waitingSince`, `pendingResponsible` y `origin`, y **ninguna trae
`studentDocument`**. Comprobarlo explícitamente, porque es el invariante del DTO:

```bash
curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests/inbox?responsible=COORDINACION' \
  | jq 'map(has("studentDocument")) | any'
# → false   (si alguna vez da true, es una regresión de minimización, §III)
```

---

## 3. Lo que espera a otra área no aparece acá

Avanzar una solicitud a la facultad y comprobar que **sale** de la bandeja de la Coordinación
y **entra** en la de la facultad:

```bash
ID=<id de una solicitud en el estado inicial>
curl -s -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests/$ID/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(grep XSRF /tmp/tramita.jar | awk '{print $7}')" \
  -d '{"toState":"EN_FACULTAD"}'

curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests/inbox?responsible=COORDINACION' \
  | jq --arg id "$ID" 'map(.id == $id) | any'    # → false
curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests/inbox?responsible=FACULTAD' \
  | jq --arg id "$ID" 'map(.id == $id) | any'    # → true
```

Esto es lo que ninguna prueba con mocks demuestra: que el responsable sale de la configuración
real y no de un supuesto.

---

## 4. Un trámite cerrado no aparece en ninguna bandeja

Llevar una solicitud hasta un estado final y consultar **todos** los responsables:

```bash
for R in COORDINACION FACULTAD REGISTRO_CALI REGISTRO_NACIONAL SEDE FINANCIERA; do
  echo -n "$R: "
  curl -s -b /tmp/tramita.jar "http://localhost:8080/api/requests/inbox?responsible=$R" \
    | jq --arg id "$ID" 'map(.id == $id) | any'
done
# todas → false
```

No hace falta un filtro de «cerradas»: de un estado final no sale ninguna transición, así que
queda fuera por construcción.

---

## 5. La espera se cuenta desde la última transición, no desde la radicación

Es la decisión D3 y la más fácil de romper sin darse cuenta. Tomar una solicitud **antigua**,
devolverla y comprobar que su `waitingSince` se **reinicia** mientras su `createdAt` no cambia:

```bash
curl -s -b /tmp/tramita.jar "http://localhost:8080/api/requests/inbox?responsible=COORDINACION" \
  | jq --arg id "$ID" '.[] | select(.id == $id) | {createdAt, waitingSince}'
```

`createdAt` debe seguir siendo el original y `waitingSince` debe ser reciente. Si los dos
coinciden siempre, el cálculo está mirando la fila de la solicitud en lugar del timeline.

**Y el orden**: la primera de la lista debe ser la de `waitingSince` más antiguo.

```bash
curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests/inbox?responsible=COORDINACION' \
  | jq -r '[.[].waitingSince] | . as $x | ($x == ($x | sort))'
# → true   (ascendente: primero lo que más espera)
```

---

## 6. Una etiqueta inexistente responde vacío, no error

```bash
curl -s -b /tmp/tramita.jar -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8080/api/requests/inbox?responsible=NO_EXISTE'
# → 200, con lista vacía
```

Un `404` filtraría qué etiquetas existen, y además no hay forma de distinguir una etiqueta
inventada de un área real sin nada pendiente.

---

## 7. Sin sesión, nada

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8080/api/requests/inbox?responsible=COORDINACION'
# → 401
```

---

## Al terminar

```bash
docker stop tramita-postgres      # solo si lo levantaste vos
```
