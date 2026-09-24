-- Glosario de códigos de workflow_state para el asistente (docs/openrouter/glosario-estados-asistente.md).
-- source_class TECHNICAL_REFERENCE: es la definición del propio sistema (V2.1.0), no reglamento
-- ni evidencia de entrevista, por lo que se sella VALIDATED sin depender de una fuente externa.
WITH source AS (
    INSERT INTO knowledge_source (
        source_id, source_class, title, document_type, version_label, issuer,
        validated_at, status, content_sha256, created_at
    ) VALUES (
        'glosario-estados-workflow', 'TECHNICAL_REFERENCE',
        'Glosario de estados de los trámites configurados', 'Referencia técnica del sistema',
        'v1-2026-09-24', 'Sistema Trámita (definiciones de V2.1.0__Seed_workflow_definitions.sql)',
        '2026-09-24 00:00:00', 'VALIDATED',
        '46AC3E570C2622F832E3EDC1053A792B4336536EDB880F563D625487BB5488A8',
        '2026-09-24 00:00:00'
    ) RETURNING id
)
INSERT INTO knowledge_chunk (source_id, chunk_order, content, section_label, locator, created_at)
SELECT source.id, chunk.chunk_order, chunk.content, chunk.section_label, chunk.locator, '2026-09-24 00:00:00'
FROM source
CROSS JOIN (VALUES
    (0, 'Adición de créditos (ADICION_CREDITOS): REGISTRADA = recién creada, aún no la revisa la facultad. EN_FACULTAD = en revisión de la facultad. APROBADA_FACULTAD = la facultad ya aprobó, falta cargarla en QF/CLASS. EN_REGISTRO_CALI = Registro y Control Cali cargándola en QF. EN_REGISTRO_NACIONAL = Registro y Control Nacional matriculando en CLASS. FINALIZADA = cerrada, la matrícula ya quedó registrada. DEVUELTA = devuelta a la Coordinación para corrección, requiere motivo. RECHAZADA = rechazada por la facultad, por ejemplo por extemporánea.', 'Glosario: adición de créditos', 'docs/openrouter/glosario-estados-asistente.md#adición-de-créditos'),
    (1, 'Novedad de notas (NOVEDAD_NOTAS): REGISTRADA = recién creada. EN_PREPARACION = armando la carpeta (planillas, formato, soportes) y firmas. EN_FACULTAD = en revisión de la facultad. EN_REVISION_FINANCIERA = en revisión del Área Financiera. EN_REGISTRO_CONTROL = en Registro y Control para el cierre. FINALIZADA = cerrada, la nota ya quedó registrada en el sistema institucional. Este trámite no tiene estado de rechazo: las devoluciones regresan a EN_PREPARACION en vez de crear un estado propio.', 'Glosario: novedad de notas', 'docs/openrouter/glosario-estados-asistente.md#novedad-de-notas')
) AS chunk(chunk_order, content, section_label, locator);
