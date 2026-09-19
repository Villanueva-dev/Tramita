-- SP3 (issue #10): qué formato oficial emite cada trámite.
--
-- EL FORMATO SE ELIGE POR DATO, NO POR CÓDIGO (§VI de la constitución). El servicio
-- resuelve la implementación por el valor de este parámetro.
--
-- Un INSERT alcanza para un trámite cuyo caso el formato YA contempla. El DO-FR-100
-- tiene que marcar una de sus cuatro casillas de tipo, y esas cuatro son parte del papel
-- impreso: un trámite que no corresponda a ninguna necesita además que el renderer lo
-- aprenda. Declarar el parámetro sin eso produce un 500, no un PDF.
--
-- La AUSENCIA del parámetro es el caso por defecto: este trámite no emite documento
-- formal. No es configuración incompleta, igual que PUBLIC_CAPTURE_ENABLED en el canal
-- público.
INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), d.id, 'DOCUMENT_TEMPLATE', 'DO_FR_100'
FROM workflow_definition d
WHERE d.code = 'ADICION_CREDITOS' AND d.version = 1;

-- NOVEDAD_NOTAS NO se declara deliberadamente: su formato oficial aún debe confirmarse
-- con la Coordinación antes de modelarlo.