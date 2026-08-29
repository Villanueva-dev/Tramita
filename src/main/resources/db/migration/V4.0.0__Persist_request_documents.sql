-- Adjuntos reales: el binario vive en almacenamiento configurable y la BD conserva metadatos.
CREATE TABLE request_document (
    id            UUID PRIMARY KEY,
    request_id    UUID NOT NULL REFERENCES request (id),
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(100) NOT NULL UNIQUE,
    content_type  VARCHAR(100) NOT NULL,
    size          BIGINT NOT NULL,
    sha256        VARCHAR(64) NOT NULL,
    created_at    TIMESTAMP NOT NULL
);

CREATE INDEX ix_request_document_request_id ON request_document (request_id);