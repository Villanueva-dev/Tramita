# Quickstart — Formularios validados y reglas de negocio (003)

Verificación manual de la feature contra una instancia local. Complementa a los tests
automatizados; no los reemplaza.

## Arranque

```sh
docker start tramita-postgres && docker ps
set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

## Autenticación

Todas las operaciones exigen sesión (FR-021).

```sh
curl -s -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"<usuario>","password":"<clave>"}'
```

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

## 8. Las notas se validan contra el rango configurado (FR-012)

Enviar una asignatura con `proposedGrade` fuera del rango configurado.

**Esperado**: `422` indicando el rango admitido. Verificar además que la columna es numérica:

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "\d request_subject" | grep grade
```

**Esperado**: `numeric(3,2)`, no `character varying`.

## 9. Una transición sin guarda se comporta como en la 002 (FR-017)

Avanzar cualquier solicitud por una transición existente (todas quedan con `guard_key` nulo).

**Esperado**: mismo comportamiento que antes de esta feature.

## 10. Una guarda desconocida bloquea la transición (FR-019)

```sh
docker exec -i tramita-postgres psql -U postgres -d tramita-db \
  -c "UPDATE workflow_transition SET guard_key = 'REGLA_INEXISTENTE'
      WHERE id = (SELECT id FROM workflow_transition LIMIT 1);"
```

**Esperado**: esa transición responde `500` de configuración inválida y **no** altera el
estado de la solicitud. Nunca debe ejecutarse omitiendo la guarda que no supo evaluar.

Revertir con `UPDATE workflow_transition SET guard_key = NULL;`.

## Apagado

```sh
kill %1 %2
docker stop tramita-postgres
```

---

**Nota sobre los datos de este documento**: todos los identificadores de estudiante son
inequívocamente sintéticos (FR-022). No usar valores con forma de documento de identidad real,
ni siquiera inventados: el repositorio es público.
