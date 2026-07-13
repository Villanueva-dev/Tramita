# Tasks: Autenticación de la Coordinación Académica (login)

**Input**: Design documents from `/specs/001-auth-login/`

**Prerequisites**: plan.md, spec.md, research.md (D0–D10), data-model.md, contracts/openapi.yaml, quickstart.md

**Tests**: Alcance de pruebas deliberadamente mínimo (Principio V — testing pragmático):
UNA prueba de integración de la cadena de filtros (`AuthControllerIT`, Testcontainers) y
unit tests SOLO de la lógica sensible (política de contraseña, ventana del throttling,
contraseña actual incorrecta → 422). Queda PROHIBIDO agregar tests de DTOs, getters,
mapeos triviales o controllers de paso.

**Integridad de los tests (no negociable)**: el fin NO justifica los medios. Está prohibido
amañar un test para forzar el verde (o el RED del TDD): nada de aserciones triviales o
vacías, mocks que solo verifican al propio mock, debilitar o borrar aserciones para que
pase, `@Disabled`/comentar tests para ocultar fallos, ni asserts calcados de la
implementación en lugar del comportamiento esperado. Cada test debe poder fallar por la
causa real que dice cubrir, y el RED del TDD debe fallar por la aserción esperada, no por
errores de setup o compilación accidental. Estos son los ÚNICOS tests del repo y cubren
precisamente las partes SENSIBLES (seguridad): un test amañado es peor que no tener test,
porque documenta una garantía que no existe.

**Organization**: Las tareas se agrupan por user story (spec.md) para que cada historia sea
implementable y verificable de forma independiente, en orden de prioridad (P1 → P2 → P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias de tareas incompletas).
- **[Story]**: user story a la que pertenece la tarea (US1, US2, US3, US4). Obligatoria en las
  fases de historia; no aplica en Setup, Foundational ni Polish.
- Cada descripción incluye el path exacto del archivo (relativo a la raíz del repo).

## Path Conventions

Backend Maven en la **raíz del repo** (un módulo desplegable, layout heredado de Convenia).
Paquete raíz `com.uniremington.api.tramita`, package-by-feature: `auth/` (feature) y
`shared/` (cross-cutting: `config/`, `exception/`, `seed/`). El SPA es un proyecto separado
futuro y NO forma parte de estas tareas (plan.md — backend-first).

---

## Phase 1: Setup (inicialización del proyecto)

**Purpose**: crear el esqueleto Spring Boot desde cero (no existe código todavía), heredando
el patrón del chasis de Convenia — solo el patrón, no las entidades.

- [x] T001 Generar el esqueleto Maven en la raíz del repo con **Spring Initializr**
      (`start.spring.io`, vía web o `curl https://start.spring.io/starter.zip -d ...`):
      `type=maven-project`, `javaVersion=21`, `bootVersion=4.0.5` (si Initializr ya no ofrece
      esa patch, tomar la 4.0.x disponible y fijar el parent a 4.0.5 en el `pom.xml`),
      `groupId=com.uniremington.api`, `artifactId=tramita`,
      `packageName=com.uniremington.api.tramita`, dependencias
      `web,security,data-jpa,validation,flyway,postgresql,lombok,testcontainers`.
      Initializr ya trae: Maven wrapper (`mvnw`, `.mvn/wrapper/`), `spring-boot-starter-test`,
      `spring-security-test` y Testcontainers de test. Completar A MANO lo que no trae:
      `springdoc-openapi`, plugin JaCoCo, verificar que `flyway-database-postgresql` esté
      presente (las versiones recientes lo agregan con Flyway+PostgreSQL; si no, añadirlo) y
      crear la estructura `src/main/java/com/uniremington/api/tramita/{auth,shared}` y
      `src/main/resources/db/migration`.
      **Sin** `jjwt` (no hay JWT — research.md D1) y **sin** MapStruct en auth (plan.md).
      **Ejecutada (2026-07-11)**: generado vía curl con Boot **4.0.7** (la 4.0.5 salió del
      catálogo de Initializr; misma minor, solo patches). Boot 4 usa starters de test
      modulares (`spring-boot-starter-{webmvc,security,data-jpa,validation,flyway}-test`) en
      lugar de `spring-boot-starter-test`/`spring-security-test`; `flyway-database-postgresql`
      y `db/migration/` vinieron incluidos; JaCoCo 0.8.13 agregado a mano;
      `springdoc-openapi` **DIFERIDO** — su última versión (2.8.6, Maven Central al
      2026-07-11) solo soporta Boot 3.x; agregarlo cuando haya release compatible con Boot 4.
      Se descartaron del esqueleto: `HELP.md`, `templates/`, `static/` y el
      `TramitaApplicationTests` (`contextLoads` — redundante con la IT única y exigiría
      Docker en todo `mvnw test`); se conservan `TestcontainersConfiguration` y
      `TestTramitaApplication` para la IT y el arranque dev con Postgres efímero.
- [x] T002 [P] Verificar la clase principal generada por Initializr en
      `src/main/java/com/uniremington/api/tramita/TramitaApplication.java`
      (nombre, paquete raíz y anotación `@SpringBootApplication`) — verificada 2026-07-11.
