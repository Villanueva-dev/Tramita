# Quickstart — Aviso de cierre al estudiante (008)

Cómo comprobar la feature contra un servidor real. No reemplaza la suite: comprueba el
cableado, que es lo que los tests con mocks no ven. Y demuestra lo central del diseño: que con
lo que el backend devuelve, **el aviso se arma sin una segunda consulta y sin que el servidor
sepa que existe**.

**Recorrido completo el 2026-09-24 contra Tomcat real, sobre `813113d`**: los diez pasos dieron
las salidas que figuran abajo. La única corrección fue de este documento: los identificadores
`SIN-DATO-REAL-<teléfono>` tenían 24 caracteres y `studentDocument` admite 20
(`PublicRequestBody:39`), así que el paso 1 respondía 422 por el documento y no por el teléfono.

## 0. Levantar

```bash
docker start tramita-postgres && docker ps          # publica en el 5433 del host, no en el 5432
set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Iniciar sesión y guardar la cookie. El login **exige el token CSRF** (patrón SPA de la 001):
primero un GET cualquiera para que el servidor emita la cookie `XSRF-TOKEN`, y después el POST
con su valor en la cabecera:

```bash
curl -s -o /dev/null -c /tmp/tramita.jar http://localhost:8080/api/auth/me   # 401, pero emite XSRF-TOKEN
XSRF="$(grep XSRF /tmp/tramita.jar | awk '{print $7}')"
curl -s -b /tmp/tramita.jar -c /tmp/tramita.jar -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d "{\"email\":\"$SEED_COORD_EMAIL\",\"password\":\"$SEED_COORD_PASSWORD\"}"   # las dos salen del .env ya cargado
# → 204
```

---

## 1. El canal público exige diez dígitos, y lo dice nombrando el campo

Es FR-009, y es lo único **no aditivo** de la feature. Primero lo que rechaza:

```bash
for TEL in '300 123 4567' '+57 3001234567' '300123456' '30012345678'; do
  echo -n "$TEL → "
  curl -s -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
    -H 'Content-Type: application/json' \
    -d "{\"studentName\":\"Estudiante De Prueba\",\"studentDocument\":\"SIN-DATO-REAL-008\",
         \"studentEmail\":\"estudiante.de.prueba@ejemplo.test\",\"studentPhone\":\"$TEL\",
         \"program\":\"Ingeniería de Sistemas\",\"campus\":\"Cali\",\"faculty\":\"Facultad de Ingeniería\",
         \"modality\":\"Distancia\",\"semester\":\"5\",
         \"reason\":\"Necesito adicionar una asignatura del siguiente nivel.\",
         \"signature\":\"data:image/png;base64,iVBORw0KGgo=\"}" \
    | jq -c '{status, title, invalidFields, missingFields}'
done
# cada uno → {"status":422,"title":"Formato inválido","invalidFields":["studentPhone"],"missingFields":[]}
```

**Lo que hay que mirar**: el `detail` nombra `studentPhone` y **no repite el valor** enviado.
Comprobarlo sobre el peor caso, el del prefijo:

```bash
curl -s -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
  -H 'Content-Type: application/json' -d '{ ..., "studentPhone": "+57 3001234567", ... }' \
  | jq -r .detail | grep -c '3001234567'
