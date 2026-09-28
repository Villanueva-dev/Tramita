package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.service.DocumentSealMark;
import com.uniremington.api.tramita.service.IDocumentRenderer;
import com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException;
import com.uniremington.api.tramita.util.CampusTime;
import com.uniremington.api.tramita.util.PdfTextEncoder;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Version;
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
@Slf4j
public class DoFr100Renderer implements IDocumentRenderer {

    private static final String LOGO = "/documents/logo-uniremington.png";

    /**
     * LA VERSIÓN DE ESTA MAQUETACIÓN. Se sube A MANO al cambiar cualquier cosa que altere los
     * bytes del documento: posiciones, tipografías, textos fijos, el orden de las secciones.
     *
     * ⚠️ SI CAMBIÁS LA MAQUETACIÓN Y NO SUBÍS ESTE NÚMERO, el sistema va a creer que el formato
     * sigue vigente, va a reconstruir documentos viejos con el papel nuevo, las huellas no van
     * a coincidir y va a responder que documentos LEGÍTIMOS fueron alterados.
     *
     * Lo que avisa es {@code DoFr100LayoutCanaryTest}: guarda la huella de un documento de
     * prueba y se pone en rojo ante cualquier cambio de trazado. Si se rompió a propósito,
     * subir este número es la respuesta correcta.
     *
     * <p>v1 → v2: aislar las fuentes por documento cambió los bytes del archivo aunque el
     * documento se vea igual. No hay sellos emitidos con v1 —la tabla nace en esta misma
     * feature—, así que no se invalidó nada; se subió igual porque la primera vez que el
     * canario avisa no puede responderse «esta vez no hace falta».
     */
    private static final String FORMAT_VERSION = "DO_FR_100/v2";

    /** Cuántos caracteres de la huella del logo entran en la versión (cabe en VARCHAR(80)). */
    private static final int LOGO_DIGEST_LENGTH = 16;

