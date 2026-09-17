package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.service.IDocumentRenderer;
import com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException;
import com.uniremington.api.tramita.util.PdfTextEncoder;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

/**
 * El formato DO-FR-100 «Solicitud de excepción de matrícula», diligenciado con los datos
 * de una solicitud.
 *
 * REPRODUCE EL PAPEL, NO INVENTA UN DOCUMENTO. El prototipo del que se cosecha esta
 * feature generaba una constancia propia titulada «DOCUMENTO OFICIAL DE CIERRE»; eso no
 * sirve para lo que el trámite necesita, porque lo que la Coordinación tramita y anexa es
 * el formato oficial. La estructura de acá —encabezado con código y versión, tabla de
 * ciudad y fecha, los cuatro tipos de solicitud, los nueve campos del solicitante, los
 * compromisos adquiridos y el campo de firmas en página aparte— sale de leer
 * `word/document.xml` de la plantilla v2024.
 *
 * DOS DESVIACIONES DELIBERADAS RESPECTO DEL PAPEL, ambas decididas y registradas:
 *
 * 1. NO se imprimen las trece casillas de motivos. Pertenecen a otros tipos de solicitud
 *    —cancelación de semestre, bajo rendimiento— y la Coordinación confirmó que casi no se
 *    diligencian. El formulario público ya tomó esa decisión; el PDF la acompaña.
 *
 * 2. SE CORRIGE la ortografía del original. La plantilla escribe «MATRICULA» y «Matricula»
 *    sin tilde; acá va «Matrícula», por la RAE. Es una decisión explícita del equipo y
 *    alinea el PDF con el formulario del front, que ya había normalizado la grafía. Quien
 *    compare el documento generado contra el papel va a ver la diferencia: está acá el
 *    porqué.
 *
 * El texto pasa por {@link PdfTextEncoder} antes de escribirse: un carácter fuera de la
 * fuente haría lanzar a PDFBox en pleno trazado y convertiría la generación en un 500.
 */
@Service
public class DoFr100Renderer implements IDocumentRenderer {

    private static final String LOGO = "/documents/logo-uniremington.png";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final float LEFT = 45, RIGHT = 567, TOP = 750, BOTTOM = 70;
    private static final float ROW_HEIGHT = 18, LINE_HEIGHT = 13, BODY_SIZE = 9;

    /**
     * La ciudad va PREIMPRESA en el papel, no es un dato del solicitante. Se descubrió
     * mirando un documento generado desde la base: una solicitud del formulario interno no
     * trae `campus` —ese campo lo agregó el canal público— y la celda «Ciudad» salía vacía
     * donde el formato oficial dice «Cali».
     *
     * Son dos campos distintos que se habían confundido: «Ciudad» pertenece al formulario
     * y «Sede» al solicitante, aunque en el alcance del MVP ambos digan Cali.
     */
    private static final String PRINTED_CITY = "Cali";

    /** Los cuatro tipos del formato, en el orden del papel. */
    private static final List<String> REQUEST_TYPES = List.of(
            "Excepción por asignatura reprobada por segunda vez",
            "Excepción de cancelación semestre por segunda o más veces",
            "Excepción de Matrícula por bajo rendimiento académico",
            "Matrícula créditos adicionales");

    /**
     * Qué casilla marca cada trámite. Vive acá y no en la base porque es conocimiento DEL
     * FORMATO —sus cuatro opciones son parte del papel—, no configuración del motor. Un
     * trámite que declare este formato sin estar en el mapa es configuración rota, y se
     * trata como tal: falla ruidosamente en vez de emitir un formato sin marcar, que sería
     * una solicitud ambigua circulando con sello institucional.
     */
    private static final Map<String, String> TYPE_BY_DEFINITION = Map.of(
            "ADICION_CREDITOS", "Matrícula créditos adicionales");

    @Override
    public String documentKey() {
        return "DO_FR_100";
    }

    @Override
    public byte[] render(Request request) {
        String markedType = TYPE_BY_DEFINITION.get(request.getDefinition().getCode());
        if (markedType == null) {
            throw new IncompleteConfigurationException(
                    "El trámite %s declara el formato %s, que no tiene casilla asignada"
                            .formatted(request.getDefinition().getCode(), documentKey()));
        }

        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            PDImageXObject logo = loadLogo(document);
            PDImageXObject signature = loadSignature(document, request.getStudentSignature());

            drawFirstPage(document, logo, request, markedType);
            drawSignaturePage(document, logo, request, signature);

            document.save(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("No fue posible generar el documento del trámite", failure);
        }
    }

    // --- páginas ---------------------------------------------------------------------------

