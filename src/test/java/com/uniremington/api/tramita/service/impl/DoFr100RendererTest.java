package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowState;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El renderer del formato DO-FR-100 (SP3, issue #10).
 *
 * QUÉ SE ASIERTA Y POR QUÉ ASÍ. El test del prototipo del que se cosecha esta feature se
 * llamaba «genera un archivo PDF válido» y comprobaba CUATRO BYTES: la firma `%PDF`.
 * Pasaba con un documento en blanco. Acá se extrae el texto con PDFTextStripper y se
 * verifica lo que el documento tiene que decir: los rótulos oficiales de la plantilla
 * v2024, los datos que el estudiante diligenció, y lo que el formato NO debe mostrar.
 *
 * Lo que un test de texto NO puede probar es que el documento se PAREZCA al formato. Eso
 * se verifica a ojo contra la plantilla, y está declarado como paso obligatorio del issue.
 *
 * Ningún dato de este test es real (constitución §III): nombre inventado, documento con
 * prefijo SIN-DATO-REAL y correo en un dominio reservado para pruebas.
 */
class DoFr100RendererTest {

    private final DoFr100Renderer renderer = new DoFr100Renderer();

    /** Los nueve rótulos de la tabla «Datos del solicitante», tal como los escribe la plantilla. */
    private static final List<String> OFFICIAL_LABELS = List.of(
            "Nombres completos del solicitante",
            "Número de identificación",
            "Correo electrónico",
            "Número de contacto",
            "Programa académico en el que se encuentra",
            "Sede",
            "Facultad",
            "Semestre cursado y aprobado",
            "Modalidad");

    /**
     * Las trece casillas de motivos del formato. Pertenecen a OTROS tipos de solicitud
     * —cancelación de semestre, bajo rendimiento— y la Coordinación confirmó que casi no
     * se diligencian. El formulario público ya decidió no mostrarlas; el PDF tampoco.
     */
    private static final List<String> REASON_CHECKBOXES = List.of(
            "Pérdida de empleo", "Bajo rendimiento", "Embarazo", "Disponibilidad horaria",
            "Docente", "Incapacidad", "Traslado", "Vocacional", "Salud Mental",
            "Aumento de carga laboral", "Inconsistencia administrativa",
            "Inconformidad con la Uniremington", "Inconformidad con el servicio");

    @Test
    @DisplayName("lleva los nueve rótulos oficiales de la plantilla v2024, literales")
    void carriesEveryOfficialLabel() throws Exception {
        String text = textOf(renderer.render(request()));

        assertThat(text)
                .as("un rótulo cambiado deja de ser el formato oficial")
                .contains(OFFICIAL_LABELS);
    }

    @Test
    @DisplayName("lleva los datos que el estudiante diligenció, con sus tildes intactas")
    void carriesTheSubmittedDataWithAccents() throws Exception {
        String text = textOf(renderer.render(request()));

        assertThat(text).contains(
                "Ana María Peñaranda Gutiérrez",
                "SIN-DATO-REAL-001",
                "ana.penaranda@correo.test",
                "Ingeniería de Sistemas",
                "Facultad de Ingenierías",
                "Noveno",
                "Distancia");
    }

    @Test
    @DisplayName("son dos páginas, y el campo de firmas va en la segunda")
    void isTwoPagesWithTheSignatureFieldOnTheSecond() throws Exception {
        byte[] pdf = renderer.render(request());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages())
                    .as("la plantilla tiene un salto de página antes del campo de firmas")
                    .isEqualTo(2);
        }

        assertThat(pageText(pdf, 2))
                .contains("Campo de firmas y aprobaciones", "Firma del estudiante", "Firma de la Facultad");
        assertThat(pageText(pdf, 1))
                .as("el campo de firmas no puede aparecer también en la primera")
                .doesNotContain("Campo de firmas y aprobaciones");
    }

    @Test
    @DisplayName("marca el tipo de solicitud correcto y SOLO ese")
    void marksOnlyTheAdditionalCreditsType() throws Exception {
        String text = pageText(renderer.render(request()), 1);

        assertThat(text).contains(
                "Excepción por asignatura reprobada por segunda vez",
                "Excepción de cancelación semestre por segunda o más veces",
                "Excepción de Matrícula por bajo rendimiento académico",
                "Matrícula créditos adicionales");

        // Ninguno de los datos de este test contiene una X suelta, a propósito: así la
        // única que puede aparecer es la marca del tipo de solicitud.
        assertThat(standaloneMarks(text))
                .as("marcar más de un tipo convierte el formato en una solicitud ambigua")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("nunca imprime las trece casillas de motivos: son de otros trámites")
    void neverPrintsTheThirteenReasonCheckboxes() throws Exception {
        String text = textOf(renderer.render(request()));

        assertThat(text).doesNotContain(REASON_CHECKBOXES.toArray(String[]::new));
    }

    @Test
    @DisplayName("sin firma —el caso del formulario interno— el documento se genera igual")
    void rendersWithoutSignature() throws Exception {
        Request withoutSignature = requestBuilder().studentSignature(null).build();

        assertThatCode(() -> renderer.render(withoutSignature)).doesNotThrowAnyException();
        assertThat(pageText(renderer.render(withoutSignature), 2))
                .as("el bloque queda vacío, como un formato todavía sin firmar")
                .contains("Firma del estudiante");
    }

    @Test
    @DisplayName("el motivo más largo que el contrato permite cabe en la página, sin cortarse")
    void theLongestAllowedReasonFitsInOnePage() throws Exception {
        // 2000 caracteres es el máximo del campo: @Size en el DTO y VARCHAR(2000) en la
        // migración. Este test fija que ese máximo CABE, que es lo que permite no tener
        // maquinaria de continuación a otra página. Si alguien sube el límite o angosta la
        // caja, cae este test o cae noTextFallsOffThePage().
        String longReason = ("Necesito adicionar la asignatura para no atrasar el plan de estudios. ")
                .repeat(29);
        Request verbose = requestBuilder().reason(longReason.substring(0, 2000)).build();

        byte[] pdf = renderer.render(verbose);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages())
                    .as("el formato son dos hojas; el motivo más largo no puede agregar una tercera")
                    .isEqualTo(2);
        }
        assertThat(textOf(pdf))
                .as("el final del motivo tiene que estar en el documento, no cortado")
                .contains("no atrasar el plan de estudios.");
    }

    @Test
    @DisplayName("ningún texto se dibuja fuera de la hoja, por largo que sea el motivo")
    void noTextFallsOffThePage() throws Exception {
        // ESTE TEST EXISTE PORQUE OTRO NO ALCANZABA. `aLongReasonDoesNotOverflow` asierta
        // que el contenido está presente, y eso sigue siendo cierto aunque el texto se
        // dibuje encima del pie de página o fuera de la hoja: la extracción de texto no
        // sabe nada de rectángulos. Se descubrió con un mutante —fijar el alto de la caja
        // en cuatro líneas— que sobrevivía a toda la clase.
        String longReason = "Necesito adicionar la asignatura para no atrasar el plan de estudios. "
                .repeat(29);
        byte[] pdf = renderer.render(requestBuilder().reason(longReason.substring(0, 2000)).build());

        // 70 es el margen inferior que el renderer reserva. Medido sobre la versión
        // correcta: nada del cuerpo baja de 80, y lo único por debajo es el pie, en 40.
        assertThat(lowestTextBaseline(pdf))
                .as("un texto que invade el margen inferior se sale de la caja y pisa el pie")
                .isGreaterThanOrEqualTo(70f);
    }

    @Test
    @DisplayName("declara su clave de documento, que es como la definición lo elige")
    void declaresItsDocumentKey() {
        assertThat(renderer.documentKey()).isEqualTo("DO_FR_100");
    }

    // --- helpers -------------------------------------------------------------------------

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String pageText(byte[] pdf, int page) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document);
        }
    }

    /**
     * La distancia al borde inferior del texto más bajo de todo el documento, en puntos.
     * PDFTextStripper entrega la Y desde ARRIBA, así que se invierte contra el alto de la
     * página para razonar en la misma dirección que el trazado.
     */
    private static float lowestTextBaseline(byte[] pdf) throws Exception {
        List<Float> distances = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    // El pie va deliberadamente bajo el margen (baseline 40, medido): es
                    // el sello del documento, no contenido del formato.
                    if (text.contains("Generado por Trámita")) {
                        return;
                    }
                    positions.forEach(position ->
                            distances.add(position.getPageHeight() - position.getY()));
                }
            };
            stripper.getText(document);
        }
        return distances.stream().min(Float::compare).orElseThrow();
    }

    /** Cuenta las X sueltas: la «x» minúscula de «Excepción» no cuenta. */
    private static int standaloneMarks(String text) {
        Matcher matcher = Pattern.compile("\\bX\\b").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static Request request() {
        return requestBuilder().build();
    }

    private static Request.RequestBuilder requestBuilder() {
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
                .createdAt(LocalDateTime.of(2026, 9, 17, 10, 30));
    }

    /** Un PNG mínimo en data URL, como el que manda el formulario público. */
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
