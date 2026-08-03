# Auditoría de seguridad — Backend Tramita

| Campo | Valor |
|---|---|
| **Fecha** | 2026-07-18 |
| **Alcance** | Backend completo en `main` (commit `069e47c`) — feature `001-auth-login` (única con código a la fecha) + configuración transversal e infraestructura |
| **Stack auditado** | Spring Boot 4.0.7 · Spring Security 7.0.6 · Java 21 · PostgreSQL 16 + Flyway |
| **Criterio** | Estándares mínimos de Spring Security para autenticación por sesión, bajo el principio KISS de la constitución del proyecto (v1.0.0) |
| **Veredicto** | **APTO** — cumple los estándares mínimos sin deshabilitar ningún default del framework; 0 hallazgos Alta, 1 Media accionable (M-1), 3 Baja (trade-offs ya documentados en el código) |

## 1. Metodología

Tres revisiones independientes en paralelo, con síntesis y verificación cruzada posterior:

1. **Configuración de Spring Security** — `SecurityConfig`, filtros custom, CORS, cookies, sesión. Las afirmaciones sobre comportamiento del framework se verificaron contra el **código fuente real de Spring Security 7.0.6** (`spring-security-web-7.0.6-sources.jar` y `spring-security-config-7.0.6-sources.jar` del repositorio Maven local), no contra documentación de memoria.
2. **Código de autenticación** — controllers, services, DTOs, políticas, seeder, manejo de errores.
3. **Infraestructura y configuración** — `pom.xml`, `application*.yml`, migración Flyway, `.env`/`.gitignore`, historial git completo (37 commits), README.

El hallazgo principal (M-1) fue detectado por dos revisiones independientes y confirmado por lectura directa del archivo.

## 2. Checklist de estándares mínimos

