-- Reglas de negocio configurables por trámite (SP2, FR-007) y guardas de
-- transición (FR-015..FR-019). Sube de MINOR porque introduce la configurabilidad
-- de las reglas, no solo datos de captura: es el mismo salto conceptual que la
-- V2.0.0 hizo con los estados.
--
-- Continúa D1 de la feature 002: la configuración ya vive en base; esto extiende
-- el mecanismo de los estados a los límites numéricos.
CREATE TABLE workflow_parameter (
    id              UUID         PRIMARY KEY,
    definition_id   UUID         NOT NULL REFERENCES workflow_definition (id),
    parameter_key   VARCHAR(50)  NOT NULL,
    parameter_value VARCHAR(100) NOT NULL,
    CONSTRAINT uq_workflow_parameter_definition_key UNIQUE (definition_id, parameter_key)
);

-- La FK apunta a la VERSIÓN concreta de la definición, que es la identidad en la
-- 002 (UNIQUE(code, version)). Con eso FR-013 se cumple sin lógica adicional: una
-- solicitud ya se rige por la versión con la que nació, y sus parámetros viajan
-- con ella. Publicar una v2 con otro tope no altera las solicitudes en curso.

-- Guarda de transición: la definición declara el NOMBRE de la regla que condiciona
-- el paso; el motor resuelve ese nombre contra las implementaciones registradas y
-- nunca conoce el trámite (FR-018). NULL = sin guarda, comportamiento idéntico al
-- de la feature 002 (FR-017), que es como quedan todas las transiciones ya
-- sembradas por V2.1.0: esta migración no toca ninguna fila existente.
ALTER TABLE workflow_transition ADD COLUMN guard_key VARCHAR(50);

-- ---------------------------------------------------------------------------
-- Semilla de parámetros. Se resuelve por (code, version) — sin UUIDs literales,
-- igual que V2.1.0.
--
-- ⚠️ VALOR PROVISIONAL Y NO AUDITADO (constitución v2.2.2 §IV).
--
-- El tope de 21 créditos tiene respaldo DERIVADO, no primario: aparece en la
-- síntesis analítica de la tercera sesión de entrevista, que es un documento
-- derivado, no un transcript. Los transcripts crudos confirman que EXISTE un tope
-- ("sobrepasado el límite de créditos"), no CUÁL es. El respaldo normativo está en
-- el reglamento estudiantil, que aún no se ha obtenido.
--
-- Lo mismo aplica a la escala de calificación 0.0–5.0.
--
-- Mientras esa fuente no se obtenga, estos valores no se presentan como hecho
-- establecido. El diseño mitiga el riesgo: corregirlos es un UPDATE, no un cambio
-- de código ni un despliegue (SC-005).
-- ---------------------------------------------------------------------------
INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), d.id, p.parameter_key, p.parameter_value
FROM workflow_definition d
         CROSS JOIN (VALUES ('MAX_CREDITS', '21'),
                            ('MIN_GRADE', '0.0'),
                            ('MAX_GRADE', '5.0'))
    AS p(parameter_key, parameter_value)
WHERE d.code = 'ADICION_CREDITOS' AND d.version = 1;

INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), d.id, p.parameter_key, p.parameter_value
FROM workflow_definition d
         CROSS JOIN (VALUES ('MIN_GRADE', '0.0'),
                            ('MAX_GRADE', '5.0'))
    AS p(parameter_key, parameter_value)
WHERE d.code = 'NOVEDAD_NOTAS' AND d.version = 1;

-- NOVEDAD_NOTAS no lleva MAX_CREDITS: su formulario no captura créditos, así que
-- ninguna validación se lo va a pedir. Sembrarlo sería la columna especulativa que
-- el Principio I prohíbe.
