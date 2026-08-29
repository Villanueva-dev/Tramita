-- Catálogo de fuentes para el asistente: la recuperación solo habilita fuentes VALIDATED.
CREATE TABLE knowledge_source (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       VARCHAR(120) NOT NULL,
    source_class    VARCHAR(40) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    document_type   VARCHAR(80) NOT NULL,
    version_label   VARCHAR(80) NOT NULL,
    issuer          VARCHAR(255),
    validated_at    TIMESTAMP,
    status          VARCHAR(20) NOT NULL,
    content_sha256  VARCHAR(64),
    created_at      TIMESTAMP NOT NULL,
    retired_at      TIMESTAMP,
    CONSTRAINT uq_knowledge_source_source_version UNIQUE (source_id, version_label),
    CONSTRAINT ck_knowledge_source_class CHECK (source_class IN (
        'OFFICIAL_INSTITUTIONAL', 'INTERVIEW_EVIDENCE', 'PROJECT_ANALYSIS', 'TECHNICAL_REFERENCE'
    )),
    CONSTRAINT ck_knowledge_source_status CHECK (status IN (
        'NOT_LOCATED', 'PROVISIONAL', 'VALIDATED', 'RETIRED'
    )),
    CONSTRAINT ck_knowledge_source_validation_date CHECK (
        status <> 'VALIDATED' OR validated_at IS NOT NULL
    )
);

CREATE INDEX ix_knowledge_source_retrieval
    ON knowledge_source (status, source_class, title);

-- Fragmentos independientes para recuperación y citas auditables.
CREATE TABLE knowledge_chunk (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID NOT NULL REFERENCES knowledge_source (id),
    chunk_order     INTEGER NOT NULL,
    content         VARCHAR(12000) NOT NULL,
    section_label   VARCHAR(255),
    page_number     INTEGER,
    locator         VARCHAR(255),
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT uq_knowledge_chunk_order UNIQUE (source_id, chunk_order),
    CONSTRAINT ck_knowledge_chunk_order CHECK (chunk_order >= 0),
    CONSTRAINT ck_knowledge_chunk_page CHECK (page_number IS NULL OR page_number > 0)
);

CREATE INDEX ix_knowledge_chunk_source_id ON knowledge_chunk (source_id, chunk_order);

CREATE FUNCTION reject_knowledge_source_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'knowledge_source es inmutable: solo se permite INSERT';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_knowledge_source_immutable
    BEFORE UPDATE OR DELETE ON knowledge_source
    FOR EACH ROW EXECUTE FUNCTION reject_knowledge_source_mutation();

CREATE FUNCTION reject_knowledge_chunk_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'knowledge_chunk es inmutable: solo se permite INSERT';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_knowledge_chunk_immutable
    BEFORE UPDATE OR DELETE ON knowledge_chunk
    FOR EACH ROW EXECUTE FUNCTION reject_knowledge_chunk_mutation();
