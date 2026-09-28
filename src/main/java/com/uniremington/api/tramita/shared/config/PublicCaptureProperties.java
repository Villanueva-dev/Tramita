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

    /**
     * Techo del tope configurable. Holgado frente al valor vigente (256 KB).
     *
     * No es una preferencia: el filtro lee con {@code readNBytes(Math.toIntExact(maxBytes + 1))}
     * y un tope que no quepa en un int haría fallar esa conversión en CADA petición, dejando el
     * canal caído por configuración. Este techo lo vuelve imposible desde el arranque —falla al
     * levantar, no en caliente— y, de paso, acota cuánto puede pedirse leer a heap por petición
     * en un canal sin sesión. Las dos guardas son deliberadas: esta decide el producto, la del
     * filtro impide que el error sea silencioso si alguien mueve esta.
     */
    private static final DataSize MAX_ALLOWED_BODY_SIZE = DataSize.ofMegabytes(1);

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
        if (maxBodySize.toBytes() > MAX_ALLOWED_BODY_SIZE.toBytes()) {
            throw new IllegalArgumentException(
                    "app.public-capture.max-body-size no puede exceder "
                            + MAX_ALLOWED_BODY_SIZE.toMegabytes() + "MB; se configuró "
                            + maxBodySize.toBytes() + " bytes. Un tope mayor haría que el filtro "
                            + "pida leer a heap más de lo que un formato admite, y por encima de "
                            + "2 GiB desbordaría la conversión a int de la propia lectura");
        }
    }
}
