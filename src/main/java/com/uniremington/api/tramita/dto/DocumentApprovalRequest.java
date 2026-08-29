package com.uniremington.api.tramita.dto;

import com.uniremington.api.tramita.model.SignatureType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Body para registrar la constancia de una firma externa sobre un PDF adjunto. */
public record DocumentApprovalRequest(
        @NotBlank @Size(max = 120) String signerName,
        @NotBlank @Size(max = 80) String signerRole,
        @NotNull SignatureType signatureType,
        @NotNull LocalDateTime signedAt,
        @Size(max = 1000) String note) {
}