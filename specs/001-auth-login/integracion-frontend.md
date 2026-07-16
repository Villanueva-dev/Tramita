# Contrato/Guía de integración — Auth (Coordinación)

> Fuente de verdad: `specs/001-auth-login/contracts/openapi.yaml` y el código del backend. Este
> doc lo traduce a "qué tiene que hacer el frontend". Si algo diverge, manda el OpenAPI.
>
> Alcance: **solo auth** (login, sesión, cambio de contraseña, logout). Trámites = Fase B (§10).

## 0. Topología de desarrollo

| Pieza | Dev | Nota |
|-------|-----|------|
| Backend | `http://localhost:8080` | perfil `dev` → cookie de sesión con `Secure=false` (si no, no viaja por http) |
| Frontend (Next) | `http://localhost:3000` | dev server |
| Base de datos | Postgres `localhost:5433` | `docker start tramita-postgres` |

**Recomendado: proxy same-origin.** Agregar rewrites en `next.config.mjs` para que el navegador
vea todo bajo `localhost:3000`. Así `SameSite=Strict` funciona, las cookies fluyen y **no hace
falta CORS**:

```js
// next.config.mjs
async rewrites() {
  return [{ source: '/api/:path*', destination: 'http://localhost:8080/api/:path*' }];
}
```

> Alternativa sin proxy (fetch directo a `:8080`): hay que setear en el backend
> `APP_CORS_ALLOWED_ORIGINS=http://localhost:3000` (sin esa env var el backend **no arranca**:
> fail-fast) y usar `credentials: 'include'`. El proxy es más simple — es la vía recomendada.

**Credenciales de dev**: el backend siembra la cuenta real desde `SEED_COORD_EMAIL` /
`SEED_COORD_PASSWORD` (`.env`, sin defaults). NO son las del mock de la maqueta
(`ana.restrepo@remington.edu.co / Coord2025!`). Villa le pasa a Juan el par sembrado.

## 1. Endpoints (resumen)

Base: `/api`. Errores siempre en `application/problem+json` (RFC 7807).

| Método | Path | Sesión | CSRF | Body | Éxito |
|--------|------|:------:|:----:|------|-------|
| `POST` | `/api/auth/login` | — | ✅ | `LoginRequest` | `204` + cookie de sesión |
| `GET` | `/api/auth/me` | ✅ | — | — | `200` `CurrentUserResponse` |
| `POST` | `/api/auth/password` | ✅ | ✅ | `ChangePasswordRequest` | `204` |
| `POST` | `/api/auth/logout` | ✅ | ✅ | — | `204` (borra ambas cookies) |

Shapes (JSON):

```jsonc
// LoginRequest
{ "email": "string (formato email, máx 255)", "password": "string" }
// ChangePasswordRequest
{ "currentPassword": "string", "newPassword": "string" }
// CurrentUserResponse (respuesta de /me)
{ "email": "string", "active": true }
// Problem (todos los errores) — solo title y status son garantizados
{ "type": "uri?", "title": "string", "status": 401, "detail": "string?", "instance": "uri?" }
```

## 2. Cookies — qué ve y qué NO ve el JS

| Cookie | ¿JS la lee? | Rol |
|--------|:-----------:|-----|
| `TRAMITA_SESSION` | ❌ `HttpOnly` | sesión opaca; `Secure` (dev=false), `SameSite=Strict`, `Path=/` |
| `XSRF-TOKEN` | ✅ (no HttpOnly) | token CSRF para el double-submit |

**Consecuencia crítica (arregla el bug de la maqueta):** el JS **no puede leer la cookie de
sesión**, así que "¿estoy logueado?" **no** se decide con una bandera en estado de React (por eso
F5 cierra sesión hoy). La única fuente de verdad es `GET /api/auth/me`: `200` = hay sesión,
`401` = no.

## 3. Flujo CSRF (double-submit) — `csrf.spa()`

1. **Sembrar el token**: hacer un `GET` cualquiera del chain (p. ej. `/api/auth/me`) — aunque
   devuelva `401`, la respuesta **setea la cookie `XSRF-TOKEN`**.
