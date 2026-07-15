# Implementation Plan: Autenticación de la Coordinación Académica (login)

**Branch**: `001-auth-login` | **Date**: 2026-07-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-auth-login/spec.md`

## Summary

Autenticación de la Coordinación Académica (único actor) mediante **sesión del lado del
servidor con cookie `HttpOnly; Secure; SameSite=Strict`** (patrón BFF, **sin JWT**), sobre
el chasis Spring Boot 4 / Java 21 / PostgreSQL heredado de Convenia. Incluye login, cambio
de contraseña por la propia Coordinación, logout, expiración por inactividad (30 min),
throttling anti fuerza bruta y una política de contraseña alineada a NIST SP 800-63B-4
(mínimo 15 caracteres, máximo 72 bytes UTF-8, sin composición ni rotación forzadas).

El backend es el foco de esta entrega (**backend-first**). El frontend es un SPA en un
origen distinto cuyo framework aún no está decidido por el equipo; por eso este plan define
un **contrato REST framework-agnóstico** que cualquier SPA puede consumir, y deja la política
de contraseña como contrato que el cliente deberá espejar para la validación en tiempo real
(US3), con la validación autoritativa siempre en el servidor.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.7 (última patch del minor 4.0.x planeado; en Boot 4
el starter web se llama `spring-boot-starter-webmvc`), `-security`, `-data-jpa`, `-validation`,
Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`), driver PostgreSQL, Lombok
(boilerplate). springdoc-openapi **diferido**: su última release (2.8.6) solo soporta Boot 3.x;
se agregará cuando exista versión compatible con Boot 4 (tasks.md T001). **MapStruct NO se usa en esta
feature** (solo 3 DTOs → mapeo a mano, YAGNI; queda disponible en el chasis para features con
muchos mapeos). **Se descartan** las tres dependencias `jjwt` de Convenia (no hay JWT).

**Storage**: PostgreSQL (schema gestionado por Flyway; Hibernate solo `validate`). **Store de
sesión: `HttpSession` en memoria** para el MVP (single-instance).

**Testing**: JUnit 5 (`spring-boot-starter-test`), `spring-security-test`, JaCoCo. Se **agrega**
Testcontainers (módulo `postgresql`, scope test) para la prueba de integración que ejercita la
cadena de filtros real (flags de cookie, 401 genérico, CSRF, 429) contra Flyway + PostgreSQL.

**Target Platform**: Servidor Linux (JAR ejecutable), mismo perfil de despliegue que Convenia.

**Project Type**: Aplicación web (backend + frontend en orígenes distintos). Esta entrega es
**backend**; el SPA se planifica en su propio slice cuando el equipo elija framework.

**Performance Goals**: No es un sistema sensible a performance. SC-001 (login < 30 s) se cumple
holgadamente; la latencia deliberada de BCrypt (cost 10, ~50–100 ms) es un rasgo de seguridad,
no un problema de throughput.

**Constraints**: expiración de sesión por inactividad = 30 min (FR-009); cookie de sesión
`HttpOnly; Secure; SameSite=Strict` (FR-008); CORS con credenciales para el origen del SPA
(allowlist por configuración); throttling de intentos fallidos auto-reparable, sin bloqueo
permanente (FR-010).

**Scale/Scope**: 1 actor, 1 sede, 1 tabla de negocio (`users`). Sin roles ni multi-tenancy.

## Constitution Check

*GATE: debe pasar antes de Phase 0. Re-evaluado tras Phase 1 (design).*

| Principio | Cumplimiento en este plan |
|-----------|---------------------------|
| **I. Simplicidad (KISS+YAGNI)** | ✅ Sesión en memoria, throttling en memoria, 1 tabla, sin roles/tenancy/columnas especulativas. Upgrades (Spring Session JDBC, Bucket4j) documentados como extensiones localizadas, **no** pre-construidas. |
| **II. Arquitectura por feature** | ✅ Paquete `auth` autocontenido (controller, service+impl, repository, entity, DTOs, política, throttling, y las piezas del login por filtro: converter JSON, success/failure handlers y filtro de throttling — D5/D7). `shared/` (config de seguridad + `CsrfCookieFilter`, exception, seed) solo para cross-cutting legítimo. Rompe a propósito el layout by-layer de Convenia. |
| **III. Seguridad por defecto** | ✅ Sesión server-side + cookie `HttpOnly; Secure; SameSite=Strict`, **sin JWT**; BCrypt; DTOs en la frontera (nunca la entity); validación autoritativa en el backend; CSRF activo (`csrf.spa()`); mensaje de error genérico anti-enumeración. |
| **IV. Decisiones trazables** | ✅ Cada decisión en `research.md` con trade-off explícito y fuente oficial (Spring Security 7, NIST SP 800-63B-4, OWASP) verificada vía Context7. |
| **V. Testing del comportamiento sensible** | ✅ Tests sobre login (éxito/fallo/inactivo), política de contraseña, throttling y flags de cookie/CSRF/401. No se testea lo trivial. |
| **Restricciones tecnológicas** | ✅ Spring Boot 4 / Java 21 / PostgreSQL, Flyway-valida-Hibernate, RFC 7807, servicios por interface, Class/QF no se integran. |

