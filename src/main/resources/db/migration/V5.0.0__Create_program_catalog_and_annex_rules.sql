-- Catálogo de programas y regla de anexo por trámite y programa (009, FR-001,
-- FR-006, FR-008). Sube de FAMILIA (V4 → V5) porque abre un dominio de
-- configuración nuevo, no una extensión del de documentos y sellos: V1
-- usuarios, V2 motor, V3 reglas y captura, V4 documento y sellos
-- (research.md D7). Es el mismo criterio que hizo subir V2.0.0 → V3.0.0
-- (V3.0.0__Configure_business_rules.sql:1-4): un salto conceptual, no un
-- ALTER más sobre lo mismo.
--
-- La FK de workflow_annex_rule a workflow_definition apunta a la VERSIÓN
-- concreta, con el mismo criterio de workflow_parameter
-- (V3.0.0__Configure_business_rules.sql:16-19): una solicitud se rige por la
-- versión con la que nació, y su requisito de anexo viaja con ella. Publicar
-- una v2 de un trámite nace sin reglas hasta que se le siembren
-- (research.md D2).
CREATE TABLE academic_program (
    id   UUID         PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    CONSTRAINT uq_academic_program_name UNIQUE (name)
);

-- ON DELETE RESTRICT y no CASCADE: quitar un programa que tiene regla falla
-- ruidosamente, en vez de borrar la regla en silencio (research.md D2).
CREATE TABLE workflow_annex_rule (
    id            UUID         PRIMARY KEY,
    definition_id UUID         NOT NULL REFERENCES workflow_definition (id),
    program_id    UUID         NOT NULL REFERENCES academic_program (id) ON DELETE RESTRICT,
    document_name VARCHAR(120) NOT NULL,
    source_hint   VARCHAR(255) NOT NULL,
    CONSTRAINT uq_workflow_annex_rule_definition_program UNIQUE (definition_id, program_id)
);
