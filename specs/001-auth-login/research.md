# Phase 0 — Research: Autenticación de la Coordinación (login)

Decisiones técnicas que resuelven el Technical Context del plan. Cada una con **trade-off
explícito** (Principio IV) y **fuente oficial** verificada vía Context7.

Fuentes base:
- Spring Security 7.0 Reference — https://docs.spring.io/spring-security/reference/7.0/ (Context7: `/websites/spring_io_spring-security_reference_7_0`)
- NIST SP 800-63B Rev 4 (26-ago-2025) — https://pages.nist.gov/800-63-4/sp800-63b.html
- OWASP Authentication Cheat Sheet — https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- OWASP Session Management Cheat Sheet — https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html

---

## D0 — Origen y encuadre de la feature: login solicitado por la tutora asignada

- **Origen del requisito**: la autenticación no formaba parte del alcance original del MVP.
  El árbol de problemas modela el motor de workflow (SP1–SP7) y no contempla un módulo de
  login. La feature se incorpora como **requerimiento solicitado por la tutora asignada
  durante la primera sesión de asesoría (Sprint 1)**; esta es su traza de procedencia
  (Principio IV — decisiones defendibles y trazables).
- **Encuadre de diseño (Principio I — KISS + YAGNI)**: se adopta la opción segura más simple
  que satisface el requerimiento —sesión del lado del servidor con cookie, sin SSO ni
  infraestructura especulativa—. El encuadre es consistente, además, con la postura que la
  Coordinación expresó sobre autenticación: preferencia por un mecanismo simple, sin
  autenticación estricta (Entrevista 3, Q9), y correo institucional como identificador mínimo
  (Entrevista 3, Q11).
