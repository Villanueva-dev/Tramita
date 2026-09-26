package com.uniremington.api.tramita.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una solicitud (contracts/openapi.yaml). availableTransitions queda
 * vacía cuando el estado actual es final: el trámite está cerrado.
 *
 * Los datos del formulario que la 003 agrega se omiten del JSON cuando son nulos
 * (NON_NULL): una solicitud registrada con el cuerpo mínimo de la 002 no devuelve
 * campos vacíos ni defaults inventados. Lo mismo vale para el contacto y el origen
 * de la 008: una solicitud sin correo o sin teléfono no trae esas claves, y una sin
 * entrada de nacimiento —anomalía de datos, no un tercer origen— no trae
 * {@code origin}. El contrato de la 008 lo declara así: ausente, nunca {@code null}.
 *
 * DESDE LA 008 EXPONE EL CONTACTO DEL ESTUDIANTE (FR-008), y conviene dejar escrito
 * por qué antes no y ahora sí. Hasta la 008 este javadoc decía «NO expone ningún dato
 * de contacto (FR-020, §III)». Esa cita sobrevivió a su base: el FR-020 de la 003
 * prohibía ALMACENAR el correo «hasta que exista quien lo use», no exponerlo, y la 004
 * lo reemplazó por su FR-005 al almacenarlo para el PDF formal. La razón vigente es la
 * minimización del §III —que habla de ALMACENAR— extendida por el equipo a exponer: un
 * dato de contacto se expone cuando tiene quién lo use. Es una lectura del equipo, no una
 * cita de la constitución ni de {@code Request}. La 008 es ese consumidor —la Coordinación arma con
 * él el aviso de cierre— y por eso el correo, el teléfono y el origen salen ACÁ, en el
 * detalle de una solicitud concreta bajo sesión, y NO en la búsqueda ni en la bandeja
 * ({@code RequestSummaryResponse}, {@code InboxEntryResponse}): la búsqueda lista por nombre
 * o cédula y la bandeja lo que espera a un responsable (007); ninguna lleva contacto.
 *
 * El teléfono sale tal como se guardó (FR-011): para las solicitudes nuevas son diez
 * dígitos, para las anteriores a la 008 puede tener cualquier forma. Si es un móvil o
 * no lo decide el cliente (FR-002, FR-012). El backend expone hechos; el cliente
 * decide si ofrece el aviso (research.md D5 de la 008).
 *
 * DESDE LA 009 EXPONE EL ANEXO POR PROGRAMA (FR-012, FR-013): {@code annexRequirement}
 * viaja aquí, en el detalle de una solicitud concreta, y en NINGÚN otro DTO
 * ({@code RequestSummaryResponse}, {@code InboxEntryResponse}) por la misma razón que el
 * contacto de la 008 — la búsqueda y la bandeja listan para decidir A QUIÉN atender, no
 * para tramitar una solicitud puntual, y el anexo es información de trámite. Es aditivo
 * y omitido cuando es nulo (NON_NULL): una solicitud sin programa, o con un programa sin
 * regla de anexo configurada, no trae la clave.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestResponse(
        UUID id,
        WorkflowDefinitionResponse definition,
        String studentName,
        String studentDocument,
        String studentCode,
        String program,
        String semester,
        String reason,
        List<SubjectResponse> subjects,
        StateResponse currentState,
        List<AvailableTransitionResponse> availableTransitions,
        LocalDateTime createdAt,
        /** Cómo nació (007, FR-007): la mitad de la condición del aviso; la otra es {@code currentState.isFinal}. */
        RequestOrigin origin,
        /** Destinatario del aviso por correo (008, P1). Ausente si no se declaró. */
        String studentEmail,
        /** Destinatario del aviso por WhatsApp (008, P2), tal como se guardó. Ausente si no se declaró. */
        String studentPhone,
        /** El anexo por programa vigente (009, FR-012). Ausente sin programa o sin regla configurada. */
        AnnexRequirementResponse annexRequirement) {
}