- [x] T003 [P] Crear `src/main/resources/application.yml`: datasource por variables de entorno
      `DB_URL`/`DB_USER`/`DB_PASSWORD` **sin defaults para secretos** (fail-fast, patrón Convenia),
      `spring.jpa.hibernate.ddl-auto=validate` (Flyway-valida-Hibernate), Flyway habilitado,
      timeout de sesión por inactividad `server.servlet.session.timeout=30m` (FR-009) y cookie de
      sesión `server.servlet.session.cookie.{name=TRAMITA_SESSION, http-only=true, secure=true,
      same-site=strict}` (FR-008, research.md D3).
- [x] T004 [P] Crear `src/main/resources/application-dev.yml` (perfil `dev`):
      `server.servlet.session.cookie.secure=false` para poder probar con curl sobre
      `http://localhost` (desviación declarada en research.md D3; en prod `Secure` siempre activo).
- [x] T005 [P] Crear `.env.example` en la raíz con `DB_URL`, `DB_USER`, `DB_PASSWORD`,
      `SEED_COORD_EMAIL`, `SEED_COORD_PASSWORD`, `APP_CORS_ALLOWED_ORIGINS` (valores ilustrativos
      del quickstart.md, sin secretos reales) y completar el `.gitignore` generado por
      Initializr (ya excluye `target/`) añadiendo `.env`.

**Checkpoint**: `./mvnw compile` pasa; el proyecto arranca en vacío (aún sin DB schema) — verificado 2026-07-12 (Flyway crea `flyway_schema_history` sobre schema vacío contra Postgres 16 en Docker, `localhost:5433`).

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: persistencia, política de contraseña, plumbing de seguridad base y provisión de la
cuenta. Ninguna user story puede empezar sin esta fase: la cuenta semilla es precondición del
Independent Test de la US1 y la política valida el seed (research.md D8).

**CRITICAL**: ninguna fase de user story arranca hasta completar esta fase.

- [X] T006 Crear la migración Flyway `src/main/resources/db/migration/V1.0.0__Create_users_table.sql`
      con el SQL exacto de data-model.md: tabla `users` (`id UUID PRIMARY KEY` — generado por la
      app, `email VARCHAR(255) NOT NULL`, `password_hash VARCHAR(255) NOT NULL`,
      `active BOOLEAN NOT NULL DEFAULT TRUE`, `created_at`/`updated_at TIMESTAMP NOT NULL`) +
      índice único funcional `CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));`.
      **Sin** migración de seed (la cuenta se provisiona por seeder env-driven, D8).
- [X] T007 [P] Crear la entity `User` en
      `src/main/java/com/uniremington/api/tramita/auth/User.java`: campos `id`, `email`,
      `passwordHash`, `active`, `createdAt`, `updatedAt` mapeados a la tabla `users`;
      timestamps poblados por callbacks `@PrePersist`/`@PreUpdate` en la propia entity
      (data-model.md); Lombok para boilerplate. La entity **nunca** se expone en la API.
- [X] T008 [P] Crear `UserRepository` en
      `src/main/java/com/uniremington/api/tramita/auth/UserRepository.java`:
      `extends JpaRepository<User, UUID>` con `Optional<User> findByEmail(String email)` y
      `boolean existsByEmail(String email)` (el caller normaliza el email a minúsculas).
- [X] T009 [P] Escribir el unit test `PasswordPolicyTest` en
      `src/test/java/com/uniremington/api/tramita/auth/PasswordPolicyTest.java` (RED antes de
      implementar): mínimo **15 caracteres**; máximo **72 bytes en UTF-8** — incluir un caso con
      caracteres no ASCII donde el conteo de caracteres pasa pero el de bytes no (p. ej. una frase
      con `ñ`/tildes: cada uno ocupa 2 bytes); nueva contraseña igual a la actual → rechazada;
      cada regla incumplida produce un mensaje propio identificable.
- [X] T010 Implementar `PasswordPolicy` en
      `src/main/java/com/uniremington/api/tramita/auth/PasswordPolicy.java` hasta poner en verde
      T009: mínimo 15 caracteres (NIST SP 800-63B-4 §3.1.1.2), máximo
      `password.getBytes(UTF_8).length <= 72` (límite real de BCrypt, research.md D6), nueva ≠
      actual, sin reglas de composición ni rotación forzada. Reportar **cada regla incumplida con
      su propio mensaje** (lo consumirán el 422 y la retroalimentación del SPA — US3). Punto único
      de extensión para la futura blocklist (no construirla — no-conformidad documentada).
- [X] T011 [P] Crear `CorsProperties` en
      `src/main/java/com/uniremington/api/tramita/shared/config/CorsProperties.java`: allowlist de
      orígenes del SPA leída de `APP_CORS_ALLOWED_ORIGINS` (coma-separada, sin comodín — D9).
- [X] T012 [P] Crear `CsrfCookieFilter` en
      `src/main/java/com/uniremington/api/tramita/shared/config/CsrfCookieFilter.java`:
      `OncePerRequestFilter` (~10 líneas) que lee el `CsrfToken` del request para forzar el
      `Set-Cookie` de `XSRF-TOKEN` en cada respuesta (deferred loading de `csrf.spa()` — el token
      rota al autenticar y la cookie no se re-emite sola; research.md D4).
