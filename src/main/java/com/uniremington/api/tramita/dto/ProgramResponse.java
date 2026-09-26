package com.uniremington.api.tramita.dto;

/**
 * Un programa del catálogo público (009, FR-001, SC-004).
 *
 * Solo el nombre —ni {@code id}, ni conteos— viaja al cliente: es lo único que el
 * formulario necesita para poblar su desplegable, y exponer el identificador interno
 * no aportaría nada mientras arriesga acoplar al front un dato que no le pertenece.
 *
 * Se modela como objeto y no como {@code String} suelto para que un campo futuro
 * (por ejemplo, si el catálogo alguna vez necesita distinguir programas activos de
 * inactivos) sea un cambio ADITIVO del contrato, no una ruptura (research.md D5).
 */
public record ProgramResponse(String name) {
}