- **Diferimiento de SSO (decisión sobre información pendiente)**: la integración con SSO
  institucional (Microsoft 365 / Entra ID) queda **diferida** a su confirmación con el área de
  tecnología (Entrevista 3, pendiente #4). La sesión con cookie se adopta como solución
  **provisional y migrable** a SSO una vez se confirme dicha integración; no se implementa en
  esta entrega (Principio I — YAGNI).

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
- **Carga diferida del token y `CsrfCookieFilter` (cierra F02 del triage)**: `csrf.spa()` usa
  *deferred loading* del token. Tras autenticar, la `CsrfAuthenticationStrategy` **rota el token
  pero la cookie `XSRF-TOKEN` no se re-escribe sola** en la respuesta: el token que el cliente
  cacheó antes del login queda obsoleto y los POST siguientes (cambio de clave, logout)
  fallarían con 403. El diseño incorpora por eso un **`CsrfCookieFilter`** propio: un
  `OncePerRequestFilter` de ~10 líneas que lee el `CsrfToken` del request para forzar el
  `Set-Cookie` de `XSRF-TOKEN` en cada respuesta, como muestra la guía de Spring Security para
  SPAs. Correlato del lado cliente: re-leer la cookie **después** del login antes de los POST
  siguientes (el quickstart lo refleja).
- **Fuente**: `CsrfConfigurer.spa()` —
  https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/config/annotation/web/configurers/CsrfConfigurer.html ;
  CSRF — Single Page Applications (deferred loading, `CsrfCookieFilter`) —
  https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html

## D5 — Login basado en filtro (`AuthenticationFilter` con credenciales JSON)

- **Decisión**: el login se procesa **dentro del filter chain** de Spring Security, no en un método
  de controller. Se registra un `AuthenticationFilter` sobre `POST /api/auth/login` con estas piezas:
  - Un `AuthenticationConverter` (componente custom, pocas líneas) que lee el body JSON
    `{email, password}` y produce un `UsernamePasswordAuthenticationToken`. Es el único componente
    con lógica propia.
  - `AuthenticationFilter(AuthenticationManager, AuthenticationConverter)` (clase de fábrica de
    Spring Security 7) que delega la validación en el `AuthenticationManager`.
  - Un `AuthenticationSuccessHandler` que devuelve **204** sin body (o
    `HttpMessageConverterAuthenticationSuccessHandler`, Security 6.4+, si se quisiera payload JSON);
    un `AuthenticationFailureHandler` que emite el **401 genérico** `problem+json` (ver D10).
  - El filtro persiste el `SecurityContext` configurando su `SecurityContextRepository` con
    `HttpSessionSecurityContextRepository` (para que la sesión quede en la `HttpSession`, no en el
    request). No se guarda el contexto a mano.
- **Rationale (corrige la vulnerabilidad de session fixation)**: al autenticar **dentro del filter
  chain**, el `SessionManagementFilter` invoca el `SessionAuthenticationStrategy` (composite que
  incluye `ChangeSessionIdAuthenticationStrategy` + `CsrfAuthenticationStrategy`), que **rota el id
  de sesión automáticamente** al autenticar. Esto entrega la protección contra session fixation que
  el contrato promete, sin depender de un `changeSessionId()` manual (fácil de omitir). El approach
  anterior de esta decisión —login programático en un controller— **se salta esos filtros** y NO
  produce la rotación: era una vulnerabilidad real (el contrato prometía una rotación que el diseño
  no entregaba), detectada en revisión adversarial antes de implementar.
- **Por qué filtro y no `formLogin`**: un SPA necesita respuestas JSON/estado HTTP, no los redirects
  de `formLogin`; y `formLogin` procesa `application/x-www-form-urlencoded`, mientras el contrato
  define `application/json` con `LoginRequest`. `AuthenticationFilter` + `AuthenticationConverter`
  da la API JSON **y** mantiene las protecciones de sesión del framework en el carril correcto.
- **Rationale KISS (Principio I)**: más KISS en el sentido de *mantenibilidad*, no de menos líneas.
  No se re-implementa a mano seguridad que el framework ya resuelve (rotación de sesión, persistencia
  del contexto) → menor superficie de error humano. Defendible ante jurado: se usa el mecanismo
  idiomático de Spring Security, no seguridad casera.
- **Trade-off**: más piezas de configuración (converter + filtro + 2 handlers + registro en el
  chain vía `http.addFilterBefore(...)`) frente a un `@PostMapping`. A cambio, session-fixation y
  persistencia del contexto las provee el framework, no el código propio.
- **Alcance CSRF (ortogonal a esta decisión)**: la rotación del **token** CSRF en autenticación la
  hace `CsrfAuthenticationStrategy`, pero el re-emitido de la cookie `XSRF-TOKEN` bajo `csrf.spa()`
  (deferred loading) requiere un `CsrfCookieFilter` — se resuelve en D4, no aquí. Cambiar a login
  basado en filtro **no** elimina esa necesidad.
- **Fuente** (verificado vía Context7 sobre Spring Security 7.0 Reference):
  - `AuthenticationFilter(AuthenticationManager, AuthenticationConverter)` —
    https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/web/authentication/AuthenticationFilter.html
  - `HttpMessageConverterAuthenticationSuccessHandler` (Since 6.4) —
    https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/web/authentication/HttpMessageConverterAuthenticationSuccessHandler.html
  - `SessionManagementFilter` → `SessionAuthenticationStrategy` (protección session fixation) y
    registro de filtros en el chain (`addFilterBefore`) —
    https://docs.spring.io/spring-security/reference/7.0/servlet/architecture.html

## D6 — Contraseñas: BCrypt; política NIST SP 800-63B-4

- **Decisión**: `BCryptPasswordEncoder` (cost por defecto 10) vía `DelegatingPasswordEncoder`.
  Política server-side: mínimo **15 caracteres**, máximo **72 bytes en UTF-8**, sin composición
  obligatoria, sin rotación forzada; en cambio de clave, la nueva **debe diferir** de la actual;
  sin historial de contraseñas.
- **Rationale**: NIST SP 800-63B-4 exige mínimo 15 cuando la contraseña es el único factor y
  desaconseja reglas de composición y rotación periódica. El **máximo 72 no es arbitrario**:
  BCrypt trunca la entrada a **72 bytes, no 72 caracteres** (OWASP Password Storage Cheat Sheet).
  En UTF-8 los caracteres fuera de ASCII cuentan más de un byte (`ñ` y las vocales acentuadas
  ocupan 2) — para una usuaria hispanohablante, una frase de paso con tildes es el caso normal,
  no el borde. Por eso `PasswordPolicy` valida el máximo **en bytes UTF-8**
  (`password.getBytes(UTF_8).length <= 72`) y el mínimo **en caracteres** (NIST lo expresa en
  caracteres, y 15 caracteres son siempre ≥ 15 bytes).
- **Trade-off**: BCrypt (no Argon2) por estar ya en el chasis Spring Security y ser suficiente
  para el modelo de amenaza del MVP; el límite de 72 bytes es su costo conocido y aquí se
  convierte en la regla FR-006.
- **Tensión resuelta (E3 Q9 «sin autenticación estricta»)**: el pedido de la Coordinación de
  "algo simple, sin autenticación estricta" se interpreta como **no construir autenticación
  institucional pesada** (SSO, identidad estricta, doble factor), no como relajar la higiene de
  seguridad. El diseño respeta ese pedido en el **mecanismo** (sesión-cookie, factor único, sin
  SSO — ver D0/D1); las defensas restantes (throttling, CSRF, mensaje genérico) son invisibles
  para la usuaria y no añaden fricción. El único parámetro con fricción real —el mínimo de 15
  caracteres— es el **piso de NIST para contraseña como factor único** (SHALL, SP 800-63B-4
  §3.1.1.2, verificado), no rigidez adicional: bajarlo a 8 exigiría sumar un segundo factor
  (MFA), lo que aumentaría la complejidad en dirección contraria a KISS y al propio pedido.
- **No-conformidad consciente (blocklist)**: la misma sección de NIST citada para el mínimo de
  15 (SP 800-63B-4 §3.1.1.2) también exige (**SHALL**) verificar las contraseñas contra una
  blocklist de contraseñas comprometidas. Este MVP **difiere ese SHALL como no-conformidad
  consciente y documentada** — no como feature opcional de v2 —, porque el riesgo residual es
  bajo: una única cuenta, provisionada por seed con frase de paso de 15+ y rotada por la propia
  Coordinación. El punto de extensión queda localizado en `PasswordPolicy`. *Plan B si el tutor
  o el jurado la exigen*: lista top-1000 embebida como recurso + un `contains` en
  `PasswordPolicy` (~1 archivo, ~5 líneas) — se anota como extensión localizada, no se construye.
- **Fuente**: NIST SP 800-63B-4 §3.1.1.2 (mínimo 15 para factor único, SHALL) —
  https://pages.nist.gov/800-63-4/sp800-63b.html ;
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
- **Impacto de D5 (login basado en filtro)**: con el login movido al filter chain, el conteo de
  intentos ya no cuelga de un método de controller. El chequeo del umbral (¿bloqueado? → 429) debe
  evaluarse **antes** de que el `AuthenticationFilter` intente autenticar, y el registro de
  fallo/limpieza debe engancharse a los resultados de la autenticación. Enfoque preferido (a
  confirmar en implementación): un `LoginThrottlingFilter` propio ubicado **antes** del
  `AuthenticationFilter`, que corta con 429 si la clave `(email + IP)` supera el umbral; el registro
  de fallo se hace en el `AuthenticationFailureHandler` y la limpieza del contador en el
  `AuthenticationSuccessHandler`. El `LoginAttemptService` en memoria (email+IP, ventana deslizante)
  no cambia; cambia **dónde se lo invoca**. Se prefiere el filtro dedicado (SRP) sobre incrustar la
  lógica en un `AuthenticationProvider`.
- **Alcance ampliado a `/auth/password` (cierra F13 del triage)**: el cambio de clave verifica
  la contraseña actual — es un **segundo oráculo de contraseña** y FR-010 habla de «intentos de
  autenticación», que es exactamente esto. Se reutiliza el **mismo** `LoginAttemptService` en el
  endpoint de cambio de clave (misma ventana deslizante, misma clave de conteo email+IP):
  superado el umbral → **429** con header `Retry-After`, igual que en login (documentado también
  en el contrato). Es reuso, no construcción nueva; deja una historia coherente para el jurado:
  *todo punto que verifica una contraseña tiene throttling*.

## D8 — Provisión de la cuenta (seed sin secreto en git)

- **Decisión**: `CoordinationUserSeeder` (boot-time, idempotente) que crea la cuenta si no existe,
  leyendo email y contraseña inicial de **variables de entorno** (`SEED_COORD_EMAIL`,
  `SEED_COORD_PASSWORD`) y hasheando con el `PasswordEncoder` de la app. **No** se commitea ningún
  hash ni contraseña al repo.
- **Rationale**: el spec pide provisión por semilla/migración; pero commitear un hash a git es un
  secreto en el repo. El seeder por env respeta el patrón fail-fast de Convenia ("no defaults para
  secretos") y usa el encoder real, garantizando consistencia con el login.
- **Consistencia con la política — sin puertas traseras (cierra F14 del triage)**: el seeder
  (a) **normaliza** `SEED_COORD_EMAIL` con la misma regla del login (minúsculas), y (b)
  **valida** `SEED_COORD_PASSWORD` contra `PasswordPolicy`, **fallando el arranque** (fail-fast)
  si no cumple. Sin esto podría nacer una cuenta cuyo estado sería imposible de crear por la app
  (p. ej. seed de 10 caracteres) — y la cuenta seed **es** la cuenta real de la Coordinación.
  Una sola política, aplicada también en el nacimiento de la cuenta; coherente con el patrón
  fail-fast ya adoptado.
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
  genérico** ("Credenciales inválidas") sin revelar qué campo falló. Cambio de clave: **todo
  rechazo de negocio del body → 422** con `detail` del motivo (actual incorrecta · nueva viola
  política · nueva igual a la actual); throttling → 429 (ver mapa de códigos más abajo).
- **Rationale**: FR-002 + SC-002 (anti-enumeración de usuarios). Distinguir "email no existe" de
  "clave incorrecta" o "cuenta inactiva" filtra información al atacante.
- **Fuente**: RFC 7807 ; OWASP Authentication Cheat Sheet (mensajes de error genéricos).
- **Impacto de D5 (login basado en filtro)**: con el login en el filter chain, el 401 genérico de
  **autenticación** lo produce un `AuthenticationFailureHandler`, no el `GlobalExceptionHandler`. El
  `AuthenticationManager` lanza excepciones distintas según la causa (`BadCredentialsException` por
  credenciales; `DisabledException` por cuenta inactiva, FR-011; `UsernameNotFoundException` por
  email inexistente). El failure handler **debe aplanar TODAS** a un único 401 genérico
  `problem+json` ("Credenciales inválidas"), sin ramificar por tipo de excepción, para preservar la
  anti-enumeración (FR-002/SC-002). Se apoya en `hideUserNotFoundExceptions=true` (default del
  `DaoAuthenticationProvider`) para que el email inexistente ya llegue como `BadCredentialsException`.
  El **cambio de clave** (US2) sigue siendo un endpoint de controller —no pasa por el filtro de
  login— y conserva su manejo vía `GlobalExceptionHandler`.
- **Mapa de códigos en `/auth/password` (decisión cerrada 2026-07-10, cierra F08 del triage)**:
  «contraseña actual incorrecta» devuelve **422**, no 401. La credencial de la *request* (la
  cookie de sesión) es válida, así que `401 Unauthorized` sería semánticamente falso
  (RFC 9110 §15.5.2 — https://www.rfc-editor.org/rfc/rfc9110#section-15.5.2); lo que falla es el
  contenido del body para esta operación → `422 Unprocessable Content`. La anti-enumeración que
  justifica el 401 genérico del login (FR-002/SC-002) **no aplica aquí**: la usuaria ya está
  autenticada y opera sobre su propia cuenta — no hay nada que enumerar. Cada código queda
  reservado a **una sola causa**:

  | Código | Única causa en `/auth/password` |
  |--------|--------------------------------|
  | `401` | Sesión ausente o expirada |
  | `403` | Fallo CSRF (token ausente o inválido) |
  | `422` | Rechazos de negocio del body: actual incorrecta · nueva viola política · nueva = actual (distinguibles por `detail`) |

  (El `429` de throttling, D7, completa el mapa.) El interceptor del SPA queda trivial y sin
  falsos positivos: `401 → re-login`, `403 → refrescar token`, `422 → mostrar mensaje en el
  formulario`. Se descartó `403` para la contraseña incorrecta porque colisionaría con el 403
  de CSRF del mismo endpoint, y mantener `401` deslogearía a la usuaria por un typo con una
  sesión perfectamente válida.
- **Rotación de sesión tras cambio de clave (decisión cerrada 2026-07-10, cierra F12 del
  triage)**: tras un cambio de clave exitoso, el endpoint llama `request.changeSessionId()`.
  OWASP recomienda renovar el id de sesión tras cualquier cambio de credencial o privilegio
  (Session Management Cheat Sheet —
  https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#renew-the-session-id-after-any-privilege-level-change).
  La usuaria sigue logueada (sin fricción) y el id viejo muere. Se pide **explícito** porque el
  cambio de clave no pasa por el filter chain de login, donde la rotación la da el framework
  (D5) — cierra la simetría con F01. *Extensión anotada, no construida*: invalidar todas las
  demás sesiones del usuario exigiría un `SessionRegistry` (configuración y estado extra) para
  un escenario marginal con un solo actor y store en memoria que se vacía en cada reinicio.

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