**Resultado**: PASS. Sin violaciones → *Complexity Tracking* vacío.

## Project Structure

### Documentation (this feature)

```text
specs/001-auth-login/
├── plan.md              # Este archivo
├── research.md          # Phase 0 — decisiones + fuentes
├── data-model.md        # Phase 1 — entidad User + migración
├── quickstart.md        # Phase 1 — cómo correr y probar el flujo
├── contracts/
│   └── openapi.yaml      # Phase 1 — contrato REST de /api/auth/*
├── checklists/
│   └── requirements.md   # (pre-existente, gate de calidad del spec)
└── tasks.md             # Phase 2 — lo genera /speckit-tasks (NO este comando)
```

### Source Code (repository root)

El backend vive en la **raíz del repo** (layout Maven estándar, un solo módulo desplegable,
igual que Convenia). El SPA será un proyecto separado (directorio/repo aparte, TBD por el
equipo) para respetar el requisito de orígenes distintos.

```text
src/main/java/com/uniremington/api/tramita/
├── TramitaApplication.java
├── auth/                              # FEATURE: autenticación
│   ├── AuthController.java             # POST /password, GET /me (login/logout van por el filter chain)
│   ├── AuthService.java               # interface
│   ├── AuthServiceImpl.java           # cambio de clave: verifica actual, aplica política; NO rota sesión (eso lo hace AuthController, capa web — JD2-003)
│   ├── AppUserDetailsService.java     # carga User por email → UserDetails
│   ├── JsonAuthenticationConverter.java # body JSON {email,password} → UsernamePasswordAuthenticationToken (D5)
│   ├── AuthSuccessHandler.java        # AuthenticationSuccessHandler: 204 sin body + limpia contador (D5/D7)
│   ├── AuthFailureHandler.java        # AuthenticationFailureHandler: 401 genérico problem+json + registra fallo (D5/D10)
│   ├── LoginThrottlingFilter.java     # corta con 429 ANTES del AuthenticationFilter (D7)
│   ├── PasswordPolicy.java            # reglas NIST (mín 15 chars, máx 72 bytes UTF-8, != actual)
│   ├── LoginAttemptService.java       # throttling en memoria (sliding window) — login y cambio de clave (D7)
│   ├── User.java                      # entity (tabla users; timestamps por @PrePersist/@PreUpdate)
│   ├── UserRepository.java            # Spring Data JPA
│   └── dto/
│       ├── LoginRequest.java
│       ├── ChangePasswordRequest.java
│       └── CurrentUserResponse.java
└── shared/                           # cross-cutting (plumbing transversal)
    ├── config/
    │   ├── SecurityConfig.java          # SecurityFilterChain: registra AuthenticationFilter (clase de
    │   │                                #   Spring Security 7, login JSON vía converter+handlers, D5),
    │   │                                #   http.logout() (sin código propio), CORS, CSRF (csrf.spa()), cookie
    │   ├── CsrfCookieFilter.java        # fuerza el Set-Cookie de XSRF-TOKEN en cada respuesta (deferred loading, D4)
    │   └── CorsProperties.java          # allowlist de orígenes del SPA (env-driven)
    ├── exception/
    │   └── GlobalExceptionHandler.java  # RFC 7807 (application/problem+json)
    └── seed/
        └── CoordinationUserSeeder.java  # provisión idempotente por env; normaliza email y valida el seed
                                         #   con PasswordPolicy, fail-fast (D8)

src/main/resources/
├── application.yml                    # session timeout 30m, cookie flags, ddl-auto=validate
├── application-dev.yml                # perfil dev: cookie sin Secure para curl sobre http://localhost (D3)
└── db/migration/
    └── V1.0.0__Create_users_table.sql

src/test/java/com/uniremington/api/tramita/auth/
├── AuthServiceImplTest.java           # unit
├── PasswordPolicyTest.java            # unit
├── LoginAttemptServiceTest.java       # unit
└── AuthControllerIT.java              # integración (Testcontainers + MockMvc)
```

**Structure Decision**: monolito Maven en la raíz (un módulo, un desplegable) + SPA separado
futuro. Raíz de paquetes `com.uniremington.api.tramita` (patrón de Convenia). Se elige
**package-by-feature**: el paquete `auth` grita el dominio y se corresponde 1:1 con un contenedor
del diagrama C4. `shared/` aloja solo lo transversal (config de seguridad, manejo de errores,
seed) que no pertenece a una única feature.

## Complexity Tracking

> Sin violaciones a la constitución. Tabla intencionalmente vacía.
