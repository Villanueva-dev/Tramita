-- Registro de emisiones del documento formal, y la precisión de las calificaciones.
--
-- FR-001/FR-002: cada vez que el sistema emite un documento queda un sello permanente que
-- nadie puede alterar ni borrar. FR-013b: una calificación no puede tener más de un decimal,
-- porque si el PDF se genera con un valor y la base guarda otro, la huella del sello deja de
-- describir lo que el trámite realmente registró.
--
-- EL ORDEN DE ESTE ARCHIVO NO ES DECORATIVO: el saneamiento va antes de la restricción que
-- lo exige. Invertirlo hace que Flyway falle al aplicar la migración y deja el arranque roto.

-- 1. Saneamiento previo, obligatorio ------------------------------------------------------
--
-- Hay filas anteriores a esta regla que no la cumplen: la columna es NUMERIC(3,2), así que
-- una nota de 3.456 se persistió como 3.46. Se redondean a un decimal ANTES de declarar la
-- restricción. Es una modificación de datos ya escritos, y es deliberada: la alternativa
-- —dejarlas y no declarar la restricción— conserva justamente la discrepancia que el sello
-- no puede tolerar. Al momento de escribir esta migración la instancia local tenía una fila
-- afectada; el WHERE la hace idempotente y segura en instancias que no tengan ninguna.

UPDATE request_subject
SET current_grade = round(current_grade, 1)
WHERE current_grade IS NOT NULL
  AND current_grade <> round(current_grade, 1);

UPDATE request_subject
SET proposed_grade = round(proposed_grade, 1)
WHERE proposed_grade IS NOT NULL
  AND proposed_grade <> round(proposed_grade, 1);

-- 2. La restricción de precisión ----------------------------------------------------------
--
-- EL TIPO DE LA COLUMNA NO CAMBIA, a propósito. V2.3.0:43-45 declara que el rango efectivo
-- de las notas lo fija la configuración del trámite y no el esquema; reescribir el tipo
-- movería la regla institucional a la base y contradiría esa decisión. Lo que sí pertenece a
-- la base es la precisión, que es una propiedad del dato y no una política de negocio.
--
-- Mismo patrón que ck_request_subject_credits_positive (V2.3.0:38-41): la regla vive en la
-- validación de entrada Y acá, porque una de las dos sola se puede esquivar.
--
-- Respaldo normativo: Acuerdo n.º 13 de 2023 (Reglamento Estudiantil de Pregrado), art. 32 —
-- las calificaciones van de 0.0 a 5.0 con un decimal.

ALTER TABLE request_subject
    ADD CONSTRAINT ck_request_subject_grades_one_decimal
    CHECK ((current_grade  IS NULL OR current_grade  = round(current_grade,  1))
       AND (proposed_grade IS NULL OR proposed_grade = round(proposed_grade, 1)));

-- 3. El registro de sellos ----------------------------------------------------------------
--
-- Es una tabla de SOLO ANEXADO: un sello es una fotografía del momento en que el documento
-- salió, y una fotografía que se puede retocar no prueba nada.
--
-- EL ESTADO SE COPIA EN VEZ DE REFERENCIARSE, Y SE COPIAN LAS DOS COLUMNAS. Una clave foránea
-- diría en qué estado está HOY aquella transición, no en cuál estaba el trámite al emitir. Y
-- guardar solo el código no alcanza: el pie impreso muestra el NOMBRE legible del estado, así
-- que si hubiera que resolverlo contra workflow_state en tiempo de verificación, un simple
-- renombre de estado —configuración pura, que el §VI habilita y que no toca ni la revisión de
-- la solicitud ni la versión del formato— haría que un documento legítimo se reportara como
-- alterado. Congelar state_name cierra esa acusación falsa y cuesta una columna.
--
-- Los anchos copian los de su origen en workflow_state (V2.0.0:24-25), no uno más angosto:
-- con fail-closed, una columna corta no truncaría el sello, apagaría la emisión entera.
--
-- No hay columna de documento: el archivo no se almacena (FR-010). El sello apunta a la
-- solicitud y a la revisión con la que se reconstruye. Tampoco hay updated_at ni deleted_at,
-- porque describirían estados que esta tabla no admite.

CREATE TABLE request_document_seal (
    id                UUID         PRIMARY KEY,
    request_id        UUID         NOT NULL REFERENCES request (id),
    verification_code VARCHAR(13)  NOT NULL,
    document_sha256   VARCHAR(64)  NOT NULL,
    format_version    VARCHAR(80)  NOT NULL,
    request_version   BIGINT       NOT NULL,
    state_code        VARCHAR(50)  NOT NULL,
    state_name        VARCHAR(120) NOT NULL,
    actor_id          UUID         NOT NULL REFERENCES users (id),
    issued_at         TIMESTAMP    NOT NULL
);

-- El único sirve a la consulta pública, que entra por el código, y garantiza que dos sellos
-- no compartan identificador impreso.
CREATE UNIQUE INDEX ux_request_document_seal_code
    ON request_document_seal (verification_code);

-- El segundo sirve al historial de emisiones de una solicitud, en orden. Misma forma que
-- ix_request_transition_log_timeline (V2.0.0:77-78), que resuelve la misma clase de consulta.
CREATE INDEX ix_request_document_seal_request
    ON request_document_seal (request_id, issued_at, id);

-- 4. Inmutabilidad ------------------------------------------------------------------------
--
-- La garantía vive en el motor y no en la disciplina del código que escribe (§VII). Se imita
-- el mecanismo que ya protege el timeline desde la 002 (trg_timeline_immutable, V2.0.0:80-88)
-- en vez de inventar uno nuevo.
--
-- ⚠️ EL TRIGGER CUBRE UPDATE Y DELETE, NUNCA INSERT. Incluir INSERT impediría crear sellos, y
-- como sellar y entregar son atómicos (FR-012), eso no degradaría la emisión: la apagaría por
-- completo. Es el modo de fallo más caro que esta feature puede introducir, y el único que se
-- introduce a sí misma. DocumentSealImmutabilityIT lo vigila con un caso propio.

CREATE FUNCTION reject_document_seal_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'request_document_seal es inmutable: solo se permite INSERT (FR-002)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_document_seal_immutable
    BEFORE UPDATE OR DELETE ON request_document_seal
    FOR EACH ROW EXECUTE FUNCTION reject_document_seal_mutation();