- [X] T013 Crear `SecurityConfig` base en
      `src/main/java/com/uniremington/api/tramita/shared/config/SecurityConfig.java`:
      bean `PasswordEncoder` (`DelegatingPasswordEncoder` con BCrypt por defecto — D6);
      `http.csrf(csrf -> csrf.spa())` + registro de `CsrfCookieFilter` (D4); CORS con
      `CorsConfigurationSource` desde `CorsProperties` (`allowCredentials=true`, métodos GET/POST,
      headers `Content-Type` y `X-XSRF-TOKEN` — D9); reglas de autorización: `POST /api/auth/login`
      permitAll, el resto de `/api/**` autenticado; `AuthenticationEntryPoint` que responde **401
      `application/problem+json`** (RFC 7807) cuando no hay sesión. El wiring del login (filtros,
      handlers) se agrega en la US1; el logout en la US4.
- [X] T014 [P] Crear `GlobalExceptionHandler` base en
      `src/main/java/com/uniremington/api/tramita/shared/exception/GlobalExceptionHandler.java`:
      respuestas `application/problem+json` (RFC 7807) con `title` y `status` (D10). Los mapeos
      específicos del cambio de clave (422/429) se agregan en la US2.
- [X] T015 Crear `CoordinationUserSeeder` en
      `src/main/java/com/uniremington/api/tramita/shared/seed/CoordinationUserSeeder.java`:
      provisión boot-time **idempotente** de la cuenta de la Coordinación desde
      `SEED_COORD_EMAIL`/`SEED_COORD_PASSWORD` (sin secretos en git — D8); **normaliza** el email a
      minúsculas con la misma regla del login; **valida** la contraseña semilla contra
      `PasswordPolicy` y **falla el arranque** (fail-fast) si no cumple — sin puertas traseras;
      hashea con el bean `PasswordEncoder`; reinicios posteriores no duplican la cuenta.

**Checkpoint**: `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` arranca, Flyway crea la tabla
`users`, el seeder crea la cuenta, y `GET /api/auth/me` sin sesión responde 401 `problem+json`.

---

## Phase 3: User Story 1 — Iniciar sesión (Priority: P1) — MVP

**Goal**: la Coordinación se autentica con email y contraseña; el login corre **dentro del filter
chain** de Spring Security (`AuthenticationFilter` + converter + handlers — research.md D5), con
401 genérico anti-enumeración (FR-002), throttling anti fuerza bruta (FR-010) y rotación
automática del id de sesión (session fixation).

**Independent Test**: con la cuenta semilla activa, credenciales correctas → 204 + cookie
`TRAMITA_SESSION` y `GET /api/auth/me` → 200; credenciales incorrectas o cuenta inactiva → 401
genérico idéntico. Verificable con quickstart.md pasos 0–2 y con `AuthControllerIT`.

### Tests for User Story 1

> Escribir primero y comprobar que FALLAN antes de implementar (TDD). `AuthControllerIT` solo
> referencia URLs y MockMvc, así que compila desde ya; quedará en verde al cerrar T026–T027.

- [X] T016 [P] [US1] Escribir el unit test `LoginAttemptServiceTest` en
      `src/test/java/com/uniremington/api/tramita/auth/LoginAttemptServiceTest.java` (RED):
      ventana deslizante por clave `(email normalizado + IP)` — bloquea al superar 5 fallos en
      15 min; se **auto-repara** al vencer la ventana (usar `Clock` inyectable, no `sleep`);
      un éxito limpia el contador; calcula los segundos restantes para `Retry-After`.
- [X] T017 [P] [US1] Escribir la prueba de integración `AuthControllerIT` en
      `src/test/java/com/uniremington/api/tramita/auth/AuthControllerIT.java`
      (`@SpringBootTest` + MockMvc con filtros de seguridad + Testcontainers PostgreSQL + Flyway;
      única IT de la feature; reusar `TestcontainersConfiguration` del esqueleto **fijando la
      imagen a una versión concreta** — Initializr la generó con `postgres:latest`, no
      reproducible) con los escenarios US1: (a) login exitoso → **204 y rotación del id de sesión**
      (id previo ≠ id posterior al autenticar); la cookie `TRAMITA_SESSION` y sus atributos
      (`HttpOnly`/`Secure`/`SameSite`) no son observables en MockMvc — se verifican manualmente
      vía quickstart (curl);
      (b) credenciales inválidas → **401 genérico**: mismo body `problem+json` para clave
      incorrecta, email inexistente y cuenta inactiva (FR-002/FR-011/SC-002); (c) POST de login
      **sin token CSRF → 403**; (d) throttling: superar el umbral de fallos → **429 con header
      `Retry-After`** (FR-010). El escenario de logout se agrega en la US4 (T035).

### Implementation for User Story 1

- [X] T018 [P] [US1] Crear el DTO `LoginRequest` en
      `src/main/java/com/uniremington/api/tramita/auth/dto/LoginRequest.java`: `email`, `password`
      (record o clase simple; las anotaciones Bean Validation son documentales aquí — en el path
      del filtro no corre `@Valid`, la validación real la hace el converter, ver T022).
