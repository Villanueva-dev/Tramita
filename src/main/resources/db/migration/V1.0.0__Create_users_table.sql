-- Identidad de la Coordinación (specs/001-auth-login/data-model.md).
-- PK UUID generada por la app (GenerationType.UUID), no por la DB: una sola
-- fuente de generación. Sin migración de seed: la cuenta se provisiona por
-- CoordinationUserSeeder (env-driven, research.md D8) para no versionar
-- credenciales en git.
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

-- Índice único funcional: unicidad case-insensitive real (atrapa Coord@ vs coord@,
-- que un UNIQUE (email) plano dejaría convivir). Hibernate en modo validate no
-- valida índices, así que no hay fricción con Flyway-valida-Hibernate.
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));
