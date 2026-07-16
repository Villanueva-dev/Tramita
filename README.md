# Tramita — Backend

Backend del MVP de **motor de workflow configurable** para trámites académicos de la
Universidad Remington (Sede Cali). Trabajo de grado — Ingeniería de Sistemas.

> **Estado**: Sprint 1 — **autenticación de la Coordinación** (`001-auth-login`) cerrado y
> mergeado a `main`. Los trámites (adición de créditos, novedad de notas) son **Fase B** y
> todavía no existen en código.

El dominio final es un motor genérico donde dos trámites de estructura idéntica (adición de
créditos y novedad de notas) se configuran por dato, no por código. El chasis técnico
(Spring Boot 4 / Java 21 / PostgreSQL / Flyway) reutiliza el patrón del proyecto hermano
`convenia/` — se copia el *plumbing* por capas, no las entidades.

---

## Stack

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.7 (WebMVC, Data JPA, Security 7, Validation) |
| Serialización | Jackson 3 vía `spring-boot-starter-json` (Boot 4 ya **no** lo trae en webmvc) |
| Persistencia | PostgreSQL + Flyway (`ddl-auto: validate` — Flyway es dueño del schema) |
| Hashing | BCrypt vía `DelegatingPasswordEncoder` (hash con prefijo `{bcrypt}`) |
| Boilerplate | Lombok (sin MapStruct en auth) |
| Build | Maven (wrapper `./mvnw`) + JaCoCo + Failsafe |
| Test | JUnit 5, Spring Security Test, Testcontainers (PostgreSQL) |

---

## Arquitectura

Organización **package-by-feature**
Base:
`com.uniremington.api.tramita`.

```
tramita/
├── auth/                    # feature de autenticación (US1–US4)
│   ├── AuthController        # GET /me, POST /password
│   ├── AuthService(Impl)     # cambio de contraseña
│   ├── AppUserDetailsService # carga el usuario para Spring Security
│   ├── JsonAuthenticationConverter / AuthSuccessHandler / AuthFailureHandler
│   ├── LoginThrottlingFilter / LoginAttemptService  # 429 anti-fuerza-bruta
│   ├── PasswordPolicy / EmailNormalizer
│   ├── User / UserRepository
│   └── dto/                  # LoginRequest, ChangePasswordRequest, CurrentUserResponse
└── shared/
    ├── config/  SecurityConfig, CsrfCookieFilter, CorsProperties
    ├── exception/  GlobalExceptionHandler, ProblemJsonWriter (RFC 7807)
    └── seed/  CoordinationUserSeeder  # provisión idempotente de la cuenta real
```

**Login y logout NO son endpoints de controller**: corren dentro del *filter chain* de Spring
Security (`AuthenticationFilter` + handlers). Así la rotación del id de sesión
(anti session-fixation) y la persistencia del contexto las provee el framework, no código
propio. Solo `/me` y `/password` son métodos de controller.

Todas las decisiones de diseño (D1–D10) están documentadas y trazadas en
[`specs/001-auth-login/research.md`](specs/001-auth-login/research.md).

---

## Modelo de datos

Una sola tabla, creada por Flyway (`V1.0.0__Create_users_table.sql`):

- `users(id UUID PK, email, password_hash, active, created_at, updated_at)`.
- **PK UUID generada por la app** (`GenerationType.UUID`), no por la BD.
- Índice único **funcional** `LOWER(email)`: unicidad case-insensitive real.
- **Sin seed en migración**: la cuenta de la Coordinación la provisiona `CoordinationUserSeeder`
  al arranque desde variables de entorno — nunca se versionan credenciales en git.

Detalle: [`specs/001-auth-login/data-model.md`](specs/001-auth-login/data-model.md).

---

## Seguridad

El corazón de este sprint. Sesión **stateful** con cookie, sin JWT.

| Mecanismo | Implementación |
|-----------|----------------|
| Sesión | Cookie `TRAMITA_SESSION` `HttpOnly` + `Secure` + `SameSite=Strict`, expira a los 30 min de inactividad |
| CSRF | Double-submit (`csrf.spa()`): cookie `XSRF-TOKEN` legible por JS + header `X-XSRF-TOKEN` en cada POST |
| Fuerza bruta | Throttling `429` + `Retry-After` tras 5 fallos por (email+IP) en 15 min; el `429` corta **antes** de autenticar |
| Anti-enumeración | `401` genérico: email inexistente, clave errada y cuenta inactiva devuelven el mismo mensaje sin `detail` |
| Hashing | `DelegatingPasswordEncoder` con BCrypt por defecto (prefijo `{bcrypt}` → migrable sin invalidar hashes) |
| Política de clave | Mínimo 15 caracteres, máximo 72 bytes UTF-8, distinta de la actual; sin composición forzada |
| CORS | Allowlist explícita desde `APP_CORS_ALLOWED_ORIGINS` (nunca comodín, obligatorio con `allowCredentials`) |
| Errores | Siempre `application/problem+json` (RFC 7807) |