# → 0
```

Y en blanco cae en `missingFields`, **solo ahí** (la ausencia domina, `ValidationFields`):

```bash
# el mismo cuerpo con "studentPhone": "   "
# → {"status":422,"title":"Formato incompleto","invalidFields":[],"missingFields":["studentPhone"]}
```

Después lo que acepta: un móvil **y también un fijo** (FR-012). Se radican dos solicitudes que
sirven para los pasos siguientes. Los números son sintéticos a simple vista (`3000000001`,
`6020000001`: cumplen la forma y no son de nadie) y los documentos cortos, porque
`studentDocument` admite 20 caracteres:

```bash
for PAR in 3000000001:M08 6020000001:F08; do TEL=${PAR%:*}; DOC=SIN-DATO-REAL-${PAR#*:}
  curl -s -o /dev/null -w "$TEL ($DOC) → %{http_code}\n" -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
    -H 'Content-Type: application/json' \
    -d "{\"studentName\":\"Estudiante Tel $TEL\",\"studentDocument\":\"$DOC\",
         \"studentEmail\":\"estudiante.$TEL@ejemplo.test\",\"studentPhone\":\"$TEL\",
         \"program\":\"Ingeniería de Sistemas\",\"campus\":\"Cali\",\"faculty\":\"Facultad de Ingeniería\",
         \"modality\":\"Distancia\",\"semester\":\"5\",
         \"reason\":\"Necesito adicionar una asignatura del siguiente nivel.\",
         \"signature\":\"data:image/png;base64,iVBORw0KGgo=\"}"
done
# 3000000001 (SIN-DATO-REAL-M08) → 201
# 6020000001 (SIN-DATO-REAL-F08) → 201
```

El canal público no devuelve el identificador (004): se localizan por documento con la sesión.

```bash
ID_MOVIL=$(curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests?search=SIN-DATO-REAL-M08' | jq -r '.[0].id')
ID_FIJO=$(curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests?search=SIN-DATO-REAL-F08' | jq -r '.[0].id')
```

---

## 2. En un estado intermedio no hay aviso, pero los hechos ya viajan

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_MOVIL \
  | jq '{origin, isFinal: .currentState.isFinal, studentEmail, studentPhone}'
# {"origin":"PUBLIC_LINK","isFinal":false,"studentEmail":"estudiante.3000000001@ejemplo.test","studentPhone":"3000000001"}
```

`origin` es `PUBLIC_LINK` porque la radicó la cuenta del portal; `isFinal` es `false` porque
está en el estado inicial. El cliente, con esos dos, **no** ofrece nada (FR-003a). Y el
teléfono sale **tal como entró** (FR-011).

---

## 3. La respuesta de la transición a un estado final ya trae todo (US1, escenario 2)

Desde el estado inicial, `RECHAZADA` es final y está a dos avances (`EN_COORDINACION →
EN_FACULTAD → RECHAZADA`, `V2.1.0`). El segundo `POST` es el momento exacto en que el aviso
puede ofrecerse, y su **propia respuesta** debe bastar:

```bash
XSRF="$(grep XSRF /tmp/tramita.jar | awk '{print $7}')"
curl -s -o /dev/null -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests/$ID_MOVIL/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" -d '{"targetStateCode":"EN_FACULTAD"}'
curl -s -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests/$ID_MOVIL/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" -d '{"targetStateCode":"RECHAZADA"}' \
  | jq '{origin, state: .currentState.name, isFinal: .currentState.isFinal, studentEmail, studentPhone, availableTransitions}'
# {"origin":"PUBLIC_LINK","state":"Rechazada","isFinal":true,
#  "studentEmail":"estudiante.3000000001@ejemplo.test","studentPhone":"3000000001","availableTransitions":[]}
```

Si para saber el origen hiciera falta un segundo `GET`, FR-008 no se cumple.

---

## 4. El aviso se arma con lo que ya vino — y solo con eso

Es la demostración de D5: el servidor no sabe qué es un aviso, y aun así el cliente lo tiene
listo en una sola acción (SC-001). El `jq` de abajo hace lo que hará el front: aplica la regla,
compone el texto con **nombre, trámite y estado** (FR-005) y arma los dos enlaces. Los saltos de
línea salen como `%0D%0A` (RFC 6068 §5) porque `@uri` percent-codifica el `\r\n`:

```bash
ENLACES=$(curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_MOVIL | jq -r '
  if .origin != "PUBLIC_LINK" or (.currentState.isFinal | not) then "sin aviso" else
    ("Hola \(.studentName).\r\nSu trámite «\(.definition.name)» quedó en estado: \(.currentState.name)." | @uri) as $texto
    | "mailto:\(.studentEmail)?subject=\("Trámite \(.definition.name)" | @uri)&body=\($texto)",
      (if (.studentPhone // "" | test("^3[0-9]{9}$"))
       then "https://wa.me/57\(.studentPhone)?text=\($texto)"
       else "sin WhatsApp: el teléfono no es un móvil colombiano" end)
  end')
echo "$ENLACES"
# mailto:estudiante.3000000001@ejemplo.test?subject=Tr%C3%A1mite%20Adici%C3%B3n%20de%20cr%C3%A9ditos&body=Hola%20Estudiante%20Tel%203000000001.%0D%0ASu%20tr%C3%A1mite%20%C2%ABAdici%C3%B3n%20de%20cr%C3%A9ditos%C2%BB%20qued%C3%B3%20en%20estado%3A%20Rechazada.
# https://wa.me/573000000001?text=Hola%20Estudiante%20Tel%203000000001.%0D%0ASu%20tr%C3%A1mite%20%C2%ABAdici%C3%B3n%20de%20cr%C3%A9ditos%C2%BB%20qued%C3%B3%20en%20estado%3A%20Rechazada.
```

**Lo que hay que mirar**:

- El texto dice **«Rechazada»**, no «aprobada» (FR-005a, US1 escenario 4).
- En los enlaces **no aparece** el documento, el programa ni el motivo (SC-003). El detalle
  sí trae el documento; es el cliente quien no lo pone:

```bash
DOC=$(curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_MOVIL | jq -r .studentDocument)
echo "$ENLACES" | grep -c "$DOC"
# → 0
```

---

## 5. Un fijo tiene correo pero no WhatsApp (US2, escenario 2)

Llevar `$ID_FIJO` a `RECHAZADA` igual que en el paso 3 y repetir el `jq` del paso 4:

```
mailto:estudiante.6020000001@ejemplo.test?subject=Tr%C3%A1mite%20...&body=Hola%20Estudiante%20Tel%206020000001.%0D%0A...Rechazada.
sin WhatsApp: el teléfono no es un móvil colombiano
```

El backend devolvió el teléfono igual que en el otro caso; la diferencia la puso el cliente
(FR-002, FR-012).

---

## 6. Lo que registra la Coordinación no ofrece aviso (FR-004)

```bash
XSRF="$(grep XSRF /tmp/tramita.jar | awk '{print $7}')"
curl -s -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"definitionCode":"ADICION_CREDITOS","studentName":"Registrada Por Coordinacion","studentDocument":"SIN-DATO-REAL-C08"}' \
  | jq '{origin, has_email: has("studentEmail"), has_phone: has("studentPhone")}'
# { "origin": "COORDINATION", "has_email": false, "has_phone": false }
```

`origin` es `COORDINATION` y, como no se declaró contacto, las claves **no viajan** (`NON_NULL`):
no hay `null` ni cadenas vacías inventadas.

**Y el canal interno también valida el teléfono si viene** (FR-010):

```bash
curl -s -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"definitionCode":"ADICION_CREDITOS","studentName":"Con Tel Malo","studentDocument":"SIN-DATO-REAL-C09","studentPhone":"300 123 4567"}' \
  | jq -c '{status, invalidFields}'
# {"status":400,"invalidFields":["studentPhone"]}
```

---

## 7. La búsqueda y la bandeja siguen sin contacto (§III)

Es el invariante que esta feature **no** rompe: el contacto se expone en el detalle de una
solicitud concreta, nunca en un listado.

```bash
curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests?search=SIN-DATO-REAL' \
  | jq 'map(has("studentEmail") or has("studentPhone")) | any'
# → false
curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests/inbox?responsible=COORDINACION&limit=200' \
  | jq 'map(has("studentEmail") or has("studentPhone") or has("studentDocument")) | any'
# → false   (si alguna vez da true, es una regresión de minimización)
```

---

## 8. Avisar no deja registro (SC-006)

No hay nada que «avisar» del lado del servidor, así que lo que se comprueba es que **consultar
el detalle no escribe**: el timeline tiene las mismas entradas antes y después.

```bash
ANTES=$(curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_MOVIL/timeline | jq length)
curl -s -o /dev/null -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_MOVIL
curl -s -o /dev/null -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_MOVIL
DESPUES=$(curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_MOVIL/timeline | jq length)
echo "$ANTES → $DESPUES"
# → 3 → 3   (radicación + dos transiciones; consultar dos veces no suma nada)
```

---

## 9. Un trámite nuevo por configuración ofrece el aviso igual (SC-005)

Es la tesis del §VI sobre esta feature. La suite lo prueba con el trámite `DEMO` que
`WorkflowGenericityIT` carga por SQL en runtime (`:112-130`, precedente de la 007): una
solicitud DEMO llevada a su estado final devuelve `currentState.isFinal = true` y, si nació por
el enlace público, `origin = PUBLIC_LINK`, sin que el código sepa que DEMO existe.

Contra el servidor real basta con comprobar que el código **no** nombra ningún estado:

```bash
git grep -nE '"(FINALIZADA|DEVUELTA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' HEAD -- 'src/main/java/*.java'
# → una sola línea, y es el rótulo impreso del papel (DoFr100Renderer)
```

Si apareciera una segunda, alguien reconoció un estado por su código para decidir el aviso.

---

## 10. Sin sesión, nada

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/requests/$ID_MOVIL
# → 401
```

El contacto del estudiante solo lo ve una sesión de la Coordinación.

---

## Al terminar

```bash
docker stop tramita-postgres      # solo si lo levantaste vos
```

Las solicitudes de prueba quedan en la base de desarrollo: son imborrables por el trigger del
timeline (§VII), y por eso llevan documentos `SIN-DATO-REAL-*` y correos `@ejemplo.test`.
