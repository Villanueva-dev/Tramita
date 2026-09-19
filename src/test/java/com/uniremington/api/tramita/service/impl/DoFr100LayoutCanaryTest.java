package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowState;
import com.uniremington.api.tramita.service.DocumentSealMark;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🚨 SI ESTE TEST SE PUSO EN ROJO, LEÉ ESTO ANTES DE TOCAR NADA.
 *
 * Significa que cambió algo que altera los bytes del documento: una posición, una tipografía,
 * un texto fijo, el orden de las secciones o el logo institucional.
 *
 * <ul>
 *   <li><b>Si el cambio fue a propósito</b>: subí {@code DoFr100Renderer.FORMAT_VERSION} —de
 *       {@code v1} a {@code v2}, por ejemplo— y recién entonces actualizá la huella de acá
 *       abajo. Las dos cosas, en ese orden. Los sellos emitidos con la versión anterior van a
 *       pasar a responder «no verificable», que es correcto: ese papel ya no se puede
 *       reconstruir, y decir «no puedo pronunciarme» es la respuesta honesta.</li>
 *   <li><b>Si no cambiaste la maquetación a propósito</b>: entonces algo se movió sin que lo
 *       notaras, y eso es el hallazgo. Averiguá qué antes de tocar la huella.</li>
 * </ul>
 *
 * <p>⛔ <b>ACTUALIZAR LA HUELLA SIN SUBIR LA VERSIÓN ES EL ÚNICO ERROR GRAVE POSIBLE ACÁ.</b>
 * Deja el test en verde y el sistema creyendo que el formato no cambió: los documentos viejos
 * se reconstruirían con el papel nuevo, las huellas no coincidirían, y la respuesta sería que
 * documentos LEGÍTIMOS fueron alterados. Es exactamente la acusación falsa que FR-007 prohíbe
 * y que esta feature entera existe para impedir.
 *
 * <p>POR QUÉ ESTE TEST EXISTE APARTE DEL DE DETERMINISMO: aquel compara dos reconstrucciones
 * entre sí, y por eso sobrevive a cualquier cambio de maquetación —dos documentos nuevos
 * siguen siendo iguales entre sí—. Se midió: mover un margen lo dejaba en verde. Hacía falta
 * algo que mirara hacia AFUERA del momento actual, y eso es una huella congelada. Acá sí puede
 * serlo porque el código de verificación es un parámetro fijo del test y no cambia entre
 * corridas, que era lo que volvía inservible la huella dorada en el otro archivo.
 */
class DoFr100LayoutCanaryTest {

    /**
     * Huella de un documento con datos, código y fecha FIJOS. Cambia con cualquier cambio de
     * trazado, y con ninguna otra cosa.
     */
    private static final String KNOWN_DIGEST =
            "0a398d6489861b968b08e8d29a7420b123e8bb1bef0dc6142b9e23467dd60055";

    private static final DocumentSealMark FIXED_MARK = new DocumentSealMark(
            "CANARY000001", LocalDateTime.of(2026, 1, 15, 12, 0), "Radicada", 0L);

    @Test
    @DisplayName("🚨 canario: la maquetación no cambió — si falla, subí FORMAT_VERSION (leé el javadoc)")
    void layoutHasNotChanged() throws Exception {
        byte[] document = new DoFr100Renderer().render(fixedRequest(), FIXED_MARK);

        assertThat(sha256(document))
                .as("La maquetación del documento cambió. Si fue a propósito, subí "
                        + "FORMAT_VERSION en DoFr100Renderer ANTES de actualizar esta huella; "
                        + "actualizarla sola hace que documentos legítimos se reporten como "
                        + "alterados. Leé el javadoc de esta clase.")
                .isEqualTo(KNOWN_DIGEST);
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    /** Sin firma escaneada: una imagen la haría depender de cómo la codifique cada JDK. */
    private static Request fixedRequest() {
        WorkflowState initial = WorkflowState.builder()
                .code("RADICADA").name("Radicada").initial(true).build();
        return Request.builder()
                .id(UUID.fromString("00000000-0000-4000-8000-000000000001"))
                .version(0L)
                .definition(WorkflowDefinition.builder()
                        .code("ADICION_CREDITOS").version(1).name("Adición de créditos")
                        .states(List.of(initial)).build())
                .currentState(initial)
                .studentName("Solicitante De Prueba")
                .studentDocument("SIN-DATO-REAL-001")
                .studentEmail("prueba@correo.test")
                .studentPhone("000 000 0000")
                .program("Ingeniería de Sistemas")
                .campus("Cali")
                .faculty("Facultad de Ingenierías")
                .modality("Distancia")
                .semester("Noveno")
                .reason("Motivo fijo para que la huella dependa solo de la maquetación.")
                .createdAt(LocalDateTime.of(2026, 1, 15, 10, 0))
                .build();
    }
}