- [X] T019 [P] [US1] Crear el DTO `CurrentUserResponse` en
      `src/main/java/com/uniremington/api/tramita/auth/dto/CurrentUserResponse.java`: solo
      `email` y `active` — **sin** id ni hash (data-model.md).
- [X] T020 [P] [US1] Crear `AppUserDetailsService` en
      `src/main/java/com/uniremington/api/tramita/auth/AppUserDetailsService.java`: implementa
      `UserDetailsService`; normaliza el email a minúsculas, busca vía `UserRepository.findByEmail`
      y mapea `active` → `enabled` del `UserDetails` (una cuenta inactiva produce
      `DisabledException` en el provider — FR-011).
- [X] T021 [P] [US1] Implementar `LoginAttemptService` en
      `src/main/java/com/uniremington/api/tramita/auth/LoginAttemptService.java` hasta poner en
      verde T016: `ConcurrentHashMap`, ventana deslizante 5 fallos / 15 min por
      `(email normalizado + IP)`, auto-reparación al vencer la ventana, limpieza en éxito, cálculo
      de segundos para `Retry-After`, `Clock` inyectable. **Nunca** bloqueo permanente (D7).
      Se reutilizará en el cambio de clave (US2).
- [X] T022 [P] [US1] Crear `JsonAuthenticationConverter` en
      `src/main/java/com/uniremington/api/tramita/auth/JsonAuthenticationConverter.java`:
      `AuthenticationConverter` que lee el body JSON `{email, password}` (deserializa a
      `LoginRequest`), normaliza el email y produce `UsernamePasswordAuthenticationToken` (D5).
      **JD2-004**: el 400 del contrato NO sale de `@Valid` (no corre en el path del filtro) — el
      converter DEBE validar body malformado / JSON inválido / campos ausentes o vacíos y disparar
      el **400 `problem+json`**: lanzar una excepción dedicada (p. ej.
      `InvalidLoginRequestException extends AuthenticationException`) que `AuthFailureHandler`
      (T024) mapea a 400, separada del 401 genérico.
- [X] T023 [P] [US1] Crear `AuthSuccessHandler` en
      `src/main/java/com/uniremington/api/tramita/auth/AuthSuccessHandler.java`:
      `AuthenticationSuccessHandler` que responde **204 sin body** y **limpia el contador** de
      `LoginAttemptService` para la clave email+IP (D5/D7).
- [X] T024 [P] [US1] Crear `AuthFailureHandler` en
      `src/main/java/com/uniremington/api/tramita/auth/AuthFailureHandler.java`:
      `AuthenticationFailureHandler` que **aplana TODAS** las fallas de autenticación
      (`BadCredentialsException`, `DisabledException`, etc.) a **un único 401 genérico**
      `problem+json` («Credenciales inválidas»), sin ramificar por tipo (FR-002/SC-002, D10), y
      **registra el fallo** en `LoginAttemptService`. Única excepción: la excepción dedicada de
      body inválido del converter (T022) mapea a **400** `problem+json` (JD2-004) y no cuenta como
      intento fallido.
- [X] T025 [P] [US1] Crear `LoginThrottlingFilter` en
      `src/main/java/com/uniremington/api/tramita/auth/LoginThrottlingFilter.java`: filtro propio
      que corre ANTES del `AuthenticationFilter`; extrae el email del body para armar la clave
      `(email + IP)` y, si está bloqueada, corta con **429 `problem+json` + header `Retry-After`**
      sin llegar a autenticar (D7). **JD2-002**: este filtro lee el body y el converter (T022) lo
      relee — DEBE envolver el request en un wrapper que **bufferee el body completo y lo re-sirva**
      en `getInputStream()`/`getReader()` para el resto de la cadena (p. ej.
      `ContentCachingRequestWrapper` si se verifica que permite la relectura downstream — su cache
      solo guarda lo ya leído —, o un wrapper propio de ~20 líneas que garantice la relectura).
- [X] T026 [US1] Completar el wiring del login en
      `src/main/java/com/uniremington/api/tramita/shared/config/SecurityConfig.java`:
      `DaoAuthenticationProvider` con `AppUserDetailsService` + `PasswordEncoder`
      (`hideUserNotFoundExceptions` en su default `true` — el email inexistente ya llega como
      `BadCredentialsException`, D10); `AuthenticationFilter(AuthenticationManager,
      JsonAuthenticationConverter)` sobre `POST /api/auth/login` con `AuthSuccessHandler`/
      `AuthFailureHandler` y `SecurityContextRepository = HttpSessionSecurityContextRepository`
      (persistencia del contexto en la `HttpSession`, D5); registrar `LoginThrottlingFilter` con
      `addFilterBefore(..., AuthenticationFilter.class)`. Al autenticar dentro del chain, la
      rotación del id de sesión la hace el framework (`ChangeSessionIdAuthenticationStrategy`).
- [X] T027 [US1] Crear `AuthController` en
      `src/main/java/com/uniremington/api/tramita/auth/AuthController.java` con **solo**
      `GET /api/auth/me`: devuelve 200 `CurrentUserResponse` (`email`, `active`) de la sesión
      activa, con mapeo **a mano** (sin MapStruct) y sin exponer id/hash; el 401 sin sesión lo
      resuelve el entry point de T013. (Login y logout NO son endpoints de controller: van por el
      filter chain — plan.md.)

