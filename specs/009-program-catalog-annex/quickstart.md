# Quickstart — Catálogo de programas y anexo exigido por programa (009)

Cómo comprobar la feature contra un servidor real. No reemplaza la suite: comprueba el
cableado —la seguridad del endpoint abierto, la resolución entre los dos manejadores de errores, la
siembra real— que los tests con mocks no ven. Y demuestra lo central del diseño: que **un programa
y una regla nuevos entran por SQL, sin reiniciar ni desplegar**.

⚠️ **Estado de este documento**: se escribe en la fase de plan, **antes de que exista el código**.
Las salidas que figuran abajo son **expectativas** derivadas del contrato
(`contracts/openapi.yaml`) y de los builders de error vigentes, no observaciones. Se recorre contra
Tomcat real al cerrar la implementación y se corrige lo que difiera, como hizo la 008.

## 0. Levantar

```bash
docker start tramita-postgres && docker ps          # publica en el 5433 del host, no en el 5432
set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Flyway aplica `V5.0.0` y `V5.1.0` al arrancar. Comprobarlo sin la aplicación:

```bash
docker exec tramita-postgres psql -U postgres -d tramita-db -c \
  "SELECT version, description, success FROM flyway_schema_history WHERE version LIKE '5.%' ORDER BY installed_rank;"
# 5.0.0 | Create program catalog and annex rules | t
# 5.1.0 | Seed program catalog and annex rules   | t
```

Iniciar sesión y guardar la cookie. El login **exige el token CSRF** (patrón SPA de la 001):

```bash
curl -s -o /dev/null -c /tmp/tramita.jar http://localhost:8080/api/auth/me   # 401, pero emite XSRF-TOKEN
XSRF="$(grep XSRF /tmp/tramita.jar | awk '{print $7}')"
curl -s -b /tmp/tramita.jar -c /tmp/tramita.jar -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d "{\"email\":\"$SEED_COORD_EMAIL\",\"password\":\"$SEED_COORD_PASSWORD\"}"   # las dos salen del .env ya cargado
# → 204
```

Un ayudante para armar el formato público con `jq`, que conserva los bytes de cada cadena —tildes y
espacios incluidos— sin pelear con las comillas de la terminal:

```bash
formato() {   # $1 = programa, $2 = documento (máximo 20 caracteres)
  jq -n --arg p "$1" --arg d "$2" '{studentName:"Estudiante De Prueba", studentDocument:$d,
    studentEmail:"estudiante.de.prueba@ejemplo.test", studentPhone:"3000000009", program:$p,
    campus:"Cali", faculty:"Facultad de Ingeniería", modality:"Distancia", semester:"5",
    reason:"Necesito adicionar una asignatura del siguiente nivel.",
    signature:"data:image/png;base64,iVBORw0KGgo="}'
}
publico() {   # $1 = programa, $2 = documento
  curl -s -X POST http://localhost:8080/api/public/requests/ADICION_CREDITOS \
    -H 'Content-Type: application/json' -d "$(formato "$1" "$2")"
}
```

Este recorrido hace ocho envíos públicos; el límite es de 20 por origen cada 15 minutos
(`application.yml:38-40`, `app.public-capture`).

---

## 1. La lista de programas, sin sesión (FR-001, SC-004)

```bash
curl -s http://localhost:8080/api/public/programs | jq -c 'length, (map(keys) | add | unique)'
# 13
# ["name"]
```

**Lo que hay que mirar**: trece elementos —si la base de desarrollo no tiene programas agregados a
mano— y **una sola clave**, `name`: ni `id`, ni conteos. Sin cookie y sin cabecera CSRF.

```bash
curl -s http://localhost:8080/api/public/programs | jq -r '.[].name'
# Los trece de la spec, en orden alfabético según la intercalación de la base (en_US.utf8 en
# tramita-postgres, research D5). Los cuatro «Ingeniería …» salen Ambiental, de Sistemas,
# en Seguridad y Salud en el Trabajo, Industrial.
```

---

## 2. El canal público acepta un programa del catálogo (US1, escenario 2)

El nombre se toma **de la propia lista**, como lo hará el cliente, sin reescribirlo:

```bash
PROG=$(curl -s http://localhost:8080/api/public/programs | jq -r '.[] | select(.name == "Ingeniería de Sistemas") | .name')
publico "$PROG" SIN-DATO-REAL-P91 | jq -c .
# {"message":"Tu solicitud llegó a la Coordinación."}     (201)
```

---

## 3. Lo que no está en el catálogo se rechaza nombrando el campo, sin eco (FR-002, FR-004)

Un programa que no está, y tres variantes de uno que sí —cada una difiere en **una sola**
dimensión: mayúsculas, tilde, espacio final (research D10)—:

```bash
for P in 'Psicología' 'ingeniería de sistemas' 'Ingenieria de Sistemas' 'Ingeniería de Sistemas '; do
  echo -n "«$P» → "
  publico "$P" SIN-DATO-REAL-P92 | jq -c '{status, title, invalidFields, missingFields}'
done
# cada uno → {"status":422,"title":"Formato inválido","invalidFields":["program"],"missingFields":[]}
```

**Lo que hay que mirar**: el `detail` nombra `program` y **no repite el valor**. Se comprueba con
`jq` y no con `grep`, porque en esta máquina `grep` es una función de shell que da falsos negativos
con tildes (y un falso negativo acá haría pasar el chequeo):

```bash
publico 'Psicología' SIN-DATO-REAL-P93 | jq -c '{detail, eco: (tostring | contains("Psicología"))}'
# {"detail":"El formato tiene campos con un valor que no se puede procesar. Revise estos campos: program","eco":false}
```

Y en blanco cae en `missingFields`, **solo ahí**, como antes de esta feature (la ausencia domina):

```bash
publico '   ' SIN-DATO-REAL-P94 | jq -c '{status, title, invalidFields, missingFields}'
# {"status":422,"title":"Formato incompleto","invalidFields":[],"missingFields":["program"]}
```

---

## 4. El canal interno: opcional, y si viene, del catálogo (FR-003)

```bash
XSRF="$(grep XSRF /tmp/tramita.jar | awk '{print $7}')"
interno() {   # $1 = cuerpo JSON
  curl -s -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests \
    -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" -d "$1"
}

interno '{"definitionCode":"ADICION_CREDITOS","studentName":"Sin Programa","studentDocument":"SIN-DATO-REAL-C91"}' \
  | jq -c '{program: has("program"), annex: has("annexRequirement")}'
# {"program":false,"annex":false}          (201: sin programa se acepta, US1 escenario 5)

interno "$(jq -n '{definitionCode:"ADICION_CREDITOS", studentName:"Programa Ajeno",
                   studentDocument:"SIN-DATO-REAL-C92", program:"Psicología"}')" \
  | jq -c '{status, title, invalidFields, missingFields, detail}'
