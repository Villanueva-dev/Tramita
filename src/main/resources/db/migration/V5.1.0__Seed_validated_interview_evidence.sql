-- Evidencia operativa anonimizada, contrastada con la transcripción consolidada.
-- No constituye reglamento institucional: source_class conserva su procedencia.
WITH source AS (
    INSERT INTO knowledge_source (
        source_id, source_class, title, document_type, version_label, issuer,
        validated_at, status, content_sha256, created_at
    ) VALUES (
        'entrevistas-coordinacion-cali', 'INTERVIEW_EVIDENCE',
        'Entrevistas a Coordinación Académica, Sede Cali', 'Entrevista semiestructurada',
        'consolidado-2026-09-10', 'Coordinación Académica, Sede Cali',
        '2026-09-10 00:00:00', 'VALIDATED',
        'EB6FAAFB610D80D381DC0851A2BCD301D153FF6E1BDB4435AB06E35A568D4BF9',
        '2026-09-10 00:00:00'
    ) RETURNING id
)
INSERT INTO knowledge_chunk (source_id, chunk_order, content, section_label, locator, created_at)
SELECT source.id, chunk.chunk_order, chunk.content, chunk.section_label, chunk.locator, '2026-09-10 00:00:00'
FROM source
CROSS JOIN (VALUES
    (0, 'La adición de créditos se inicia cuando CLASS impide matricular una asignatura por superar los créditos permitidos del semestre. La Coordinación verifica el caso; el estudiante diligencia un formato Word completo y lo envía por correo. La Coordinación lo revisa y lo devuelve para corrección cuando encuentra errores.', 'Adición de créditos: inicio y revisión', '§5.2, P2-P8'),
    (1, 'Para adición de créditos, una vez el formato está correcto se remite a la facultad para revisión y firma. Después Registro y Control Cali carga el PDF en QF y Registro y Control Nacional realiza la matrícula en CLASS. Los tiempos reportados van de tres a cinco días en el mejor caso a cerca de dos semanas; las devoluciones pueden extender el trámite.', 'Adición de créditos: flujo y tiempos', '§5.2, P5-P8'),
    (2, 'La novedad de notas procede cuando una calificación faltante no se corrige antes del cierre nacional de CLASS. La evidencia reporta que no tiene caducidad. Una corrección de nota mal calculada usa otro formato y requiere carta de justificación del docente; no pertenece a la novedad de notas del alcance actual.', 'Novedad de notas: causal y alcance', '§5.3, P14-P15; §6.2, P6'),
    (3, 'El flujo operativo de novedad de notas reúne planillas, formato Word y soportes de pago en una carpeta de OneDrive. Incluye revisión de Coordinación, docente, Dirección de Sede, facultad, Área Financiera y Registro y Control. El cierre ocurre al cargar el PDF en QF y registrar la nota en el sistema.', 'Novedad de notas: flujo operativo', '§5.3, P16-P19'),
    (4, 'Según la Coordinación, el máximo no puede superar 21 créditos y las asignaturas elegibles son las que CLASS habilita; los prerrequisitos se validan en CLASS. Esta es evidencia operativa reportada por Coordinación y requiere contraste con el Reglamento Estudiantil antes de presentarse como norma institucional.', 'Adición de créditos: regla operativa', '§6.2, P5')
) AS chunk(chunk_order, content, section_label, locator);