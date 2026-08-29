-- Parámetros de reglas de negocio para SP2; los valores se pueden ajustar por definición.
CREATE TABLE workflow_parameter (
    id             UUID PRIMARY KEY,
    definition_id  UUID NOT NULL REFERENCES workflow_definition (id),
    parameter_key  VARCHAR(50) NOT NULL,
    parameter_value VARCHAR(100) NOT NULL,
    CONSTRAINT uq_workflow_parameter_definition_key UNIQUE (definition_id, parameter_key)
);

-- Valor provisional confirmado en entrevistas; queda editable por configuración.
INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), id, 'MAX_CREDITS', '21'
FROM workflow_definition
WHERE code = 'ADICION_CREDITOS' AND version = 1;