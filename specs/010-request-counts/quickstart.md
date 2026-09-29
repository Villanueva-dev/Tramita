# Quickstart — Conteos de solicitudes por trámite y estado (010)

Cómo comprobar la feature contra un servidor real. No reemplaza la suite: comprueba el cableado
—la sesión que protege el endpoint, la consulta con su `left join` sobre la base real, la ruta que
compite con `/{id}`— que los tests con mocks no ven. Y demuestra lo central del diseño: que **un trámite
nuevo entra por SQL y aparece en los conteos sin reiniciar ni desplegar**.

> ⏳ **Estado de este recorrido: PENDIENTE.** La Fase A entrega spec, plan y contrato; el endpoint **no
> está implementado** (plan, *Fases*). Las salidas de abajo son **expectativas derivadas del contrato**,
> no observaciones: se corren al terminar la Fase B y ahí se marcan como recorridas, con su fecha y el
> commit. Lo único **medido hoy** es la línea base del paso 9 (§VI).
>
> Las cifras son **ilustrativas** donde dependen de la base local; lo que se comprueba son las
> **diferencias** antes/después, no los totales, porque la base de desarrollo acumula solicitudes de
> corridas anteriores.

> ⚠️ Desde `3869a34` (H-10, PR #61), `POST /api/requests/{id}/transitions` exige `fromStateCode`, el
> código del estado vigente que se vio antes de enviar (`400` si falta, `409` si ya no coincide). Los
> cuerpos de abajo lo llevan.

## 0. Levantar

```bash
docker start tramita-postgres && docker ps          # publica en el 5433 del host, no en el 5432
set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # puerto 8080
```

Flyway no aplica nada nuevo: esta feature no tiene migración. Comprobarlo:

```bash
docker exec tramita-postgres psql -U postgres -d tramita-db -Atc \
  "SELECT max(version) FROM flyway_schema_history WHERE success;"
# 5.2.0      (la misma de antes de la feature)
```

Iniciar sesión y guardar la cookie. El login **exige el token CSRF** (patrón SPA de la 001) y las
credenciales salen del `.env` ya cargado, sin escribirlas acá:

```bash
curl -s -o /dev/null -c /tmp/tramita.jar http://localhost:8080/api/auth/me   # 401, pero emite XSRF-TOKEN
XSRF="$(grep XSRF /tmp/tramita.jar | awk '{print $7}')"
curl -s -b /tmp/tramita.jar -c /tmp/tramita.jar -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d "{\"email\":\"$SEED_COORD_EMAIL\",\"password\":\"$SEED_COORD_PASSWORD\"}"
# → 204
```

Ayudantes. `cuenta` lee un conteo por código de trámite y de estado; `interno` radica por el canal
interno:

```bash
cuenta() {   # $1 = código del trámite, $2 = código del estado
  curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/counts \
    | jq --arg d "$1" --arg s "$2" '[.[] | select(.definitionCode == $d) | .states[] | select(.state.code == $s) | .count] | first'
}
interno() {  # $1 = cuerpo JSON
  curl -s -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests \
    -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" -d "$1"
}
```

---

## 1. Sin sesión: 401 y ninguna cifra (FR-001, US1 escenario 6)

```bash
curl -s -w '\n%{http_code}\n' http://localhost:8080/api/requests/counts
# {"type":"about:blank","title":"Autenticación requerida","status":401}   (application/problem+json; título en SecurityConfig.java:206)
# 401
```

**Lo que hay que mirar**: **401** y no 403 (el 403 de este backend es de las peticiones mutantes sin
CSRF), y un cuerpo `problem+json` **sin ningún número**.

---

## 2. Con sesión: los dos trámites con todos sus estados (FR-002, FR-003, SC-004)

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/counts \
  | jq -c '.[] | {definitionCode, definitionName, estados: (.states | length)}'
# {"definitionCode":"ADICION_CREDITOS","definitionName":"Adición de créditos","estados":8}
# {"definitionCode":"NOVEDAD_NOTAS","definitionName":"Novedad de notas","estados":7}
```

**Lo que hay que mirar**: los dos trámites, en orden de nombre (adición antes que novedad); **8 y 7
estados** (la versión vigente: 15 en total, con la v2 de la novedad, H-11), aunque varios traigan `count`
0. Si la base de desarrollo tiene otros trámites cargados a mano, saldrán además.

Un estado sin solicitudes **no se omite**:

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/counts \
  | jq -c '.[].states[] | select(.count == 0) | .state.code'
# (los estados que hoy no tienen solicitudes; en una base recién sembrada, casi todos)
```

`EN_FIRMA_SEDE`, que solo existe en la v2 de la novedad, tiene que estar:

```bash
cuenta NOVEDAD_NOTAS EN_FIRMA_SEDE
# 0   (o el número que haya; lo que importa es que NO sea null)
```

---

## 3. La suma por trámite es igual al número de solicitudes de ese trámite (SC-002)

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/counts \
  | jq -c '.[] | {definitionCode, suma: ([.states[].count] | add)}'
docker exec tramita-postgres psql -U postgres -d tramita-db -Atc \
  "SELECT d.code, count(*) FROM request r JOIN workflow_definition d ON d.id = r.definition_id GROUP BY d.code ORDER BY d.code;"
```

**Lo que hay que mirar**: por cada código, `suma` del endpoint = `count(*)` de la base. Vale aunque las
solicitudes estén repartidas entre la v1 y la v2 de la novedad, porque los conteos se suman por código.

---

## 4. Radicar y avanzar una adición cambia exactamente un conteo (US1 escenarios 2 y 3, SC-003)

Se mide antes y después. Es la prueba de que **una solicitud cuenta solo en su estado actual** y de que
la adición **no mezcla** su `EN_FACULTAD` con el de la novedad:

```bash
A0=$(cuenta ADICION_CREDITOS EN_COORDINACION); F0=$(cuenta ADICION_CREDITOS EN_FACULTAD); N0=$(cuenta NOVEDAD_NOTAS EN_FACULTAD)

ID=$(interno '{"definitionCode":"ADICION_CREDITOS","studentName":"Conteo De Prueba","studentDocument":"SIN-DATO-REAL-K91"}' | jq -r .id)

echo "radicada: EN_COORDINACION $A0 → $(cuenta ADICION_CREDITOS EN_COORDINACION)   (espera +1)"
echo "          EN_FACULTAD     $F0 → $(cuenta ADICION_CREDITOS EN_FACULTAD)   (espera +0)"
```

Ahora se lleva a la facultad:

```bash
curl -s -o /dev/null -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests/$ID/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"fromStateCode":"EN_COORDINACION","targetStateCode":"EN_FACULTAD"}'

