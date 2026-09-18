-- Requiere conservar la cédula del estudiante como dato de identificación del trámite.
-- El cliente y la API exigen student_document; esta migración no elimina la columna ni la
-- indexación que soporta la búsqueda y la trazabilidad del expediente.

ALTER TABLE request
    ALTER COLUMN student_document SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_request_student_document
    ON request (student_document);