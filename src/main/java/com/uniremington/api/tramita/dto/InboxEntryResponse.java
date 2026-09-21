package com.uniremington.api.tramita.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entrada de la bandeja de trabajo (004, US2; enmendada por la 007). Es
 * {@link RequestSummaryResponse} MENOS el número de documento (FR-014 de la 004),
 * MÁS lo que la bandeja necesita para priorizar: a quién espera y de dónde vino
 * (007, FR-007).
 *
 * NO ES DUPLICACIÓN, y la diferencia es exactamente el punto. Son dos contratos con
 * reglas de exposición distintas: {@code GET /api/requests} localiza UN trámite a
 * partir de un criterio que la Coordinación ya conoce, y devolver el documento ahí
 * no expone nada que quien busca no tuviera; la bandeja, en cambio, lista SIN
 * criterio, y con el documento incluido sería un volcado del padrón.
 *
 * La razón ya está escrita en {@code IRequestRepo}, a propósito de por qué la
 * búsqueda escapa los comodines: un listado con «nombre y cédula de cada
 * estudiante» *«no es solo un bug de búsqueda»* sino un problema de minimización
 * bajo la Ley 1581 de 2012. La objeción es a exponer documentos en masa, no a
 * listar — y un DTO propio sin ese campo conserva el listado sin la objeción
 * (research.md D8).
 *
 * Se descartó reusar {@code RequestSummaryResponse} dejando el campo en null: un
 * campo presente-pero-vacío invita a que alguien lo llene más adelante sin advertir
 * por qué estaba vacío.
 */
public record InboxEntryResponse(
        UUID id,
        WorkflowDefinitionResponse definition,
        String studentName,
        StateResponse currentState,
        LocalDateTime createdAt,
        /** Redundante con el filtro pedido, y deliberado: la respuesta se lee sola. */
        String pendingResponsible,
        Origin origin) {

    /**
     * Cómo nació la solicitud (007, FR-007). Se deriva del actor de su entrada de
     * nacimiento: el canal público actúa con una cuenta propia desde la 004, así que
     * no hay nada nuevo que persistir. Importa porque cambia qué se verificó antes de
     * que llegara: lo que entra por el enlace público lo diligenció el estudiante.
     *
     * {@code COORDINATION} significa «cualquier cuenta autenticada» mientras no haya
     * roles —hoy hay una sola, la de la Coordinación—; NO es una comprobación de rol.
     */
    public enum Origin {
        COORDINATION,
        PUBLIC_LINK
    }
}