echo "en facultad: EN_COORDINACION $(cuenta ADICION_CREDITOS EN_COORDINACION)   (vuelve a $A0)"
echo "             EN_FACULTAD     $F0 → $(cuenta ADICION_CREDITOS EN_FACULTAD)   (espera +1)"
echo "             NOVEDAD EN_FACULTAD $N0 → $(cuenta NOVEDAD_NOTAS EN_FACULTAD)   (espera +0: no se mezclan)"
```

**Lo que hay que mirar**: la solicitud **se mueve** de un conteo a otro (uno sube y el otro baja), y el
`EN_FACULTAD` de la **novedad no cambia**. Si subiera, dos trámites se estarían sumando (mutante «plegar
solo por código de estado»).

Un rechazo y un cierre, cada uno en su estado, sin rótulo del sistema (US1 escenario 4). Desde
`EN_FACULTAD` la adición puede rechazarse (`V2.1.0`, `EN_FACULTAD → RECHAZADA`):

```bash
R0=$(cuenta ADICION_CREDITOS RECHAZADA)
curl -s -o /dev/null -b /tmp/tramita.jar -X POST http://localhost:8080/api/requests/$ID/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"fromStateCode":"EN_FACULTAD","targetStateCode":"RECHAZADA"}'
echo "RECHAZADA $R0 → $(cuenta ADICION_CREDITOS RECHAZADA)   (espera +1)"
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/counts \
  | jq -c '.[] | select(.definitionCode=="ADICION_CREDITOS") | .states[] | select(.state.code=="RECHAZADA") | .state'
# {"code":"RECHAZADA","name":"Rechazada","isInitial":false,"isFinal":true}
```

`RECHAZADA` sale **marcada como final** y **sin ningún rótulo de «rechazada»** más allá de su propio
código y nombre: clasificarla es del cliente (FR-008).

---

## 5. Un parámetro se ignora (FR-006)

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/counts | md5sum
curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests/counts?responsible=COORDINACION' | md5sum
curl -s -b /tmp/tramita.jar 'http://localhost:8080/api/requests/counts?program=Derecho&limit=1' | md5sum
# los tres md5 iguales
```

**Lo que hay que mirar**: la respuesta es **byte a byte la misma**. Si un filtro cambiara algo, se abriría
la puerta que FR-006 cierra a propósito.

---

## 6. La respuesta no tiene datos personales (FR-009, SC-005)

El conjunto de claves es **exacto**:

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/counts \
  | jq -c '[.. | objects | keys] | add | unique'
# ["code","count","definitionCode","definitionName","isFinal","isInitial","name","state","states"]
```

**Lo que hay que mirar**: nueve claves y ninguna más —ni `id`, ni `studentName`, ni `studentDocument`, ni
`program`, ni `version`—. Y el documento de prueba del paso 4 no aparece en ninguna parte:

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/counts | jq -c 'tostring | contains("SIN-DATO-REAL-K91")'
# false
```

