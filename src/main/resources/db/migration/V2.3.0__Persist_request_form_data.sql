-- Persiste los datos estructurados del formulario (Prioridad 2).
-- Los campos nuevos son opcionales para no romper solicitudes creadas por V2.0.0.
ALTER TABLE request ADD COLUMN student_code VARCHAR(30);
ALTER TABLE request ADD COLUMN student_email VARCHAR(255);
ALTER TABLE request ADD COLUMN program VARCHAR(120);
ALTER TABLE request ADD COLUMN semester VARCHAR(50);
ALTER TABLE request ADD COLUMN reason TEXT;
ALTER TABLE request ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'normal';

CREATE TABLE request_subject (
    id             UUID PRIMARY KEY,
    request_id     UUID NOT NULL REFERENCES request (id),
    code           VARCHAR(30) NOT NULL,
    name           VARCHAR(150) NOT NULL,
    credits        INT,
    subject_group  VARCHAR(30),
    current_grade  VARCHAR(20),
    proposed_grade VARCHAR(20)
);

CREATE INDEX ix_request_subject_request_id ON request_subject (request_id);