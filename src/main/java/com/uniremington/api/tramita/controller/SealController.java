package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.VerdictResponse;
import com.uniremington.api.tramita.dto.VerifyBody;
import com.uniremington.api.tramita.service.IDocumentSealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificación exacta de un documento contra su sello, con sesión (006, FR-014d).
 *
 * RECIBE LA HUELLA, NO EL ARCHIVO (research.md D10): calcular un SHA-256 es trivial en
 * cualquier entorno, y enviarlo en vez del archivo elimina el manejo de cargas —sin
 * {@code multipart}, sin tope de tamaño, sin materializar un cuerpo grande en memoria— y hace
 * literal el FR-010: el sistema nunca recibe el archivo.
 *
 * SIN {@code permitAll} propio: exige sesión por el {@code anyRequest().authenticated()} por
 * defecto de {@code SecurityConfig}, igual que el resto de las rutas de la Coordinación.
 */
@RestController
@RequestMapping("/api/seals")
@RequiredArgsConstructor
public class SealController {

    private final IDocumentSealService sealService;

    @PostMapping("/verify")
    public VerdictResponse verify(@Valid @RequestBody VerifyBody body) {
        return sealService.verify(body.code(), body.sha256());
    }
}