| Estándar | Estado | Evidencia |
|---|---|---|
| CSRF para SPA | ✅ Cumple | `SecurityConfig.java:101` — `csrf.spa()`: `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `SpaCsrfTokenRequestHandler` (XOR para render anti-BREACH, resolución plain por header). Patrón canónico de la [referencia oficial de CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) |
| Protección contra session fixation | ✅ Cumple | Verificado en fuente 7.0.6 (`AuthenticationFilter.java:220-223`): rota el id de sesión al autenticar vía `request.changeSessionId()` |
| Cookie de sesión endurecida | ✅ Cumple | `application.yml:32-36` — `TRAMITA_SESSION` con `HttpOnly`, `Secure`, `SameSite=Strict`, timeout de inactividad 30 min. `application-dev.yml` relaja **solo** `Secure`, con la desviación comentada |
| Autorización deny-by-default | ✅ Cumple | `SecurityConfig.java:107-109` — único `permitAll`: `POST /api/auth/login`; `anyRequest().authenticated()` |
| Headers de seguridad por defecto | ✅ Cumple | Sin bloque `.headers(...)` ni ningún `.disable()`: se conservan `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Cache-Control` y HSTS ([referencia de headers](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html)) |
| CORS restringido con credenciales | ✅ Supera | `CorsProperties.java:14-23` — fail-fast en el arranque si la allowlist está vacía o contiene `*`; métodos y headers acotados; solo sobre `/api/**` |
| Almacenamiento de contraseñas | ✅ Cumple | `DelegatingPasswordEncoder` con BCrypt (`SecurityConfig.java:55-58`); política de mínimo 15 caracteres (NIST SP 800-63B) y tope de 72 **bytes** UTF-8 que evita la truncación silenciosa de BCrypt (`PasswordPolicy.java:43`) |
| Anti-enumeración de usuarios | ✅ Cumple | `AuthFailureHandler.java:34-50` aplana toda `AuthenticationException` al mismo 401 «Credenciales inválidas»; `hideUserNotFoundExceptions` en su default `true` |
| Protección contra fuerza bruta | ✅ Supera | `LoginAttemptService` — 5 fallos / ventana deslizante de 15 min por email normalizado + IP; el 429 corta **antes** de autenticar; reset al éxito; sin lockout permanente; barrido programado de claves abandonadas |
| Logout completo | ✅ Cumple | `SecurityConfig.java:117-122` — invalida la sesión, borra `TRAMITA_SESSION` (y el framework borra `XSRF-TOKEN` vía `CsrfLogoutHandler`), responde 204, exige CSRF |
| Secretos fuera del código | ✅ Cumple | Todo por env vars **sin defaults** (fail-fast): BD, CORS y credenciales seed. `.env` ignorado y nunca commiteado; grep de secretos sobre los 37 commits del historial: 0 resultados |
| Errores sin fuga de internals | ✅ Cumple | RFC 7807 en ambos planos (MVC vía `GlobalExceptionHandler`, filter chain vía `ProblemJsonWriter`); el 500 genérico no expone mensaje ni stack trace |
| Validación de entrada | ✅ Cumple | Login validado en `JsonAuthenticationConverter`; cambio de clave con Bean Validation + `PasswordPolicy` server-side; normalización de email consistente en los 5 puntos de uso (`EmailNormalizer`) |
| Inyección SQL | ✅ Cumple | `UserRepository` solo usa queries derivadas de Spring Data; cero `@Query`, cero SQL nativo, cero concatenación |

## 3. Hallazgos

### M-1 (Media) — Buffering del body sin límite en endpoint no autenticado

**Ubicación**: `LoginThrottlingFilter.java:102` (`CachedBodyRequest`).

**Defecto**: `request.getInputStream().readAllBytes()` materializa en heap el body completo de `POST /api/auth/login` — el único endpoint `permitAll` — sin verificar `Content-Length` ni acotar la lectura.

**Por qué las mitigaciones aparentes no aplican**:

- `maxPostSize` de Tomcat solo limita el parseo de parámetros form-url-encoded, no lecturas crudas del stream ([doc de Tomcat](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html)).
- El CSRF no lo mitiga: con `CookieCsrfTokenRepository` (double-submit sin estado en servidor) un atacante directo fabrica su propio par cookie+header coincidente y el filtro llega igual a bufferear el body.

**Impacto**: requests con bodies de cientos de MB (o varios concurrentes) agotan la memoria de la única instancia antes de cualquier autenticación o throttling — denegación de servicio sin credenciales.

**Corrección recomendada (KISS)**: rechazar con 400/413 cuando `Content-Length` supere un tope pequeño (~8 KB; un login legítimo son dos campos) antes de `readAllBytes()`, o leer con límite de bytes. Estimación: ~5 líneas + test.

### B-1 (Baja, condicional al despliegue) — Clave de throttling atada a `getRemoteAddr()`

**Ubicación**: `LoginThrottlingFilter.java:35-36, 68` y `AuthFailureHandler.java:45`.

Correcto para el despliegue actual sin proxy (usar `X-Forwarded-For` sin proxy sería spoofeable; el trade-off está documentado en el javadoc). Si el despliegue final queda detrás de un reverse proxy, todos los clientes compartirán la IP del proxy: un atacante que conozca el email institucional podría mantener el 429 sobre la cuenta legítima con 5 intentos cada 15 min. **Acción**: nota de despliegue, no fix hoy.

### B-2 (Baja, trade-off aceptado) — El cambio de contraseña no invalida sesiones concurrentes

**Ubicación**: `AuthController.java:45-49`.

Rota el id de la sesión actual (`changeSessionId()`, alineado con OWASP Session Management) pero una sesión robada en otro dispositivo sobrevive al cambio de clave hasta expirar. Aceptado y documentado en el código para un MVP con una sola cuenta. Registrar como deuda si el sistema crece a multi-usuario.

### B-3 (Baja) — Sin control de sesiones concurrentes, y el camino estándar sería inefectivo

No hay `maximumSessions`. Impacto bajo (una cuenta de Coordinación). **Advertencia verificada en fuente 7.0.6**: agregar `.sessionManagement(s -> s.maximumSessions(1))` sería **silenciosamente inefectivo** con este login — `AuthenticationFilter` no invoca ninguna `SessionAuthenticationStrategy` ([referencia de session management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)). Implementarlo exigiría cableado manual (`ConcurrentSessionControlAuthenticationStrategy` + `SessionRegistry` + `HttpSessionEventPublisher` en el success handler). Documentado aquí para que un futuro "lo agregamos y listo" no cree una garantía falsa.

### Infraestructura (aceptable en dev, bloqueante pre-despliegue)

- **PostgreSQL dev con `postgres/postgres` y puerto 5433 publicado al host** (`README.md:124-131`): correcto para contenedor local con datos sintéticos; un despliegue real exige contraseña fuerte, usuario dedicado no-superusuario y puerto no expuesto fuera de la red interna.
- **`.env` con permisos 644**: recomendado `chmod 600 .env`. Riesgo mínimo en máquina personal; relevante en servidores compartidos.

## 4. Observaciones (no son defectos)

- **`CsrfCookieFilter` es redundante en Security 7.0.6**: `csrf.spa()` ya fuerza la carga eager del token y emite la cookie en cada request (verificado en fuente: `SpaCsrfTokenRequestHandler` → `SupplierCsrfToken` → `RepositoryDeferredCsrfToken.init()`). Mantenerlo es inofensivo (idempotente) y sirve como defensa documental; eliminarlo también sería válido por KISS.
- **Fixation residual del token CSRF pre-login** (no rota al autenticar): ya analizado y aceptado en JD3-004 — la sesión sí rota y el token CSRF no es una credencial.
- **Costo BCrypt en el default del framework (10)**: válido hoy; subir a 12 es endurecimiento opcional con costo de latencia en login.
- **Sesiones y throttling en memoria** (sin Spring Session): coherente con el alcance mono-instancia del MVP; reinicio del proceso = logout global. Limitación conocida si algún día hay más de una instancia.
- **Mitigación de timing para usuario inexistente**: es la estándar del `DaoAuthenticationProvider` (encode de un hash dummy); no se verificó empíricamente en esta auditoría.
- **`APP_CORS_ALLOWED_ORIGINS` vacía en `.env.example`**: no es un hueco — `CorsProperties.java:15-18` rompe el arranque con mensaje claro si la lista queda vacía. Fricción de onboarding, no de seguridad.
- **Sin Actuator**: hoy es superficie de ataque *menos*. Un despliegue real con orquestación necesitará `spring-boot-starter-actuator` con exposición restringida (`management.endpoints.web.exposure.include=health`).

## 5. Notas para la defensa

1. **Narrativa central**: el backend cumple los mínimos de Spring Security **sin deshabilitar un solo default del framework**, y cada desviación del estándar es un trade-off explícito documentado en el propio código (B-1, B-2, JD3-004).
2. Las decisiones de sesión (`HttpOnly`, stateful, sin JWT) se defienden con la checklist de la sección 2: fixation, cookie endurecida, CSRF de SPA canónico y logout completo son exactamente las garantías que el enfoque de sesión da "gratis" frente a JWT.
3. B-3 es un buen ejemplo de rigor para el jurado: se verificó contra el código fuente del framework que la solución "obvia" no funcionaría, en lugar de asumirla.

## 6. Fuentes

- Código fuente verificado localmente: `~/.m2/repository/org/springframework/security/spring-security-web/7.0.6/spring-security-web-7.0.6-sources.jar` y `spring-security-config-7.0.6-sources.jar`.
- [Spring Security — Cross Site Request Forgery](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security — Security HTTP Response Headers](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html)
- [Spring Security — Authentication Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [Apache Tomcat 11 — HTTP Connector (`maxPostSize`)](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html)
- NIST SP 800-63B — Digital Identity Guidelines (longitud mínima de contraseñas)
