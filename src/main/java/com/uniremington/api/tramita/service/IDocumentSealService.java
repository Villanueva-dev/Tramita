package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestDocumentSeal;
import com.uniremington.api.tramita.model.User;
import java.time.LocalDateTime;

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
}