**Checkpoint**: `./mvnw -Dtest=AuthControllerIT test` en verde (escenarios US1) y quickstart.md
pasos 0–2 funcionan con curl. La US1 es demostrable por sí sola (MVP).

---

## Phase 4: User Story 2 — Cambiar la contraseña (Priority: P2)

**Goal**: la Coordinación autenticada rota su propia contraseña: exige la actual correcta
(FR-005), valida la nueva contra `PasswordPolicy` (FR-006), reutiliza el throttling del login
(D7) y responde con el mapa un-código-una-causa (401 sesión · 403 CSRF · 422 negocio · 429
throttling — D10).

**Independent Test**: autenticada, `POST /api/auth/password` con la actual correcta y una nueva
válida → 204; el próximo login exige la nueva y rechaza la anterior (SC-003). Verificable con
quickstart.md paso 3 + re-login.

### Tests for User Story 2

- [ ] T028 [P] [US2] Escribir el unit test `AuthServiceImplTest` en
      `src/test/java/com/uniremington/api/tramita/auth/AuthServiceImplTest.java` (RED), acotado a
      la lógica sensible: contraseña actual **incorrecta** → excepción de negocio que mapea a
      **422** (no 401 — la cookie de sesión es válida; research.md D10, RFC 9110 §15.5.2) y
      **registra el intento fallido** en `LoginAttemptService`; clave email+IP bloqueada →
      excepción que mapea a 429. Sin tests del happy path trivial ni de mapeos.

### Implementation for User Story 2

- [ ] T029 [P] [US2] Crear el DTO `ChangePasswordRequest` en
      `src/main/java/com/uniremington/api/tramita/auth/dto/ChangePasswordRequest.java`:
      `currentPassword`, `newPassword`, con `@NotBlank` (aquí sí corre `@Valid` → 400 del
      contrato para campos vacíos).
- [ ] T030 [US2] Crear `AuthService` (interface) en
      `src/main/java/com/uniremington/api/tramita/auth/AuthService.java` y `AuthServiceImpl` en
      `src/main/java/com/uniremington/api/tramita/auth/AuthServiceImpl.java` hasta poner en verde
      T028. Orden del cambio de clave: (1) consultar `LoginAttemptService` (misma clave email+IP
      del login — D7): bloqueada → excepción de throttling (429 + `Retry-After`); (2) verificar la
      actual con `PasswordEncoder.matches` → incorrecta: excepción de negocio (422) + registrar
      fallo; (3) validar la nueva con `PasswordPolicy` (mín. 15 chars, máx. 72 bytes UTF-8,
      ≠ actual) → violación: excepción de negocio (422) con los mensajes por regla; (4) encode +
      persistir (`updated_at` lo cubre `@PreUpdate`); (5) limpiar el contador.
      **JD2-003**: `AuthServiceImpl` NO rota el id de sesión — la rotación es responsabilidad de
      la capa web (T032); el service no toca `HttpServletRequest`.
- [ ] T031 [US2] Extender `GlobalExceptionHandler` en
      `src/main/java/com/uniremington/api/tramita/shared/exception/GlobalExceptionHandler.java`:
      excepciones de negocio del cambio de clave → **422 `problem+json`** con `detail`
      distinguible por causa (actual incorrecta · nueva viola la política · nueva igual a la
      actual — contrato `/auth/password`); excepción de throttling → **429** con header
      `Retry-After` (segundos restantes de la ventana).
- [ ] T032 [US2] Agregar `POST /api/auth/password` a
      `src/main/java/com/uniremington/api/tramita/auth/AuthController.java`: body `@Valid
      ChangePasswordRequest`, delega en `AuthService` y, tras el cambio exitoso, llama
      `request.changeSessionId()` **en el controller** (capa web — JD2-003; OWASP Session
      Management: renovar el id tras cualquier cambio de credencial; aquí es explícito porque
      este endpoint no pasa por el filter chain del login, D10/F12) y responde **204**. La
      usuaria sigue logueada; el id viejo muere.

**Checkpoint**: unit tests T028 en verde; quickstart.md paso 3 responde 204; el login siguiente
exige la nueva contraseña y rechaza la anterior (SC-003). US1 sigue funcionando.

---

## Phase 5: User Story 3 — Validación de contraseña en tiempo real (Priority: P2)

**Goal**: entregar el lado backend de la US3: una política **espejable** por el cliente y una
re-validación autoritativa cuyo 422 informa cada regla incumplida (FR-003/FR-006/SC-005). La UI
en tiempo real pertenece al slice del SPA (framework diferido — plan.md); NO se construye
frontend en esta fase.

**Independent Test**: enviar a `POST /api/auth/password` una nueva contraseña que un cliente
hipotético marcaría como válida pero que viola la política (p. ej. 16 caracteres con tildes que
superan 72 bytes UTF-8) → el servidor la rechaza con 422 y `detail` enumera la(s) regla(s)
incumplida(s). Confirma que la validación del servidor es la autoritativa (SC-005).

### Implementation for User Story 3

