package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.service.DocumentSealMark;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowState;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-004 y FR-005: EL DOCUMENTO SE PUEDE RECONSTRUIR BYTE A BYTE.
 *
 * Este archivo nació como sonda de medición —no afirmaba nada, imprimía lo que medía— para
 * responder si un hash calculado sobre un PDF regenerado verifica algo. La respuesta y sus
 * números quedaron registrados en `research.md` D1: de 52 348 bytes, los primeros 52 021 ya
 * eran idénticos, y toda la diferencia caía dentro del `/ID` aleatorio que PDFBox sortea en
 * cada `save()`. Cumplida esa función, la sonda se convierte en la guarda de lo que midió.
 *
 * ⚠️ ESTE TEST COMPARA DOS RECONSTRUCCIONES ENTRE SÍ, NUNCA CONTRA UNA HUELLA DORADA.
 * La tentación es congelar un SHA-256 literal del documento «tal como sale hoy» y compararlo.
 * Sería una trampa con fecha de vencimiento: al imprimirse el código de verificación en el pie
 * (FR-003), ese hash cambia, y la reacción natural —actualizar la constante— desactiva en
 * silencio la única barrera que D5 pone contra olvidar bumpear la versión del formato. Un test
 * que se «arregla» reescribiendo su valor esperado dejó de medir algo.
 *
 * 🔑 LO QUE SE GARANTIZA ES REPRODUCIBILIDAD A CÓDIGO FIJO, no que dos emisiones den lo mismo.
 * Dos emisiones de la misma solicitud imprimen códigos distintos y por lo tanto difieren a
 * propósito (D2): son dos papeles distinguibles, cada uno verificable contra su propio sello.
 */
class PdfDeterminismProbeTest {

    private final DoFr100Renderer renderer = new DoFr100Renderer();

    /**
     * EL MISMO CÓDIGO EN LAS DOS RECONSTRUCCIONES, y eso ES el requisito (FR-004). No se
     * garantiza que dos emisiones den lo mismo —cada una imprime su propio código, así que
     * difieren a propósito—, sino que reconstruir UNA emisión concreta, con el código que su
     * sello guarda, devuelva sus bytes exactos.
     */
    private static final DocumentSealMark FIXED_MARK = new DocumentSealMark(
            "FIXEDCODE001", LocalDateTime.of(2026, 9, 18, 15, 30), "Radicada", 0L);

    private static final UUID A_REQUEST = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ANOTHER_REQUEST = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    @DisplayName("FR-004: reconstruir la misma solicitud devuelve exactamente los mismos bytes")
    void rebuildingTheSameRequestIsByteIdentical() {
        Request request = request(A_REQUEST, 0L);

        byte[] first = renderer.render(request, FIXED_MARK);
        byte[] second = renderer.render(request, FIXED_MARK);

        assertArrayEquals(
                first,
                second,
                "El documento no es reproducible: sin bytes estables, la huella del sello no "
                        + "describe nada verificable");
    }

    @Test
    @DisplayName("FR-005: dos solicitudes distintas no comparten el identificador del documento")
    void differentRequestsDoNotShareTheDocumentId() {
        byte[] one = renderer.render(request(A_REQUEST, 0L), FIXED_MARK);
        byte[] other = renderer.render(request(ANOTHER_REQUEST, 0L), FIXED_MARK);

        assertThat(permanentId(one))
                .as("Un /ID compartido haría que todos los documentos del sistema se "
                        + "identificaran igual, que es justo lo que el campo existe para evitar")
                .isNotEqualTo(permanentId(other));
    }

    @Test
    @DisplayName("FR-005: una revisión nueva de la solicitud cambia la segunda cadena del /ID")
    void aNewRevisionChangesTheChangingHalfOfTheDocumentId() {
        byte[] before = renderer.render(request(A_REQUEST, 0L), FIXED_MARK);
        byte[] after = renderer.render(request(A_REQUEST, 1L), FIXED_MARK);

        assertThat(permanentId(before))
                .as("La primera cadena identifica al documento de forma permanente: todas las "
                        + "emisiones de un mismo trámite la comparten")
                .isEqualTo(permanentId(after));
        assertThat(changingId(before))
                .as("La segunda cambia cuando los datos cambian, que es cuando el documento "
                        + "deja de ser el mismo")
                .isNotEqualTo(changingId(after));
    }

    // --- lectura del trailer ---------------------------------------------------------------

    /** La primera cadena del `/ID`: identifica al documento de forma permanente. */
    private static String permanentId(byte[] pdf) {
        return trailerId(pdf).getString(0);
    }

    /** La segunda cadena del `/ID`: cambia cuando el documento se modifica. */
    private static String changingId(byte[] pdf) {
        return trailerId(pdf).getString(1);
    }

    private static COSArray trailerId(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSArray id = (COSArray) document.getDocument().getTrailer().getDictionaryObject(COSName.ID);
            assertThat(id).as("El documento no declara /ID en el trailer").isNotNull();
            return id;
        } catch (Exception failure) {
            throw new IllegalStateException("No fue posible leer el trailer del documento", failure);
        }
    }

    // --- datos de prueba ---------------------------------------------------------------------

    private static Request request(UUID id, long version) {
        WorkflowState initial = WorkflowState.builder()
                .code("RADICADA").name("Radicada").initial(true).build();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .code("ADICION_CREDITOS").version(1)
                .name("Adición de créditos")
                .states(List.of(initial))
                .build();

        return Request.builder()
                .id(id)
                .version(version)
                .definition(definition)
                .currentState(initial)
                .studentName("Ana María Peñaranda Gutiérrez")
                .studentDocument("SIN-DATO-REAL-001")
                .studentEmail("ana.penaranda@correo.test")
                .studentPhone("000 000 0000")
                .program("Ingeniería de Sistemas")
                .campus("Cali")
                .faculty("Facultad de Ingenierías")
                .modality("Distancia")
                .semester("Noveno")
                .reason("Con las homologaciones de mi plan quedo un crédito por encima del tope.")
                .studentSignature(signatureDataUrl())
                .createdAt(LocalDateTime.of(2026, 9, 17, 10, 30))
                .build();
    }

    private static String signatureDataUrl() {
        try {
            BufferedImage stroke = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
            var graphics = stroke.createGraphics();
            graphics.drawLine(10, 40, 190, 20);
            graphics.dispose();
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(stroke, "png", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
