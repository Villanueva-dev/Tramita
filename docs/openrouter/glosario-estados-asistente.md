# Glosario de estados — fuente para el asistente

Este documento **es la fuente de autoría** del `knowledge_source` `glosario-estados-workflow`
sembrado en `V5.3.0__Seed_assistant_state_glossary.sql`. Igual que
`material-coord/transcript-entrevista-coordi.md` es la fuente de `V5.1.0`, este archivo se edita
aquí y se refleja a mano en la migración — `knowledge_chunk` es inmutable por diseño
(`V5.0.0`), así que un cambio posterior exige una migración nueva, no un `UPDATE`.

`source_class = 'TECHNICAL_REFERENCE'`: no es reglamento ni evidencia de entrevista, es la
definición del propio sistema (los códigos de `workflow_state` sembrados en
`V2.1.0__Seed_workflow_definitions.sql`), por lo que puede marcarse `VALIDATED` sin depender de
un documento institucional externo.

## Por qué existe

El asistente (`AssistantServiceImpl`) ahora puede responder preguntas operativas ("¿cuántas
solicitudes hay pendientes?", "¿cómo va el proceso?") usando las métricas agregadas de
`IRequestMetricsService` — sin datos personales, ver `RequestMetricsResponse`. Esas métricas
llegan al modelo con los **códigos crudos** del estado (`EN_FACULTAD`, `EN_REGISTRO_CALI`,...);
sin este glosario el modelo tendría que adivinar su significado, con riesgo de alucinar una
explicación incorrecta del proceso. `AssistantServiceImpl.STATE_GLOSSARY` ya trae una copia en
Java para la traducción determinista; este `knowledge_chunk` es la versión recuperable y citable
para cuando la pregunta es sobre el significado de un estado en sí, no solo un conteo.

## Contenido

### Adición de créditos (`ADICION_CREDITOS`, v1)

| Código | Significado |
|---|---|
| `REGISTRADA` | Recién creada; aún no la revisa la facultad. |
| `EN_FACULTAD` | En revisión de la facultad. |
| `APROBADA_FACULTAD` | La facultad ya aprobó; falta cargarla en QF/CLASS. |
| `EN_REGISTRO_CALI` | Registro y Control Cali la está cargando en QF. |
| `EN_REGISTRO_NACIONAL` | Registro y Control Nacional está matriculando en CLASS. |
| `FINALIZADA` | Cerrada: la matrícula ya quedó registrada. |
| `DEVUELTA` | Devuelta a la Coordinación para corrección; requiere motivo. |
| `RECHAZADA` | Rechazada por la facultad (p. ej. extemporánea); cierre sin éxito. |

### Novedad de notas (`NOVEDAD_NOTAS`, v1)

| Código | Significado |
|---|---|
| `REGISTRADA` | Recién creada. |
| `EN_PREPARACION` | Armando la carpeta (planillas, formato, soportes) y firmas. |
| `EN_FACULTAD` | En revisión de la facultad. |
| `EN_REVISION_FINANCIERA` | En revisión del Área Financiera (soporte de pago). |
| `EN_REGISTRO_CONTROL` | En Registro y Control para el cierre. |
| `FINALIZADA` | Cerrada: la nota ya quedó registrada en el sistema institucional. |

No existe estado de rechazo en este trámite (evidencia de entrevista, `V5.1.0`, §5.3): las
devoluciones regresan a `EN_PREPARACION` en vez de crear un estado propio.
