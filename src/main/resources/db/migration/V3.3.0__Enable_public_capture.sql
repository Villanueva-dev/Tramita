-- Canal público de captura del formato DO-FR-100 (SP2, feature 004). Hasta aquí una
-- solicitud solo podía nacer desde una sesión de la Coordinación: el estudiante
-- mandaba el Word por correo y alguien lo transcribía. Esta migración habilita que el
-- propio estudiante lo entregue diligenciado y firmado.
--
-- Sube de MINOR y no de PATCH porque agrega columnas, un parámetro nuevo al
-- vocabulario de configuración y una identidad al sistema; no corrige un dato.

-- 1) Los seis campos del formato que la solicitud todavía no guardaba ------------------
--
-- POR QUÉ ENTRAN AHORA los que V2.3.0 dejó fuera. Aquella migración excluyó el correo
-- declarando «el dato entra cuando exista quien lo use», y el mismo criterio se aplicó
-- luego al teléfono. El criterio no cambió: cambió el censo de consumidores, porque
-- aquel análisis solo miró el flujo de captura. El consumidor que no miró es la
-- GENERACIÓN DEL PDF FORMAL del trámite (SP3, Villanueva-dev/Tramita#10): un PDF que no
-- reproduce el formato oficial no sirve para lo que el trámite necesita, y el DO-FR-100
-- v2024 pide estos cuatro datos en su tabla del solicitante —verificado contra
-- material-coord/2026-06-03-coord-DO-FR-100-formato-solicitud-excepcion-de-matricula-v2024.docx
-- (research.md D10). El correo suma además el canal por el que la Coordinación responde.
--
-- La constitución §III se cumple igual: minimizar datos personales es conservar los que
-- tienen una finalidad declarada, no conservar los menos posibles.
--
-- POR QUÉ QUEDAN TODAS OPCIONALES pese a ser obligatorias en el canal público: esta
-- migración corre sobre filas ya creadas —por V2.0.0 y por el registro autenticado—, que
-- no tienen estos datos y no hay de dónde sacarlos. La obligatoriedad es del CONTRATO DE
-- ENTRADA del canal público (PublicRequestBody), no del esquema. Declararlas NOT NULL
-- rompería la migración contra toda base existente y, además, prohibiría el registro
-- mínimo que FR-006 de la 002 garantiza.
ALTER TABLE request ADD COLUMN student_email     VARCHAR(255);
ALTER TABLE request ADD COLUMN student_phone     VARCHAR(30);
ALTER TABLE request ADD COLUMN campus            VARCHAR(120);
ALTER TABLE request ADD COLUMN faculty           VARCHAR(120);
ALTER TABLE request ADD COLUMN modality          VARCHAR(50);

-- La firma es el único campo sin longitud declarada en la columna, a diferencia de
-- reason en V2.3.0. No es una inconsistencia: el trazo llega como URL de datos y su cota
-- real la fija el filtro sobre el cuerpo entero (256 KB, research.md D7), no el campo. Un
-- VARCHAR(n) acá solo agregaría un número inventado que habría que mantener sincronizado
-- con ese tope.
ALTER TABLE request ADD COLUMN student_signature TEXT;

-- 2) Qué trámites admiten captura pública (FR-002, research.md D1) ---------------------
--
-- Misma semántica que CAPTURES_CREDITS en V3.1.0, y por la misma razón: el parámetro es
-- OPCIONAL y su ausencia significa «este trámite no tiene canal público». Exigirlo en
-- toda definición encarecería crear un trámite nuevo, que es justo lo que el motor
-- configurable abarata.
--
-- NOVEDAD_NOTAS no se declara, y no por descuido: su formato lo diligencia el docente
-- —no el estudiante—, de modo que un enlace público para el estudiante no tendría a
-- quién servir. Cuando eso cambie, es una fila, no un despliegue.
INSERT INTO workflow_parameter (id, definition_id, parameter_key, parameter_value)
SELECT gen_random_uuid(), d.id, 'PUBLIC_CAPTURE_ENABLED', 'true'
FROM workflow_definition d
WHERE d.code = 'ADICION_CREDITOS' AND d.version = 1;

-- 3) La identidad del canal público (research.md D4) -----------------------------------
--
-- ⚠️ ESTA FILA NO ES UNA PERSONA Y NO SE DEBE BORRAR. Existe porque
-- request_transition_log.actor_id es NOT NULL por el §VII de la constitución: todo tramo
-- del histórico nombra a un responsable. El responsable del tramo inicial de una
-- solicitud pública ES el portal, así que la fila además es honesta: el histórico se lee
-- «lo radicó el portal público», que informa más que un valor vacío.
--
-- Se descartó permitir actor_id nulo: aflojar una garantía estructural para cubrir un
-- caso equivale a perderla para todos.
--
-- No puede iniciar sesión, por dos vías independientes:
--   (a) el password_hash declara el algoritmo BCrypt pero su contenido NO es un hash
--       BCrypt válido. BCryptPasswordEncoder.matches() no reconoce el patrón, registra
--       una advertencia y devuelve false: el intento termina en el 401 genérico, igual
--       que cualquier otra credencial equivocada (FR-002, anti-enumeración).
--   (b) active = FALSE — AppUserDetailsService la mapea a disabled, de modo que aunque
--       existiera una clave que la abriera, la cuenta seguiría rechazada.
--
-- ⚠️ EL PREFIJO {bcrypt} NO ES DECORATIVO, y este es el detalle que cuesta un 500 si se
-- omite. SecurityConfig usa DelegatingPasswordEncoder, que elige el codificador por ese
-- prefijo; sin él no tiene a quién delegar y LANZA IllegalArgumentException en lugar de
-- devolver false. Medido el 2026-09-16: la versión sin prefijo de esta misma fila hizo
-- fallar portalAccountCannotAuthenticate con
--   «Given that there is no default password encoder configured, each password must have
--    a password encoding prefix».
--
-- Y no alcanza con que la cuenta esté inactiva: en Spring Security 7 el orden es
-- performPreCheck → additionalAuthenticationChecks, es decir, la contraseña se evalúa
-- ANTES que el estado de la cuenta (AbstractUserDetailsAuthenticationProvider:159→191).
-- Ese orden es deliberado —mitiga el ataque de tiempo que distinguiría una cuenta
-- deshabilitada de una inexistente—, y su consecuencia acá es que (a) es la defensa que
-- de verdad se ejecuta primero, no (b).
--
-- El dominio .local señala que la dirección no es real y no enruta a ningún buzón.
INSERT INTO users (id, email, password_hash, active, created_at, updated_at)
VALUES (gen_random_uuid(), 'portal-publico@tramita.local',
        '{bcrypt}CUENTA-DE-SISTEMA-SIN-CLAVE', FALSE, NOW(), NOW());