2. Leer `XSRF-TOKEN` de `document.cookie`.
3. En **cada POST** (`login`, `password`, `logout`) enviar header `X-XSRF-TOKEN: <ese valor>`.
4. El **login NO rota el token** (verificado JD3-004): el valor previo sigue sirviendo.
   El **logout SÍ borra** `XSRF-TOKEN` → antes del próximo login, repetir el paso 1.
5. Si un POST devuelve `403` → releer `XSRF-TOKEN` y reintentar una vez.

## 4. Ciclo de vida de la sesión

- **Login** `204` → el navegador guarda `TRAMITA_SESSION` (HttpOnly). Acto seguido, `GET /me`
  para poblar la UI con `{email, active}`.
- **Al montar la app y tras F5** → `GET /me`. `200` rehidrata la sesión; `401` manda a login.
  Esto reemplaza el "logueado en memoria" de la maqueta.
- **Inactividad > 30 min** → la sesión expira; `/me` empieza a dar `401`.
- **Logout** `204` → invalida la sesión server-side y borra `TRAMITA_SESSION` + `XSRF-TOKEN`.
  Limpiar estado local y re-sembrar CSRF (paso 3.1) antes del siguiente login.

## 5. Detalle por endpoint (errores exactos)

### `POST /api/auth/login`
- `204` — éxito, **sin body**. Sesión en la cookie.
- `400` — body malformado / campos vacíos. `title: "Solicitud inválida"`, con `detail`.
- `401` — credenciales inválidas. `title: "Credenciales inválidas"`, **sin detail**. **Genérico
  a propósito**: no revela si falló email, contraseña o cuenta inactiva (anti-enumeración). En la
  UI: un único mensaje "Correo o contraseña incorrectos".
- `403` — CSRF ausente/inválido (ver §3, paso 5).
- `429` — throttling: tras **5 fallos** por (email+IP) en **15 min**. Header **`Retry-After`**
  (segundos). Auto-reparable, nunca permanente. UI: "Demasiados intentos, probá en N s".

### `GET /api/auth/me`
- `200` — `{ "email": "...", "active": true }` (snapshot de la sesión, no toca BD).
- `401` — no hay sesión. **No es un error a mostrar**: es la señal de "andá a login".

### `POST /api/auth/password`  (cambio de contraseña — US2/US3)
- `204` — cambiada. La sesión sigue activa (se rota su id internamente).
- `401` — **solo** sesión ausente/expirada (mapa un-código-una-causa).
- `403` — CSRF (ver §3, paso 5).
- `422` — rechazo de negocio. `title: "Regla de negocio incumplida"`; el `detail` distingue la
  causa (textos EXACTOS del backend):
  - `"La contraseña actual es incorrecta"`
  - Violaciones de política, unidas por `"; "` (puede venir más de una):
    - `"La contraseña debe tener al menos 15 caracteres"`
    - `"La contraseña no debe superar 72 bytes en UTF-8 (la ñ y las letras acentuadas cuentan doble)"`
    - `"La nueva contraseña debe ser distinta de la actual"`
- `429` — mismo throttling del login (la verificación de la contraseña actual cuenta como intento).

### `POST /api/auth/logout`
- `204` — ver §4. `401` si no había sesión; `403` si falta CSRF.

## 6. Política de contraseña — espejo en el cliente (US3)

El servidor **siempre re-valida** (el espejo es UX, no seguridad). Reglas a replicar en vivo:

| Regla | Backend | JS equivalente |
|-------|---------|----------------|
| Mínimo 15 caracteres | `password.length() >= 15` | `pw.length >= 15` |
| Máximo 72 **bytes UTF-8** | `getBytes(UTF_8).length <= 72` | `new TextEncoder().encode(pw).length <= 72` |
| Distinta de la actual | `!newPassword.equals(current)` | `next !== current` |
| Confirmación coincide | (UI) | `next === confirm` |

