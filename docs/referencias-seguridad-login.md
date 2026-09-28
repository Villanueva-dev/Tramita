# Referencias — Seguridad del login (autenticación por sesión con cookie HttpOnly)

> Fuentes que respaldan la decisión de arquitectura del login: sesión del lado del
> servidor + cookie `HttpOnly; Secure; SameSite=Strict`, en lugar de guardar el
> token en almacenamiento accesible por JavaScript (localStorage / JWT en el front).
>
> Verificadas el **2 de julio de 2026**; la referencia 3 se re-verificó el **19 de septiembre de 2026**,
> cuando el borrador que citaba pasó a ser el RFC 10017. Formateadas en **APA 7**.
> Revisá los puntos marcados con ⚠️ antes de pegarlas (ver "Notas de precisión" abajo).

---

## Listas para copiar (APA 7)

Spring. (2025). *Spring Security reference documentation* (Versión 7.0) [Documentación de software]. https://docs.spring.io/spring-security/reference/7.0/

OWASP Foundation. (s. f.). *Session management cheat sheet*. OWASP Cheat Sheet Series. Recuperado el 2 de julio de 2026, de https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html

Parecki, A., De Ryck, P., & Waite, D. (2026). *OAuth 2.0 for browser-based applications* (RFC N.º 10017; BCP N.º 212). Internet Engineering Task Force. https://www.rfc-editor.org/rfc/rfc10017.html

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
- **Autores**: A. Parecki (Okta), P. De Ryck (Pragmatic Web Security) y D. Waite (Ping Identity). Van como autores personales: el RFC publicado los lista en su encabezado, así que ya no hace falta recurrir a la IETF como autor institucional.
- **Título**: OAuth 2.0 for Browser-Based Applications.
- **Identificador**: RFC 10017, que además integra el BCP 212.
- **Estado**: **Best Current Practice publicada**, agosto de 2026. Ya no es un borrador: el trabajo se publicó como RFC tras la revisión -27 del draft `draft-ietf-oauth-browser-based-apps`, que fue la última.
- **Secciones citadas** (la numeración del borrador se conservó en el RFC):
  - §6.1.3.2 «Cookie Security»: "The BFF MUST enable the `HttpOnly` flag for its cookies". La obligación recae sobre el **BFF**, no sobre un backend cualquiera.
  - §6.1.4.2 «Mitigated Attack Scenarios»: "The BFF counters the first two attack scenarios by not exposing any tokens to the browser-based application".

---

## Notas de precisión (leer antes de citar)

- ⚠️ **Edición APA**: el ejemplo de la plantilla oficial ("Borges, J.L. (2013). *Ficciones*. Buenos Aires, Argentina: Debolsillo.") incluye el lugar de publicación, lo que corresponde a **APA 6.ª edición**. Las de arriba están en **APA 7.ª** (sin lugar). Confirmá con la tutora / la norma de la universidad qué edición usar y ajustá.
- ✅ **Autores del RFC 10017 — resuelto**: son Parecki, De Ryck y Waite, verificados en el encabezado del RFC publicado. La versión anterior de este archivo usaba la IETF como autor institucional porque no se habían comprobado los nombres.
- ⚠️ **Año de Spring Security 7**: 2025 es la fecha aproximada de la versión 7.0 (acompaña a Spring Boot 4). La doc es viva y no lleva una fecha de publicación única; por eso corresponde la fecha de recuperación si tu norma la pide.
- ✅ **Ya no hay que advertir que es un borrador.** Hasta la revisión del 2026-09-19 esta lista citaba `draft-ietf-oauth-browser-based-apps-26`, un Internet-Draft **que expiró el 7 de junio de 2026**, y esta nota aconsejaba identificarlo como estándar en desarrollo. El trabajo se publicó entretanto como **RFC 10017 / BCP 212** (agosto de 2026), de modo que ahora se cita una Best Current Practice cerrada. Conviene decirlo ante el jurado como lo que es: la fuente se fortaleció, no se reemplazó por otra.
