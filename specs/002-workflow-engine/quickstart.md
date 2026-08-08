# Quickstart — Motor de workflow (002)

Cómo levantar el backend y recorrer el motor de punta a punta con `curl`, incluida la
demostración en vivo de SC-005 (trámite nuevo sin recompilar ni reiniciar). Asume el
perfil `dev` (cookie sin `Secure`, ver `001`).

## 1. Arranque

```bash
docker start tramita-postgres
set -a; source .env; set +a
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Flyway aplica `V2.0.0` (tablas + trigger) y `V2.1.0` (semilla de los dos trámites) al
arrancar.

## 2. Autenticarse (feature 001 — FR-012)

El login es un `POST`, o sea una mutación, y `csrf.spa()` está activo: **hay que traer
primero la cookie `XSRF-TOKEN`**. La emite cualquier respuesta del chain — en el SPA, el
GET inicial; acá, este `curl`. El orden no es opcional.

```bash
# 1) GET inicial: responde 401 (todavía no hay sesión) pero deja la cookie XSRF-TOKEN
curl -s -o /dev/null -c cookies.txt http://localhost:8080/api/workflow-definitions

# 2) El token CSRF sale del propio jar
XSRF=$(grep XSRF-TOKEN cookies.txt | awk '{print $7}')

# 3) Login con el token en el header → 204 No Content
curl -s -c cookies.txt -b cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"email":"<EMAIL_SEED>","password":"<PASSWORD_SEED>"}' -i
```

El login **no** rota el token (D4/JD3-004): el mismo `$XSRF` sirve para las mutaciones
que siguen.

Sin este paso, **todo** lo que sigue responde `401 problem+json` — son los escenarios
FR-012 de US1/US2/US3.

> ⚠️ **Un 401 acá puede no ser por la contraseña.** Si se saltea el paso 1 o falta el
> header `X-XSRF-TOKEN`, el rechazo es de CSRF, pero como todavía no hay sesión el
> `ExceptionTranslationFilter` deriva al `AuthenticationEntryPoint` en lugar del
> `AccessDeniedHandler`: la respuesta es **401 «Autenticación requerida», no 403**. Antes
> de sospechar de las credenciales, verificar que la cookie esté en el jar y el header
> viaje en el request.

## 3. Recorrer el motor

```bash
# Definiciones vigentes (insumo del formulario de registro)
curl -s -b cookies.txt http://localhost:8080/api/workflow-definitions

# Registrar una solicitud de adición de créditos (US1)
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"definitionCode":"ADICION_CREDITOS","studentName":"Ana María Pérez","studentDocument":"1144099888"}'
# → 201, nace en REGISTRADA; availableTransitions dice a dónde puede ir
# Guardar el id devuelto:
ID=<uuid>

# Avanzar: la Coordinación la envía a la facultad (US2)
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests/$ID/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"targetStateCode":"EN_FACULTAD"}'

# Transición ilegal: saltar directo a FINALIZADA → 409, el estado no cambia (FR-004)
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests/$ID/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"targetStateCode":"FINALIZADA"}'

# Devolución sin motivo → 422 (FR-014); con motivo → vuelve a DEVUELTA (US5)
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests/$ID/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"targetStateCode":"DEVUELTA"}'                       # 422
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests/$ID/transitions \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"targetStateCode":"DEVUELTA","note":"Falta firma del formato en la casilla 2"}'

# Localizar por cédula o nombre (US3, FR-011)
curl -s -b cookies.txt 'http://localhost:8080/api/requests?search=1144099888'

# Timeline completo: nacimiento + avances + devolución con su motivo (US3, SC-006)
curl -s -b cookies.txt http://localhost:8080/api/requests/$ID/timeline
```

## 4. Demostración SC-005 — trámite nuevo SIN tocar el código ni reiniciar

Con la aplicación **corriendo**, cargar un tercer trámite directamente en la BD:

```bash
docker exec -it tramita-postgres psql -U postgres -d tramita-db
```

```sql
-- Definición mínima de prueba: DEMO v1, dos estados, una transición.
-- created_at es NOT NULL y NO tiene DEFAULT: hay que darlo explícitamente.
INSERT INTO workflow_definition (id, code, version, name, created_at)
  VALUES (gen_random_uuid(), 'DEMO', 1, 'Trámite de demostración', now());
INSERT INTO workflow_state (id, definition_id, code, name, is_initial, is_final)
  SELECT gen_random_uuid(), d.id, s.code, s.name, s.ini, s.fin
  FROM workflow_definition d,
       (VALUES ('ABIERTO','Abierto',true,false),
               ('CERRADO','Cerrado',false,true)) AS s(code,name,ini,fin)
  WHERE d.code = 'DEMO' AND d.version = 1;
INSERT INTO workflow_transition (id, definition_id, from_state_id, to_state_id, responsible, requires_note)
  SELECT gen_random_uuid(), d.id, f.id, t.id, 'COORDINACION', false
  FROM workflow_definition d
  JOIN workflow_state f ON f.definition_id = d.id AND f.code = 'ABIERTO'
  JOIN workflow_state t ON t.definition_id = d.id AND t.code = 'CERRADO'
  WHERE d.code = 'DEMO' AND d.version = 1;
```

Inmediatamente después — misma JVM, sin redeploy:

```bash
curl -s -b cookies.txt http://localhost:8080/api/workflow-definitions   # DEMO aparece
curl -s -b cookies.txt -X POST http://localhost:8080/api/requests \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"definitionCode":"DEMO","studentName":"Demo","studentDocument":"999"}'
# → 201: el motor lo orquesta igual que a los dos trámites reales (US4)
```

## 5. Verificar la inmutabilidad del timeline (SC-002)

En `psql`, intentar mutar el log — el trigger lo rechaza aunque el acceso sea directo:

```sql
UPDATE request_transition_log SET note = 'hackeado' WHERE id = 1;
-- ERROR:  request_transition_log es inmutable: solo se permite INSERT (FR-007)
DELETE FROM request_transition_log WHERE id = 1;
-- ERROR:  request_transition_log es inmutable: solo se permite INSERT (FR-007)
```

> **Efecto colateral de esta garantía: la cuenta que operó queda sellada.** El timeline
> referencia al actor con `ON DELETE NO ACTION` y sus filas no se pueden borrar, así que
> `DELETE FROM users` falla en cuanto la cuenta registró algo. Para cambiar la clave de la
> cuenta sembrada **no sirve borrarla y dejar que el seeder la recree** —es idempotente y
> además la FK lo impide—: usar `POST /api/auth/password`. Para volver a una base virgen,
> recrear el volumen de Docker.

## 6. Suite completa

```bash
docker start tramita-postgres && ./mvnw clean verify
```

(`verify`, no `test`: los IT de Testcontainers corren con failsafe.)