    /**
     * La zona de la sede vive en {@link CampusTime}, ÚNICA fuente (revisión #34 M1): antes
     * era una constante privada de este renderer, y los DTO que exponen `issuedAt` por la
     * API devolvían el UTC crudo sin convertir — el JSON dejaba de ser «contrastable contra
     * el pie impreso» que promete el contrato. `createdAt` se persiste en UTC —convención
     * del chasis— y este documento lleva la fecha EN LA CELDA DE UN PAPEL QUE SE FIRMA:
     * imprimir el instante UTC sin convertir adelanta un día entre las 19:00 y las 23:59 de
     * Cali, porque Colombia es UTC−5. No se cambia el almacenamiento, se convierte al
     * formatear.
     */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * ⛔ LAS FUENTES NO PUEDEN SER CONSTANTES COMPARTIDAS, Y ESTO NO ES UNA PREFERENCIA DE
     * ESTILO: era el bug. Una instancia de {@link PDFont} acumula estado al escribirse en un
     * documento, así que compartirla hace que el resultado dependa de qué documentos se
     * dibujaron antes en el mismo proceso. Medido: tras emitir un documento con el motivo en
     * su largo máximo (2000 caracteres, permitido por @Size), el MISMO documento pasaba a dar
     * otros bytes. Como el renderer es un @Service singleton que vive todo el uptime, eso
     * significaba reconstruir un documento viejo y obtener un archivo distinto, es decir
     * responder que un documento LEGÍTIMO fue alterado.
     *
     * Aislarlas también elimina una condición de carrera: dos peticiones concurrentes
     * compartían la misma instancia mutable.
     *
     * {@code DoFr100FontIsolationTest} lo vigila. Si alguien «optimiza» esto de vuelta a una
     * constante, ese test se pone en rojo.
     */
    private static PDFont regular() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private static PDFont bold() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }

    private static final float LEFT = 45, RIGHT = 567, TOP = 750, BOTTOM = 70;

    /** Longitud en bytes de cada mitad del `/ID`, la que usa PDFBox por omisión. */
    private static final int ID_LENGTH = 16;
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

    /**
     * Huella del logo institucional, calculada una sola vez al construir el renderer.
     *
     * El logo es la parte del formato que puede cambiar SIN que nadie toque el código: alcanza
     * con reemplazar el archivo en un despliegue. Por eso esta mitad de la versión se detecta
     * sola, mientras que la maquetación necesita que alguien suba {@link #FORMAT_VERSION}.
     */
    private final String logoDigest = digestOfLogo();

    private static String digestOfLogo() {
        try (InputStream stream = DoFr100Renderer.class.getResourceAsStream(LOGO)) {
            if (stream == null) {
                throw new IllegalStateException("Falta el recurso del logo institucional: " + LOGO);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes());
            return HexFormat.of().formatHex(digest).substring(0, LOGO_DIGEST_LENGTH);
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("No fue posible identificar el logo institucional", failure);
        }
    }

    /**
     * INCLUYE LA VERSIÓN DE PDFBOX, y esa parte no se escribe a mano: se lee en tiempo de
     * ejecución. El pom fija la versión de la biblioteca JUSTAMENTE para poder actualizarla
     * ante vulnerabilidades, así que va a cambiar. Si un cambio de PDFBox altera un solo byte
     * de la salida, los sellos anteriores deben pasar a «no verificable» —ese papel ya no se
     * puede reconstruir— y no a «alterado», que sería acusar de falsificación a documentos
     * legítimos por haber aplicado un parche de seguridad.
     */
    @Override
    public String formatVersion() {
        return FORMAT_VERSION + "+logo." + logoDigest + "+pdfbox." + Version.getVersion();
    }

    @Override
    public String documentKey() {
        return "DO_FR_100";
    }

    @Override
    public byte[] render(Request request, DocumentSealMark mark) {
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

            List<String> pending = drawFirstPage(document, logo, request, markedType, mark);
            drawOverflowPages(document, logo, pending, mark);
            drawSignaturePage(document, logo, request, signature, mark);

            fixDocumentId(document, request);
            document.save(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("No fue posible generar el documento del trámite", failure);
        }
    }

    /**
     * FIJA EL `/ID` DEL TRAILER PARA QUE EL DOCUMENTO SE PUEDA RECONSTRUIR BYTE A BYTE.
     *
     * Sin esto el documento NO es reproducible, y se midió dónde: de 52 348 bytes, los
     * primeros 52 021 ya son idénticos entre dos renders: el contenido es determinista. Lo
     * único que varía son los 32 bytes que PDFBox sortea en cada {@code save()} para el
     * identificador del trailer. Una huella calculada sobre un documento reconstruido no
     * verificaría nada mientras esos bytes cambien (`research.md` D1).
     *
     * SE RESPETA LA SEMÁNTICA QUE EL FORMATO PDF LE DA AL CAMPO, que son dos cadenas con
     * papeles distintos: la primera identifica al documento de forma permanente, la segunda
     * cambia cuando el documento se modifica. De ahí que la primera derive solo del
     * identificador de la solicitud y la segunda incorpore además su revisión.
     *
     * ⛔ NO PUEDE SER UNA CONSTANTE LITERAL, aunque daría bytes igual de estables y costaría
     * menos: haría que TODOS los documentos del sistema se identificaran igual, que es
     * exactamente lo que este campo existe para evitar.
     */
    private static void fixDocumentId(PDDocument document, Request request) {
        UUID requestId = Objects.requireNonNull(
                request.getId(),
                "La solicitud no tiene identificador, y sin él el documento no puede declarar "
                        + "un /ID propio: todos los documentos emitidos compartirían el mismo");

        COSString permanent = new COSString(derive(requestId.toString()));
        COSString revision = new COSString(derive(requestId + ":" + request.getVersion()));
        document.getDocument().setDocumentID(new COSArray(List.of(permanent, revision)));
    }

    /** Los primeros {@value #ID_LENGTH} bytes del SHA-256 de la semilla. */
    private static byte[] derive(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest, ID_LENGTH);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 es parte de la plataforma", impossible);
        }
    }

    // --- páginas ---------------------------------------------------------------------------

    /** Dibuja la primera página y devuelve las líneas del motivo que no cupieron. */
    private List<String> drawFirstPage(
            PDDocument document, PDImageXObject logo, Request request, String markedType,
            DocumentSealMark mark)
            throws IOException {

        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            float y = header(content, logo) - 18;

            float[] dateColumns = {LEFT, LEFT + 150, LEFT + 250, LEFT + 350, RIGHT};
            y = row(content, y, dateColumns, bold(), "Ciudad", "Día", "Mes", "Año");
            LocalDateTime filedAt = atCampus(request.getCreatedAt());
            y = row(content, y, dateColumns, regular(),
                    PRINTED_CITY,
                    filedAt.format(DAY),
                    filedAt.format(MONTH),
                    filedAt.format(YEAR));

            y = sectionTitle(content, y, "Tipo de solicitud:");
            float[] typeColumns = {LEFT, RIGHT - 40, RIGHT};
            for (String type : REQUEST_TYPES) {
                y = row(content, y, typeColumns, regular(), type, type.equals(markedType) ? "X" : "");
            }

            y = sectionTitle(content, y, "Datos del solicitante");
            float[] dataColumns = {LEFT, LEFT + 230, RIGHT};
            y = row(content, y, dataColumns, regular(), "Nombres completos del solicitante", request.getStudentName());
            y = row(content, y, dataColumns, regular(), "Número de identificación", request.getStudentDocument());
            y = row(content, y, dataColumns, regular(), "Correo electrónico", request.getStudentEmail());
            y = row(content, y, dataColumns, regular(), "Número de contacto", request.getStudentPhone());
            y = row(content, y, dataColumns, regular(), "Programa académico en el que se encuentra", request.getProgram());
            y = row(content, y, dataColumns, regular(), "Sede", request.getCampus());
            y = row(content, y, dataColumns, regular(), "Facultad", request.getFaculty());
            y = row(content, y, dataColumns, regular(), "Semestre cursado y aprobado", request.getSemester());
            y = row(content, y, dataColumns, regular(), "Modalidad", request.getModality());

            y = sectionTitle(content, y, "Compromisos adquiridos:");

            // LA CAJA CRECE CON EL TEXTO, y lo que no cabe pasa a una hoja de continuación.
            //
            // Hubo una versión sin continuación, apoyada en una medición que decía que el
            // máximo del campo —2000 caracteres— cabía siempre. Esa medición se hizo con UNA
            // cadena de prosa española y se generalizó. Es falsa: con palabras anchas entran
            // muchas más por línea, y 2000 caracteres de «MM » terminaban en el baseline 30,
            // por debajo del propio pie de página. Una medición de peor caso no se hace con
            // una muestra típica.
            //
            // Perder texto tampoco es opción: es un documento oficial, y recortar en
            // silencio lo que el estudiante escribió es peor que gastar una hoja.
            List<String> lines = wrap(request.getReason(), RIGHT - LEFT - 16, BODY_SIZE);
            int fit = Math.max(0, (int) ((y - BOTTOM - 10) / LINE_HEIGHT));
            List<String> here = lines.subList(0, Math.min(fit, lines.size()));
            textBox(content, y, here);

            footer(content, mark);
            return new ArrayList<>(lines.subList(here.size(), lines.size()));
        }
    }

    /**
     * Las hojas que hagan falta para el resto del motivo. Van ANTES del campo de firmas,
     * para que la firma siga cerrando el documento como en el papel.
     */
    private void drawOverflowPages(PDDocument document, PDImageXObject logo, List<String> pending,
            DocumentSealMark mark)
            throws IOException {
        while (!pending.isEmpty()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = header(content, logo) - 18;
                y = sectionTitle(content, y, "Compromisos adquiridos (continuación):");
                int fit = Math.max(1, (int) ((y - BOTTOM - 10) / LINE_HEIGHT));
                List<String> here = pending.subList(0, Math.min(fit, pending.size()));
                textBox(content, y, here);
                footer(content, mark);
                pending = new ArrayList<>(pending.subList(here.size(), pending.size()));
            }
        }
    }

    private void drawSignaturePage(
            PDDocument document, PDImageXObject logo, Request request, PDImageXObject signature,
            DocumentSealMark mark)
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
            centered(content, bold(), 10, "Campo de firmas y aprobaciones", LEFT, RIGHT, y - 14);
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

            write(content, bold(), BODY_SIZE, "Firma del estudiante", LEFT + 12, y - height + 28);
            write(content, regular(), BODY_SIZE,
                    "Fecha: " + atCampus(request.getCreatedAt()).format(FULL_DATE),
                    LEFT + 12, y - height + 14);
            write(content, bold(), BODY_SIZE, "Firma de la Facultad", middle + 12, y - height + 28);
            write(content, regular(), BODY_SIZE, "Fecha: ______________", middle + 12, y - height + 14);

            footer(content, mark);
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

        centered(content, bold(), 11, "SOLICITUD DE EXCEPCIÓN DE MATRÍCULA",
                titleStart, titleEnd, y - height / 2 - 4);
        write(content, regular(), 8, "DO-FR-100", titleEnd + 8, y - 16);
        write(content, regular(), 8, "Versión. 01", titleEnd + 8, y - 30);
        write(content, regular(), 8, "Fecha. 19/11/2024", titleEnd + 8, y - 44);
        return y - height;
    }

    /**
     * EL PIE ES LA MARCA LEGIBLE DEL SELLO (FR-003), y existe para quien recibe el papel
     * impreso y no tiene cuenta en el sistema: sin herramientas, puede leer con qué código
     * consultar el documento, cuándo se emitió, en qué estado estaba el trámite y sobre qué
     * revisión de los datos. Es la única vía de contraste para esa persona.
     *
     * ⚠️ VA DELIBERADAMENTE POR DEBAJO DEL MARGEN INFERIOR (línea base 40 y 30, contra un
     * BOTTOM de 70): es el sello del documento, no contenido del formato oficial. Los tests
     * que vigilan que el cuerpo no invada el margen descartan estas líneas por sus marcadores
     * de texto, así que cambiarlas obliga a actualizar ese filtro.
     *
     * El estado se imprime con su NOMBRE legible y no con su código, y ese nombre es el que el
     * sello congela: si se resolviera contra la configuración al verificar, renombrar un estado
     * convertiría un documento legítimo en uno «alterado».
     */
    private void footer(PDPageContentStream content, DocumentSealMark mark)
            throws IOException {
        write(content, regular(), 7,
                "Generado por Trámita — Coordinación Académica, Sede Cali", LEFT, 40);
        write(content, regular(), 7,
                "Verificación: %s · Emitido: %s · Estado: %s · Revisión: %d".formatted(
                        mark.verificationCode(),
                        CampusTime.toCampus(mark.issuedAt()).format(FULL_DATE),
                        mark.stateName(),
                        mark.requestVersion()),
                LEFT, 30);
    }

    private float sectionTitle(PDPageContentStream content, float y, String title) throws IOException {
        y -= 20;
        write(content, bold(), 10, title, LEFT, y);
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
            // LA CELDA RECORTA LO QUE NO CABE, y lo marca. Sin esto, un valor más ancho que
            // su columna se dibuja fuera de la hoja y desaparece del documento impreso: un
            // studentName de 120 caracteres es entrada legal y llegaba a x=809 sobre una
            // hoja de 612. Se marca con «…» en vez de cortar en seco, por la misma razón
            // que PdfTextEncoder marca con «?»: una pérdida silenciosa es peor que una
            // visible, sobre todo en un documento que alguien firma.
            String fitted = fitInto(cells[i], columns[i + 1] - columns[i] - 12, font);
            write(content, font, BODY_SIZE, fitted, columns[i] + 6, y - ROW_HEIGHT + 6);
        }
        return y - ROW_HEIGHT;
    }

    private void textBox(PDPageContentStream content, float y, List<String> lines) throws IOException {
        float height = Math.max(1, lines.size()) * LINE_HEIGHT + 10;
        box(content, LEFT, y - height, RIGHT - LEFT, height);
        float lineY = y - 16;
        for (String line : lines) {
            write(content, regular(), BODY_SIZE, line, LEFT + 8, lineY);
            lineY -= LINE_HEIGHT;
        }
    }

    /**
     * Parte el texto en líneas que quepan en el ancho dado, CONSERVANDO SUS PÁRRAFOS.
     *
     * LOS SALTOS DE LÍNEA SE TRATAN ANTES DE SANEAR, y ese orden es el arreglo. Al revés
     * —sanear primero— la fuente se topaba con un `\n`, no podía escribirlo y lo reemplazaba
     * por «?»: cada Enter que el estudiante apretaba en «Compromisos adquiridos» terminaba
     * impreso como un interrogante en un documento que se firma y se anexa. Medido:
     * «Me comprometo a lo siguiente:??1. Sostener el promedio…».
     */
    private List<String> wrap(String text, float maxWidth, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        // UN SALTO SIMPLE NO ES UN PÁRRAFO NUEVO. Se distinguen: una línea en blanco separa
        // párrafos, un salto suelto empieza renglón. Tratarlos igual metía una línea vacía
        // entre cada ítem de una lista numerada, y con diez ítems eso gasta diez renglones
        // y puede empujar el texto a una hoja de continuación sin necesidad.
        boolean firstParagraph = true;
        for (String paragraph : text.split("\\R\\s*\\R")) {
            if (paragraph.isBlank()) {
                continue;
            }
            if (!firstParagraph) {
                lines.add("");   // la línea en blanco que separa un párrafo del siguiente
            }
            firstParagraph = false;
            for (String line : paragraph.split("\\R")) {
                if (line.isBlank()) {
                    continue;
                }
                lines.addAll(wrapParagraph(line, maxWidth, size));
            }
        }
        return lines;
    }

    private List<String> wrapParagraph(String paragraph, float maxWidth, float size)
            throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : PdfTextEncoder.sanitize(paragraph, regular()).split("\\s+")) {
            // UNA PALABRA MÁS ANCHA QUE LA CAJA SE PARTE POR CARÁCTER. Sin esto, la guarda
            // de abajo no puede cortarla —`current` está vacío— y la línea entera se traza
            // fuera de la hoja: 2000 caracteres sin un solo espacio son entrada legal, y
            // medido llegaban a x=12059 sobre una hoja de 612 puntos. Lo que se dibuja
            // pasado el borde no existe para quien imprime el formato.
            if (widthOf(word, size) > maxWidth) {
                for (String piece : splitToFit(word, maxWidth, size)) {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder();
                    }
                    lines.add(piece);
                }
                if (!lines.isEmpty()) {
                    current = new StringBuilder(lines.removeLast());
                }
                continue;
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (widthOf(candidate, size) > maxWidth && !current.isEmpty()) {
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

    private static LocalDateTime atCampus(LocalDateTime utc) {
        return CampusTime.toCampus(utc).toLocalDateTime();
    }

    /** Recorta un valor al ancho de su celda, marcando el recorte. */
    private String fitInto(String text, float maxWidth, PDFont font) throws IOException {
        if (font.getStringWidth(text) / 1000 * BODY_SIZE <= maxWidth) {
            return text;
        }
        StringBuilder fitted = new StringBuilder();
        for (char character : text.toCharArray()) {
            String candidate = fitted.toString() + character + "…";
            if (font.getStringWidth(candidate) / 1000 * BODY_SIZE > maxWidth) {
                break;
            }
            fitted.append(character);
        }
        return fitted + "…";
    }

    private float widthOf(String text, float size) throws IOException {
        return regular().getStringWidth(text) / 1000 * size;
    }

    /** Parte una palabra que no cabe en trozos del ancho máximo. */
    private List<String> splitToFit(String word, float maxWidth, float size) throws IOException {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (char character : word.toCharArray()) {
            if (!piece.isEmpty() && widthOf(piece.toString() + character, size) > maxWidth) {
                pieces.add(piece.toString());
                piece = new StringBuilder();
            }
            piece.append(character);
        }
        if (!piece.isEmpty()) {
            pieces.add(piece.toString());
        }
        return pieces;
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
        try {
            byte[] png = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            return PDImageXObject.createFromByteArray(document, png, "firma");
        } catch (IllegalArgumentException unreadable) {
            // UN TRAZO ILEGIBLE NO PUEDE IMPEDIR EMITIR EL FORMATO. El canal es anónimo y
            // `signature` solo exige @NotBlank, así que cualquiera puede persistir una
            // cadena que no sea una imagen; y como la columna es updatable=false, ese dato
            // queda para siempre. Sin este catch, esa solicitud respondía 500 en cada
            // intento de emitir su documento: un actor sin sesión inutilizaba el
            // entregable central de la feature, sin forma de recuperarse salvo editando
            // la base.
            //
            // Se atrapa IllegalArgumentException y no IOException a propósito: es lo que
            // lanzan tanto Base64.decode ante una carga corrupta como createFromByteArray
            // ante bytes que no son una imagen, y NINGUNA de las dos es IOException, de
            // modo que el catch de render() no las veía.
            log.warn("Firma ilegible en una solicitud; el documento se emite sin ella: {}",
                    unreadable.getMessage());
            return null;
        }
    }
}