`/me` responde desde el **snapshot de la sesión** (email + active del `UserDetails` capturado al
autenticar), sin tocar la BD.

---

## Endpoints

Base: `/api`. Contrato completo (shapes, errores exactos, guía para el frontend) en
[`specs/001-auth-login/integracion-frontend.md`](specs/001-auth-login/integracion-frontend.md)
y [`contracts/openapi.yaml`](specs/001-auth-login/contracts/openapi.yaml).

| Método | Path | Sesión | CSRF | Éxito |
|--------|------|:------:|:----:|-------|
| `POST` | `/api/auth/login` | — | ✅ | `204` + cookie de sesión |
| `GET`  | `/api/auth/me` | ✅ | — | `200` `{ email, active }` |
| `POST` | `/api/auth/password` | ✅ | ✅ | `204` (rota el id de sesión) |
| `POST` | `/api/auth/logout` | ✅ | ✅ | `204` (borra ambas cookies) |

---

## Puesta en marcha

### Prerrequisitos

- Java 21 (`java -version`).
- Docker (para PostgreSQL local y para los tests de integración con Testcontainers).

### 1. Base de datos

```bash
docker run -d \
  --name tramita-postgres \
  -e POSTGRES_DB=tramita-db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5433:5432 \
  -v tramita-pgdata:/var/lib/postgresql/data \
  postgres:16

# En arranques posteriores basta con:
docker start tramita-postgres && docker ps
```

### 2. Variables de entorno

Copiar `.env.example` a `.env` y completar. **Sin defaults para secretos**: si falta una
variable, el arranque falla a propósito (*fail-fast*).

| Variable | Ejemplo | Rol |
|----------|---------|-----|
| `DB_URL` | `jdbc:postgresql://localhost:5433/tramita-db` | Conexión JDBC |
| `DB_USER` | `postgres` | Usuario de BD |
| `DB_PASSWORD` | `postgres` | Clave de BD |
| `SEED_COORD_EMAIL` | `coordinacion.cali@uniremington.edu.co` | Email de la cuenta real de la Coordinación |
| `SEED_COORD_PASSWORD` | *(frase de paso ≥ 15 caracteres)* | Clave inicial; la Coordinación la rota luego vía `/password` |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowlist del SPA (coma-separada, sin comodín) |

### 3. Levantar (perfil `dev`)

El perfil `dev` desactiva **solo** el flag `Secure` de la cookie (una cookie `Secure` no viaja
por `http://localhost`). En producción `Secure` queda siempre activo.

```bash
set -a; source .env; set +a
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Al primer arranque, Flyway crea la tabla `users` y `CoordinationUserSeeder` provisiona la cuenta
(hasheando `SEED_COORD_PASSWORD` con BCrypt). Reinicios posteriores no la duplican (idempotente).

Flujo de humo con `curl` (login → `/me` → password → logout):
[`specs/001-auth-login/quickstart.md`](specs/001-auth-login/quickstart.md).

---

## Tests

- **Unitarios** (`*Test`, Surefire): servicio, política de contraseña, throttling.
- **Integración** (`*IT`, Failsafe + Testcontainers): cadena de filtros end-to-end contra un
  PostgreSQL real efímero.
- **Cobertura**: reporte JaCoCo generado en la fase `verify`.

```bash
./mvnw test                          # solo unitarios
./mvnw clean verify                  # unitarios + integración + reporte JaCoCo
./mvnw -Dtest=AuthControllerIT test  # solo la integración de la cadena de filtros
```

> Nota: el build incremental de Maven puede reportar un `BUILD SUCCESS` engañoso tras editar un
> test ("Nothing to compile"). Para evidenciar el RED de TDD usar `./mvnw clean test-compile`.

---

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [`specs/001-auth-login/spec.md`](specs/001-auth-login/spec.md) | Requisitos funcionales (FR) y criterios de aceptación |
| [`specs/001-auth-login/plan.md`](specs/001-auth-login/plan.md) | Plan técnico, stack y estructura |
| [`specs/001-auth-login/research.md`](specs/001-auth-login/research.md) | Decisiones de diseño D1–D10 con justificación |
| [`specs/001-auth-login/integracion-frontend.md`](specs/001-auth-login/integracion-frontend.md) | Guía de integración para el SPA (Juan) |
| [`docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md`](docs/nuevo-proyecto/01-planteamiento/arbol-de-problemas.md) | Planteamiento (Marco Lógico), alcance y supuestos |

Flujo SDD con **Spec Kit v0.8.12**: `specify → plan → tasks → implement`. La constitución del
proyecto (5 principios) está en [`.specify/memory/constitution.md`](.specify/memory/constitution.md).

---

## Equipo

Backend: Julian Villanueva · Frontend: Juan Ramirez. Universidad Remington — Ingeniería de Sistemas (Distancia,
SNIES 53112). Documentación, commits y comentarios en español; identificadores de código en inglés.
