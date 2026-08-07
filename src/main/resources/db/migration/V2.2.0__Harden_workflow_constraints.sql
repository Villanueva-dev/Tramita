-- Endurecimiento de invariantes del motor tras el code review de la feature 002.
-- Migración aparte (no edición de V2.0.0) porque esa ya corrió en los entornos
-- de desarrollo y en Testcontainers: editarla rompería su checksum de Flyway.
--
-- Las tres reglas de abajo eran ciertas por convención de la semilla, no por el
-- esquema. Como SC-005 declara que cargar un trámite nuevo por SQL es el camino
-- soportado, la convención no alcanza: quien inserte a mano debe encontrarse el
-- error, no un comportamiento silenciosamente raro.

-- Exactamente un estado inicial por definición. Sin esto, dos filas con
-- is_initial = TRUE hacen que el estado de nacimiento lo decida el orden de
-- filas de Postgres —no determinista entre llamadas—, porque el motor resuelve
-- el inicial con un findFirst() sobre una colección sin orden garantizado.
-- Índice parcial: solo restringe las filas que declaran ser iniciales.
CREATE UNIQUE INDEX uq_workflow_state_one_initial_per_definition
    ON workflow_state (definition_id)
    WHERE is_initial;

-- Una transición hacia el mismo estado no tiene sentido en una cadena de
-- aprobación, y además rompería el locking optimista: el motor solo escribe
-- current_state_id, así que sin cambio de estado no hay campo sucio, no hay
-- UPDATE y no hay chequeo de @Version — dos avances simultáneos entrarían
-- ambos y el timeline registraría dos veces la misma acción.
ALTER TABLE workflow_transition
    ADD CONSTRAINT ck_workflow_transition_not_self
    CHECK (from_state_id <> to_state_id);