Sin composición forzada (no exigir mayúsculas/números/símbolos). El máximo se mide en **bytes**,
no en caracteres: una frase con `ñ`/acentos llega a 72 bytes con menos de 72 caracteres.

## 7. Mapeo de errores → UI (tabla de acción)

| Status | Endpoint | Qué hizo el frontend mal / qué mostrar |
|:------:|----------|----------------------------------------|
| `400` | login/password | body mal armado — bug del cliente; mostrar `detail` |
| `401` | login | "Correo o contraseña incorrectos" (genérico, sin detail) |
| `401` | me/password/logout | no hay sesión → redirigir a login (no es "error") |
| `403` | cualquier POST | CSRF: releer `XSRF-TOKEN` y reintentar una vez (§3, paso 5) |
| `422` | password | mostrar `detail` (puede traer varias reglas con `"; "`) |
| `429` | login/password | leer `Retry-After`; deshabilitar submit N segundos |
| `5xx` | cualquiera | "Error del servidor" genérico (el backend no filtra detalles) |

## 8. Patrón de referencia (no implementación cerrada)

```ts
// lib/api.ts — cliente base
function readCookie(name: string): string | null {
  return document.cookie.split('; ').find(c => c.startsWith(name + '='))?.split('=')[1] ?? null;
}

async function apiFetch(path: string, opts: RequestInit = {}) {
  const method = (opts.method ?? 'GET').toUpperCase();
  const headers = new Headers(opts.headers);
  if (method !== 'GET') {
    headers.set('Content-Type', 'application/json');
    const csrf = readCookie('XSRF-TOKEN');
    if (csrf) headers.set('X-XSRF-TOKEN', decodeURIComponent(csrf));
  }
  return fetch(`/api${path}`, { ...opts, method, headers, credentials: 'include' });
}

// login: sembrar CSRF si hace falta → POST → rehidratar con /me
// rehidratación al montar: const res = await apiFetch('/auth/me'); if (res.ok) setUser(await res.json());
```

## 9. Checklist de cableado (maqueta actual → real)

- [ ] `next.config.mjs`: agregar los `rewrites` (§0).
- [ ] `lib/api.ts`: crear el cliente base (§8) con `credentials: 'include'` + CSRF.
- [ ] `lib/store.tsx`: **borrar el mock** (login simulado, credenciales hardcodeadas, `setTimeout`).
  - `login(email, pw)` → `POST /auth/login`; en `204`, `GET /me` y poblar `user`.
  - Quitar "logueado en memoria" como fuente de verdad → rehidratar con `GET /me` al montar y tras F5.
  - `logout()` → `POST /auth/logout`; limpiar estado; re-sembrar CSRF.
- [ ] Nueva **página de cambio de contraseña** (falta en la maqueta): form con espejo de política
  en vivo (§6) y manejo del `422` (§5).
- [ ] Mapear errores `problem+json` a la UI (§7).
- [ ] Resolver el error TS oculto por `ignoreBuildErrors: true` (`app/.../[id]/page.tsx:132`,
  `req` possibly undefined) — no es de auth, pero conviene apagar el `ignoreBuildErrors`.

## 10. Fuera de alcance (Fase B)

Trámites (adición de créditos, novedad de notas) requieren backend `002+` que **aún no existe**.
`lib/types.ts` de la maqueta es un **borrador de contrato**, no cablear todavía.

---

## Verificación (end-to-end, cuando se cablee)

1. Backend dev arriba: `docker start tramita-postgres` → `set -a; source .env; set +a;
   SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`.
2. Frontend: `next dev` (con los rewrites).
3. Recorrido feliz: cargar app (→ `/me 401` → login), login con credenciales seed (→ `204` →
   `/me 200` → dashboard), **F5 → sigue logueado**, cambiar contraseña (clave corta → `422` con
   motivo; válida → `204`), logout (→ `204` → `/me 401` → login).
4. Errores: clave mala → `401` genérico; 5 intentos → `429` + `Retry-After`; POST sin
   `X-XSRF-TOKEN` → `403`.
