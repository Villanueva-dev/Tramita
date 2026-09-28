-- Distingue un retorno a corrección de una transición que solo exige observación.
-- DEFAULT FALSE conserva el comportamiento de cualquier transición ya configurada;
-- abajo se marcan explícitamente los retornos de las definiciones vigentes.
ALTER TABLE workflow_transition
    ADD COLUMN return_for_correction BOOLEAN NOT NULL DEFAULT FALSE;

WITH return_edges(definition_code, definition_version, from_code, to_code) AS (VALUES
    ('ADICION_CREDITOS', 1, 'EN_COORDINACION', 'DEVUELTA'),
    ('ADICION_CREDITOS', 1, 'EN_FACULTAD', 'DEVUELTA'),
    ('ADICION_CREDITOS', 1, 'EN_REGISTRO_CALI', 'DEVUELTA'),
    ('ADICION_CREDITOS', 1, 'EN_REGISTRO_NACIONAL', 'DEVUELTA'),
    ('NOVEDAD_NOTAS', 1, 'EN_FACULTAD', 'EN_PREPARACION'),
    ('NOVEDAD_NOTAS', 1, 'EN_REVISION_FINANCIERA', 'EN_PREPARACION'),
    ('NOVEDAD_NOTAS', 1, 'EN_REGISTRO_CONTROL', 'EN_PREPARACION')
)
UPDATE workflow_transition transition
SET return_for_correction = TRUE
FROM return_edges edge
JOIN workflow_definition definition
  ON definition.code = edge.definition_code
 AND definition.version = edge.definition_version
JOIN workflow_state source_state
  ON source_state.definition_id = definition.id
 AND source_state.code = edge.from_code
JOIN workflow_state target_state
  ON target_state.definition_id = definition.id
 AND target_state.code = edge.to_code
WHERE transition.definition_id = definition.id
  AND transition.from_state_id = source_state.id
  AND transition.to_state_id = target_state.id;