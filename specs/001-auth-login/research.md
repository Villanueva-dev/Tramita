# Phase 0 — Research: Autenticación de la Coordinación (login)

Decisiones técnicas que resuelven el Technical Context del plan. Cada una con **trade-off
explícito** (Principio IV) y **fuente oficial** verificada vía Context7.

Fuentes base:
- Spring Security 7.0 Reference — https://docs.spring.io/spring-security/reference/7.0/ (Context7: `/websites/spring_io_spring-security_reference_7_0`)
- NIST SP 800-63B Rev 4 (26-ago-2025) — https://pages.nist.gov/800-63-4/sp800-63b.html
- OWASP Authentication Cheat Sheet — https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- OWASP Session Management Cheat Sheet — https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html

---

## D1 — Mecanismo de autenticación: sesión server-side, NO JWT

- **Decisión**: sesión HTTP del lado del servidor identificada por cookie opaca. El backend
  guarda el `SecurityContext` en la `HttpSession`; el navegador solo tiene un id de sesión
  inaccesible por script.
- **Rationale**: la constitución lo fija (Principio III). Para un único actor y un SPA propio,
  la sesión con cookie es la opción segura más simple: revocación inmediata (logout invalida la
  sesión en el server), sin exponer un token en JS, sin manejar refresh/expiración de JWT.
- **Trade-off**: se pierde el statelessness del JWT (irrelevante en single-instance) a cambio de
  simplicidad, revocación real y menor superficie XSS. Convenia usa JWT; **aquí se diverge a
  propósito** y se descartan las deps `jjwt`.
- **Alternativa rechazada**: JWT en `localStorage` (superficie XSS, sin revocación) o JWT en
  cookie (mismo costo de cookie pero con complejidad de firma/rotación que no aporta valor acá).

## D2 — Store de sesión: en memoria (MVP)

- **Decisión**: `HttpSession` en memoria del contenedor (default de Spring Boot).
- **Rationale**: KISS+YAGNI. Un solo actor, una sola instancia, demo ante jurado. Cero
  dependencias extra, cero tablas extra.
- **Trade-off**: las sesiones se pierden al reiniciar el backend (hay que volver a loguear) y no
  sirven para múltiples instancias. Aceptable para el MVP.
- **Upgrade documentado (no implementado)**: Spring Session JDBC persiste la sesión en PostgreSQL
  (tablas `SPRING_SESSION*` vía Flyway) → sobrevive reinicios y habilita HA. Es un cambio
  localizado (una dependencia + una migración + config), coherente con "la extensibilidad la da
  el proceso de migraciones, no las estructuras pre-construidas".

## D3 — Cookie de sesión: `HttpOnly; Secure; SameSite=Strict`

- **Decisión**: configurar la cookie de sesión con `HttpOnly=true`, `Secure=true`,
  `SameSite=Strict`, nombre neutro (`TRAMITA_SESSION`) para no filtrar el stack.
  Vía `application.yml`: `server.servlet.session.cookie.{http-only,secure,same-site,name}`.
- **Rationale**: FR-008 + OWASP Session Management. `HttpOnly` bloquea lectura por JS (mitiga
  robo por XSS); `Secure` restringe a HTTPS; `SameSite=Strict` es la defensa CSRF primaria.
- **Trade-off / tensión resuelta**: la constitución exige `SameSite=Strict`, pero el spec asume
  frontend y backend en **orígenes distintos**. `SameSite` opera a nivel de *site* (dominio
  registrable), no de *origin*. Resolución que **mantiene Strict**:
  - **Desarrollo**: el SPA usa un **dev-proxy** (p. ej. proxy de Vite) → las llamadas salen
    same-origin y la cookie Strict viaja sin problema.
  - **Producción**: desplegar SPA y API **same-site** (subdominios del mismo dominio registrable,
    p. ej. `app.` y `api.` de `tramita.<dominio>`) → Strict sigue funcionando.
  - **Solo** si el equipo obliga a un despliegue *cross-site real* (dominios registrables
    distintos) habría que relajar a `SameSite=None; Secure` + allowlist CORS estricta. Se documenta
    como desviación a decidir en despliegue, no se adopta por defecto.
