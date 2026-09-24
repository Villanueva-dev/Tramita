-- Contexto operativo general del asistente (docs/openrouter/contexto-asistente.md).
-- source_class TECHNICAL_REFERENCE: describe el propio sistema (glosario, flujo, límites de
-- runtime), no reglamento ni evidencia de entrevista, por lo que se sella VALIDATED directo.
WITH source AS (
    INSERT INTO knowledge_source (
        source_id, source_class, title, document_type, version_label, issuer,
        validated_at, status, content_sha256, created_at
    ) VALUES (
        'contexto-operativo-asistente', 'TECHNICAL_REFERENCE',
        'Contexto operativo del asistente Trámita', 'Referencia técnica del sistema',
        'v1-2026-09-24', 'Sistema Trámita',
        '2026-09-24 00:00:00', 'VALIDATED',
        'C9C7F1E9A2B5D6A47F6E2E36D5D8A44E6C8B23A4F1B7C0D9E5F3A2B1C0D9E8F7',
        '2026-09-24 00:00:00'
    ) RETURNING id
)
INSERT INTO knowledge_chunk (source_id, chunk_order, content, section_label, locator, created_at)
SELECT source.id, chunk.chunk_order, chunk.content, chunk.section_label, chunk.locator, '2026-09-24 00:00:00'
FROM source
CROSS JOIN (VALUES
    (0, 'Trámita es un motor de workflow configurable para dos trámites académicos de la Sede Cali: adición de créditos (autorizar matrícula por encima del tope de créditos) y novedad de notas (corregir/registrar nota tras el cierre del periodo). Es una bitácora de seguimiento: el sistema registra el avance, no aprueba ni decide. Class y QF son cajas negras; el sistema entrega el PDF formal y un humano lo asienta ahí.', 'Qué es Trámita', 'docs/openrouter/contexto-asistente.md#qué-es-trámita'),
    (1, 'El asistente puede responder preguntas documentales con respaldo en fuentes validadas, y preguntas operativas sobre el estado agregado de la bandeja (totales, distribución por estado, tiempo promedio de ciclo, devoluciones), siempre sin datos personales. Sin evidencia documental ni pregunta operativa reconocible, se abstiene: "No encontré respaldo suficiente en las fuentes validadas disponibles."', 'Qué puede responder el asistente', 'docs/openrouter/contexto-asistente.md#qué-puede-responder-el-asistente'),
    (2, 'El asistente no cambia solicitudes, no ejecuta transiciones de workflow y no accede directamente a la base de datos ni al sistema de archivos. No expone el detalle de una solicitud individual por folio en el chat: ese detalle vive en la bandeja normal con su propio control de acceso. No responde con autoridad normativa hasta que exista un Reglamento Estudiantil validado como fuente institucional oficial.', 'Qué NO hace el asistente', 'docs/openrouter/contexto-asistente.md#qué-no-hace-el-asistente-por-diseño-no-por-bug')
) AS chunk(chunk_order, content, section_label, locator);
