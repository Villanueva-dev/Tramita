package com.uniremington.api.tramita.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.uniremington.api.tramita.TestcontainersConfiguration;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.service.IRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * IT de la inmutabilidad del timeline a nivel de BD (T029, SC-002): la promesa
 * de FR-007 no depende solo de que la aplicación no exponga edición/borrado —
 * el trigger de V2.0.0 rechaza la mutación incluso con acceso directo por SQL.
 * Nota TDD: este test nace en verde a propósito — el trigger se creó con el
 * esquema (T002, fase fundacional); el test lo fija como contrato y verifica
 * que discrimina por el mensaje exacto del RAISE EXCEPTION.
 */
@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://placeholder:5432/placeholder",
        "DB_USER=placeholder",
        "DB_PASSWORD=placeholder",
        "APP_CORS_ALLOWED_ORIGINS=http://localhost:5173",
        // Mismos literales que AuthControllerIT (package-private, inaccesible
        // desde repo/): el caché de contexto compara properties por valor
        "SEED_COORD_EMAIL=" + TimelineImmutabilityIT.SEED_EMAIL,
        "SEED_COORD_PASSWORD=" + TimelineImmutabilityIT.SEED_PASSWORD
})
@Import(TestcontainersConfiguration.class)
class TimelineImmutabilityIT {

    static final String SEED_EMAIL = "coordinacion.cali@uniremington.edu.co";
    static final String SEED_PASSWORD = "frase de paso de integracion";

    @Autowired
    private IRequestService requestService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("UPDATE y DELETE directos sobre el log: el trigger los rechaza (SC-002)")
    void directUpdateAndDeleteOnTimelineAreRejectedByTrigger() {
        // Una entrada real: el registro escribe el nacimiento en el log
        requestService.register(
                new CreateRequestBody("ADICION_CREDITOS", "Inmutable Total", "501"),
                SEED_EMAIL);
        Long entryId = jdbcTemplate.queryForObject(
                "SELECT max(id) FROM request_transition_log", Long.class);
        assertThat(entryId).isNotNull();

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate.update(
                        "UPDATE request_transition_log SET note = 'hackeada' WHERE id = ?", entryId))
                .withMessageContaining("inmutable");

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate.update(
                        "DELETE FROM request_transition_log WHERE id = ?", entryId))
                .withMessageContaining("inmutable");

        // La entrada sigue intacta después de ambos intentos
        String note = jdbcTemplate.queryForObject(
                "SELECT note FROM request_transition_log WHERE id = ?", String.class, entryId);
        assertThat(note).isNull();
    }
}
