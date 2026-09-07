-- Qué trámites capturan créditos (FR-009). Sube de MINOR y no de PATCH porque
-- agrega un parámetro nuevo al vocabulario de configuración, no corrige un dato.
--
-- Antes de esta migración, "el trámite no captura créditos" y "al operador se le
-- olvidó cargar MAX_CREDITS" eran el mismo estado: la ausencia del tope. Esa
-- ambigüedad tenía dos consecuencias, ambas medidas contra la instancia local:
--
--   1. Una adición de créditos SIN declararlos se registraba con 201 y el tope no
--      se aplicaba nunca — bastaba omitir el dato para esquivar la validación.
--   2. Una novedad de notas CON créditos moría con un 500 de configuración
--      incompleta, culpando al servidor de un dato de más del cliente.
--
-- Declararlo explícitamente separa los dos casos. Se descartó deducirlo de la
-- presencia de MAX_CREDITS: la ausencia del tope volvería a significar dos cosas a
-- la vez, y un borrado accidental del parámetro pasaría a responder 422 en lugar
-- de 500, que es exactamente el fallo que FR-010 y FR-011 existen para impedir.
--
-- El parámetro es OPCIONAL y su ausencia significa "no captura créditos". Esa
-- asimetría es deliberada: obligar a declararlo en toda definición encarecería
-- crear un trámite nuevo, que es justamente lo que el motor configurable abarata
-- (el trámite de demostración se sigue creando sin un solo parámetro).
INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), d.id, 'CAPTURES_CREDITS', 'true'
FROM workflow_definition d
WHERE d.code = 'ADICION_CREDITOS' AND d.version = 1;

-- NOVEDAD_NOTAS no se declara, y su fuente es el formato oficial, no una
-- inferencia: el documento que la Coordinación diligencia
-- (material-coord/2026-06-03-coord-formato-novedad-notas.docx) tiene código y
-- nombre de asignatura, programa, modalidad, la estructura de evaluación y la
-- nota definitiva — ninguna columna de créditos. La coordinación lo enumera
-- igual al describirlo: "los datos de la materia, el código, el Penum, tu
-- nombre, tu cédula, las cuatro notas y la definitiva".

-- ERRATA que esta migración no puede corregir en su origen: el comentario de la
-- V2.3.0 sobre `priority` afirma "sin respaldo en las entrevistas". Es falso — la
-- fuente registra que la coordinación atiende por orden de llegada y que una
-- bandera de prioridad es deseable pero no bloqueante para la primera versión
-- (Q20, material-coord/2026-06-04-entrevista3-sintesis-analitica.md:165-168), lo
-- que AVALA diferirla, solo que por otra razón. La corrección se deja aquí y no
-- allá porque una migración aplicada es inmutable: editarla, aunque sea un
-- comentario, cambia su checksum y rompe el arranque contra toda base existente.
