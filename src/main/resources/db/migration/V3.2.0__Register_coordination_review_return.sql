-- La revisión de la Coordinación y su devolución al estudiante (H1).
--
-- La entrevista 1 describe el primer filtro del trámite: «Yo reviso si está
-- bien. Si está mal, se lo regreso». Ese bucle es la mayor fuente de dilación
-- medida en la propia entrevista —«me he demorado hasta dos meses, pero es
-- porque el estudiante lo mandó mal»— y es el que el árbol de problemas (§23)
-- invoca para clasificar la adición de créditos como automatización profunda.
-- Hasta hoy no tenía dónde registrarse: REGISTRADA solo salía hacia
-- EN_FACULTAD, y el retorno de DEVUELTA entraba a la facultad sin que nadie
-- volviera a revisar.
--
-- El sistema no devuelve nada: la Coordinación sigue escribiéndole al
-- estudiante por correo. Lo que se agrega es la CONSTANCIA de que devolvió,
-- cuándo y por qué —el motivo es el único dato que el hilo de correos no deja
-- medible—, coherente con el rol de cockpit interno y no de orquestador.
--
-- Sube a MINOR y no a PATCH porque amplía el vocabulario de la definición con
-- un camino que antes no existía; no es la corrección de un dato mal sembrado.
--
-- Se aplica SOLO a ADICION_CREDITOS: la cadena de NOVEDAD_NOTAS sigue siendo
-- provisional (ver encabezado de V2.1.0) y conserva su REGISTRADA hasta que se
-- confirme con la Coordinación. Que dos definiciones nombren distinto su estado
-- inicial no es inconsistencia: cada trámite nombra sus estados, que es lo que
-- el motor configurable sostiene.

-- 1. El estado inicial pasa a nombrar DÓNDE está el trámite, como el resto de
-- la cadena (EN_FACULTAD, EN_REGISTRO_CALI, EN_REGISTRO_NACIONAL). REGISTRADA
-- nombraba un evento ya ocurrido, y por eso «vuelve a REGISTRADA» se leía como
-- un nuevo registro en lugar de un reingreso a revisión. Las claves foráneas
-- apuntan al id del estado, no a su código: las solicitudes en curso no se ven
-- afectadas por el renombre.
UPDATE workflow_state s
SET code = 'EN_COORDINACION',
    name = 'En coordinación (revisión)'
FROM workflow_definition d
WHERE s.definition_id = d.id
  AND d.code = 'ADICION_CREDITOS'
  AND d.version = 1
  AND s.code = 'REGISTRADA';

-- 2. La devolución dentro de la propia revisión. Exige motivo (FR-014) por la
-- misma razón que las demás devoluciones: sin el motivo, la entrada del
-- timeline no dice nada que el correo no dijera ya.
INSERT INTO workflow_transition (id, definition_id, from_state_id, to_state_id, responsible, requires_note)
SELECT gen_random_uuid(), d.id, f.id, t.id, 'COORDINACION', TRUE
FROM workflow_definition d
JOIN workflow_state f ON f.definition_id = d.id AND f.code = 'EN_COORDINACION'
JOIN workflow_state t ON t.definition_id = d.id AND t.code = 'DEVUELTA'
WHERE d.code = 'ADICION_CREDITOS' AND d.version = 1;

-- 3. Lo corregido reentra por la Coordinación y no directo a la facultad. El
-- retorno a EN_FACULTAD saltaba el filtro que el paso 2 acaba de registrar: una
-- solicitud devuelta por la Coordinación habría vuelto a la facultad sin que
-- nadie comprobara la corrección. La entrevista respalda el recorrido completo
-- —«tenemos que volver a empezar con el proceso»—, y el timeline conserva los
-- tramos anteriores, así que re-entrar no borra la historia.
UPDATE workflow_transition tr
SET to_state_id = coordinacion.id
FROM workflow_definition d,
     workflow_state devuelta,
     workflow_state facultad,
     workflow_state coordinacion
WHERE tr.definition_id = d.id
  AND d.code = 'ADICION_CREDITOS'
  AND d.version = 1
  AND devuelta.definition_id     = d.id AND devuelta.code     = 'DEVUELTA'
  AND facultad.definition_id     = d.id AND facultad.code     = 'EN_FACULTAD'
  AND coordinacion.definition_id = d.id AND coordinacion.code = 'EN_COORDINACION'
  AND tr.from_state_id = devuelta.id
  AND tr.to_state_id   = facultad.id;
