package com.uniremington.api.tramita.shared.exception;

import java.util.List;

/**
 * Un campo cuyo valor se comprueba contra configuración persistida, no contra una
 * restricción de Bean Validation (009, FR-004; research.md D4). El caso de origen es
 * el catálogo de programas: {@code @Size} no puede saber qué programas existen, así
 * que la pertenencia se valida en {@code RequestBusinessRulesImpl}, junto al resto de
 * las reglas leídas de configuración.
 *
 * NO SE REUSÓ {@link UnprocessableRequestException}: esa excepción no nombra campos —
 * PublicCaptureExceptionHandler y GlobalExceptionHandler necesitan el nombre exacto
 * del campo para poblar {@code invalidFields} con el mismo contrato que ya usan para
 * los rechazos de Bean Validation (issue #27).
 *
 * El mensaje nombra SOLO campos, nunca el valor rechazado (§III de la constitución):
 * el valor puede haber llegado del formulario público y no puede reflejarse de vuelta.
 */
public class InvalidFieldValueException extends RuntimeException {

    private final List<String> invalidFields;

    public InvalidFieldValueException(List<String> invalidFields) {
        super("Campos con un valor que no pertenece a la configuración vigente: "
                + String.join(", ", invalidFields));
        this.invalidFields = List.copyOf(invalidFields);
    }

    public List<String> getInvalidFields() {
        return invalidFields;
    }
}
