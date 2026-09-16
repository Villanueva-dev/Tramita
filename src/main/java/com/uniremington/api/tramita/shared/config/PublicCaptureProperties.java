package com.uniremington.api.tramita.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Protección del canal público de captura (004, FR-016 a FR-019), leída de
 * `app.public-capture` en application.yml.
 *
 * NO VIVE EN `workflow_parameter`, y es deliberado (research.md D3-bis). El tope de
 * créditos sí es configuración por trámite porque es regla de negocio y varía por
 * facultad —esa variabilidad es la tesis del motor—, pero cuántas peticiones por hora
 * tolera un endpoint es propiedad del canal HTTP: no cambia entre adición de créditos
 * y novedad de notas, y declararlo por trámite obligaría a replicarlo en cada
 * definición nueva sin que ninguna lo necesite distinto.
 *
 * Fail-fast al arranque, como CorsProperties: un valor absurdo acá no se descubre
 * cuando un estudiante queda bloqueado, sino cuando la aplicación no levanta.
 */
@ConfigurationProperties(prefix = "app.public-capture")
public record PublicCaptureProperties(
        int maxSubmissions, Duration window, DataSize maxBodySize) {

    public PublicCaptureProperties {
        if (maxSubmissions < 1) {
            throw new IllegalArgumentException(
                    "app.public-capture.max-submissions debe ser al menos 1; un límite de cero "
                            + "cerraría el canal para todo el mundo");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException(
                    "app.public-capture.window debe ser una duración positiva: sin ventana, el "
                            + "bloqueo nunca expiraría y FR-019 exige que expire solo");
        }
        if (maxBodySize == null || maxBodySize.toBytes() < 1) {
            throw new IllegalArgumentException(
                    "app.public-capture.max-body-size debe ser positivo");
        }
    }
}
