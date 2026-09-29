ALTER TABLE request
    ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'normal';

ALTER TABLE request
    ADD CONSTRAINT ck_request_priority
        CHECK (priority IN ('normal', 'urgente'));