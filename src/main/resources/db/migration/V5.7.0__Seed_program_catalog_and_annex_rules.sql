-- Semilla del catálogo de programas y de la única regla de anexo del alcance
-- (research.md D7). Se resuelve por (code, version) y por nombre — sin UUIDs
-- literales, igual que V2.1.0__Seed_workflow_definitions.sql:4-5 — y con
-- gen_random_uuid() como V3.0.0__Configure_business_rules.sql:47.
--
-- ---------------------------------------------------------------------------
-- ⚠️ LISTA PROVISIONAL Y NO AUDITADA (constitución §IV).
--
-- Los trece programas los dictó el usuario el 2026-09-25 a partir de lo que
-- le indicó la Coordinación Académica de la Sede Cali, SIN documento de
-- respaldo y SIN confirmación escrita (spec.md, Assumptions). Mientras esa
-- confirmación no llegue, esta lista no se presenta como hecho establecido.
--
-- El diseño mitiga el riesgo: corregirla es un cambio de FILAS, no de código
-- ni un despliegue (FR-006).
-- ---------------------------------------------------------------------------
INSERT INTO academic_program (id, name)
SELECT gen_random_uuid(), p.name
FROM (VALUES ('Administración de Negocios'),
             ('Administración de Empresas'),
             ('Contaduría Pública'),
             ('Derecho'),
             ('Enfermería'),
             ('Ingeniería Ambiental'),
             ('Ingeniería de Sistemas'),
             ('Ingeniería en Seguridad y Salud en el Trabajo'),
             ('Ingeniería Industrial'),
             ('Medicina'),
             ('Medicina Veterinaria'),
             ('Nutrición y Dietética'),
             ('Química Farmacéutica'))
    AS p(name);

-- ---------------------------------------------------------------------------
-- Regla de anexo — una sola, para ADICION_CREDITOS v1 × Ingeniería de
-- Sistemas.
--
-- Respaldo: «a solo sistemas me pide que [...] anexe la hoja de vida
-- académica» (material-coord/evidencia-entrevistas-coordinacion.md:184),
-- resumido como «Hoja de vida académica (solo Ingeniería de Sistemas)»
-- (:762); la descarga el estudiante desde CLASS (:553).
--
-- NOVEDAD_NOTAS entra sin reglas (spec.md, Assumptions; US2 escenario 4).
-- ---------------------------------------------------------------------------
INSERT INTO workflow_annex_rule (id, definition_id, program_id, document_name, source_hint)
SELECT gen_random_uuid(), d.id, p.id, 'Hoja de vida académica', 'La descarga el estudiante desde CLASS'
FROM workflow_definition d
JOIN academic_program p ON p.name = 'Ingeniería de Sistemas'
WHERE d.code = 'ADICION_CREDITOS' AND d.version = 1;
