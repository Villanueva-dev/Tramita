-- Evidencia append-only del cierre: envío SMTP real o fallback manual cuando no esté disponible.
CREATE TABLE request_notification (
    id              BIGSERIAL PRIMARY KEY,
    request_id      UUID NOT NULL REFERENCES request (id),
    channel         VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    recipient_email VARCHAR(255),
    subject         VARCHAR(200) NOT NULL,
    body            VARCHAR(4000) NOT NULL,
    failure_reason  VARCHAR(500),
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX ix_request_notification_request_id
    ON request_notification (request_id, created_at, id);

CREATE FUNCTION reject_request_notification_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'request_notification es inmutable: solo se permite INSERT';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_request_notification_immutable
    BEFORE UPDATE OR DELETE ON request_notification
    FOR EACH ROW EXECUTE FUNCTION reject_request_notification_mutation();