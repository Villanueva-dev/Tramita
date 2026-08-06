package com.uniremington.api.tramita.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Unit test del mapeo de concurrencia (T023): la carrera real de dos avances no
 * se puede orquestar honestamente en MockMvc — aquí se verifica que, cuando el
 * provider la detecta (@Version, research.md D6), el cliente recibe un 409 con
 * instrucción de reintento y sin internals filtrados.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("conflicto optimista: 409 con detail accionable y sin internals de JPA")
    void optimisticLockMapsTo409WithoutLeakingInternals() {
        var exception = new ObjectOptimisticLockingFailureException(
                "com.uniremington.api.tramita.model.Request", "un-uuid-interno");

        ProblemDetail problem = handler.handleOptimisticLock(exception);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Conflicto de concurrencia");
        // Nada del mensaje del provider (clase, id) llega al cliente
        assertThat(problem.getDetail()).doesNotContain("Request", "uuid-interno");
    }
}