# {"status":400,"title":"Petición inválida","invalidFields":["program"],"missingFields":[],
#  "detail":"El cuerpo de la petición tiene campos con un valor inválido. Campos: program"}
```

Es 400 y no 422: la convención del canal interno para un campo fuera de contrato
(`GlobalExceptionHandler`). Y una cadena vacía también es 400, porque «vino»:

```bash
interno '{"definitionCode":"ADICION_CREDITOS","studentName":"Programa Vacio","studentDocument":"SIN-DATO-REAL-C93","program":""}' \
  | jq -c '{status, invalidFields}'
# {"status":400,"invalidFields":["program"]}
```

---

## 5. El detalle trae el requisito desde el registro (US2, escenarios 1 y 2)

La solicitud del paso 2 es de Ingeniería de Sistemas. El canal público no devuelve el
identificador (004): se localiza por documento con la sesión.

```bash
ID_SIS=$(curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests?search=SIN-DATO-REAL-P91' | jq -r '.[0].id')
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_SIS \
  | jq -c '{program, state: .currentState.code, annexRequirement}'
# {"program":"Ingeniería de Sistemas","state":"EN_COORDINACION",
#  "annexRequirement":{"documentName":"Hoja de vida académica","sourceHint":"La descarga el estudiante desde CLASS"}}
```

Otra del mismo trámite y otro programa **no** trae nada:

```bash
publico 'Derecho' SIN-DATO-REAL-P95 > /dev/null
ID_DER=$(curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests?search=SIN-DATO-REAL-P95' | jq -r '.[0].id')
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_DER | jq -c '{program, annex: has("annexRequirement")}'
# {"program":"Derecho","annex":false}
```

---

## 6. Al llevarla a la facultad, la misma respuesta lo trae (US2 escenario 3, SC-006)

Desde el estado inicial, `EN_FACULTAD` está a un avance y no exige observación:

```bash
curl -s -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests/$ID_SIS/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" -d '{"targetStateCode":"EN_FACULTAD"}' \
  | jq -c '{state: .currentState.code, annexRequirement}'
# {"state":"EN_FACULTAD","annexRequirement":{"documentName":"Hoja de vida académica","sourceHint":"La descarga el estudiante desde CLASS"}}
```

Si para verlo hiciera falta un segundo `GET`, SC-006 no se cumple. Y el requisito **no depende del
estado**: sigue ahí en `EN_FACULTAD` como estaba en `EN_COORDINACION` (FR-010).

---

## 7. Un trámite sin reglas no produce requisito (US2, escenario 4)

La novedad de notas entra sin reglas, sea cual sea el programa:

```bash
interno "$(jq -n '{definitionCode:"NOVEDAD_NOTAS", studentName:"Novedad De Sistemas",
                   studentDocument:"SIN-DATO-REAL-N91", program:"Ingeniería de Sistemas"}')" \
  | jq -c '{definition: .definition.code, program, annex: has("annexRequirement")}'
# {"definition":"NOVEDAD_NOTAS","program":"Ingeniería de Sistemas","annex":false}
```

---

## 8. Lo ya radicado no se toca (US1 escenario 7, US2 escenario 6, FR-005)

La base de desarrollo tiene solicitudes anteriores con el programa escrito a mano (la spec midió
tres «Ing» y dos «Sistemas»). Una de ellas:

```bash
ID_VIEJA=$(docker exec tramita-postgres psql -U postgres -d tramita-db -Atc \
  "SELECT id FROM request WHERE program IN ('Ing', 'Sistemas') LIMIT 1;")
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_VIEJA | jq -c '{program, annex: has("annexRequirement")}'
# {"program":"Sistemas","annex":false}      (o "Ing": el que haya; tal como se escribió)
```

Se consulta sin error, el programa sale **tal como se escribió** y no hay requisito. Que también se
pueda mover de estado lo prueba la suite (research D10), porque acá depende del estado en que esté
esa fila.

---

## 9. Un programa y una regla nuevos, sin reiniciar ni desplegar (FR-006, FR-011, SC-003)

Es la tesis del §VI sobre esta feature. Con el servidor **corriendo**, se configura por SQL una
regla de demostración para **otro trámite** (la novedad de notas) y un programa existente:

```bash
docker exec -it tramita-postgres psql -U postgres -d tramita-db
```

```sql
INSERT INTO workflow_annex_rule (id, definition_id, program_id, document_name, source_hint)
SELECT gen_random_uuid(), d.id, p.id, 'Documento de demostración', 'Solo para el quickstart de la 009'
FROM workflow_definition d
JOIN academic_program p ON p.name = 'Enfermería'
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 1;
-- INSERT 0 1
```

Sin reiniciar, una solicitud nueva de ese trámite y ese programa ya lo trae:

```bash
interno "$(jq -n '{definitionCode:"NOVEDAD_NOTAS", studentName:"Demostracion Configurable",
                   studentDocument:"SIN-DATO-REAL-N92", program:"Enfermería"}')" \
  | jq -c '{definition: .definition.code, annexRequirement}'
