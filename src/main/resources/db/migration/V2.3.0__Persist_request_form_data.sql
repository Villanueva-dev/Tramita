-- Datos estructurados del formulario de cada trámite (SP2, FR-001 y FR-002).
-- Hasta aquí la solicitud solo guardaba nombre y cédula: el resto del formulario
-- vivía en el Word adjunto al correo.
--
-- Todas las columnas nuevas son opcionales. No es una preferencia de estilo: esta
-- migración corre sobre una tabla con filas ya creadas por V2.0.0, y FR-006 exige
-- que un cliente que registre con el formulario mínimo anterior siga funcionando.
ALTER TABLE request ADD COLUMN student_code VARCHAR(30);
ALTER TABLE request ADD COLUMN program VARCHAR(120);
ALTER TABLE request ADD COLUMN semester VARCHAR(50);

-- FR-004: el motivo lleva longitud máxima declarada en la columna, no solo en la
-- anotación del DTO. Con TEXT, la única cota viviría en la capa de aplicación y
-- cualquier otra vía de escritura la esquivaría.
ALTER TABLE request ADD COLUMN reason VARCHAR(2000);

-- NO se agrega student_email: su único consumidor previsto era la notificación al
-- estudiante (SP7), fuera del alcance de este sprint. El dato entra cuando exista
-- quien lo use (FR-020, constitución §III — minimización de datos personales).
--
-- NO se agrega priority: sin respaldo en las entrevistas y diferida a SP5, donde
-- su consumidor natural es la bandeja de trabajo de la Coordinación.

-- Las asignaturas se separan en su propia tabla para conservar su cardinalidad:
-- un trámite involucra N asignaturas y cada una tiene sus propios datos.
CREATE TABLE request_subject (
    id             UUID         PRIMARY KEY,
    request_id     UUID         NOT NULL REFERENCES request (id),
    code           VARCHAR(30)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    credits        INT,
    subject_group  VARCHAR(30),
    current_grade  NUMERIC(3,2),
    proposed_grade NUMERIC(3,2),

    -- FR-009: los créditos son positivos. La restricción vive también en la base
    -- porque de lo contrario un valor negativo podría restar del total y situar
    -- por debajo del límite una solicitud que en realidad lo excede.
    CONSTRAINT ck_request_subject_credits_positive CHECK (credits IS NULL OR credits > 0),

    -- Cota de sanidad, no la regla institucional: el rango efectivo de las notas
    -- lo fija la configuración del trámite (workflow_parameter), no esta columna.
    CONSTRAINT ck_request_subject_grades_not_negative
        CHECK ((current_grade IS NULL OR current_grade >= 0)
           AND (proposed_grade IS NULL OR proposed_grade >= 0))
);

-- credits es nulo-permitido porque el trámite de novedad de notas no lo usa; la
-- obligatoriedad por trámite es regla de negocio, no de esquema.
--
-- subject_group y no group: group es palabra reservada en SQL.
--
-- Sin UNIQUE sobre (request_id, code): dos grupos de la misma materia son un caso
-- real de la Coordinación, y el sistema no tiene fuente para decidir que un
-- duplicado sea un error.

CREATE INDEX ix_request_subject_request_id ON request_subject (request_id);
