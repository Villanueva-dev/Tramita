# Quickstart — Formularios validados y reglas de negocio (003)

Verificación manual de la feature contra una instancia local. Complementa a los tests
automatizados; no los reemplaza.

## Arranque

```sh
docker start tramita-postgres && docker ps
set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

> ⚠️ **Las contraseñas del `.env` van entre comillas simples.** `source` no lee el archivo como
> texto: lo ejecuta como shell, y por lo tanto expande `$`. Una clave que contenga `$$` se
> convierte en el PID del proceso —distinto en cada ejecución—, así que lo que llega a la
> aplicación **no es lo que dice el archivo**. Falla en silencio: el arranque es normal y el
> login rebota con `401`.
>
> ```
> SEED_COORD_PASSWORD='clave$$con$signos'   # comillas SIMPLES; las dobles no alcanzan
> ```
>
> Comprobarlo antes de sospechar de la base:
>
> ```sh
> RAW=$(rg -N '^SEED_COORD_PASSWORD=' .env | sed 's/^[^=]*=//')
> set -a; source .env; set +a
> echo "crudo ${#RAW} vs cargado ${#SEED_COORD_PASSWORD}"
> ```
>
> Descontando las dos comillas, las longitudes deben coincidir.

## Autenticación

Todas las operaciones exigen sesión (FR-021). El backend aplica CSRF *double-submit* (D4), así
que el login necesita **dos** peticiones: una que materialice la cookie `XSRF-TOKEN`, y el POST
que la devuelve en el header `X-XSRF-TOKEN`.

```sh
# 1. Materializa la cookie XSRF-TOKEN. Responde 401: es lo esperado, todavía no hay sesión.
curl -s -o /dev/null -c cookies.txt http://localhost:8080/api/auth/me

TOKEN=$(rg -N 'XSRF-TOKEN' cookies.txt | awk '{print $NF}')

# 2. Login
curl -s -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: $TOKEN" \
  -d "$(jq -n --arg e "$SEED_COORD_EMAIL" --arg p "$SEED_COORD_PASSWORD" \
        '{email:$e,password:$p}')"
```

**Esperado**: `204` sin cuerpo. Confirmar la sesión:

```sh
curl -s -b cookies.txt http://localhost:8080/api/auth/me
```

**Esperado**: `200` con el email de la cuenta.

> El campo es `email`, no `username`: así lo declara `LoginRequest`. Las credenciales son las
> de `SEED_COORD_EMAIL` / `SEED_COORD_PASSWORD` del `.env`.

### Diagnóstico de un `401`

Hay **dos** `401` distintos y el título del `problem+json` dice cuál es. No diagnosticar sin leerlo:

| Título | Origen | Significa |
|---|---|---|
| `"Autenticación requerida"` | entry point (`SecurityConfig`) | la petición **no llegó** al filtro de login |
| `"Credenciales inválidas"` | `AuthFailureHandler` | el filtro corrió y la credencial no validó |

- **`"Autenticación requerida"` en el POST del login** = falta el paso 1, o el header
  `X-XSRF-TOKEN`. El `CsrfFilter` responde `403`; ese `403` dispara un forward interno a `/error`,
  que no es `permitAll`, y el entry point lo convierte en `401`. **El síntoma miente: la causa
  es CSRF.**
- **`"Credenciales inválidas"`** = la clave no valida contra el hash. Comprobarlo sin HTTP antes
  de tocar la base —no consume intentos de login ni dispara el throttling (`429`)—:

  ```sh
  ./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
  export DB_HASH=$(docker exec -i tramita-postgres psql -U postgres -d tramita-db -tAc \
    "SELECT password_hash FROM users WHERE email='<correo>';")
  export PWD_LITERAL='<la clave, entre comillas SIMPLES>'
  printf '%s\n' \
    'import org.springframework.security.crypto.factory.PasswordEncoderFactories;' \
    'var enc = PasswordEncoderFactories.createDelegatingPasswordEncoder();' \
    'System.out.println("RESULTADO: " + enc.matches(System.getenv("PWD_LITERAL"), System.getenv("DB_HASH")));' \
    '/exit' > /tmp/check.jsh
  jshell --class-path "$(</tmp/cp.txt)" -s /tmp/check.jsh
  ```

  Si devuelve `true` y el login igual falla, el problema es la expansión del `.env` (ver Arranque).

> ⛔ **No borrar la fila de `users`.** `request_transition_log.actor_id` la referencia por FK, y
> el timeline es inmutable por trigger (`trg_timeline_immutable`, FR-007): las filas que sostienen
> esa FK no se pueden borrar. Desactivar el trigger para destrabar un login anularía justamente
> la trazabilidad que la feature 002 existe para garantizar.

## 1. El contrato anterior sigue funcionando (FR-006, SC-007)

```sh
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' \
  -d '{"definitionCode":"ADICION_CREDITOS","studentName":"Estudiante De Prueba","studentDocument":"DOC-TEST-0001"}'