# {"definition":"NOVEDAD_NOTAS","annexRequirement":{"documentName":"Documento de demostración","sourceHint":"Solo para el quickstart de la 009"}}
```

Y un programa nuevo aparece en la lista en la petición siguiente:

```sql
INSERT INTO academic_program (id, name) VALUES (gen_random_uuid(), 'Programa De Demostración');
-- INSERT 0 1
```

```bash
curl -s http://localhost:8080/api/public/programs | jq 'map(.name) | index("Programa De Demostración") != null'
# true
```

**La base impide una regla huérfana (FR-008)**: con la regla de demostración todavía puesta,
quitar su programa falla:

```sql
DELETE FROM academic_program WHERE name = 'Enfermería';
-- ERROR:  update or delete on table "academic_program" violates foreign key constraint "…" on table "workflow_annex_rule"
```

Limpieza, en orden —primero la regla, después el programa de demostración—:

```sql
DELETE FROM workflow_annex_rule WHERE document_name = 'Documento de demostración';
DELETE FROM academic_program WHERE name = 'Programa De Demostración';
\q
```

**Y una consecuencia que conviene ver con los propios ojos** (research D6): la solicitud
`SIN-DATO-REAL-N92` sigue existiendo —es imborrable por el trigger del timeline—, pero como el
requisito se deriva y no se guarda, ahora su detalle **ya no lo trae**:

```bash
ID_DEMO=$(curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests?search=SIN-DATO-REAL-N92' | jq -r '.[0].id')
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/$ID_DEMO | jq 'has("annexRequirement")'
# false
```

---

## 10. El código sigue sin nombrar trámites ni estados

```bash
git grep -nE '"(FINALIZADA|DEVUELTA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' HEAD -- 'src/main/java/*.java'
# → una sola línea, y es el rótulo impreso del papel (DoFr100Renderer)
```

La regla de Ingeniería de Sistemas vive en `V5.1.0`, no en Java. Si apareciera una segunda línea,
alguien reconoció un trámite por su código para decidir el anexo.

---

## 11. Sin sesión: la lista sí, el detalle no

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/public/programs
# → 200
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/requests/$ID_SIS
# → 401
```

El catálogo es público; el requisito, como todo el detalle, solo lo ve una sesión de la
Coordinación.

---

## Al terminar

```bash
docker stop tramita-postgres      # solo si lo levantaste vos
```

Las solicitudes de prueba quedan en la base de desarrollo: son imborrables por el trigger del
timeline (§VII), y por eso llevan documentos `SIN-DATO-REAL-*` y correos `@ejemplo.test`. La regla y
el programa de demostración del paso 9 **sí** se borran: son configuración, sin trigger.
