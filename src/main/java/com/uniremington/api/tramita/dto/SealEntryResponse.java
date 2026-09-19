package com.uniremington.api.tramita.dto;

import java.time.LocalDateTime;

/**
 * Una emisión registrada, en el historial de una solicitud (`GET /requests/{id}/seals`,
 * contracts/openapi.yaml, FR-008, US3).
 *
 * CADA EMISIÓN ES UNA ENTRADA, SIN DEDUPLICAR: dos emisiones sin cambios producen documentos
 * idénticos —mismos datos, mismo formato— y aun así se registran por separado, con su propio
 * {@code verificationCode}, porque el valor de este historial es precisamente el recuento:
 * cuántas veces se emitió el documento de un trámite y quién lo pidió.
 */
public record SealEntryResponse(
        String verificationCode,
        LocalDateTime issuedAt,
        String issuedBy,
        long revision,
        String formatVersion,
        String stateName) {
}
