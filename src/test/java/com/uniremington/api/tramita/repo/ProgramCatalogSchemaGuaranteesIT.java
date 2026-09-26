package com.uniremington.api.tramita.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * G-3 (review M-3, FR-008): las garantías del catálogo de programas y su regla de
 * anexo viven en el ESQUEMA (V5.0.0), no en la disciplina del código que las usa —
 * mismo criterio que {@link TimelineImmutabilityIT} y {@link DocumentSealImmutabilityIT}
 * para el timeline y los sellos. Cada caso ataca la base directo por SQL, sin pasar
 * por el servicio, y comprueba que la base queda como estaba tras el rechazo.
 */
@TramitaIntegrationTest
class ProgramCatalogSchemaGuaranteesIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("borrar un programa con regla de anexo falla: la FK RESTRICT lo impide (FR-008)")
    void deletingAProgramWithAnAnnexRuleFails() {
        // "Ingeniería de Sistemas" trae la única regla de la siembra V5.1.0
        // (ADICION_CREDITOS v1); ningún otro test la borra ni la renombra.
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate.update(
                        "DELETE FROM academic_program WHERE name = ?", "Ingeniería de Sistemas"))
                .withMessageContaining("workflow_annex_rule_program_id_fkey");

        Integer stillThere = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM academic_program WHERE name = ?",
                Integer.class, "Ingeniería de Sistemas");
        assertThat(stillThere)
                .as("el rechazo de la FK no debe haber borrado el programa")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("insertar un programa con nombre duplicado falla: la unicidad la impone la base (FR-008)")
    void insertingADuplicateProgramNameFails() {
        // "Derecho" es uno de los trece de la siembra V5.1.0.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO academic_program (id, name) VALUES (gen_random_uuid(), ?)",
                        "Derecho"))
                .withMessageContaining("uq_academic_program_name");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM academic_program WHERE name = ?", Integer.class, "Derecho");
        assertThat(count)
                .as("el rechazo del duplicado no debe haber dejado una segunda fila")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("insertar una regla de anexo con un programa inexistente falla: la FK lo impide (FR-008)")
    void insertingAnAnnexRuleWithAnUnknownProgramFails() {
        Integer before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM workflow_annex_rule", Integer.class);
        String definitionId = jdbcTemplate.queryForObject("""
                SELECT id FROM workflow_definition WHERE code = 'ADICION_CREDITOS' AND version = 1
                """, String.class);

        // gen_random_uuid() como program_id: no referencia ninguna fila de academic_program.
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO workflow_annex_rule
                            (id, definition_id, program_id, document_name, source_hint)
                        VALUES (gen_random_uuid(), ?::uuid, gen_random_uuid(), 'Doc', 'Hint')
                        """, definitionId))
                .withMessageContaining("workflow_annex_rule_program_id_fkey");

        Integer after = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM workflow_annex_rule", Integer.class);
        assertThat(after)
                .as("el rechazo de la FK no debe haber insertado ninguna regla nueva")
                .isEqualTo(before);
    }
}
