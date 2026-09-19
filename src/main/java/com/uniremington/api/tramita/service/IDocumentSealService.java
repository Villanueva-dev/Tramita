package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.PublicSealResponse;
import com.uniremington.api.tramita.dto.SealEntryResponse;
import com.uniremington.api.tramita.dto.VerdictResponse;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestDocumentSeal;
import com.uniremington.api.tramita.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Registro de las emisiones del documento formal (FR-001, FR-002). */
public interface IDocumentSealService {

    /**
     * Deja constancia permanente de que un documento salió del sistema.
     *
     * Congela la fotografía del momento: la huella del archivo entregado, el código impreso, la
     * versión del formato con que se dibujó, la revisión de los datos y el estado del trámite
     * —código y nombre—. Sin esos valores congelados, reconstruir el documento para verificarlo
     * lo compararía contra un papel distinto del que salió.
     */
    RequestDocumentSeal record(
            Request request,
            User actor,
            String verificationCode,
            String documentSha256,
            String formatVersion,
            LocalDateTime issuedAt);

    /**
     * Dice si un documento es el que el sistema emitió (FR-006, FR-007).
     *
     * 🔑 SE COMPARA CONTRA LA HUELLA GUARDADA, NO CONTRA UN DOCUMENTO REGENERADO. El sello ya
     * registró la huella del archivo que salió del sistema, así que esa comparación es
     * definitiva: si coincide, ese archivo ES el emitido, con certeza criptográfica, sin
     * importar cuánto haya avanzado el trámite desde entonces.
     *
     * Cuando NO coincide, recién ahí se buscan las explicaciones legítimas antes de acusar:
     * el formato del papel cambió, o los datos avanzaron. Solo si ninguna aplica se dice
     * «alterado» — que es lo que el FR-007 exige: no acusar sin poder sostenerlo.
     *
     * @param documentSha256 la huella del archivo, no el archivo (D10). El sistema nunca lo
     *     recibe, así que no puede almacenarlo ni por accidente (FR-010)
     * @throws com.uniremington.api.tramita.shared.exception.ResourceNotFoundException si no hay
     *     ningún sello con ese código. NO es un cuarto veredicto: un papel que el sistema nunca
     *     emitió no se declara «alterado», porque no hay nada contra qué compararlo
     */
    VerdictResponse verify(String verificationCode, String documentSha256);

    /**
     * Dice si existe un sello con ese código, sin comparar nada (FR-014b, canal público).
     *
     * ⚠️ NO ES UNA VERSIÓN RECORTADA DE {@link #verify}: no recibe huella, así que no hay
     * comparación posible y por lo tanto tampoco hay «alterado» ni «no verificable» — el
     * único resultado con sello existente es {@code ISSUED}. Que el resultado esté acotado a
     * uno solo es la decisión que separa este canal del autenticado (research.md D9).
     *
     * @throws com.uniremington.api.tramita.shared.exception.ResourceNotFoundException si no hay
     *     ningún sello con ese código
     */
    PublicSealResponse lookup(String verificationCode);

    /**
     * El historial de emisiones de una solicitud, de la más antigua a la más reciente
     * (FR-008, US3).
     *
     * UNA SOLICITUD SIN EMISIONES DEVUELVE LISTA VACÍA, NO UN ERROR: existe y la respuesta
     * correcta es que no tiene emisiones — mismo criterio que
     * {@code RequestServiceImpl#getTimeline}. Solo cuando la SOLICITUD MISMA no existe la
     * respuesta es 404: ahí sí no hay nada de qué listar el historial.
     *
     * @throws com.uniremington.api.tramita.shared.exception.ResourceNotFoundException si la
     *     solicitud no existe
     */
    List<SealEntryResponse> history(UUID requestId);
}