```

**Esperado**: `201`, solicitud en el estado inicial, `subjects` vacío.

## 2. Registro con el formulario completo (FR-001, FR-002)

```sh
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' \
  -d '{
        "definitionCode": "ADICION_CREDITOS",
        "studentName": "Estudiante De Prueba",
        "studentDocument": "DOC-TEST-0001",
        "studentCode": "EST-0001",
        "program": "Ingeniería de Sistemas",
        "semester": "2026-2",
        "reason": "Requiere cursar una asignatura adicional para completar el plan.",
        "subjects": [
          {"code":"MAT-101","name":"Cálculo Diferencial","credits":3,"group":"G1"},
          {"code":"FIS-201","name":"Física I","credits":4,"group":"G2"}
        ]
      }'
```

**Esperado**: `201` con las dos asignaturas. **Verificar que la respuesta NO trae ningún campo
de correo** (FR-020).

## 3. El tope de créditos se aplica (FR-008)

Con `MAX_CREDITS = 21`, enviar asignaturas que sumen 22.

**Esperado**: `422` con `detail` indicando el límite. La solicitud **no** queda registrada.

## 3b. Qué trámites capturan créditos se configura, no se codifica (FR-009a)

`ADICION_CREDITOS` declara `CAPTURES_CREDITS = true`; `NOVEDAD_NOTAS` no lo declara, porque su
formato oficial no tiene columna de créditos.

**Omitir el dato no esquiva el tope** — una asignatura sin `credits` en un trámite que los captura:

```sh
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $TOKEN" \
  -d '{"definitionCode":"ADICION_CREDITOS","studentName":"Estudiante De Prueba",
       "studentDocument":"DOC-TEST-0001",
       "subjects":[{"code":"MAT-101","name":"Cálculo Diferencial"}]}'
```

**Esperado**: `422`. Antes de FR-009a esto respondía `201` y el tope no llegaba a evaluarse.

**Un dato de más es del cliente, no del servidor** — créditos en un trámite que no los captura:

```sh
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $TOKEN" \
  -d '{"definitionCode":"NOVEDAD_NOTAS","studentName":"Estudiante De Prueba",
       "studentDocument":"DOC-TEST-0001",
       "subjects":[{"code":"A-1","name":"Uno","credits":3,"proposedGrade":4.2}]}'
```

**Esperado**: `422` con «no captura créditos». Antes respondía `500`, culpando al servidor de un
dato de más de quien envía —y dejando un `log.error` por un error que no era del sistema—.

> El paso 5 sigue devolviendo `500` y eso es correcto: ahí el trámite **declara** capturar créditos
> y le falta su tope, que sí es configuración rota. Distinguir los dos casos es justamente el
> propósito de `CAPTURES_CREDITS`.

## 4. Los créditos negativos no burlan el tope (FR-009) — el caso que el prototipo permitía

```sh
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' \
  -d '{"definitionCode":"ADICION_CREDITOS","studentName":"Estudiante De Prueba",
       "studentDocument":"DOC-TEST-0001",
       "subjects":[{"code":"A-1","name":"Uno","credits":30},
                   {"code":"A-2","name":"Dos","credits":-20}]}'
```

**Esperado**: `400`. La suma daría 10 y pasaría el tope de 21; la validación de forma lo
rechaza antes. Verificar además que no se creó ninguna fila:

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "SELECT count(*) FROM request_subject WHERE credits <= 0;"
```

**Esperado**: `0`. El `CHECK` de base lo garantiza incluso ante escritura directa:

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "INSERT INTO request_subject (id, request_id, code, name, credits)
      VALUES (gen_random_uuid(), (SELECT id FROM request LIMIT 1), 'X', 'X', -5);"
```

**Esperado**: error de violación de restricción.

## 5. Configuración incompleta no se acepta en silencio (FR-010) — el otro caso del prototipo

Borrar el parámetro de un trámite y registrar una solicitud para él:

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "DELETE FROM workflow_parameter WHERE parameter_key = 'MAX_CREDITS';"
```

**Esperado**: la siguiente solicitud responde `500` con título «Configuración del trámite
incompleta», **sin detalle interno**, y no queda registrada. El comportamiento incorrecto que
esto reemplaza era responder `201` sin aplicar límite alguno.

Restaurar después:

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
      SELECT gen_random_uuid(), id, 'MAX_CREDITS', '21'
      FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 1;"