- [ ] T033 [US3] Garantizar la retroalimentación por regla end-to-end: revisar
      `src/main/java/com/uniremington/api/tramita/auth/PasswordPolicy.java` y
      `src/main/java/com/uniremington/api/tramita/shared/exception/GlobalExceptionHandler.java`
      para que el `detail` del 422 enumere **cada** regla incumplida con su mensaje (mínimo 15
      caracteres · máximo 72 bytes UTF-8 · distinta de la actual), de modo que el SPA pueda
      espejar las reglas y mostrar los motivos exactos del rechazo (US3, FR-003). Ajustar solo si
      T010/T031 no lo dejaron cubierto.
- [ ] T034 [US3] Verificar la coherencia del contrato espejable: comparar la política implementada
      con la descripción de `newPassword` en
      `specs/001-auth-login/contracts/openapi.yaml` (mín. 15 **caracteres**, máx. 72 **bytes
      UTF-8** medibles en el cliente con `TextEncoder`, ≠ actual, sin composición forzada) y
      corregir el YAML únicamente si diverge de lo implementado. Este contrato es la fuente que
      el SPA espejará en tiempo real; la validación autoritativa sigue en el servidor (FR-003).

**Checkpoint**: un 422 de política lista todos los motivos; el contrato OpenAPI describe
exactamente las reglas que el servidor aplica.

---

## Phase 6: User Story 4 — Cerrar sesión (Priority: P3)

**Goal**: logout seguro vía `http.logout()` del filter chain (sin controller propio): invalida la
`HttpSession`, limpia la cookie y responde 204 (FR-007).

**Independent Test**: con sesión activa, `POST /api/auth/logout` (con CSRF) → 204; después,
`GET /api/auth/me` → 401. Verificable con quickstart.md paso 4.

### Tests for User Story 4

- [ ] T035 [US4] Extender `AuthControllerIT` en
      `src/test/java/com/uniremington/api/tramita/auth/AuthControllerIT.java` (RED — hoy el
      default de logout redirige) con el escenario de logout: con sesión activa y header CSRF,
      `POST /api/auth/logout` → **204 sin redirect**; a continuación `GET /api/auth/me` → 401
      (sesión invalidada). Con esto la IT única cubre todo lo pedido: login exitoso + rotación de
      session id, 401 genérico, logout 204, CSRF 403 y throttling 429 + `Retry-After`.

### Implementation for User Story 4

- [ ] T036 [US4] Configurar el logout en
      `src/main/java/com/uniremington/api/tramita/shared/config/SecurityConfig.java` (GREEN de
      T035): `http.logout()` con `logoutUrl("/api/auth/logout")` y
      `HttpStatusReturningLogoutSuccessHandler` (204) — **JD2-001**: el default de `http.logout()`
      responde con redirect 302, incompatible con el contrato —, más `invalidateHttpSession(true)`
      y `deleteCookies("TRAMITA_SESSION")`. El logout queda protegido por CSRF (POST) como exige
      el contrato. **JD3-004**: el logout **sí** borra la cookie `XSRF-TOKEN`
      (`CsrfLogoutHandler`) y el 204 terminal no la re-emite — decidir al implementar: re-emitir
      el token en la respuesta del logout o documentar que el SPA repita el GET inicial antes del
      próximo login. (El login NO tiene este problema: verificado 2026-07-13 contra la fuente
      7.0.6 + IT de evidencia — el token no rota al autenticar.)

**Checkpoint**: `AuthControllerIT` completa en verde; quickstart.md pasos 0–4 completos.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: cerrar las notas documentales de los triages judgment-day (JD2 sobre artefactos,
JD3 sobre código) y validar el flujo completo. Salvo T043 (hardening menor adelantado), ninguna
tarea de esta fase cambia el comportamiento del backend.

- [ ] T037 [P] **JD2-003 (documental)**: corregir el comentario desalineado en
      `specs/001-auth-login/plan.md` (línea ~99): `AuthServiceImpl.java` ya no «rota id de sesión»
      — la rotación tras el cambio de clave vive en la **capa web** (`AuthController`, T032).
      Ajustar el comentario del árbol de estructura para reflejarlo.
- [ ] T038 [P] **JD2-005 (documental)**: corregir `specs/001-auth-login/quickstart.md`
      (líneas 110–111): «longitud (15..72)» mezcla unidades — debe decir mínimo **15 caracteres**
      y máximo **72 bytes UTF-8** (espejo cliente con `TextEncoder`), consistente con FR-006 y el
      contrato OpenAPI.
- [ ] T039 [P] **JD2-007 (decisión + documental)**: definir la semántica del campo `active` de
      `GET /api/auth/me` bajo la ventana aceptada de FR-011: fuente del valor (snapshot de la
      sesión al autenticar vs lectura fresca de BD en cada `/me`) y su refresco. Documentar la
      decisión en la descripción de `CurrentUserResponse` en
      `specs/001-auth-login/contracts/openapi.yaml` y ajustar
      `src/main/java/com/uniremington/api/tramita/auth/AuthController.java` solo si la decisión
      difiere de lo implementado en T027. **JD3-009 (fusionada aquí)**: la misma decisión debe
      cubrir el edge de sesión huérfana — hoy un usuario autenticado ausente en BD produce
      `IllegalStateException` → 500; semánticamente correspondería 401 (inalcanzable mientras
      no exista borrado de usuarios, pero decidirlo aquí evita heredarlo).
