package com.uniremington.api.tramita.dto;

import java.time.LocalDateTime;

/**
 * EL RESULTADO DE VERIFICAR UN DOCUMENTO CONTRA SU SELLO, para quien tiene sesión
 * (`POST /api/seals/verify`, FR-006, FR-014d).
 *
 * 🔑 SON TRES RESULTADOS Y NO DOS, Y ESA ES LA DECISIÓN CENTRAL DE LA FEATURE. Un sistema
 * ingenuo compara huellas y responde «íntegro» o «alterado». Pero hay dos situaciones en las
 * que las huellas difieren sin que nadie haya falsificado nada: el formato del papel cambió
 * —logo, maquetación, tipografías—, o los datos de la solicitud avanzaron después de emitir.
 * En ambas, el documento en la mano es legítimo y el sistema simplemente ya no puede
 * reconstruirlo para compararlo.
 *
 * Responder «alterado» ahí sería acusar de falsificación sin poder sostenerlo, que es lo que
 * el FR-007 prohíbe expresamente: una acusación que el sistema no puede sostener es peor que
 * admitir que no puede pronunciarse.
 *
 * ⚠️ ES EXCLUSIVO DEL CANAL AUTENTICADO. El canal público (`PublicSealResponse`) no recibe
 * huella (D10), así que no compara nada: solo afirma que el sello existe (FR-014b). Los tres
 * resultados de acá salen de comparar, y esa comparación necesita la huella que solo trae
 * este canal.
 *
 * @param reason por qué el sistema no puede pronunciarse. Presente SOLO con
 *     {@link Status#NOT_VERIFIABLE}; en los otros dos veredictos es {@code null}, porque ahí
 *     la comparación sí ocurrió y su resultado se explica solo
 * @param issuedAt cuándo se emitió el documento que este sello respalda
 * @param issuedBy quién pidió la emisión. Solo en este canal autenticado — el público no lo
 *     lleva, es dato personal indirecto (FR-014c, §III)
 * @param revision la revisión de los datos de la solicitud al emitir (research.md D7)
 */
public record VerdictResponse(
        Status status, Reason reason, LocalDateTime issuedAt, String issuedBy, long revision) {

    /** Los tres resultados posibles. No hay un cuarto: «sin sello conocido» es un 404. */
    public enum Status {
        /** Las huellas coinciden: es exactamente el archivo que salió del sistema. */
        INTACT,
        /** Las huellas difieren Y el sistema SÍ podía comparar. Acá la acusación se sostiene. */
        TAMPERED,
        /** El sistema no puede reconstruir el documento, así que no se pronuncia (FR-007). */
        NOT_VERIFIABLE
    }

    /** Por qué la reconstrucción no es posible. Los dos casos del {@code research.md} D6. */
    public enum Reason {
        /** El papel cambió desde la emisión: todos los sellos anteriores caen a la vez. */
        FORMAT_CHANGED,
        /** Los datos de la solicitud avanzaron: el documento es viejo, no falso. */
        DATA_CHANGED
    }
}