- **Fuente**: OWASP Session Management Cheat Sheet (SameSite); MDN Set-Cookie SameSite.

## D4 — CSRF: `csrf.spa()` (Spring Security 7)

- **Decisión**: mantener la protección CSRF activa con el helper de Security 7 para SPAs:
  `http.csrf(csrf -> csrf.spa())`, que configura un `CookieCsrfTokenRepository` (cookie
  `XSRF-TOKEN` legible por JS) + el request handler adecuado. El SPA lee `XSRF-TOKEN` y lo
  reenvía en el header `X-XSRF-TOKEN` en las peticiones que cambian estado (login, logout,
  cambio de clave).
- **Rationale**: defensa en profundidad. `SameSite=Strict` (D3) ya mitiga CSRF, pero OWASP
  recomienda no depender de una sola capa; `csrf.spa()` es la opción idiomática y de menor
  fricción en Security 7.
- **Trade-off**: el SPA debe hacer un GET inicial para recibir la cookie del token y reenviar el
  header. Costo mínimo frente a la garantía de doble verificación.
- **Fuente**: `CsrfConfigurer.spa()` —
  https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/config/annotation/web/configurers/CsrfConfigurer.html

## D5 — Login programático (endpoint REST, no formLogin con redirect)

- **Decisión**: `AuthController` expone `POST /api/auth/login` que autentica con
  `AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken)` y, en éxito, **guarda
  explícitamente** el `SecurityContext` en la sesión vía `HttpSessionSecurityContextRepository`
  (en Security 6/7 el contexto ya no se persiste solo). Devuelve 200/204, no un redirect HTML.
- **Rationale**: un SPA necesita respuestas JSON/estado HTTP, no los redirects de `formLogin`.
- **Trade-off**: un poco más de código explícito (guardar el contexto a mano) a cambio de una
  API limpia para el cliente. *(Confianza media-alta en el detalle de persistencia manual del
  contexto: verificar contra "Storing the SecurityContext" de la doc de Security 7 al implementar.)*
- **Fuente**: Spring Security 7 Reference — Authentication / SecurityContextRepository.

## D6 — Contraseñas: BCrypt; política NIST SP 800-63B-4

- **Decisión**: `BCryptPasswordEncoder` (cost por defecto 10) vía `DelegatingPasswordEncoder`.
  Política server-side: longitud **15..72**, sin composición obligatoria, sin rotación forzada;
  en cambio de clave, la nueva **debe diferir** de la actual; sin historial de contraseñas.
- **Rationale**: NIST SP 800-63B-4 exige mínimo 15 cuando la contraseña es el único factor y
  desaconseja reglas de composición y rotación periódica. El **máximo 72 no es arbitrario**:
  BCrypt trunca la entrada a 72 bytes, así que aceptar más daría una falsa sensación de fuerza.
- **Trade-off**: BCrypt (no Argon2) por estar ya en el chasis Spring Security y ser suficiente
  para el modelo de amenaza del MVP; el límite de 72 bytes es su costo conocido y aquí se
  convierte en la regla FR-006.
- **Fuera de alcance (v2)**: blocklist de contraseñas filtradas (documentado en el spec). La
  política se concentra en `PasswordPolicy` para que incorporarla luego sea local.
- **Fuente**: NIST SP 800-63B-4 §3.1.1 — https://pages.nist.gov/800-63-4/sp800-63b.html ;
  OWASP Authentication Cheat Sheet (password storage / BCrypt 72-byte limit).

## D7 — Throttling anti fuerza bruta (FR-010)

- **Decisión**: `LoginAttemptService` en memoria (`ConcurrentHashMap`), ventana deslizante por
  **(email normalizado + IP del cliente)**. Umbral inicial sugerido: **5 intentos fallidos / 15
  min → 429** con header `Retry-After`; el contador se auto-repara al vencer la ventana. Un login
  exitoso limpia el contador. **Nunca** bloqueo permanente de cuenta.
- **Rationale**: FR-010 pide throttling temporal auto-reparable. La combinación email+IP evita que
  un atacante deje sin acceso a la Coordinación con solo martillar su email (el legítimo entra
  desde otra IP), sin la complejidad de un rate-limiter distribuido.
