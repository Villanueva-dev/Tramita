-- Trazabilidad documental append-only: evidencia de firmas externas sobre cada PDF.
CREATE TABLE request_document_approval (
    id              BIGSERIAL PRIMARY KEY,
    document_id     UUID NOT NULL REFERENCES request_document (id),
    actor_id        UUID NOT NULL REFERENCES users (id),
    signer_name     VARCHAR(120) NOT NULL,
    signer_role     VARCHAR(80) NOT NULL,
    signature_type  VARCHAR(20) NOT NULL,
    document_sha256 VARCHAR(64) NOT NULL,
    note            VARCHAR(1000),
    signed_at       TIMESTAMP NOT NULL,
    timestamped_at  TIMESTAMP NOT NULL
);

CREATE INDEX ix_request_document_approval_document_id
    ON request_document_approval (document_id, timestamped_at, id);

CREATE FUNCTION reject_request_document_approval_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'request_document_approval es inmutable: solo se permite INSERT';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_request_document_approval_immutable
    BEFORE UPDATE OR DELETE ON request_document_approval
    FOR EACH ROW EXECUTE FUNCTION reject_request_document_approval_mutation();