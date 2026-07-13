# Phase 1 — Data Model: Autenticación de la Coordinación

Deriva del spec (Key Entities) y de las decisiones de `research.md`. Una sola entidad de
negocio: `User`. El schema lo gestiona **Flyway**; Hibernate solo valida.

## Entidad: `User` (tabla `users`)

Representa la identidad de la Coordinación que se autentica. En este alcance **no** tiene rol
ni sede (un único actor, una única sede).

| Campo | Tipo Java | Columna | Tipo SQL | Reglas |
|-------|-----------|---------|----------|--------|
| `id` | `UUID` | `id` | `UUID` | PK generada por la app (`GenerationType.UUID`, UUIDv4). **Nunca se expone** en la API (el identificador de negocio es el email). |
| `email` | `String` | `email` | `VARCHAR(255)` | `UNIQUE NOT NULL`. Identificador de login. Se **normaliza a minúsculas** al escribir y al buscar (login case-insensitive). |
| `passwordHash` | `String` | `password_hash` | `VARCHAR(255)` | `NOT NULL`. Hash con prefijo `{bcrypt}` del `DelegatingPasswordEncoder` (68 chars). Nunca en texto plano, nunca en un DTO de respuesta. |
| `active` | `boolean` | `active` | `BOOLEAN NOT NULL DEFAULT true` | Solo `true` puede autenticarse (FR-011). La desactivación **no** revoca una sesión ya establecida (ver «Estados / transiciones»). |
| `createdAt` | `LocalDateTime` | `created_at` | `TIMESTAMP NOT NULL` | Auditoría. Poblado por `@PrePersist` en la entity. |
| `updatedAt` | `LocalDateTime` | `updated_at` | `TIMESTAMP NOT NULL` | Auditoría. Poblado por `@PrePersist`/`@PreUpdate` en cada escritura (incluye cambio de clave). |

### Decisiones de modelado (trazables)

- **PK `UUID`, no `BIGINT IDENTITY`** (decisión del equipo, 2026-07-12 — revierte la elección
  inicial): aunque el id no sale del servidor hoy (el email es el identificador público), el
  equipo prefiere eliminar de raíz la clase de riesgo de enumeración ante una futura exposición
  del id (nuevas URLs, logs, integraciones), en lugar de depender de que esa invariante se
  sostenga al crecer el sistema. El costo es ~cero en este contexto (tabla de una fila: índice,
  tamaño y legibilidad no pesan). Trade-off aceptado: id no legible en debugging y sin orden de
  inserción. Generación **UUIDv4 en la app** (`@GeneratedValue(strategy = GenerationType.UUID)`
  de JPA/Hibernate) y no `DEFAULT gen_random_uuid()` en la DB: una sola fuente de generación —
  la app es la única que inserta (seeder vía JPA) — y la entity conoce su id antes del INSERT.
  UUIDv7 (ordenado por tiempo) se descartó: exigiría una librería externa y su beneficio
  (localidad de índice B-tree) es irrelevante con este volumen.
- **Email normalizado a minúsculas** en vez de extensión `citext`: KISS, sin depender de una
  extensión de PostgreSQL. La normalización vive en el service/repository.
- **`password_hash VARCHAR(255)`**: el límite de 72 bytes de BCrypt aplica al **plaintext** que
  el algoritmo acepta, no al tamaño del hash almacenado (dimensionar la columna con ese número
  era un error de categoría). Con `DelegatingPasswordEncoder` (ver `research.md` D6) se persiste
  el prefijo `{bcrypt}` más los 60 caracteres del hash BCrypt = **68 caracteres**; se
  dimensiona holgado (255) para **desacoplar el
  schema del encoder**: una futura migración a `{argon2}` (hash más largo) no tocaría la tabla.
  En PostgreSQL `VARCHAR(n)` no reserva espacio, así que 255 no cuesta nada.
- **Solo `created_at`/`updated_at`** (sin `created_by`/`updated_by` como Convenia): con un único
  actor y provisión por seed, la autoría no aporta valor todavía (YAGNI). Se puede reintroducir
  con el patrón `AuditableEntity` si el dominio crece.
