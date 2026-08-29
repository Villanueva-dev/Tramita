package com.uniremington.api.tramita.dto;

import com.uniremington.api.tramita.model.SignatureType;
import java.time.LocalDateTime;

/** Evidencia persistida de una aprobación documental registrada por la Coordinación. */
public record DocumentApprovalResponse(
        long id,
        String signerName,
        String signerRole,
        SignatureType signatureType,
        String documentSha256,
        String recordedByEmail,
        String note,
        LocalDateTime signedAt,
        LocalDateTime timestampedAt) {
}