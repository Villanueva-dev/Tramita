-- Semilla de las definiciones v1 de los dos trámites del alcance
-- (specs/002-workflow-engine/data-model.md, research.md D10). Las definiciones
-- son datos de configuración: este archivo ES la configuración inicial del
-- sistema, no schema. Las referencias entre filas se resuelven por (code,
-- version) y códigos de estado — sin UUIDs literales.
--
-- ADICIÓN DE CRÉDITOS: cadena CERRADA, leída de un hilo de correo real con la
-- Coordinación de la Sede Cali.
--
-- ⚠️ NOVEDAD DE NOTAS: cadena PROVISIONAL — armada desde las entrevistas 1-3,
-- PENDIENTE DE CONFIRMAR con la Coordinación (reunión virtual pactada; quedan 3
-- preguntas: granularidad de EN_PREPARACION, destino de las devoluciones y
-- obligatoriedad del paso financiero). Si cambia antes de correr esta migración
-- en un ambiente compartido, se edita aquí; si ya corrió, se carga una
-- definición (NOVEDAD_NOTAS, 2) — las solicitudes en curso conservan la v1
-- (FR-009).

-- ============================================================================
-- Adición de créditos v1
-- ============================================================================
INSERT INTO workflow_definition (id, code, version, name, created_at)
VALUES (gen_random_uuid(), 'ADICION_CREDITOS', 1, 'Adición de créditos', now());

INSERT INTO workflow_state (id, definition_id, code, name, is_initial, is_final)
SELECT gen_random_uuid(), d.id, s.code, s.name, s.is_initial, s.is_final
FROM workflow_definition d,
     (VALUES ('REGISTRADA',           'Registrada',                       TRUE,  FALSE),
             ('EN_FACULTAD',          'En facultad',                      FALSE, FALSE),
             ('APROBADA_FACULTAD',    'Aprobada por facultad',            FALSE, FALSE),
             ('EN_REGISTRO_CALI',     'En registro Cali (carga en QF)',   FALSE, FALSE),
             ('EN_REGISTRO_NACIONAL', 'En registro nacional',             FALSE, FALSE),
             ('FINALIZADA',           'Finalizada',                       FALSE, TRUE),
             ('DEVUELTA',             'Devuelta para corrección',         FALSE, FALSE),
             ('RECHAZADA',            'Rechazada',                        FALSE, TRUE))
         AS s(code, name, is_initial, is_final)
WHERE d.code = 'ADICION_CREDITOS' AND d.version = 1;

-- La devolución es un estado propio (DEVUELTA) con retorno a EN_FACULTAD; el
-- rechazo (solicitud extemporánea) solo existe desde EN_FACULTAD: es la
-- facultad quien niega. Las transiciones de devolución exigen motivo (FR-014).
INSERT INTO workflow_transition (id, definition_id, from_state_id, to_state_id, responsible, requires_note)
SELECT gen_random_uuid(), d.id, f.id, t.id, x.responsible, x.requires_note
FROM workflow_definition d
JOIN (VALUES ('REGISTRADA',           'EN_FACULTAD',          'COORDINACION',       FALSE),
             ('EN_FACULTAD',          'APROBADA_FACULTAD',    'FACULTAD',           FALSE),
             ('APROBADA_FACULTAD',    'EN_REGISTRO_CALI',     'COORDINACION',       FALSE),
             ('EN_REGISTRO_CALI',     'EN_REGISTRO_NACIONAL', 'REGISTRO_CALI',      FALSE),
             ('EN_REGISTRO_NACIONAL', 'FINALIZADA',           'REGISTRO_NACIONAL',  FALSE),
             ('EN_FACULTAD',          'DEVUELTA',             'FACULTAD',           TRUE),
             ('EN_REGISTRO_CALI',     'DEVUELTA',             'REGISTRO_CALI',      TRUE),
             ('EN_REGISTRO_NACIONAL', 'DEVUELTA',             'REGISTRO_NACIONAL',  TRUE),
             ('DEVUELTA',             'EN_FACULTAD',          'COORDINACION',       FALSE),
             ('EN_FACULTAD',          'RECHAZADA',            'FACULTAD',           FALSE))
         AS x(from_code, to_code, responsible, requires_note) ON TRUE
JOIN workflow_state f ON f.definition_id = d.id AND f.code = x.from_code
JOIN workflow_state t ON t.definition_id = d.id AND t.code = x.to_code
WHERE d.code = 'ADICION_CREDITOS' AND d.version = 1;

-- ============================================================================
-- Novedad de notas v1 — ⚠️ PROVISIONAL (ver encabezado)
-- ============================================================================
INSERT INTO workflow_definition (id, code, version, name, created_at)
VALUES (gen_random_uuid(), 'NOVEDAD_NOTAS', 1, 'Novedad de notas', now());

INSERT INTO workflow_state (id, definition_id, code, name, is_initial, is_final)
SELECT gen_random_uuid(), d.id, s.code, s.name, s.is_initial, s.is_final
FROM workflow_definition d,
     (VALUES ('REGISTRADA',            'Registrada',                          TRUE,  FALSE),
             ('EN_PREPARACION',        'En preparación (carpeta y firmas)',   FALSE, FALSE),
             ('EN_FACULTAD',           'En facultad',                         FALSE, FALSE),
             ('EN_REVISION_FINANCIERA','En revisión financiera',              FALSE, FALSE),
             ('EN_REGISTRO_CONTROL',   'En registro y control',               FALSE, FALSE),
             ('FINALIZADA',            'Finalizada',                          FALSE, TRUE))
         AS s(code, name, is_initial, is_final)
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 1;

-- Sin estado de rechazo (E3-Q19: «por más que se demoren, siempre termina») y
-- la devolución NO es un estado: es la transición de retorno a EN_PREPARACION,
-- donde vive la carpeta editable. Que un trámite modele la devolución como
-- estado y el otro como retorno directo, sobre el mismo esquema, es parte de la
-- demostración de US4 (SC-004).
INSERT INTO workflow_transition (id, definition_id, from_state_id, to_state_id, responsible, requires_note)
SELECT gen_random_uuid(), d.id, f.id, t.id, x.responsible, x.requires_note
FROM workflow_definition d
JOIN (VALUES ('REGISTRADA',            'EN_PREPARACION',        'COORDINACION',      FALSE),
             ('EN_PREPARACION',        'EN_FACULTAD',           'SEDE',              FALSE),
             ('EN_FACULTAD',           'EN_REVISION_FINANCIERA','FACULTAD',          FALSE),
             ('EN_REVISION_FINANCIERA','EN_REGISTRO_CONTROL',   'FINANCIERA',        FALSE),
             ('EN_REGISTRO_CONTROL',   'FINALIZADA',            'REGISTRO_NACIONAL', FALSE),
             ('EN_FACULTAD',           'EN_PREPARACION',        'FACULTAD',          TRUE),
             ('EN_REVISION_FINANCIERA','EN_PREPARACION',        'FINANCIERA',        TRUE),
             ('EN_REGISTRO_CONTROL',   'EN_PREPARACION',        'REGISTRO_NACIONAL', TRUE))
         AS x(from_code, to_code, responsible, requires_note) ON TRUE
JOIN workflow_state f ON f.definition_id = d.id AND f.code = x.from_code
JOIN workflow_state t ON t.definition_id = d.id AND t.code = x.to_code
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 1;
