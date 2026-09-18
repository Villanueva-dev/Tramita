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
 * FR-004: EMITIR UN DOCUMENTO NO PUEDE CAMBIAR CÓMO SE RECONSTRUYEN LOS ANTERIORES.
 *
 * 🚨 SI ESTE TEST SE PUSO EN ROJO, LA CAUSA CASI SEGURA ES QUE ALGUIEN VOLVIÓ A COMPARTIR UNA
 * INSTANCIA DE {@code PDFont} ENTRE DOCUMENTOS — una constante estática, un campo del renderer,
 * un caché. ⛔ NO actualices ninguna huella ni subas {@code FORMAT_VERSION}: eso deja el defecto
 * adentro y todo en verde. Devolvé las fuentes a una instancia nueva por documento.
 *
 * <p>ESTE TEST EXISTE PORQUE EL DEFECTO YA OCURRIÓ, y porque ninguna otra prueba lo nombraba.
 * Una instancia de {@code PDFont} acumula estado al escribirse en un documento, así que
 * compartirla hace que el resultado dependa de qué se dibujó antes en el mismo proceso. Medido
 * sobre el código que tenía el defecto: tras emitir un documento con el motivo en su largo
 * máximo —2000 caracteres, que {@code @Size} permite—, el MISMO documento pasaba a dar otros
 * bytes. Como el renderer es un {@code @Service} singleton que vive todo el uptime del
 * servidor, eso significaba reconstruir un documento viejo y obtener un archivo distinto: el
 * sistema respondería que un documento LEGÍTIMO fue alterado.
 *
 * <p>⚠️ NO ALCANZA CON {@code DoFr100LayoutCanaryTest}, aunque también se ponga rojo ante esta
 * regresión. Aquel la reporta como un cambio de maquetación y su mensaje pide subir
 * {@code FORMAT_VERSION} y actualizar la huella, que ante este defecto es justo lo que NO hay
 * que hacer. Detecta, pero manda a la acción equivocada. Por eso hace falta este test aparte.
 *
 * <p>Y tampoco alcanza {@code PdfDeterminismProbeTest}: compara dos reconstrucciones
 * consecutivas, y entre esas dos no pasa nada que contamine. Daba verde con el defecto presente.
 *
 * <p>⛔ ESTA CLASE TIENE UN SOLO CASO A PROPÓSITO, Y NO HAY QUE AGREGARLE OTRO QUE RENDERICE
 * DOCUMENTOS GRANDES. Se probó: un segundo caso que emitía uno de 2000 caracteres corría
 * antes, dejaba la fuente ya contaminada, y entonces el caso de arriba comparaba dos
 * reconstrucciones igualmente contaminadas y **pasaba en verde con el defecto presente**.
 * El suite se enmascaraba a sí mismo. Un caso nuevo va en otra clase.
 */
class DoFr100FontIsolationTest {

    private static final DocumentSealMark MARK = new DocumentSealMark(
            "ISOLATION001", LocalDateTime.of(2026, 1, 15, 12, 0), "Radicada", 0L);

    /** El largo máximo que {@code @Size(max = 2000)} admite: no es un extremo inventado. */
    private static final String LONGEST_ALLOWED_REASON = "A".repeat(2000);

    @Test
    @DisplayName("🚨 emitir un documento enorme no altera la reconstrucción de los anteriores")
    void emittingAHugeDocumentDoesNotChangeHowEarlierOnesRebuild() throws Exception {
        DoFr100Renderer renderer = new DoFr100Renderer();
        Request ordinary = request("Un motivo corto, del largo que se escribe siempre.");

        String before = sha256(renderer.render(ordinary, MARK));
        renderer.render(request(LONGEST_ALLOWED_REASON), MARK);
        String after = sha256(renderer.render(ordinary, MARK));

        assertThat(after)
                .as("Reconstruir este documento dio otros bytes solo porque en el medio se "
                        + "emitió uno largo. En producción eso es acusar de alterado a un "
                        + "documento legítimo. Revisá si alguna PDFont volvió a compartirse.")
                .isEqualTo(before);
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static Request request(String reason) {
        WorkflowState initial = WorkflowState.builder()
                .code("RADICADA").name("Radicada").initial(true).build();
        return Request.builder()
                .id(UUID.fromString("00000000-0000-4000-8000-000000000002")).version(0L)
                .definition(WorkflowDefinition.builder()
                        .code("ADICION_CREDITOS").version(1).name("Adición de créditos")
                        .states(List.of(initial)).build())
                .currentState(initial)
                .studentName("Solicitante De Prueba").studentDocument("SIN-DATO-REAL-001")
                .studentEmail("prueba@correo.test").studentPhone("000 000 0000")
                .program("Ingeniería de Sistemas").campus("Cali")
                .faculty("Facultad de Ingenierías").modality("Distancia").semester("Noveno")
                .reason(reason).createdAt(LocalDateTime.of(2026, 1, 15, 10, 0))
                .build();
    }
}
