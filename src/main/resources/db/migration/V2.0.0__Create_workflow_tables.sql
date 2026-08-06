-- Motor de workflow configurable (specs/002-workflow-engine/data-model.md).
-- 5 tablas: la configuración de los trámites vive en BD como datos versionados
-- (research.md D1/D2); workflow_parameter y guard_key llegan en la feature 003
-- con SP2 (research.md D3). PK UUID generada por la app (GenerationType.UUID),
-- salvo el log, que usa BIGSERIAL para desempate monótono del orden cronológico
-- (research.md D11). Timestamps TIMESTAMP sin zona + UTC por la app, convención
-- del chasis (001, JD3-012).

-- La versión es parte de la identidad de la definición: editar un trámite es
-- insertar (code, version+1). Cada solicitud queda atada a la versión con la
-- que nació (FR-009) — por eso no hay tabla padre "trámite" separada.
CREATE TABLE workflow_definition (
    id         UUID PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL,
    version    INT          NOT NULL,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT uq_workflow_definition_code_version UNIQUE (code, version)
);

CREATE TABLE workflow_state (
    id            UUID PRIMARY KEY,
    definition_id UUID         NOT NULL REFERENCES workflow_definition (id),
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    is_initial    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_final      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_workflow_state_definition_code UNIQUE (definition_id, code)
);

-- responsible: etiqueta de configuración del responsable del paso (FR-001); el
-- timeline deriva el "en nombre de" por join estable contra la definición
-- inmutable (FR-006, research.md D5). requires_note: el motor exige observación
-- cuando la transición la declara — la "devolución" es concepto de la
-- configuración, no del motor (FR-014, research.md D4).
CREATE TABLE workflow_transition (
    id            UUID        NOT NULL PRIMARY KEY,
    definition_id UUID        NOT NULL REFERENCES workflow_definition (id),
    from_state_id UUID        NOT NULL REFERENCES workflow_state (id),
    to_state_id   UUID        NOT NULL REFERENCES workflow_state (id),
    responsible   VARCHAR(50) NOT NULL,
    requires_note BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_workflow_transition_route UNIQUE (definition_id, from_state_id, to_state_id)
);

-- Datos personales minimizados a nombre + cédula (Ley 1581 de 2012, supuesto de
-- la spec): son los dos datos con los que la Coordinación localiza un trámite
-- (FR-011). version = locking optimista @Version: ante dos avances simultáneos
-- solo prospera el que vio el estado vigente (research.md D6).
CREATE TABLE request (
    id               UUID         PRIMARY KEY,
    definition_id    UUID         NOT NULL REFERENCES workflow_definition (id),
    current_state_id UUID         NOT NULL REFERENCES workflow_state (id),
    student_name     VARCHAR(120) NOT NULL,
    student_document VARCHAR(20)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL
);

CREATE INDEX ix_request_student_document ON request (student_document);
CREATE INDEX ix_request_student_name_lower ON request (LOWER(student_name));

-- El timeline de SP6. from_state_id NULL = entrada de registro (nacimiento de
-- la solicitud, research.md D7). Solo recibe INSERT: no existe operación de
-- edición ni borrado en la aplicación (FR-007) y el trigger de abajo lo
-- garantiza incluso ante acceso directo a la BD (SC-002, research.md D8).
CREATE TABLE request_transition_log (
    id            BIGSERIAL PRIMARY KEY,
    request_id    UUID      NOT NULL REFERENCES request (id),
    from_state_id UUID      REFERENCES workflow_state (id),
    to_state_id   UUID      NOT NULL REFERENCES workflow_state (id),
    actor_id      UUID      NOT NULL REFERENCES users (id),
    note          TEXT,
    occurred_at   TIMESTAMP NOT NULL
);

CREATE INDEX ix_request_transition_log_timeline
    ON request_transition_log (request_id, occurred_at, id);

CREATE FUNCTION reject_timeline_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'request_transition_log es inmutable: solo se permite INSERT (FR-007)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_timeline_immutable
    BEFORE UPDATE OR DELETE ON request_transition_log
    FOR EACH ROW EXECUTE FUNCTION reject_timeline_mutation();
