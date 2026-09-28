package com.uniremington.api.tramita.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Unit test de la validación fail-fast del canal público (004, FR-016 a FR-019).
 *
 * El javadoc del record promete que «un valor absurdo acá no se descubre cuando un
 * estudiante queda bloqueado, sino cuando la aplicación no levanta». Estos tests
 * verifican esa promesa, que hasta el 2026-09-21 era más ancha que la validación: solo
 * se exigía {@code > 0}, de modo que un tope disparatado arrancaba sin chistar y
 * derribaba el canal en caliente.
 */
class PublicCapturePropertiesTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Test
    @DisplayName("el tope vigente (256 KB) es válido: la guarda no estorba a la configuración real")
    void acceptsTheConfiguredLimit() {
        assertThatCode(() -> new PublicCaptureProperties(20, WINDOW, DataSize.ofKilobytes(256)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un tope de 2 GiB se rechaza al arrancar, no en caliente")
    void rejectsBodySizeThatOverflowsTheRead() {
        // El filtro lee con readNBytes((int) maxBytes + 1). Con 2 GiB el cast desborda a
        // negativo y readNBytes lanza IllegalArgumentException EN CADA PETICIÓN: el canal
        // queda caído por configuración, no por carga. Con el tope de hoy (256 KB) no puede
        // ocurrir; esto es prevención de configuración, no un defecto vivo.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicCaptureProperties(20, WINDOW, DataSize.ofGigabytes(2)))
                .withMessageContaining("max-body-size");
    }

    @Test
    @DisplayName("el mensaje del rechazo dice cuál es el máximo, no solo que está mal")
    void explainsTheUpperBound() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicCaptureProperties(20, WINDOW, DataSize.ofMegabytes(64)))
                .satisfies(ex -> assertThat(ex.getMessage())
                        .as("quien configura mal necesita saber qué valor sí sirve")
                        .contains("1MB"));
    }

    @Test
    @DisplayName("el techo exacto se acepta: la frontera es «mayor que», no «mayor o igual»")
    void acceptsExactlyTheUpperBound() {
        // Sin este caso, cambiar > por >= deja la suite entera en verde y el techo pasa a ser
        // uno menos de lo que su mensaje promete.
        assertThatCode(() -> new PublicCaptureProperties(20, WINDOW, DataSize.ofMegabytes(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un tope no positivo sigue rechazándose: la guarda nueva no reemplaza a la vieja")
    void stillRejectsNonPositiveBodySize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicCaptureProperties(20, WINDOW, DataSize.ofBytes(0)));
    }
}