```

## 6. Un valor no interpretable se trata como configuración incompleta (FR-011)

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "UPDATE workflow_parameter SET parameter_value = 'veintiuno' WHERE parameter_key = 'MAX_CREDITS';"
```

**Esperado**: `500` de configuración incompleta. **No** debe comportarse como límite cero
—que rechazaría toda solicitud con un `422` culpando al usuario—.

## 7. Ajustar una regla sin desplegar (SC-005)

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "UPDATE workflow_parameter SET parameter_value = '24' WHERE parameter_key = 'MAX_CREDITS';"
```

**Esperado**: sin reiniciar la aplicación, una solicitud de 22 créditos que antes daba `422`
ahora responde `201`.

Restaurar después —si no, el tope queda en 24 y el paso 3 de la próxima corrida falla por este
residuo y no por un defecto real—:

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "UPDATE workflow_parameter SET parameter_value = '21' WHERE parameter_key = 'MAX_CREDITS';"
```

## 8. Las notas se validan contra el rango configurado (FR-012)

`NOVEDAD_NOTAS` tiene configurados `MIN_GRADE = 0.0` y `MAX_GRADE = 5.0`.

```sh
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $TOKEN" \
  -d '{"definitionCode":"NOVEDAD_NOTAS","studentName":"Estudiante De Prueba",
       "studentDocument":"DOC-TEST-0001",
       "subjects":[{"code":"A-1","name":"Uno","currentGrade":2.5,"proposedGrade":7.5}]}'
```

**Esperado**: `422` con `detail` = «Las notas deben estar entre 0.0 y 5.0».

> ⚠️ **No incluir `credits` en una solicitud de `NOVEDAD_NOTAS`.** Ese trámite no captura
> créditos y por eso no tiene `MAX_CREDITS` configurado; si se envían créditos, la regla se
> activa, exige el parámetro ausente y responde `500` de configuración incompleta. Es el
> comportamiento correcto (`RequestBusinessRulesImpl`: un parámetro solo se exige cuando la
> validación que lo usa aplica), pero se confunde fácil con un defecto.

Control con una nota válida (`4.2`): **esperado `201`**, y la nota vuelve como **número**, no
como string —`"proposedGrade":4.2`— que es el cambio de contrato de la decisión D3.

Verificar además que la columna es numérica:

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "\d request_subject" | rg grade
```

**Esperado**: `numeric(3,2)`, no `character varying`.

## 9. Una transición sin guarda se comporta como en la 002 (FR-017)

Avanzar una solicitud recién registrada por una transición existente (todas quedan con
`guard_key` nulo):

```sh
curl -s -b cookies.txt -X POST "http://localhost:8080/api/requests/$ID/transitions" \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $TOKEN" \
  -d '{"targetStateCode":"EN_PREPARACION"}'
```

**Esperado**: `200`, `currentState` actualizado, y una entrada nueva en el timeline —mismo
comportamiento que antes de esta feature.

## 10. Una guarda desconocida bloquea la transición (FR-019)

Marcar la transición **que se va a ejercitar**, no una cualquiera:

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "UPDATE workflow_transition SET guard_key = 'REGLA_INEXISTENTE'
      WHERE definition_id = (SELECT id FROM workflow_definition
                             WHERE code='ADICION_CREDITOS' AND version=1)
        AND from_state_id = (SELECT s.id FROM workflow_state s
                             JOIN workflow_definition d ON d.id = s.definition_id
                             WHERE d.code='ADICION_CREDITOS' AND d.version=1
                               AND s.code='REGISTRADA');"
```

> ⚠️ **No usar `WHERE id = (SELECT id FROM workflow_transition LIMIT 1)`.** Un `LIMIT` sin
> `ORDER BY` no es determinista: puede marcar una transición de otro trámite, la solicitud
> avanzaría con `200` y el paso daría un falso verde sin haber ejercitado ninguna guarda.

Intentar avanzar por esa transición.

**Esperado**: `500` de configuración inválida, **sin `detail` interno**, y la solicitud **sigue
en `REGISTRADA`**. Nunca debe ejecutarse omitiendo la guarda que no supo evaluar. Confirmarlo:

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "SELECT s.code FROM request r JOIN workflow_state s ON s.id = r.current_state_id
      WHERE r.id = '<id>';"
```

Revertir con `UPDATE workflow_transition SET guard_key = NULL;` y confirmar que quedan **0**
transiciones con guarda.

## Apagado

```sh
kill %1 %2
docker stop tramita-postgres
```

---

**Nota sobre los datos de este documento**: todos los identificadores de estudiante son
inequívocamente sintéticos (FR-022). No usar valores con forma de documento de identidad real,
ni siquiera inventados: el repositorio es público.
