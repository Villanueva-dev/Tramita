# Quickstart — Auth (login de la Coordinación)

Cómo levantar el backend de autenticación y ejercer el flujo end-to-end. Asume el chasis Maven
heredado de Convenia (wrapper `./mvnw`) y PostgreSQL local.

## Prerrequisitos

- Java 21 (`java -version`).
- PostgreSQL en local (por convención Convenia usa `localhost:5433`; ajustable por env).
- Docker (solo para la prueba de integración con Testcontainers).

## Variables de entorno

Copiar `.env.example` a `.env` en la raíz y completar. **Sin defaults para secretos** (fail-fast,
igual que Convenia):

```bash
# Base de datos
DB_URL=jdbc:postgresql://localhost:5433/tramita-db
DB_USER=postgres
DB_PASSWORD=postgres

# Semilla de la cuenta de la Coordinación (provisión sin hash en git — research.md D8).
# El email es ILUSTRATIVO: reemplazar por el correo institucional real de la Coordinación
# al desplegar (los correos de sede son públicos en la web institucional — Entrevista 3, Q10).
SEED_COORD_EMAIL=coordinacion.cali@uniremington.edu.co
SEED_COORD_PASSWORD=<una-frase-de-paso-de-al-menos-15-chars>

# Allowlist CORS del SPA (cuando exista); coma-separada, sin comodín
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

## Levantar

El flujo local corre con el **perfil `dev`** (`application-dev.yml`), que setea
`server.servlet.session.cookie.secure: false`: una cookie con flag `Secure` no viaja por
`http://localhost` y el flujo curl se rompería en el paso 2 (`/me`). En prod la cookie
**siempre** lleva `Secure` (FR-008 intacto; la desviación dev está declarada en `research.md` D3).

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # aplica Flyway (crea tabla users) y corre el seeder idempotente
# equivalente: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Al primer arranque, `CoordinationUserSeeder` crea la cuenta si no existe, hasheando
`SEED_COORD_PASSWORD` con BCrypt. Reinicios posteriores no la duplican.

## Probar el flujo con curl

> El SPA real usará `fetch` con `credentials: 'include'`. Con curl usamos un cookie-jar.
> CSRF (`csrf.spa()`): primero un GET para recibir la cookie `XSRF-TOKEN`, luego reenviarla en el
> header `X-XSRF-TOKEN` en cada POST. **Precisión (JD3-004, verificado 2026-07-13)**: con el
> login por filtro (D5) el token **no rota al autenticar** — el valor capturado antes del login
> sigue siendo válido. Quien **sí** borra la cookie es el **logout** (`CsrfLogoutHandler`): tras
> el paso 4, un nuevo login exige repetir el paso 0.

```bash
# 0) Obtener cookie CSRF (y guardar cookies en jar)
curl -c jar.txt -b jar.txt http://localhost:8080/api/auth/me   # 401 esperado, pero setea XSRF-TOKEN
XSRF=$(grep XSRF-TOKEN jar.txt | awk '{print $7}')

# 1) Login (US1) — credenciales correctas → 204 + cookie de sesión
curl -i -c jar.txt -b jar.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"email":"coordinacion.cali@uniremington.edu.co","password":"<clave-semilla>"}' \
  http://localhost:8080/api/auth/login

# (el token CSRF NO rota con el login — JD3-004 verificado; el mismo $XSRF sigue sirviendo)

# 2) Sesión actual (GET /me) → 200 {email, active}
curl -b jar.txt http://localhost:8080/api/auth/me

# 3) Cambiar contraseña (US2) → 204
curl -i -c jar.txt -b jar.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"currentPassword":"<clave-semilla>","newPassword":"<nueva-clave-15+>"}' \
  http://localhost:8080/api/auth/password

# 4) Logout (US4) → 204; luego /me da 401
# (-X POST explícito: sin body, curl haría GET; el contrato y el LogoutFilter exigen POST)
curl -i -X POST -c jar.txt -b jar.txt -H "X-XSRF-TOKEN: $XSRF" \
  http://localhost:8080/api/auth/logout
```

## Verificaciones clave (mapa a criterios de aceptación)

| Verificar | Cómo | Requisito |
|-----------|------|-----------|
| Credenciales incorrectas → 401 genérico | login con clave mala → mismo mensaje que email inexistente | FR-002, SC-002 |
| Cuenta inactiva → acceso denegado | marcar `active=false` en BD, intentar login → 401 genérico | FR-011 |
| Cookie con flags de seguridad | inspeccionar `Set-Cookie`: `HttpOnly; SameSite=Strict` (+ `Secure` fuera del perfil `dev`, que lo omite a propósito — ver «Levantar») | FR-008 |
| Nueva clave < 15 o = actual → rechazo | `POST /password` → 422 con motivo | FR-006, edge case |
| Throttling | repetir login fallido > umbral → 429 + `Retry-After`; esperar la ventana → vuelve a permitir | FR-010 |
| Expiración por inactividad | sesión ociosa > 30 min → `/me` da 401 | FR-009 |
| Logout invalida sesión | tras logout, `/me` → 401 | US4 |

## Tests

```bash
./mvnw test                                   # unit (service, política, throttling)
./mvnw clean verify                           # + integración (Testcontainers) + reporte JaCoCo
./mvnw -Dtest=AuthControllerIT test           # solo la integración de la cadena de filtros
```

## Nota sobre el SPA (frontend, diferido)

El framework lo elegirá el equipo. Contrato que el SPA debe respetar:
- Llamar con `credentials: 'include'` (para que viaje la cookie de sesión).
- Leer `XSRF-TOKEN` de cookie y reenviar `X-XSRF-TOKEN` en POSTs.
- **US3**: espejar en tiempo real longitud (15..72) y coincidencia con la confirmación; el
  servidor re-valida siempre (FR-003).
- En desarrollo, usar dev-proxy (p. ej. Vite) para llamar same-origin y que `SameSite=Strict`
  funcione (research.md D3).
