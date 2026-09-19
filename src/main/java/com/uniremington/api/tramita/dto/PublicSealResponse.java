package com.uniremington.api.tramita.dto;

import java.time.LocalDateTime;

/**
 * Lo que ve quien verifica SIN cuenta (`GET /public/seals/{code}`, FR-014b).
 *
 * Contiene ÚNICAMENTE datos de emisión: ningún nombre, ninguna cédula, ningún correo, ningún
 * dato académico (FR-014c, §III). Este canal no recibe huella (research.md D10), así que no
 * compara nada y no puede pronunciarse sobre integridad — ni «íntegro» ni «no verificable» son
 * respuestas posibles acá. Eso es exclusivo de {@link VerdictResponse}, en
 * {@code POST /api/seals/verify}, con sesión.
 *
 * @param status único valor posible, {@link Status#ISSUED}: existe un sello con ese código. Se
 *     conserva como campo —en vez de responder solo los datos— para que el JSON sea
 *     autoexplicativo
 * @param issuedAt cuándo se emitió. Contrastable contra el pie impreso
 * @param stateName nombre del estado del trámite al emitir, congelado: es el mismo texto que
 *     el pie del documento muestra, para que el contraste sea directo
 * @param revision revisión de los datos al emitir. También impresa en el pie
 */
public record PublicSealResponse(
        Status status, LocalDateTime issuedAt, String stateName, long revision) {

    /** Un solo valor. No hay «no verificable» acá: este canal no compara nada (research.md D9). */
    public enum Status {
        ISSUED
    }
}