Se comprueba con `jq` y no con `grep`, porque en esta máquina `grep` es una función de shell que da falsos
negativos con tildes.

---

## 7. Un trámite nuevo, por SQL, sin reiniciar ni desplegar (FR-010, SC-006, US1 escenario 5)

Es la tesis del §VI sobre esta feature. Con el servidor **corriendo**, se carga un trámite de dos estados
sin solicitudes:

```bash
docker exec -it tramita-postgres psql -U postgres -d tramita-db
```

```sql
INSERT INTO workflow_definition (id, code, version, name, created_at)
VALUES (gen_random_uuid(), 'QS010', 1, 'Trámite de quickstart 010', now());

INSERT INTO workflow_state (id, definition_id, code, name, is_initial, is_final)
SELECT gen_random_uuid(), d.id, s.code, s.name, s.is_initial, s.is_final
FROM workflow_definition d,
     (VALUES ('ABIERTO', 'Abierto', TRUE, FALSE), ('CERRADO', 'Cerrado', FALSE, TRUE))
       AS s(code, name, is_initial, is_final)
WHERE d.code = 'QS010' AND d.version = 1;

INSERT INTO workflow_transition (id, definition_id, from_state_id, to_state_id, responsible, requires_note)
SELECT gen_random_uuid(), d.id, f.id, t.id, 'COORDINACION', FALSE
FROM workflow_definition d
JOIN workflow_state f ON f.definition_id = d.id AND f.code = 'ABIERTO'
JOIN workflow_state t ON t.definition_id = d.id AND t.code = 'CERRADO'
WHERE d.code = 'QS010' AND d.version = 1;
\q
```

Sin reiniciar, ya está en los conteos, con **todos** sus estados en 0:

```bash
curl -s -b /tmp/tramita.jar http://localhost:8080/api/requests/counts \
  | jq -c '.[] | select(.definitionCode == "QS010") | {definitionName, estados: [.states[] | {c: .state.code, n: .count}]}'
# {"definitionName":"Trámite de quickstart 010","estados":[{"c":"ABIERTO","n":0},{"c":"CERRADO","n":0}]}
```

Y su nombre empieza por «Trámite …»: ordena **después** de «Novedad de notas» (el mismo criterio que fija
el IT para no romper `WorkflowDefinitionControllerIT`).

Limpieza, en orden —transiciones, estados, definición—; es configuración, sin trigger:

```sql
DELETE FROM workflow_transition WHERE definition_id IN (SELECT id FROM workflow_definition WHERE code = 'QS010');
DELETE FROM workflow_state      WHERE definition_id IN (SELECT id FROM workflow_definition WHERE code = 'QS010');
DELETE FROM workflow_definition WHERE code = 'QS010';
```

---

## 8. Con sesión, la ruta no cae en `/{id}` (research D9)

Antes de la feature, con sesión, esta ruta respondía **400** (convertía «counts» a UUID). Ahora:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -b /tmp/tramita.jar http://localhost:8080/api/requests/counts
# 200
curl -s -o /dev/null -w '%{http_code}\n' -b /tmp/tramita.jar http://localhost:8080/api/requests/no-es-un-uuid
# 400      (el detalle por id sigue igual)
```

---

## 9. El código sigue sin nombrar trámites ni estados (§VI)

```bash
git grep -nE '"(FINALIZADA|DEVUELTA|RECHAZADA|ADICION_CREDITOS|NOVEDAD_NOTAS)"' -- 'src/main/java/*.java'
# → src/main/java/com/uniremington/api/tramita/service/impl/DoFr100Renderer.java:166:            "ADICION_CREDITOS", "Matrícula créditos adicionales");
```

✅ **Línea base MEDIDA el 2026-09-29 sobre `5f6936c`, antes de escribir código**: **una sola línea**,
`DoFr100Renderer.java:166`, el rótulo impreso del papel. Tras la Fase B tiene que dar **exactamente lo
mismo**. Si apareciera una segunda línea, alguien reconoció un trámite o un estado por su código para
calcular un conteo.

Y un solo sitio para la consulta:

```bash
git grep -n 'count(' -- 'src/main/java/*.java'
# hoy (2026-09-29): 0 líneas.  Tras la Fase B: exactamente la de IRequestRepo.
```

---

## Al terminar

```bash
docker stop tramita-postgres      # solo si lo levantaste vos
```

Las solicitudes de prueba quedan en la base de desarrollo: son imborrables por el trigger del timeline
(§VII), y por eso llevan documentos `SIN-DATO-REAL-*`. El trámite `QS010` del paso 7 **sí** se borra: es
configuración, sin trigger.
