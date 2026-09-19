package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.model.RequestDocumentSeal;

/**
 * EL RESULTADO DE VERIFICAR UN DOCUMENTO CONTRA SU SELLO (FR-006).
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
 * @param reason por qué el sistema no puede pronunciarse. Presente SOLO con
 *     {@link Status#NOT_VERIFIABLE}; en los otros dos veredictos es {@code null}, porque ahí
 *     la comparación sí ocurrió y su resultado se explica solo
 * @param seal el sello contra el que se verificó, para que quien reciba el veredicto pueda
 *     mostrar cuándo se emitió y sobre qué revisión
 */
public record SealVerdict(Status status, Reason reason, RequestDocumentSeal seal) {

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

    public static SealVerdict intact(RequestDocumentSeal seal) {
        return new SealVerdict(Status.INTACT, null, seal);
    }

    public static SealVerdict tampered(RequestDocumentSeal seal) {
        return new SealVerdict(Status.TAMPERED, null, seal);
    }

    public static SealVerdict notVerifiable(Reason reason, RequestDocumentSeal seal) {
        return new SealVerdict(Status.NOT_VERIFIABLE, reason, seal);
    }
}