- **Trade-off**: en memoria no se comparte entre instancias y, tras un proxy, la IP puede ser la
  del proxy (leer `X-Forwarded-For` con cuidado). Aceptable en single-instance; el upgrade limpio
  es **Bucket4j** (token bucket, backend en memoria o distribuido) documentado, no adoptado.
- **Alternativa rechazada**: bloqueo de cuenta tras N intentos → viola FR-010 (sería bloqueo
  permanente / DoS del usuario legítimo).
- **Fuente**: OWASP Authentication Cheat Sheet — "Protect Against Automated Attacks" (rate
  limiting temporal en vez de account lockout).

## D8 — Provisión de la cuenta (seed sin secreto en git)

- **Decisión**: `CoordinationUserSeeder` (boot-time, idempotente) que crea la cuenta si no existe,
  leyendo email y contraseña inicial de **variables de entorno** (`SEED_COORD_EMAIL`,
  `SEED_COORD_PASSWORD`) y hasheando con el `PasswordEncoder` de la app. **No** se commitea ningún
  hash ni contraseña al repo.
- **Rationale**: el spec pide provisión por semilla/migración; pero commitear un hash a git es un
  secreto en el repo. El seeder por env respeta el patrón fail-fast de Convenia ("no defaults para
  secretos") y usa el encoder real, garantizando consistencia con el login.
- **Trade-off**: requiere setear dos env vars en cada entorno (documentado en quickstart) a cambio
  de no filtrar credenciales. La rotación de la clave inicial la hace la Coordinación vía US2.
- **Alternativa rechazada**: `V1.0.1__Seed_user.sql` con hash literal → secreto versionado en git.

## D9 — CORS con credenciales

- **Decisión**: `CorsConfigurationSource` con `allowedOrigins` = allowlist por configuración
  (env-driven, sin comodín), `allowCredentials=true`, métodos `GET/POST`, headers incluyendo
  `X-XSRF-TOKEN` y `Content-Type`.
- **Rationale**: el SPA en otro origen necesita enviar la cookie de sesión (`credentials: 'include'`);
  eso exige `allowCredentials=true`, que **prohíbe** `Access-Control-Allow-Origin: *` → allowlist
  explícita.
- **Trade-off**: hay que mantener la lista de orígenes por entorno. Es el precio correcto de
  seguridad; nunca comodín con credenciales.
- **Nota**: con el dev-proxy de D3 muchas llamadas son same-origin y no gatillan CORS; la config
  cubre el caso de despliegue same-site con subdominios distintos.
- **Fuente**: Spring Security 7 Reference — CORS ; MDN CORS con credenciales.

## D10 — Errores RFC 7807 y mensaje genérico anti-enumeración

- **Decisión**: `GlobalExceptionHandler` devuelve `application/problem+json`. **Toda** falla de
  autenticación (credenciales malas, email inexistente, cuenta inactiva) mapea a **un único 401
  genérico** ("Credenciales inválidas") sin revelar qué campo falló. Cambio de clave: actual
  incorrecta → 401 genérico; nueva viola política → 422 con `detail` del motivo; throttling → 429.
- **Rationale**: FR-002 + SC-002 (anti-enumeración de usuarios). Distinguir "email no existe" de
  "clave incorrecta" o "cuenta inactiva" filtra información al atacante.
- **Fuente**: RFC 7807 ; OWASP Authentication Cheat Sheet (mensajes de error genéricos).

---

## Resolución de NEEDS CLARIFICATION

| Ítem | Estado |
|------|--------|
| Framework del frontend | **Diferido a decisión del equipo.** El plan es framework-agnóstico; el contrato REST no depende del SPA. No bloquea el backend. |
| Store de sesión | Resuelto → D2 (en memoria, MVP). |
| SameSite vs orígenes distintos | Resuelto → D3 (Strict + dev-proxy + deploy same-site). |
| Mecanismo de throttling | Resuelto → D7 (in-memory sliding window). |
| Provisión de cuenta sin secreto en git | Resuelto → D8 (seeder por env). |

Sin `NEEDS CLARIFICATION` que bloqueen Phase 1.
