package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestDocumentSeal;
import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.repo.IRequestDocumentSealRepo;
import com.uniremington.api.tramita.service.IDocumentRenderer;
import com.uniremington.api.tramita.service.IDocumentSealService;
import com.uniremington.api.tramita.service.SealVerdict;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Solo para saber QUÉ VERSIONES DE FORMATO siguen vigentes, no para renderizar. No se
     * resuelve cuál renderer le toca a la solicitud: eso duplicaría el §VI. Inyectar la lista
     * NO crea ciclo — ningún renderer depende de este servicio.
     */
    private final List<IDocumentRenderer> renderers;

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

    @Override
    @Transactional(readOnly = true)
    public SealVerdict verify(String verificationCode, String documentSha256) {
        RequestDocumentSeal seal = sealRepo.findByVerificationCode(verificationCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe un sello con el código " + verificationCode));

        // La comparación que SÍ significa algo: contra la huella del documento EMITIDO.
        // Es definitiva y no caduca: no depende de poder reconstruir nada.
        if (seal.getDocumentSha256().equals(documentSha256)) {
            return SealVerdict.intact(seal);
        }

        // No coincide. Antes de acusar, buscar una explicación legítima (FR-007).
        boolean formatoVigente = renderers.stream()
                .anyMatch(renderer -> renderer.formatVersion().equals(seal.getFormatVersion()));
        if (!formatoVigente) {
            return SealVerdict.notVerifiable(SealVerdict.Reason.FORMAT_CHANGED, seal);
        }
        if (seal.getRequestVersion() != seal.getRequest().getVersion()) {
            return SealVerdict.notVerifiable(SealVerdict.Reason.DATA_CHANGED, seal);
        }

        // Ninguna explicación legítima: acá la acusación se sostiene.
        return SealVerdict.tampered(seal);
    }
}
