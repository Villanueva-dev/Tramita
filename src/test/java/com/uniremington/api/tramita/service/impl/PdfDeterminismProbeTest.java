package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowState;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SONDA DE MEDICIÓN — no es un test de comportamiento, es un instrumento.
 *
 * Existe para responder UNA pregunta que decide el diseño de SP4 (issue #11): ¿dos
 * renders del mismo trámite producen el mismo archivo? Si la respuesta es no, un hash
 * calculado sobre un PDF regenerado no verifica nada, y el sello obliga a persistir el
 * documento en vez de reconstruirlo.
 *
 * No afirma nada: imprime lo que mide. Se borra o se convierte en guarda según el
 * resultado.
 */
class PdfDeterminismProbeTest {

    private final DoFr100Renderer renderer = new DoFr100Renderer();

    @Test
    @DisplayName("SONDA: ¿el mismo trámite renderizado dos veces da los mismos bytes?")
    void probeDeterminism() throws Exception {
        Request request = request();

        byte[] first = renderer.render(request);
        byte[] second = renderer.render(request);

        System.out.println("=== SONDA DE DETERMINISMO DEL PDF ===");
        System.out.println("tamaño #1        : " + first.length + " bytes");
        System.out.println("tamaño #2        : " + second.length + " bytes");
        System.out.println("sha256 #1        : " + sha256(first));
        System.out.println("sha256 #2        : " + sha256(second));
        System.out.println("¿bytes iguales?  : " + Arrays.equals(first, second));

        System.out.println("--- metadatos del render #1 ---");
        describe(first);
        System.out.println("--- metadatos del render #2 ---");
        describe(second);

        if (!Arrays.equals(first, second)) {
            System.out.println("--- dónde difieren ---");
            int diffs = 0;
            for (int i = 0; i < Math.min(first.length, second.length) && diffs < 6; i++) {
                if (first[i] != second[i]) {
                    int from = Math.max(0, i - 40);
                    int to = Math.min(first.length, i + 40);
                    System.out.println("offset " + i + ":");
                    System.out.println("   #1: " + printable(first, from, to));
                    System.out.println("   #2: " + printable(second, from, to));
                    diffs++;
                    i = to;
                }
            }
        }
        System.out.println("=== FIN DE LA SONDA ===");
    }

    @Test
    @DisplayName("SONDA 2: ¿fijar el /ID del trailer alcanza para que dos guardados coincidan?")
    void probeFixedTrailerId() throws Exception {
        System.out.println("=== SONDA 2: /ID FIJADO ===");
        String primero = sha256(savedWithFixedId());
        String segundo = sha256(savedWithFixedId());
        System.out.println("sha256 #1        : " + primero);
        System.out.println("sha256 #2        : " + segundo);
        System.out.println("¿iguales?        : " + primero.equals(segundo));

        System.out.println("--- control: el mismo documento SIN fijar el /ID ---");
        System.out.println("sha256 #1        : " + sha256(savedWithDefaultId()));
        System.out.println("sha256 #2        : " + sha256(savedWithDefaultId()));
        System.out.println("=== FIN DE LA SONDA 2 ===");
    }

    /** El documento más simple posible, guardado con un /ID constante. */
    private static byte[] savedWithFixedId() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            org.apache.pdfbox.cos.COSString fixed =
                    new org.apache.pdfbox.cos.COSString("tramita-sello-determinista".getBytes());
            document.getDocument().setDocumentID(new COSArray(List.of(fixed, fixed)));
            document.save(output);
            return output.toByteArray();
        }
    }

    /** El mismo documento sin tocar el /ID, para aislar la variable. */
    private static byte[] savedWithDefaultId() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void describe(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            var info = document.getDocumentInformation();
            System.out.println("  CreationDate : " + format(info.getCreationDate()));
            System.out.println("  ModDate      : " + format(info.getModificationDate()));
            System.out.println("  Producer     : " + info.getProducer());
            COSArray id = (COSArray) document.getDocument().getTrailer().getDictionaryObject(COSName.ID);
            System.out.println("  /ID          : " + (id == null ? "(ausente)" : id.toString()));
        }
    }

    private static String format(java.util.Calendar calendar) {
        return calendar == null ? "(ausente)" : calendar.toInstant().toString();
    }

    private static String printable(byte[] data, int from, int to) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < to; i++) {
            char c = (char) (data[i] & 0xFF);
            out.append(c >= 32 && c < 127 ? c : '.');
        }
        return out.toString();
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static Request request() {
        WorkflowState initial = WorkflowState.builder()
                .code("RADICADA").name("Radicada").initial(true).build();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .code("ADICION_CREDITOS").version(1)
                .name("Adición de créditos")
                .states(List.of(initial))
                .build();

        return Request.builder()
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
