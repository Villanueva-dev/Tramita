package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.PublicReceiptResponse;
import com.uniremington.api.tramita.dto.PublicRequestBody;
import com.uniremington.api.tramita.service.IRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recepción del formato DO-FR-100 diligenciado por el estudiante, sin sesión (004,
 * US1). Es el SEGUNDO endpoint abierto del sistema; el primero es el login.
 *
 * A diferencia de RequestController, acá no hay Authentication del que tomar el
 * actor: el responsable del tramo inicial lo pone el servicio y es el portal
 * (research.md D4).
 */
@RestController
@RequestMapping("/api/public/requests")
@RequiredArgsConstructor
public class PublicRequestController {

    private final IRequestService requestService;

    /**
     * 201 SIN cabecera Location, a propósito y a diferencia del registro autenticado
     * (FR-008, research.md D5): apuntaría a un recurso que quien envía no puede
     * consultar. Por eso se usa @ResponseStatus y no ResponseEntity.created(...).
     *
     * El trámite viaja en la ruta y nunca en el cuerpo (FR-002a).
     */
    /**
     * ⚠️ EL PARÁMETRO DE RUTA NO LLEVA CONSTRAINTS, y es a propósito. Basta una
     * anotación de validación en CUALQUIER parámetro del método para que Spring
     * valide el handler completo por método: entonces el fallo del cuerpo deja de
     * llegar como MethodArgumentNotValidException y llega como
     * HandlerMethodValidationException, que PublicCaptureExceptionHandler no atiende
     * y termina en el 400 genérico en lugar del 422 del contrato. Medido el
     * 2026-09-16 — el cuerpo observado fue
     * {@code {"detail":"Validation failure","status":400}}.
     *
     * Tampoco hacen falta: un código de trámite inexistente o absurdo ya se resuelve
     * en el servicio con el 404 que el contrato pide (FR-002).
     */
    @PostMapping("/{definitionCode}")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicReceiptResponse submit(
            @PathVariable String definitionCode,
            @Valid @RequestBody PublicRequestBody body) {
        requestService.registerFromPublicChannel(definitionCode, body);
        return new PublicReceiptResponse("Tu solicitud llegó a la Coordinación.");
    }
}