- **Timestamps poblados por `@PrePersist`/`@PreUpdate` en la entity `User`**: callbacks JPA
  locales — cero configuración global, cero beans nuevos, visibles en la propia entity. Con
  Flyway-valida-Hibernate y columnas `NOT NULL`, dejar el mecanismo sin nombrar arriesgaba un
  INSERT que explota en runtime. Alternativas anotadas, no adoptadas: `@EnableJpaAuditing` +
  `@CreatedDate`/`@LastModifiedDate` es el upgrade natural si el dominio crece (entra con el
  `AuditableEntity` ya previsto arriba); `DEFAULT now()` + trigger en la DB repartiría la verdad
  entre DB y app (anti-KISS aquí).
- **Sin columna de rol/sede/last_login/intentos**: YAGNI. El throttling vive en memoria
  (`LoginAttemptService`), no en la tabla.

### Validación (dónde ocurre)

- **Formato de email y campos requeridos**: Bean Validation en los DTOs (`@Email`, `@NotBlank`).
- **Política de contraseña** (mínimo 15 caracteres, máximo 72 bytes UTF-8, nueva ≠ actual): `PasswordPolicy` en el service
  (autoritativo). El cliente **espeja** longitud y coincidencia con la confirmación para US3, pero
  no es la fuente de verdad (FR-003).
- **Unicidad de email**: normalización a minúsculas en el service (app-side) + **índice único
  funcional sobre `LOWER(email)`** en BD (defensa final real, case-insensitive). Un
  `UNIQUE (email)` plano dejaría convivir `Coord@...` y `coord@...`, y la afirmación de
  «defensa final» sería falsa.

### Estados / transiciones

No hay máquina de estados (a diferencia del `Agreement` de Convenia). El único atributo de estado
es `active` (`true`/`false`), gestionado **fuera de la app** (seed/re-provisión). El cambio de
contraseña (US2) actualiza `password_hash` + `updated_at`; no cambia `active`.

*Ventana aceptada (FR-011)*: `active` se chequea **solo en el login** — la desactivación surte
efecto en el próximo intento de login y una sesión ya establecida sobrevive hasta su cierre o
expiración por inactividad (≤30 min, FR-009). Se acepta y documenta en vez de revocar: hoy no
existe ninguna acción dentro del sistema capaz de desactivar la cuenta, y la revocación exigiría
chequear la BD en cada request o un `SessionRegistry` (maquinaria contra amenaza teórica).

## Migración Flyway

`src/main/resources/db/migration/V1.0.0__Create_users_table.sql`:

```sql
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));
```

- **Índice único funcional en vez de `UNIQUE (email)` plano**: atrapa las variantes de
  mayúsculas (`Coord@...` vs `coord@...`) que el constraint plano dejaría pasar. Es PostgreSQL
  estándar sin extensiones (coherente con el rechazo de `citext`) —
  [PostgreSQL — Indexes on Expressions](https://www.postgresql.org/docs/current/indexes-expressional.html).
  Hibernate en modo `validate` **no valida índices** → sin fricción con Flyway-valida-Hibernate.
- **No** hay migración de seed: la cuenta se provisiona por `CoordinationUserSeeder` (env-driven,
  ver `research.md` D8) para no versionar credenciales en git.
- Nomenclatura `V{major}.{minor}.{patch}__Descripción.sql`, igual que Convenia.

## Repositorio

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email); // email ya normalizado a minúsculas por el caller
    boolean existsByEmail(String email);
}
```

## DTOs (nunca se expone la entity)

| DTO | Uso | Campos |
|-----|-----|--------|
| `LoginRequest` | body de `POST /login` | `email` (`@Email @NotBlank`), `password` (`@NotBlank`) |
| `ChangePasswordRequest` | body de `POST /password` | `currentPassword` (`@NotBlank`), `newPassword` (`@NotBlank`) |
| `CurrentUserResponse` | body de `GET /me` | `email`, `active` — **sin** id ni hash |

> `passwordHash`, `id`, `createdAt`/`updatedAt` **nunca** salen en una respuesta. El mapeo
> `User → CurrentUserResponse` se hace **a mano** (3 DTOs → sin MapStruct, YAGNI), exponiendo
> solo los campos permitidos.
