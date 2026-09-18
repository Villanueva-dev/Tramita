package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestDocumentSeal;
import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.repo.IRequestDocumentSealRepo;
import com.uniremington.api.tramita.service.IDocumentSealService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Registro de emisiones (FR-001).
 *
 * NO ABRE TRANSACCIÓN PROPIA, a propósito: se ejecuta dentro de la que emite el documento, para
 * que sellar y entregar sean atómicos (FR-012, fail-closed). Anotarlo con {@code REQUIRES_NEW}
 * rompería esa garantía — quedaría el sello de un documento que nunca se entregó, o peor, un
 * documento entregado sin sello.
 */
@Service
@RequiredArgsConstructor
public class DocumentSealServiceImpl implements IDocumentSealService {

    private final IRequestDocumentSealRepo sealRepo;

    @Override
    public RequestDocumentSeal record(
            Request request,
            User actor,
            String verificationCode,
            String documentSha256,
            String formatVersion,
            LocalDateTime issuedAt) {

        return sealRepo.save(RequestDocumentSeal.builder()
                .request(request)
                .actor(actor)
                .verificationCode(verificationCode)
                .documentSha256(documentSha256)
                .formatVersion(formatVersion)
                .requestVersion(request.getVersion())
                // Las dos columnas del estado, no solo el código: el pie imprime el nombre.
                .stateCode(request.getCurrentState().getCode())
                .stateName(request.getCurrentState().getName())
                .issuedAt(issuedAt)
                .build());
    }
}
