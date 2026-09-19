package com.uniremington.api.tramita.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body de {@code POST /api/seals/verify} (contracts/openapi.yaml, research.md D10).
 *
 * EL ARCHIVO NO VIAJA, SU HUELLA SÍ. Calcularla es trivial en cualquier entorno —
 * {@code sha256sum} en la terminal, {@code crypto.subtle.digest} en el navegador— y enviarla
 * en lugar del archivo elimina de un plumazo el manejo de cargas: sin {@code multipart}, sin
 * tope de tamaño, sin materializar un cuerpo grande en memoria. Hace además literal el
 * FR-010: el sistema nunca recibe el archivo, así que no puede almacenarlo ni por accidente.
 *
 * Un cuerpo inválido responde {@code 400}, no {@code 422} (D10): acá el cliente es el
 * frontend de la Coordinación y un campo ausente o mal formado es un defecto del contrato de
 * entrada, no un formulario que un estudiante llenó a medias.
 */
public record VerifyBody(
        @NotBlank @Size(max = 13) String code,
        @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String sha256) {
}
