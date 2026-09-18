package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.model.Request;

/**
 * Condición de negocio que puede bloquear una transición (FR-015..FR-019).
 *
 * El motor conoce el NOMBRE de la regla, nunca el trámite al que pertenece
 * (FR-018): la definición declara la clave en {@code workflow_transition.guard_key}
 * y el motor la resuelve contra las implementaciones que Spring le inyecta
 * (research.md D5). Agregar una guarda es agregar una clase que se auto-registra;
 * el motor no se toca.
 *
 * Se descartaron un enum de guardas y un {@code switch} sobre la clave —ambos
 * recompilan el motor con cada guarda nueva y le devuelven el conocimiento de
 * dominio que la feature 002 le sacó— y resolver la clase por reflection, que
 * rompe en runtime ante cualquier rename, sin aviso del compilador.
 *
 * <p>Esta feature entrega el mecanismo y ninguna implementación de producción: hoy
 * ningún trámite del alcance tiene una transición condicionada, y la lectura
 * estricta de YAGNI del Principio I prohíbe inventar una. Las consumen SP3/SP4.
 */
public interface IWorkflowGuard {

    /** Clave que esta guarda atiende; se compara contra {@code guard_key}. */
    String guardKey();

    /**
     * @return true si la solicitud cumple la regla y la transición puede proceder;
     *         false si debe bloquearse sin alterar el estado (FR-016)
     */
    boolean isSatisfiedBy(Request request);
}