- [ ] T040 [P] **JD2-008 (documental)**: añadir en `specs/001-auth-login/research.md` (sección
      D10) una nota sobre el diferencial de timing del fail-fast de `DaoAuthenticationProvider`
      (cuenta inactiva responde sin computar BCrypt vs credenciales malas que sí lo computan):
      el 401 genérico iguala el mensaje pero no el tiempo de respuesta — no sobre-vender el
      anti-enumeration ante el jurado.
- [ ] T041 **JD2-006 (documental, opcional)**: uniformar la redacción de los requisitos SHALL de
      NIST SP 800-63B-4 entre `specs/001-auth-login/spec.md`, `specs/001-auth-login/research.md` y
      `specs/001-auth-login/quickstart.md` (misma forma de citar §3.1.1.2, mínimo de 15 y
      blocklist diferida). Se ejecuta después de T038/T040 porque toca los mismos archivos.
- [ ] T042 Validación final: ejecutar `./mvnw clean verify` (unit + IT + reporte JaCoCo) y
      recorrer el flujo curl completo de `specs/001-auth-login/quickstart.md` (pasos 0–4 + tabla
      de verificaciones: 401 genérico, cuenta inactiva, flags de cookie, 422 de política, 429 con
      `Retry-After`, logout). Registrar cualquier desviación antes de dar por cerrada la feature.
- [X] T043 **Hardening menor post-JD3 (JD3-001 + JD3-007 + JD3-012)** — se adelanta: ejecutar
      **antes de US2 (T028)**; a diferencia del resto de la fase, sí cambia comportamiento:
      (1) **JD3-001**: exponer `Retry-After` al SPA cross-origin en
      `src/main/java/com/uniremington/api/tramita/shared/config/SecurityConfig.java`
      (`config.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER))`) — sin esto, `fetch` no
      puede leer el header del 429 en el deploy por subdominios (D3/D9);
      (2) **JD3-007**: `LoginThrottlingFilter.shouldNotFilter` con `PathPatternRequestMatcher`
      context-path-aware, alineado con el matcher del login — hoy compara `getRequestURI()`
      crudo y un context-path configurado saltearía el throttling en silencio;
      (3) **JD3-012**: timestamps de auditoría de `User` en UTC
      (`LocalDateTime.now(ZoneOffset.UTC)` en `@PrePersist`/`@PreUpdate`), coherentes con el
      `Clock` UTC de la app (columnas `TIMESTAMP` sin zona).
      Cierre: `./mvnw verify` en verde.
- [X] T044 **JD3-002: eviction en `LoginAttemptService`** — ejecutar junto a T043, antes de US2
      (T028). RED primero en `LoginAttemptServiceTest` (con el `Clock` inyectable): una clave
      cuyo último fallo superó la ventana de 15 min desaparece del mapa tras el barrido. GREEN:
      barrido `@Scheduled` con el período de la ventana que remueva las deques expiradas
      (requiere `@EnableScheduling`) y remoción de deques vacías también en
      `retryAfterSeconds()`. Motivo: hoy una clave que no se re-consulta nunca se libera —
      crecimiento de memoria sin techo ante spray de emails distintos en el path `permitAll`.
      Nota oportunista: si la sincronización se reescribe con `Map.compute(...)`, la race
      benigna JD3-003 (fallo perdido por *check-then-act*, solo *undercount*) se cierra de
      paso — documentarlo si ocurre.
- [X] T045 **JD3-008: cobertura del branch 400 del login** — test-only, en el batch pre-US2
      junto a T043/T044. Dos métodos nuevos en `AuthControllerIT` (email propio por test —
      ver higiene JD3-010 en las notas): (1) JSON malformado y campos vacíos →
      **400 `problem+json`** (no 401); (2) varios bodies malformados seguidos **no** cuentan
      para la ventana de throttling (no producen 429 ni contaminan el contador). Motivo: la
      rama `InvalidLoginRequestException` → 400 decide «400 vs 401 vs contar intento» y hoy
      no tiene cobertura — una regresión que contara los malformados podría auto-bloquear a
      la usuaria vía throttling sin que ningún test lo detecte.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup. **Bloquea todas las user stories** (cuenta
  semilla + política + plumbing de seguridad base).
- **US1 (Phase 3)**: depende de Foundational. No depende de otras historias. **Es el MVP.**
- **US2 (Phase 4)**: depende de Foundational; reutiliza `LoginAttemptService` (T021) y el 401 de
  sesión de la US1 para su Independent Test → implementar después de US1.
- **US3 (Phase 5)**: depende de US2 (afina el 422 y el contrato del cambio de clave).
- **US4 (Phase 6)**: depende de Foundational y de la US1 (necesita una sesión que cerrar y
  extiende `AuthControllerIT`).
- **Polish (Phase 7)**: depende de todas las historias deseadas (T037–T041 son documentales y
  podrían adelantarse, pero T042 exige todo completo).

### Dependencias internas clave

- T010 (PasswordPolicy) requiere T009 en RED; T015 (seeder) requiere T007, T008, T010 y el
  encoder de T013.
