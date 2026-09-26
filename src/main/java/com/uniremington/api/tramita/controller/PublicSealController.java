package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.PublicSealResponse;
import com.uniremington.api.tramita.service.IDocumentSealService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificación abierta por posesión del código impreso en el documento (006, FR-014,
 * FR-014b). TERCER endpoint sin sesión del sistema, después del login y de la captura
 * pública (004); el cuarto es el catálogo de programas (009, {@code PublicProgramController}).
 *
 * LA POSESIÓN DEL DOCUMENTO ES LA AUTORIZACIÓN (research.md D9): el código tiene 64 bits de
 * entropía, así que recorrer el espacio no es viable y este canal no necesita límite de tasa
 * propio, a diferencia de la captura pública.
 *
 * ⚠️ EL {@code code} NO LLEVA CONSTRAINTS, a propósito y por la misma razón que
 * {@link PublicRequestController}: un código con forma inválida y uno válido que nunca
 * existió responden exactamente igual —404, vía {@link IDocumentSealService#lookup}—, porque
 * distinguirlos permitiría sondear el espacio de códigos. Validar la forma acá no sumaría
 * nada y solo arriesgaría el mismo defecto de method validation que ese controller documenta.
 */
@RestController
@RequestMapping("/api/public/seals")
@RequiredArgsConstructor
public class PublicSealController {

    private final IDocumentSealService sealService;

    @GetMapping("/{code}")
    public PublicSealResponse lookup(@PathVariable String code) {
        return sealService.lookup(code);
    }
}
