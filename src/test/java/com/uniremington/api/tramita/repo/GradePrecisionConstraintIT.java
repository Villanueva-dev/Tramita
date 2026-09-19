package com.uniremington.api.tramita.repo;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.service.IRequestService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * FR-013b (Acuerdo n.º 13 de 2023, art. 32): una calificación no puede tener más de un
 * decimal. La restricción vive en la base desde {@code V4.1.0}
 * ({@code ck_request_subject_grades_one_decimal}), pero ningún IT la ejercitaba por acceso
 * directo a la tabla — es el hueco que nombra el issue #34 (R3-3).
 *
 * ⚠️ EL CONTROL (3.5 → aceptada) NO ES RELLENO. Sin un caso que SÍ deba pasar, este test no
 * distingue «el CHECK rechazó la fila» de «el INSERT estaba mal escrito por otra razón» —ese
 * falso positivo ya ocurrió al medir este mismo mecanismo, según documenta el issue #34—.
 */
@TramitaIntegrationTest
class GradePrecisionConstraintIT {

    @Autowired
    private IRequestService requestService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("calificación con más de un decimal: el CHECK la rechaza (#34 R3-3)")
    void gradeWithMoreThanOneDecimalIsRejected() {
        UUID requestId = registerRequest();

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertSubject(requestId, new BigDecimal("3.46")))
                .withMessageContaining("ck_request_subject_grades_one_decimal");
    }

    @Test
    @DisplayName("control: una calificación con un decimal se acepta (distingue el CHECK de un INSERT mal escrito)")
    void gradeWithOneDecimalIsAccepted() {
        UUID requestId = registerRequest();

        assertThatCode(() -> insertSubject(requestId, new BigDecimal("3.5")))
                .doesNotThrowAnyException();
    }

    /** Una solicitud real, cuyo id sale de la respuesta de {@code register()} (issue #35). */
    private UUID registerRequest() {
        RequestResponse request = requestService.register(
                new CreateRequestBody("ADICION_CREDITOS", "Precision De Notas", "888"),
                TramitaIntegrationTest.SEED_EMAIL);
        return request.id();
    }

    private void insertSubject(UUID requestId, BigDecimal proposedGrade) {
        jdbcTemplate.update("""
                INSERT INTO request_subject (id, request_id, code, name, credits, proposed_grade)
                VALUES (?, ?, 'A-1', 'Asignatura de prueba', 3, ?)
                """, UUID.randomUUID(), requestId, proposedGrade);
    }
}
