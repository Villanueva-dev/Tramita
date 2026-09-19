package com.uniremington.api.tramita.dto;

import java.time.OffsetDateTime;

/**
 * Una emisión registrada, en el historial de una solicitud (`GET /requests/{id}/seals`,
 * contracts/openapi.yaml, FR-008, US3).
 *
 * CADA EMISIÓN ES UNA ENTRADA, SIN DEDUPLICAR: dos emisiones sin cambios producen documentos
 * idénticos —mismos datos, mismo formato— y aun así se registran por separado, con su propio
 * {@code verificationCode}, porque el valor de este historial es precisamente el recuento:
 * cuántas veces se emitió el documento de un trámite y quién lo pidió.
 *
 * {@code issuedAt} va en la zona de la sede ({@code util.CampusTime}), CON offset — no el
 * {@code LocalDateTime} UTC crudo con que se persiste (revisión #34 M1): contrastable contra
 * el pie impreso.
 */
public record SealEntryResponse(
        String verificationCode,
        OffsetDateTime issuedAt,
        String issuedBy,
        long revision,
        String formatVersion,
        String stateName) {
}
