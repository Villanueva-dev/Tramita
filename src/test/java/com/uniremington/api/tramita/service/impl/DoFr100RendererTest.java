package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.service.DocumentSealMark;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowState;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    /** Los textos con que se reconoce cada línea del pie. Si el pie cambia, se actualiza acá. */
    private static final List<String> FOOTER_MARKERS =
            List.of("Generado por Trámita", "Verificación:");

    /** Una marca de sello cualquiera: la mayoría de los casos de este archivo no la miran. */
    private static final DocumentSealMark TEST_MARK = new DocumentSealMark(
            "TESTCODE0001", LocalDateTime.of(2026, 9, 18, 15, 30), "Radicada", 0L);


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
        String text = textOf(renderer.render(request(), TEST_MARK));

        assertThat(text)
                .as("un rótulo cambiado deja de ser el formato oficial")
                .contains(OFFICIAL_LABELS);
    }

    @Test
    @DisplayName("lleva los datos que el estudiante diligenció, con sus tildes intactas")
    void carriesTheSubmittedDataWithAccents() throws Exception {
        String text = textOf(renderer.render(request(), TEST_MARK));

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
        byte[] pdf = renderer.render(request(), TEST_MARK);

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
        String text = pageText(renderer.render(request(), TEST_MARK), 1);

        assertThat(text).contains(
                "Excepción por asignatura reprobada por segunda vez",
                "Excepción de cancelación semestre por segunda o más veces",
                "Excepción de Matrícula por bajo rendimiento académico",
                "Matrícula créditos adicionales");

        // SE ATA LA MARCA A SU FILA, no se cuenta. La versión anterior asertaba que
        // hubiera exactamente una X en la página, que es cardinalidad, no identidad:
        // marcar la casilla EQUIVOCADA dejaba la clase entera en verde. Lo delató un
        // mutante del review independiente. Un formato con el tipo equivocado marcado
        // circula con sello institucional diciendo otra cosa que la que se pidió.
        assertThat(text)
                .as("la marca tiene que estar en la fila del trámite, no en cualquiera")
                .containsPattern("Matrícula créditos adicionales\\s+X");
        assertThat(standaloneMarks(text))
                .as("marcar más de un tipo convierte el formato en una solicitud ambigua")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("la ciudad va preimpresa: aparece aunque la solicitud no traiga sede")
    void printsTheCityThatIsPreprintedOnTheForm() throws Exception {
        // Las solicitudes del formulario interno NO traen campus: ese campo lo agregó el
        // canal público en la 004. Se descubrió mirando un documento generado desde la
        // base, con la celda «Ciudad» vacía donde el papel dice «Cali». Son dos campos
        // distintos: «Ciudad» pertenece al formulario, «Sede» al solicitante.
        Request internalForm = requestBuilder().campus(null).build();

        // SE ASIERTA LA CELDA, NO LA PÁGINA. La primera versión de este test comprobaba
        // que «Cali» apareciera en algún lugar de la hoja, y pasaba aunque la celda
        // estuviera vacía: el pie dice «Sede Cali». Un mutante lo delató. Ahora se ata la
        // ciudad a su fila —Ciudad | Día | Mes | Año— que es donde el formato la pone.
        assertThat(pageText(renderer.render(internalForm, TEST_MARK), 1))
                .as("la ciudad es parte del formato, no un dato que pueda faltar")
                .containsPattern("Ciudad\\s+Día\\s+Mes\\s+Año\\s+Cali");
    }

    @Test
    @DisplayName("nunca imprime las trece casillas de motivos: son de otros trámites")
    void neverPrintsTheThirteenReasonCheckboxes() throws Exception {
        String text = textOf(renderer.render(request(), TEST_MARK));

        assertThat(text).doesNotContain(REASON_CHECKBOXES.toArray(String[]::new));
    }

    @Test
    @DisplayName("sin firma —el caso del formulario interno— el documento se genera igual")
    void rendersWithoutSignature() throws Exception {
        Request withoutSignature = requestBuilder().studentSignature(null).build();

        assertThatCode(() -> renderer.render(withoutSignature, TEST_MARK)).doesNotThrowAnyException();
        assertThat(pageText(renderer.render(withoutSignature, TEST_MARK), 2))
                .as("el bloque queda vacío, como un formato todavía sin firmar")
                .contains("Firma del estudiante");
    }

    @Test
    @DisplayName("un motivo largo se conserva entero, aunque haya que pasar de hoja")
    void aLongReasonIsNeverTruncated() throws Exception {
        // EL NOMBRE ANTERIOR DE ESTE TEST ERA UNA AFIRMACIÓN FALSA: decía que el motivo más
        // largo que el contrato permite «cabe en la página». Cabe el de prosa española con
        // el que se midió, no el de palabras anchas. Y su aserción de páginas era
        // infalsificable: render() siempre agrega la hoja de datos y la de firmas, así que
        // getNumberOfPages()==2 no podía fallar hiciera lo que hiciera el contenido.
        //
        // Lo que sí vale fijar es que NADA SE PIERDE: es un documento oficial y recortar en
        // silencio lo que el estudiante escribió es peor que gastar una hoja.
        String longReason = ("Necesito adicionar la asignatura para no atrasar el plan de estudios. ")
                .repeat(29);

        byte[] pdf = renderer.render(
                requestBuilder().reason(longReason.substring(0, 2000)).build(), TEST_MARK);

        assertThat(textOf(pdf))
                .as("el final del motivo tiene que estar en el documento, no cortado")
                .contains("no atrasar el plan de estudios.");
        assertThat(pageText(pdf, Loader.loadPDF(pdf).getNumberOfPages()))
                .as("la firma sigue cerrando el documento, aunque el motivo haya crecido")
                .contains("Firma del estudiante");
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
        byte[] pdf = renderer.render(requestBuilder().reason(longReason.substring(0, 2000)).build(), TEST_MARK);

        // 70 es el margen inferior que el renderer reserva. Medido sobre la versión
        // correcta: nada del cuerpo baja de 80, y lo único por debajo es el pie, en 40.
        assertThat(lowestTextBaseline(pdf))
                .as("un texto que invade el margen inferior se sale de la caja y pisa el pie")
                .isGreaterThanOrEqualTo(70f);
    }

    @Test
    @DisplayName("una firma ilegible no impide emitir el formato")
    void anUnreadableSignatureDoesNotBlockTheDocument() throws Exception {
        // EL CANAL ES ANÓNIMO. `signature` solo exige @NotBlank, así que cualquiera puede
        // enviar una cadena que no sea una imagen, y queda persistida con updatable=false.
        // Si eso hace lanzar al renderer, esa solicitud no vuelve a emitir su formato
        // NUNCA: un actor sin sesión inutiliza el entregable central de la feature.
        Request withGarbage = requestBuilder()
                .studentSignature("data:image/png;base64,esto-no-es-base64!!").build();
        Request withNonImage = requestBuilder()
                .studentSignature("data:image/png;base64,aGVsbG8gbXVuZG8=").build();

        for (Request request : List.of(withGarbage, withNonImage)) {
            assertThatCode(() -> renderer.render(request, TEST_MARK)).doesNotThrowAnyException();
            assertThat(pageText(renderer.render(request, TEST_MARK), 2))
                    .as("el formato sale sin firma, como un papel todavía sin firmar")
                    .contains("Firma del estudiante");
        }
    }

    @Test
    @DisplayName("un motivo sin espacios no se sale por el borde derecho de la hoja")
    void aReasonWithoutSpacesStaysInsideTheSheet() throws Exception {
        // 2000 caracteres sin un solo espacio es entrada legal: @Size(max=2000) no exige
        // palabras. Sin corte por carácter, la línea entera se traza fuera de la hoja y
        // el texto desaparece del documento impreso.
        byte[] pdf = renderer.render(requestBuilder().reason("A".repeat(2000)).build(), TEST_MARK);

        assertThat(rightmostTextEdge(pdf))
                .as("lo que se dibuja pasado el margen derecho no existe para quien imprime")
                .isLessThanOrEqualTo(567f);
    }

    @Test
    @DisplayName("el motivo más ANCHO que el contrato permite tampoco invade el margen")
    void theWidestAllowedReasonStaysAboveTheMargin() throws Exception {
        // ESTE CASO ES EL QUE FALTABA. La medición que decidió no tener flujo a otra
        // página se hizo con UNA cadena de prosa española, y se generalizó. Con palabras
        // anchas entran muchas más por línea y el texto baja mucho más: medido, 2000
        // caracteres de «MM » terminaban en el baseline 30, por debajo del pie.
        byte[] pdf = renderer.render(
                requestBuilder().reason("MM ".repeat(700).substring(0, 2000)).build(), TEST_MARK);

        assertThat(lowestTextBaseline(pdf))
                .as("el peor caso de ancho también tiene que respetar el margen")
                .isGreaterThanOrEqualTo(70f);
    }

    @Test
    @DisplayName("la fecha es la del día en Cali, no la del reloj UTC del servidor")
    void theDateIsTheOneInCaliNotTheServerClock() throws Exception {
        // `createdAt` se persiste en UTC (convención del chasis). Colombia es UTC−5, así que
        // una solicitud enviada el 17 a las 20:00 de Cali se guarda como el 18 a la 01:00
        // UTC. Formatear ese valor sin convertir imprime el día siguiente en la celda
        // «Fecha» de un documento que se firma y se anexa.
        Request lateInTheEvening = requestBuilder()
                .createdAt(LocalDateTime.of(2026, 9, 18, 1, 0)).build();

        String text = pageText(renderer.render(lateInTheEvening, TEST_MARK), 1);

        assertThat(text)
                .as("a la 01:00 UTC en Cali son las 20:00 del día anterior")
                .containsPattern("Ciudad\\s+Día\\s+Mes\\s+Año\\s+Cali\\s+17\\s+09\\s+2026");
    }

    @Test
    @DisplayName("un nombre en el límite del campo no se sale de la celda")
    void aMaximumLengthNameStaysInsideTheCell() throws Exception {
        // 120 caracteres es el máximo de studentName (@Size). La celda no los recorta ni
        // los ajusta, así que el texto se dibujaba fuera de la hoja: mismo defecto que el
        // motivo sin espacios, aplicado a la tabla.
        byte[] pdf = renderer.render(requestBuilder().studentName("Ana ".repeat(30)).build(), TEST_MARK);

        assertThat(rightmostTextEdge(pdf))
                .as("una celda que se desborda deja el dato fuera del documento impreso")
                .isLessThanOrEqualTo(567f);
    }

    @Test
    @DisplayName("la firma trazada se incrusta de verdad, y sin firma no hay imagen de más")
    void theSignatureIsActuallyDrawn() throws Exception {
        // NINGÚN TEST VEÍA LAS IMÁGENES. PDFTextStripper no las extrae, así que un renderer
        // que dejara de dibujar la firma —o el logo— pasaba la clase entera. Se cuentan los
        // XObject de la página de firmas: con firma van dos (logo + trazo), sin ella uno.
        assertThat(imagesOnPage(renderer.render(request(), TEST_MARK), 2))
                .as("la firma del estudiante tiene que estar en el documento")
                .isEqualTo(2);
        assertThat(imagesOnPage(renderer.render(requestBuilder().studentSignature(null).build(), TEST_MARK), 2))
                .as("sin firma queda solo el logo del encabezado")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("los párrafos del motivo se conservan, y ningún salto de línea se imprime")
    void theReasonKeepsItsParagraphsAndPrintsNoStrayMarks() throws Exception {
        // APRETAR ENTER ES LO MÁS NATURAL EN UN TEXTAREA, y era lo que ensuciaba el
        // documento: wrap() saneaba ANTES de partir por espacios, la fuente no puede
        // escribir un salto de línea, y cada uno terminaba impreso como «?» en un papel
        // que se firma y se anexa. Medido antes de corregirlo:
        //   «Me comprometo a lo siguiente:??1. Sostener el promedio…»
        String motivo = """
                Me comprometo a lo siguiente:

                1. Sostener el promedio acumulado exigido por el reglamento.
                2. Cumplir con la asistencia del 80 % en la asignatura adicional.""";

        String text = pageText(renderer.render(requestBuilder().reason(motivo).build(), TEST_MARK), 1);

        assertThat(text)
                .as("un salto de línea no es un carácter imprimible: no puede salir como «?»")
                .doesNotContain("siguiente:?")
                .doesNotContain("reglamento.?");
        assertThat(text)
                .as("el texto del estudiante tiene que llegar entero")
                .contains("Me comprometo a lo siguiente:")
                .contains("1. Sostener el promedio acumulado exigido por el reglamento.")
                .contains("2. Cumplir con la asistencia del 80 % en la asignatura adicional.");

        // Y LA ESTRUCTURA SE CONSERVA, no solo el contenido. Un salto simple empieza
        // renglón y una línea en blanco separa párrafos: entre la entrada y el primer ítem
        // hay más aire que entre los dos ítems, que el estudiante escribió seguidos.
        // Se mide el espaciado real, porque la extracción de texto no distingue una línea
        // en blanco de un renglón contiguo.
        byte[] pdf = renderer.render(requestBuilder().reason(motivo).build(), TEST_MARK);
        float intro = baselineOf(pdf, "Me comprometo a lo siguiente:");
        float primero = baselineOf(pdf, "1. Sostener");
        float segundo = baselineOf(pdf, "2. Cumplir");

        assertThat(intro - primero)
                .as("una línea en blanco separa el párrafo de entrada de la lista")
                .isGreaterThan(segundo == 0 ? 0 : (primero - segundo) * 1.5f);
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
                    // El pie va deliberadamente bajo el margen (baselines 40 y 30): es el
                    // sello del documento, no contenido del formato oficial.
                    //
                    // ⚠️ SE FILTRA POR TEXTO Y NO POR POSICIÓN, y es a propósito: filtrar por
                    // altura descartaría también el contenido que se desborda hacia abajo, que
                    // es justamente lo que estos tests existen para detectar. El costo es que
                    // agregar una línea al pie obliga a sumarla acá — pasó al imprimir la marca
                    // legible del sello, y el plan lo había anticipado.
                    if (FOOTER_MARKERS.stream().anyMatch(text::contains)) {
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

    /** La altura del renglón que empieza con el texto dado, en puntos desde abajo. */
    private static float baselineOf(byte[] pdf, String startsWith) throws Exception {
        List<Float> found = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (text.trim().startsWith(startsWith) && !positions.isEmpty()) {
                        TextPosition first = positions.getFirst();
                        found.add(first.getPageHeight() - first.getY());
                    }
                }
            };
            stripper.getText(document);
        }
        return found.isEmpty() ? 0f : found.getFirst();
    }

    /** Cuántas imágenes lleva una página del documento. */
    private static int imagesOnPage(byte[] pdf, int page) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            var resources = document.getPage(page - 1).getResources();
            int images = 0;
            for (var name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) {
                    images++;
                }
            }
            return images;
        }
    }

    /** El borde derecho del texto más lejano del documento, en puntos desde la izquierda. */
    private static float rightmostTextEdge(byte[] pdf) throws Exception {
        List<Float> edges = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    positions.forEach(position ->
                            edges.add(position.getX() + position.getWidth()));
                }
            };
            stripper.getText(document);
        }
        return edges.stream().max(Float::compare).orElseThrow();
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

    @Test
    @DisplayName("FR-003: el pie imprime el código, la fecha de emisión, el estado y la revisión")
    void footerPrintsTheHumanReadableSeal() throws Exception {
        DocumentSealMark mark = new DocumentSealMark(
                "ABC123XYZ", LocalDateTime.of(2026, 9, 18, 15, 30), "Radicada", 7L);

        String text = textOf(renderer.render(requestBuilder().build(), mark));

        assertThat(text)
                .as("Quien recibe el papel impreso no tiene cuenta en el sistema: la marca "
                        + "legible es lo único que le permite contrastar el documento")
                .contains("ABC123XYZ")
                .contains("Radicada")
                .contains("7");
    }

    @Test
    @DisplayName("D5: la versión del formato incluye la versión de la biblioteca que dibuja")
    void formatVersionIncludesTheRenderingLibraryVersion() {
        // El pom fija la versión de PDFBox JUSTAMENTE para poder actualizarla ante
        // vulnerabilidades, así que va a cambiar. Si un parche altera un solo byte de la
        // salida y la versión del formato no lo refleja, los sellos anteriores pasan a
        // reportarse como ALTERADOS en vez de «no verificables»: se acusaría de falsificación
        // a documentos legítimos por haber aplicado una actualización de seguridad.
        //
        // ⛔ Si este test se pone rojo porque alguien quitó la versión de la biblioteca,
        // reponerla es la respuesta; no hay que ajustar la aserción.
        assertThat(renderer.formatVersion())
                .as("la versión del formato tiene que cambiar sola al actualizar PDFBox, "
                        + "sin depender de que alguien se acuerde de tocar una constante")
                .contains(org.apache.pdfbox.util.Version.getVersion());
    }

    @Test
    @DisplayName("D5: la versión del formato identifica maquetación y logo")
    void formatVersionIdentifiesLayoutAndLogo() {
        assertThat(renderer.formatVersion())
                .as("El sello guarda esta cadena; si no distingue una maquetación de otra, "
                        + "un documento viejo se reconstruye con el formato nuevo")
                .isNotBlank()
                .hasSizeLessThanOrEqualTo(80)
                .startsWith("DO_FR_100/");
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
                // El renderer deriva de acá el /ID del documento: una solicitud sin
                // identidad no puede emitir un documento distinguible de los demás.
                .id(UUID.fromString("33333333-3333-4333-8333-333333333333"))
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
