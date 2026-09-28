-- Novedad de notas captura nota actual y nota propuesta por asignatura (003, FR-003).
-- La guarda es configurable para que el motor no convierta esta regla del trámite en
-- una condición global del modelo. El cuerpo legado sin asignaturas sigue siendo válido.
INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), d.id, 'CAPTURES_GRADES', 'true'
FROM workflow_definition d
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 1;