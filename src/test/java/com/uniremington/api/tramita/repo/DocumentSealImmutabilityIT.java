package com.uniremington.api.tramita.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.uniremington.api.tramita.TramitaIntegrationTest;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.RequestResponse;
import com.uniremington.api.tramita.service.IRequestService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * FR-002: EL REGISTRO DE EMISIONES NO SE PUEDE ALTERAR NI BORRAR, TAMPOCO POR SQL DIRECTO.
 *
 * Un sello cuyo dueño pueda editarlo no prueba nada: la garantía tiene que vivir en la base
 * y no en la disciplina del código que la usa. Es el §VII de la constitución, y el mecanismo
 * es el mismo que ya protege el timeline desde la 002 ({@code trg_timeline_immutable}), así
 * que esta feature imita un patrón vigente en vez de inventar uno.
 *
 * ⚠️ EL CASO DEL INSERT NO ES DE RELLENO. Si el trigger llegara a cubrir {@code INSERT}
 * además de {@code UPDATE} y {@code DELETE}, ningún sello podría crearse; y como sellar y
 * entregar son atómicos (FR-012, fail-closed), eso no degradaría la emisión: la apagaría por
 * completo. Es el modo de fallo más caro que esta feature puede causarse a sí misma, y el
 * único que se introduce sola. Por eso se afirma explícitamente que insertar funciona.
 */
@TramitaIntegrationTest
class DocumentSealImmutabilityIT {

    @Autowired
    private IRequestService requestService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("UPDATE y DELETE directos sobre el registro de sellos: el trigger los rechaza")
    void directUpdateAndDeleteOnSealsAreRejectedByTrigger() {
        UUID sealId = insertSeal("SELLO-UPD-DEL", "a".repeat(64));

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate.update(
                        "UPDATE request_document_seal SET document_sha256 = ? WHERE id = ?",
                        "b".repeat(64), sealId))
                .withMessageContaining("inmutable");

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcTemplate.update(
                        "DELETE FROM request_document_seal WHERE id = ?", sealId))
                .withMessageContaining("inmutable");

        // La huella original sigue en pie después de los dos intentos: el rechazo no fue
        // un mensaje de error sobre un cambio que igual ocurrió.
        String digest = jdbcTemplate.queryForObject(
                "SELECT document_sha256 FROM request_document_seal WHERE id = ?", String.class, sealId);
        assertThat(digest).isEqualTo("a".repeat(64));
    }

    @Test
    @DisplayName("INSERT sigue permitido: el trigger NO cubre la creación de sellos")
    void insertingASealIsStillAllowed() {
        assertThatCode(() -> insertSeal("SELLO-INSERT", "c".repeat(64)))
                .as("Si el trigger cubriera INSERT, con fail-closed el sistema no podría "
                        + "emitir ningún documento: no es una degradación, es un apagón")
                .doesNotThrowAnyException();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM request_document_seal WHERE verification_code = ?",
                        Integer.class, "SELLO-INSERT"))
                .isEqualTo(1);
    }

    /**
     * #34 R3-2: el mecanismo de unicidad del código impreso (V4.1.0,
     * {@code ux_request_document_seal_code}) existe desde que se escribió la migración, pero
     * ningún test lo vigilaba — es el hueco que el issue nombra.
     *
     * SIN ESTO, DOS SELLOS PODRÍAN COMPARTIR CÓDIGO Y ROMPER EL CANAL PÚBLICO (FR-006): la
     * consulta por código dejaría de identificar un sello único.
     */
    @Test
    @DisplayName("dos sellos con el mismo código de verificación: el índice único los rechaza (#34 R3-2)")
    void duplicateVerificationCodeIsRejectedByUniqueIndex() {
        insertSeal("SELLO-DUP", "d".repeat(64));

        assertThatExceptionOfType(org.springframework.dao.DataIntegrityViolationException.class)
                .isThrownBy(() -> insertSeal("SELLO-DUP", "e".repeat(64)))
                .withMessageContaining("ux_request_document_seal_code");
    }

    /**
     * Un sello cualquiera, colgado de una solicitud y un actor reales.
     *
     * ⚠️ EL {@code id} SALE DE LA RESPUESTA DE {@code register()}, NO DE UNA CONSULTA
     * «la última fila» (issue #35). {@code register()} ya lo devuelve: consultarlo de nuevo
     * por {@code ORDER BY created_at DESC} desempataba por UUID, que no ordena en el tiempo
     * — con más de una fila creada en el mismo milisegundo, podía traer la solicitud
     * equivocada. Usar el valor que el servicio ya entregó elimina la carrera por completo.
     */
    private UUID insertSeal(String verificationCode, String sha256) {
        RequestResponse request = requestService.register(
                new CreateRequestBody("ADICION_CREDITOS", "Sello Inmutable", "777"),
                TramitaIntegrationTest.SEED_EMAIL);
        UUID requestId = request.id();

        UUID actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE lower(email) = lower(?)", UUID.class,
                TramitaIntegrationTest.SEED_EMAIL);

        UUID sealId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO request_document_seal
                    (id, request_id, verification_code, document_sha256, format_version,
                     request_version, state_code, state_name, actor_id, issued_at)
                VALUES (?, ?, ?, ?, 'DO_FR_100/v1+test', 0, 'RADICADA', 'Radicada', ?, now())
                """, sealId, requestId, verificationCode, sha256, actorId);
        return sealId;
    }
}
