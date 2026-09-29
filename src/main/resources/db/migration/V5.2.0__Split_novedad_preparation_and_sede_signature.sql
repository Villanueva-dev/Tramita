-- Novedad de notas v2: la carpeta en preparación y la firma de la sede pasan a
-- ser dos estados (H-11, hallazgo del E2E del 28-sep-2026).
--
-- EL DEFECTO. La bandeja de la Coordinación lista una solicitud cuando, desde su
-- estado actual, existe una transición cuyo `responsible` es el área pedida
-- (IRequestRepo). En la v1 de NOVEDAD_NOTAS la única salida de EN_PREPARACION es
-- EN_PREPARACION → EN_FACULTAD con responsable SEDE, así que TODA novedad en
-- preparación —recién pasada a preparación o devuelta por la facultad, la
-- financiera o registro— desaparecía de la bandeja de la Coordinación aunque
-- armar la carpeta es trabajo suyo.
--
-- LA SEMÁNTICA NO CAMBIA. `responsible` es «el área cuya firma o aprobación
-- espera el paso — no quién ejecuta la transición en el sistema, que en el MVP
-- es siempre la Coordinación» (specs/007-coordination-inbox/research.md:24-35,
-- que ya registra este caso en su punto 2). SEDE era correcto para la firma: el
-- formato real lleva la firma del «Jefe Regional de Sede». El defecto es de
-- GRANULARIDAD: EN_PREPARACION mezclaba el trabajo de la Coordinadora (armar la
-- carpeta) con la espera de la firma de la sede. Es la pregunta abierta que el
-- encabezado de V2.1.0 dejaba anotada («granularidad de EN_PREPARACION»).
--
-- LA SOLUCIÓN: un estado más. EN_FIRMA_SEDE separa las dos cosas:
--   · EN_PREPARACION → EN_FIRMA_SEDE  COORDINACION  (la carpeta lista la entrega ella)
--   · EN_FIRMA_SEDE  → EN_FACULTAD    SEDE          (la sede firma y envía a la facultad)
--   · EN_FIRMA_SEDE  → EN_PREPARACION SEDE, con nota (la sede la devuelve para corregir;
--     entrevista 2: «Ella me manda a corregir», material-coord/transcript-entrevista-coordi-2.md:4)
-- El resto de la cadena y de las devoluciones es idéntico a la v1.
--
-- POR QUÉ v2 Y NO EDITAR LA v1. (1) Flyway valida checksums: editar V2.1.0 rompe
-- toda base que ya la corrió. (2) Tocar la definición v1 reescribiría el
-- historial: el responsable de cada entrada del timeline se deriva AL LEER desde
-- la definición de la solicitud (RequestServiceImpl.toTimelineEntry), así que
-- cambiar la v1 cambiaría lo que hoy muestran las novedades ya radicadas. (3) El
-- propio encabezado de V2.1.0 manda cargar una definición (NOVEDAD_NOTAS, 2) si
-- la cadena cambia tras correr la migración. Las solicitudes existentes conservan
-- la v1 (FR-009); las nuevas nacen en la v2 porque el registro usa la versión
-- vigente, la de mayor `version`.
--
-- ALCANCE. El sistema sirve solo a la Coordinación (decisión de alcance del
-- usuario): la sede se MODELA como un área cuya firma se espera, igual que la
-- facultad o la financiera, no como usuaria. Por eso no hay roles ni pantalla
-- para la sede: quien registra EN_FIRMA_SEDE → EN_FACULTAD es la Coordinación,
-- dejando constancia de que la sede ya firmó.
--
-- ⚠️ La cadena sigue PROVISIONAL, como la v1 (ver encabezado de V2.1.0): las
-- otras dos preguntas abiertas —destino de las devoluciones y obligatoriedad del
-- paso financiero— no las resuelve esta migración.
--
-- La v1 no tiene guard_key asignados ni otras columnas que replicar; sus únicos
-- datos por definición son los dos parámetros de V3.0.0 (MIN_GRADE, MAX_GRADE).
-- No tiene reglas de anexo, plantilla de documento ni captura pública (V3.1.0,
-- V3.3.0, V4.0.0 y V5.1.0 la dejan fuera a propósito), así que no hay más que copiar.