- T021 requiere T016 en RED; T026 requiere T020–T025; T027 requiere T019.
- T030 requiere T028 en RED y reutiliza T010/T021; T032 requiere T030/T031.
- T036 requiere T035 en RED.
- T041 corre después de T038 y T040 (mismos archivos).
- T043–T045 (hardening post-JD3) se ejecutan antes de T028 (US2); T044 requiere su RED en
  `LoginAttemptServiceTest`; T045 es test-only sobre `AuthControllerIT`.

### User Story Dependencies

- **US1 (P1)**: arranca tras Foundational — independiente.
- **US2 (P2)**: tras US1 (reuso del throttling y de la sesión).
- **US3 (P2)**: tras US2 (misma superficie: política + 422 + contrato).
- **US4 (P3)**: tras US1 (extiende la IT y cierra el ciclo de sesión).

---

## Parallel Example: User Story 1

```text
# Lote 1 — tests en RED (archivos distintos):
Tarea T016: LoginAttemptServiceTest en src/test/java/com/uniremington/api/tramita/auth/LoginAttemptServiceTest.java
Tarea T017: AuthControllerIT (escenarios US1) en src/test/java/com/uniremington/api/tramita/auth/AuthControllerIT.java

# Lote 2 — DTOs y servicios (archivos distintos):
Tarea T018: LoginRequest · Tarea T019: CurrentUserResponse
Tarea T020: AppUserDetailsService · Tarea T021: LoginAttemptService

# Lote 3 — piezas del pipeline de login (archivos distintos):
Tarea T022: JsonAuthenticationConverter · Tarea T023: AuthSuccessHandler
Tarea T024: AuthFailureHandler · Tarea T025: LoginThrottlingFilter

# Secuencial al final (mismo archivo compartido / dependencias):
Tarea T026: wiring en SecurityConfig → Tarea T027: AuthController GET /me
```

En Foundational: T007, T008, T009 en paralelo tras T006; T011, T012, T014 en paralelo tras T010.
En Polish: T037–T040 en paralelo (archivos distintos); T041 y T042 al final.

---

## Implementation Strategy

### MVP First (solo User Story 1)

1. Completar Phase 1 (Setup) y Phase 2 (Foundational — bloqueante).
2. Completar Phase 3 (US1 — Iniciar sesión).
3. **PARAR y VALIDAR**: `./mvnw -Dtest=AuthControllerIT test` + quickstart.md pasos 0–2.
4. Demo posible ante la Coordinación/jurado: login funcional con seguridad completa
   (cookie endurecida, 401 genérico, CSRF, throttling, rotación de sesión).

### Incremental Delivery

1. Setup + Foundational → base que arranca con cuenta provisionada.
2. US1 → probar → demo (MVP).
3. US2 → probar (SC-003) → demo de rotación de contraseña.
4. US3 → contrato espejable + 422 con motivos (backend listo para el SPA futuro).
5. US4 → logout 204 → ciclo de sesión completo.
6. Polish → notas JD2 + validación final (T042).

Cada incremento agrega valor sin romper los anteriores; la IT única (`AuthControllerIT`) crece
con las historias (US1 la crea, US4 la completa) y actúa como red de regresión del filter chain.

---

## Notes

- Tests deliberadamente mínimos (constitución, Principio V): 1 IT de la cadena de filtros +
  3 unit tests de lógica sensible (T009, T016, T028). No agregar tests de DTOs/getters/mapeos.
- Integridad: ningún test se amaña para forzar el verde o el RED — ver el bloque
  **Integridad de los tests** al inicio de este documento.
- Login y logout NO tienen métodos de controller: viven en el filter chain (D5). El único
  controller es `AuthController` (`GET /me`, `POST /password`).
- Las notas JD2 de la Ronda 2 están integradas: JD2-001 → T036 · JD2-002 → T025 · JD2-003 →
  T032 (código) + T037 (doc) · JD2-004 → T022/T024 · JD2-005 → T038 · JD2-006 → T041 ·
  JD2-007 → T039 · JD2-008 → T040.
- Las notas JD3 (código, triage 2026-07-13) están integradas: JD3-001/007/012 → T043 ·
  JD3-002 → T044 (JD3-003 se cierra de paso si se reescribe la sincronización) · JD3-008 →
  T045 · JD3-004 → **refutado con evidencia** (docs honestas en research D4/quickstart/código
  + nota en T036) · JD3-005 → openapi (400 solo JSON/campos vacíos) · JD3-006 → ya cubierta
  por T040 · JD3-009 → T039 · JD3-010 → higiene de la IT (línea siguiente) · JD3-011 → T017
  reescrita · JD3-013 → data-model.md · JD3-003/014 → aceptadas documentadas (traza en
  Notion/engram).
- Higiene de la IT (JD3-010): `AuthControllerIT` comparte estado entre métodos (singleton del
  throttling + fila `users` sin rollback automático). Cada test nuevo usa un **email propio**
  (patrón del escenario de throttling) y restaura lo que mute (patrón try/finally del
  escenario de cuenta inactiva).
- Commit al cierre de cada tarea o grupo lógico (el auto-commit de Spec Kit cubre el cierre de
  fase); detenerse en cualquier checkpoint deja un incremento demostrable.
