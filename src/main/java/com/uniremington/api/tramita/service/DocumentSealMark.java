package com.uniremington.api.tramita.service;

import java.time.LocalDateTime;

/**
 * LO QUE EL PIE DEL DOCUMENTO IMPRIME, CONGELADO EN EL MOMENTO DE LA EMISIÓN (FR-003).
 *
 * 🔑 EXISTE PORQUE RECONSTRUIR NO ES VOLVER A EMITIR. Para verificar un documento el sistema lo
 * reconstruye y compara huellas, y esa reconstrucción puede ocurrir meses después. Si el
 * renderer leyera estos cuatro datos del estado vigente —la fecha con {@code now()}, el estado
 * y la revisión desde la solicitud— el documento reconstruido llevaría valores distintos del
 * que se emitió, las huellas no coincidirían y el sistema respondería que un documento
 * LEGÍTIMO fue alterado. Basta con que el trámite haya avanzado un paso, que es lo normal.
 *
 * Por eso el sello guarda estos valores (ver {@code request_document_seal}) y por eso el
 * renderer los recibe en vez de averiguarlos: al emitir vienen del presente, al reconstruir
 * vienen del sello, y el documento sale idéntico en los dos casos.
 *
 * @param verificationCode el código impreso, con el que el documento se consulta después
 * @param issuedAt el instante de la emisión, en UTC
 * @param stateName el NOMBRE legible del estado al emitir, no su código: es el texto impreso, y
 *     resolverlo contra la configuración al verificar haría que un renombre de estado —puro
 *     cambio de configuración— convirtiera un documento legítimo en uno «alterado»
 * @param requestVersion la revisión de los datos de la solicitud al emitir
 */
public record DocumentSealMark(
        String verificationCode, LocalDateTime issuedAt, String stateName, long requestVersion) {}