    private void drawFirstPage(
            PDDocument document, PDImageXObject logo, Request request, String markedType)
            throws IOException {

        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            float y = header(content, logo) - 18;

            float[] dateColumns = {LEFT, LEFT + 150, LEFT + 250, LEFT + 350, RIGHT};
            y = row(content, y, dateColumns, BOLD, "Ciudad", "Día", "Mes", "Año");
            y = row(content, y, dateColumns, REGULAR,
                    PRINTED_CITY,
                    request.getCreatedAt().format(DAY),
                    request.getCreatedAt().format(MONTH),
                    request.getCreatedAt().format(YEAR));

            y = sectionTitle(content, y, "Tipo de solicitud:");
            float[] typeColumns = {LEFT, RIGHT - 40, RIGHT};
            for (String type : REQUEST_TYPES) {
                y = row(content, y, typeColumns, REGULAR, type, type.equals(markedType) ? "X" : "");
            }

            y = sectionTitle(content, y, "Datos del solicitante");
            float[] dataColumns = {LEFT, LEFT + 230, RIGHT};
            y = row(content, y, dataColumns, REGULAR, "Nombres completos del solicitante", request.getStudentName());
            y = row(content, y, dataColumns, REGULAR, "Número de identificación", request.getStudentDocument());
            y = row(content, y, dataColumns, REGULAR, "Correo electrónico", request.getStudentEmail());
            y = row(content, y, dataColumns, REGULAR, "Número de contacto", request.getStudentPhone());
            y = row(content, y, dataColumns, REGULAR, "Programa académico en el que se encuentra", request.getProgram());
            y = row(content, y, dataColumns, REGULAR, "Sede", request.getCampus());
            y = row(content, y, dataColumns, REGULAR, "Facultad", request.getFaculty());
            y = row(content, y, dataColumns, REGULAR, "Semestre cursado y aprobado", request.getSemester());
            y = row(content, y, dataColumns, REGULAR, "Modalidad", request.getModality());

            y = sectionTitle(content, y, "Compromisos adquiridos:");

            // LA CAJA CRECE CON EL TEXTO, y no al revés. El prototipo del que se cosecha
            // esta feature fijaba el alto en un número de líneas, así que un motivo largo
            // se salía de la caja y pisaba lo que viniera debajo.
            //
            // No hay página de continuación, y NO es un olvido: `reason` está acotado a
            // 2000 caracteres (@Size en el DTO y VARCHAR(2000) en la migración), y se midió
            // que ese máximo ocupa ~18 líneas contra las ~19 que caben hasta el margen.
            // El máximo del campo entra en la página. Construir el flujo a otra hoja sería
            // maquinaria para un caso que el contrato de entrada no permite (§I).
            // Lo que vigila que siga siendo cierto es noTextFallsOffThePage().
            textBox(content, y, wrap(request.getReason(), RIGHT - LEFT - 16, BODY_SIZE));

            footer(content);
        }
    }

    private void drawSignaturePage(
            PDDocument document, PDImageXObject logo, Request request, PDImageXObject signature)
            throws IOException {

        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            float y = header(content, logo) - 24;

            content.setNonStrokingColor(new Color(217, 217, 217));
            content.addRect(LEFT, y - 20, RIGHT - LEFT, 20);
            content.fill();
            content.setNonStrokingColor(Color.BLACK);
            box(content, LEFT, y - 20, RIGHT - LEFT, 20);
            centered(content, BOLD, 10, "Campo de firmas y aprobaciones", LEFT, RIGHT, y - 14);
            y -= 20;

            float middle = (LEFT + RIGHT) / 2f;
            float height = 120;
            box(content, LEFT, y - height, middle - LEFT, height);
            box(content, middle, y - height, RIGHT - middle, height);

            if (signature != null) {
                float width = 150;
                float drawn = width * signature.getHeight() / (float) signature.getWidth();
                content.drawImage(signature, LEFT + 20, y - height + 50, width, drawn);
            }
            line(content, LEFT + 12, y - height + 45, middle - 12, y - height + 45);
            line(content, middle + 12, y - height + 45, RIGHT - 12, y - height + 45);

            write(content, BOLD, BODY_SIZE, "Firma del estudiante", LEFT + 12, y - height + 28);
            write(content, REGULAR, BODY_SIZE,
                    "Fecha: " + request.getCreatedAt().format(FULL_DATE), LEFT + 12, y - height + 14);
            write(content, BOLD, BODY_SIZE, "Firma de la Facultad", middle + 12, y - height + 28);
            write(content, REGULAR, BODY_SIZE, "Fecha: ______________", middle + 12, y - height + 14);

            footer(content);
        }
    }

    // --- piezas de trazado -----------------------------------------------------------------

    private float header(PDPageContentStream content, PDImageXObject logo) throws IOException {
        float height = 52, y = TOP;
        float titleStart = LEFT + 150, titleEnd = RIGHT - 120;

        box(content, LEFT, y - height, RIGHT - LEFT, height);
        line(content, titleStart, y - height, titleStart, y);
        line(content, titleEnd, y - height, titleEnd, y);

        float width = 120;
        float drawn = width * logo.getHeight() / (float) logo.getWidth();
        content.drawImage(logo, LEFT + 14, y - height / 2 - drawn / 2, width, drawn);

        centered(content, BOLD, 11, "SOLICITUD DE EXCEPCIÓN DE MATRÍCULA",
                titleStart, titleEnd, y - height / 2 - 4);
        write(content, REGULAR, 8, "DO-FR-100", titleEnd + 8, y - 16);
        write(content, REGULAR, 8, "Versión. 01", titleEnd + 8, y - 30);
        write(content, REGULAR, 8, "Fecha. 19/11/2024", titleEnd + 8, y - 44);
        return y - height;
    }

    private void footer(PDPageContentStream content) throws IOException {
        write(content, REGULAR, 7,
                "Generado por Trámita — Coordinación Académica, Sede Cali", LEFT, 40);
    }

    private float sectionTitle(PDPageContentStream content, float y, String title) throws IOException {
        y -= 20;
        write(content, BOLD, 10, title, LEFT, y);
        return y - 6;
    }

    private float row(PDPageContentStream content, float y, float[] columns, PDFont font, String... cells)
            throws IOException {
        box(content, columns[0], y - ROW_HEIGHT, columns[columns.length - 1] - columns[0], ROW_HEIGHT);
        for (int i = 1; i < columns.length - 1; i++) {
            line(content, columns[i], y - ROW_HEIGHT, columns[i], y);
        }
        for (int i = 0; i < cells.length && i < columns.length - 1; i++) {
            if (cells[i] == null || cells[i].isEmpty()) {
                continue;
            }
            write(content, font, BODY_SIZE, cells[i], columns[i] + 6, y - ROW_HEIGHT + 6);
        }
        return y - ROW_HEIGHT;
    }

    private void textBox(PDPageContentStream content, float y, List<String> lines) throws IOException {
        float height = Math.max(1, lines.size()) * LINE_HEIGHT + 10;
        box(content, LEFT, y - height, RIGHT - LEFT, height);
        float lineY = y - 16;
        for (String line : lines) {
            write(content, REGULAR, BODY_SIZE, line, LEFT + 8, lineY);
            lineY -= LINE_HEIGHT;
        }
    }

    /** Parte el texto en líneas que quepan en el ancho dado. */
    private List<String> wrap(String text, float maxWidth, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : PdfTextEncoder.sanitize(text, REGULAR).split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (REGULAR.getStringWidth(candidate) / 1000 * size > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void write(PDPageContentStream content, PDFont font, float size, String text, float x, float y)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(PdfTextEncoder.sanitize(text, font));
        content.endText();
    }

    private void centered(PDPageContentStream content, PDFont font, float size, String text,
            float from, float to, float y) throws IOException {
        String safe = PdfTextEncoder.sanitize(text, font);
        float width = font.getStringWidth(safe) / 1000 * size;
        write(content, font, size, safe, (from + to) / 2 - width / 2, y);
    }

    private void box(PDPageContentStream content, float x, float y, float width, float height)
            throws IOException {
        content.setLineWidth(0.7f);
        content.addRect(x, y, width, height);
        content.stroke();
    }

    private void line(PDPageContentStream content, float x1, float y1, float x2, float y2)
            throws IOException {
        content.setLineWidth(0.7f);
        content.moveTo(x1, y1);
        content.lineTo(x2, y2);
        content.stroke();
    }

    // --- imágenes --------------------------------------------------------------------------

    private PDImageXObject loadLogo(PDDocument document) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(LOGO)) {
            if (stream == null) {
                throw new IllegalStateException("Falta el recurso del logo institucional: " + LOGO);
            }
            return PDImageXObject.createFromByteArray(document, stream.readAllBytes(), "logo");
        }
    }

    /**
     * La firma llega como data URL desde el formulario público. Es nullable a propósito:
     * el formulario interno de la Coordinación no la captura, y ese formato circula sin
     * firmar hasta que alguien lo firme, igual que el papel.
     */
    private PDImageXObject loadSignature(PDDocument document, String dataUrl) throws IOException {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return null;
        }
        byte[] png = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        return PDImageXObject.createFromByteArray(document, png, "firma");
    }
}
