# Phase 1 — Data Model: Autenticación de la Coordinación

Deriva del spec (Key Entities) y de las decisiones de `research.md`. Una sola entidad de
negocio: `User`. El schema lo gestiona **Flyway**; Hibernate solo valida.

## Entidad: `User` (tabla `users`)

Representa la identidad de la Coordinación que se autentica. En este alcance **no** tiene rol
ni sede (un único actor, una única sede).

| Campo | Tipo Java | Columna | Tipo SQL | Reglas |
|-------|-----------|---------|----------|--------|
| `id` | `Long` | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK. **Nunca se expone** en la API (el identificador de negocio es el email). |
| `email` | `String` | `email` | `VARCHAR(255)` | `UNIQUE NOT NULL`. Identificador de login. Se **normaliza a minúsculas** al escribir y al buscar (login case-insensitive). |
| `passwordHash` | `String` | `password_hash` | `VARCHAR(72)` | `NOT NULL`. Hash BCrypt (60 chars). Nunca en texto plano, nunca en un DTO de respuesta. |
| `active` | `boolean` | `active` | `BOOLEAN NOT NULL DEFAULT true` | Solo `true` puede autenticarse (FR-011). |
| `createdAt` | `LocalDateTime` | `created_at` | `TIMESTAMP NOT NULL` | Auditoría. Set en creación. |
| `updatedAt` | `LocalDateTime` | `updated_at` | `TIMESTAMP NOT NULL` | Auditoría. Set en cada update (incluye cambio de clave). |

### Decisiones de modelado (trazables)

- **PK `BIGINT IDENTITY`, no UUID**: el id no sale nunca del servidor (el email es el identificador
  público), así que no hay riesgo de enumeración por id secuencial → gana la simplicidad. Si más
  adelante un id se expusiera en URLs, se reevaluaría a UUID.
- **Email normalizado a minúsculas** en vez de extensión `citext`: KISS, sin depender de una
  extensión de PostgreSQL. La normalización vive en el service/repository.
- **`password_hash VARCHAR(72)`**: alineado con el límite de 72 bytes de BCrypt (ver `research.md`
  D6). Un hash BCrypt ocupa 60 chars; 72 deja margen sin sobredimensionar.
- **Solo `created_at`/`updated_at`** (sin `created_by`/`updated_by` como Convenia): con un único
  actor y provisión por seed, la autoría no aporta valor todavía (YAGNI). Se puede reintroducir
  con el patrón `AuditableEntity` si el dominio crece.
- **Sin columna de rol/sede/last_login/intentos**: YAGNI. El throttling vive en memoria
  (`LoginAttemptService`), no en la tabla.

### Validación (dónde ocurre)

- **Formato de email y campos requeridos**: Bean Validation en los DTOs (`@Email`, `@NotBlank`).
- **Política de contraseña** (longitud 15..72, nueva ≠ actual): `PasswordPolicy` en el service
  (autoritativo). El cliente **espeja** longitud y coincidencia con la confirmación para US3, pero
  no es la fuente de verdad (FR-003).
- **Unicidad de email**: constraint `UNIQUE` en BD (defensa final) + normalización en el service.

### Estados / transiciones

No hay máquina de estados (a diferencia del `Agreement` de Convenia). El único atributo de estado
es `active` (`true`/`false`), gestionado **fuera de la app** (seed/re-provisión). El cambio de
contraseña (US2) actualiza `password_hash` + `updated_at`; no cambia `active`.

## Migración Flyway

`src/main/resources/db/migration/V1.0.0__Create_users_table.sql`:

```sql
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);
```

- **No** hay migración de seed: la cuenta se provisiona por `CoordinationUserSeeder` (env-driven,
  ver `research.md` D8) para no versionar credenciales en git.
- Nomenclatura `V{major}.{minor}.{patch}__Descripción.sql`, igual que Convenia.

## Repositorio

```java
public interface UserRepository extends JpaRepository<User, Long> {
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
