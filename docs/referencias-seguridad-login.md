# Referencias — Seguridad del login (autenticación por sesión con cookie HttpOnly)

> Fuentes que respaldan la decisión de arquitectura del login: sesión del lado del
> servidor + cookie `HttpOnly; Secure; SameSite=Strict`, en lugar de guardar el
> token en almacenamiento accesible por JavaScript (localStorage / JWT en el front).
>
> Verificadas el **2 de julio de 2026**. Formateadas en **APA 7**.
> Revisá los puntos marcados con ⚠️ antes de pegarlas (ver "Notas de precisión" abajo).

---

## Listas para copiar (APA 7)

Spring. (2025). *Spring Security reference documentation* (Versión 7.0) [Documentación de software]. https://docs.spring.io/spring-security/reference/7.0/

OWASP Foundation. (s. f.). *Session management cheat sheet*. OWASP Cheat Sheet Series. Recuperado el 2 de julio de 2026, de https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html

Internet Engineering Task Force. (2025). *OAuth 2.0 for browser-based applications* (Internet-Draft N.º draft-ietf-oauth-browser-based-apps-26). https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps

---

## Metadatos crudos (para reformatear a Vancouver u otra edición)

### 1. Spring Security 7 — documentación oficial
- **Autor/editor**: Spring (proyecto Spring, bajo Broadcom).
- **Título**: Spring Security Reference — Versión 7.0.
- **Corresponde a**: el chasis Spring Boot 4 / Java 21 heredado de Convenia.
- **Páginas específicas citadas en el texto**:
  - Session Management: https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/session-management.html
  - CSRF (método `csrf.spa()` para SPAs): https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html
  - CSRF and Stateless Browser Applications: https://docs.spring.io/spring-security/reference/7.0/features/exploits/csrf.html
- **Cita textual usada**: sobre apps con cookies de autenticación que igual son vulnerables a CSRF porque "the browser's automatic inclusion of stateful information makes them vulnerable".

### 2. OWASP — Session Management Cheat Sheet
- **Autor/editor**: OWASP Foundation (Open Worldwide Application Security Project).
- **Título**: Session Management Cheat Sheet (OWASP Cheat Sheet Series).
- **Citas textuales usadas**:
  - `HttpOnly`: "instructs web browsers not to allow scripts ... an ability to access the cookies via the DOM document.cookie object".
  - `Secure`: "only send the cookie through an encrypted HTTPS (SSL/TLS) connection".
  - `SameSite`: "Session cookies must explicitly set SameSite=Strict (preferred) or SameSite=Lax. Never use SameSite=None without Secure".
  - Web Storage: "Do not store authentication tokens, session IDs, JWTs, refresh tokens, or any credential in localStorage or sessionStorage. These APIs are accessible to any JavaScript executing in the origin, so a single XSS vulnerability discloses every token".

### 3. IETF — OAuth 2.0 for Browser-Based Applications
- **Autor/editor**: Internet Engineering Task Force (IETF). ⚠️ El borrador tiene autores individuales listados en su cabecera (p. ej. editores del grupo de trabajo OAuth) — si tu norma exige autores personales, tomalos del encabezado del draft.
- **Título**: OAuth 2.0 for Browser-Based Applications.
- **Identificador / versión**: draft-ietf-oauth-browser-based-apps-26.
- **Estado**: Internet-Draft, en vía a Best Current Practice. Publicado 4-dic-2025, expira 7-jun-2026.
- **Secciones citadas**: §6.1.3.2 (el backend "MUST enable the HttpOnly flag for its cookies") y §6.1.4.2 (el patrón BFF "counters ... by not exposing any tokens to the browser-based application").

---

## Notas de precisión (leer antes de citar)

- ⚠️ **Edición APA**: el ejemplo de la plantilla oficial ("Borges, J.L. (2013). *Ficciones*. Buenos Aires, Argentina: Debolsillo.") incluye el lugar de publicación, lo que corresponde a **APA 6.ª edición**. Las de arriba están en **APA 7.ª** (sin lugar). Confirmá con la tutora / la norma de la universidad qué edición usar y ajustá.
- ⚠️ **Autores del draft IETF**: usé la IETF como autor institucional porque no verifiqué los nombres personales. Si tu norma los exige, sacalos del encabezado del documento.
- ⚠️ **Año de Spring Security 7**: 2025 es la fecha aproximada de la versión 7.0 (acompaña a Spring Boot 4). La doc es viva y no lleva una fecha de publicación única; por eso corresponde la fecha de recuperación si tu norma la pide.
- El **IETF draft es un borrador**, no un RFC finalizado. Es legítimo citarlo, pero identificalo como *Internet-Draft* / *Best Current Practice en desarrollo*, no como estándar cerrado. Honestidad ante el jurado.