-- ============================================================================
-- Novedad de notas v2
-- ============================================================================
INSERT INTO workflow_definition (id, code, version, name, created_at)
VALUES (gen_random_uuid(), 'NOVEDAD_NOTAS', 2, 'Novedad de notas', now());

INSERT INTO workflow_state (id, definition_id, code, name, is_initial, is_final)
SELECT gen_random_uuid(), d.id, s.code, s.name, s.is_initial, s.is_final
FROM workflow_definition d,
     (VALUES ('REGISTRADA',            'Registrada',                          TRUE,  FALSE),
             ('EN_PREPARACION',        'En preparación (carpeta y firmas)',   FALSE, FALSE),
             ('EN_FIRMA_SEDE',         'En firma de la sede',                 FALSE, FALSE),
             ('EN_FACULTAD',           'En facultad',                         FALSE, FALSE),
             ('EN_REVISION_FINANCIERA','En revisión financiera',              FALSE, FALSE),
             ('EN_REGISTRO_CONTROL',   'En registro y control',               FALSE, FALSE),
             ('FINALIZADA',            'Finalizada',                          FALSE, TRUE))
         AS s(code, name, is_initial, is_final)
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 2;

-- Sin estado de rechazo (E3-Q19) y con la devolución como transición de retorno
-- a EN_PREPARACION, igual que la v1. Las devoluciones exigen motivo (FR-014),
-- también la de la sede.
INSERT INTO workflow_transition (id, definition_id, from_state_id, to_state_id, responsible, requires_note)
SELECT gen_random_uuid(), d.id, f.id, t.id, x.responsible, x.requires_note
FROM workflow_definition d
JOIN (VALUES ('REGISTRADA',            'EN_PREPARACION',        'COORDINACION',      FALSE),
             ('EN_PREPARACION',        'EN_FIRMA_SEDE',         'COORDINACION',      FALSE),
             ('EN_FIRMA_SEDE',         'EN_FACULTAD',           'SEDE',              FALSE),
             ('EN_FACULTAD',           'EN_REVISION_FINANCIERA','FACULTAD',          FALSE),
             ('EN_REVISION_FINANCIERA','EN_REGISTRO_CONTROL',   'FINANCIERA',        FALSE),
             ('EN_REGISTRO_CONTROL',   'FINALIZADA',            'REGISTRO_NACIONAL', FALSE),
             ('EN_FIRMA_SEDE',         'EN_PREPARACION',        'SEDE',              TRUE),
             ('EN_FACULTAD',           'EN_PREPARACION',        'FACULTAD',          TRUE),
             ('EN_REVISION_FINANCIERA','EN_PREPARACION',        'FINANCIERA',        TRUE),
             ('EN_REGISTRO_CONTROL',   'EN_PREPARACION',        'REGISTRO_NACIONAL', TRUE))
         AS x(from_code, to_code, responsible, requires_note) ON TRUE
JOIN workflow_state f ON f.definition_id = d.id AND f.code = x.from_code
JOIN workflow_state t ON t.definition_id = d.id AND t.code = x.to_code
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 2;

-- Los parámetros son de la VERSIÓN, no del código del trámite (FR-013): la v2 no
-- hereda nada de la v1, así que declara los mismos límites de nota (V3.0.0). Sin
-- MAX_CREDITS: el formulario de la novedad no captura créditos.
INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), d.id, p.parameter_key, p.parameter_value
FROM workflow_definition d
         CROSS JOIN (VALUES ('MIN_GRADE', '0.0'),
                            ('MAX_GRADE', '5.0'))
    AS p(parameter_key, parameter_value)
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 2;
