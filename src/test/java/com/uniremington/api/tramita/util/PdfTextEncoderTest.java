package com.uniremington.api.tramita.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El saneador de texto del PDF formal (SP3, issue #10).
 *
 * POR QUÉ EXISTE Y POR QUÉ ES TAN ANGOSTO. El prototipo de `router-ia` aplicaba
 * {@code replaceAll("[^\\x20-\\x7E]", "?")} sobre todo el texto, lo que convertía en
 * interrogantes cada tilde y cada eñe de un documento oficial en español. Se midió
 * (sonda de la Fase 0 del issue): Helvetica con WinAnsiEncoding escribe sin problema
 * á é í ó ú, Á É Í Ó Ú, ñ Ñ, ü, ¿ ¡, «», —, las comillas tipográficas y los puntos
 * suspensivos. NO HABÍA problema de codificación que resolver: el parche destruía
 * caracteres que la fuente soporta.
 *
 * Lo que sí puede llegar, porque `reason` admite 2000 caracteres de texto libre desde
 * un canal anónimo, es algo fuera de WinAnsi —un emoji pegado desde un teléfono—. Eso
 * hace que PDFBox lance al escribir, y una excepción ahí convierte la generación del
 * documento en un 500.
 *
 * Por eso el saneador NO lleva lista blanca de caracteres: LE PREGUNTA A LA FUENTE si
 * puede codificar cada uno. Una lista blanca sería adivinar, y quedaría desactualizada
 * el día que se cambie la fuente.
 */
class PdfTextEncoderTest {

    private static final PDFont HELVETICA =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    @Test
    @DisplayName("el español del formato pasa intacto: tildes, eñes, signos y puntuación tipográfica")
    void spanishSurvivesUntouched() {
        String original = "Ana María Peñaranda Gutiérrez — «Ingenierías» ¿sí? ¡Año 2026! Ünicode… ª º";

        String saneado = PdfTextEncoder.sanitize(original, HELVETICA);

        assertThat(saneado)
                .as("ni una tilde puede perderse: es un documento oficial en español")
                .isEqualTo(original);
    }

    @Test
    @DisplayName("un carácter que la fuente no puede escribir se reemplaza, y el resto sobrevive")
    void unsupportedCharacterIsReplacedWithoutDamagingTheRest() {
        // Un emoji pegado desde el teléfono en «Compromisos adquiridos». WinAnsi no lo tiene.
        String original = "Necesito adicionar la asignatura 🎓 para no atrasar el plan";

        String saneado = PdfTextEncoder.sanitize(original, HELVETICA);

        // SE ASIERTA LA CADENA COMPLETA, no sus extremos. La versión anterior comprobaba
        // doesNotContain + startsWith + endsWith, y BORRAR el carácter satisfacía las tres:
        // la garantía que esta clase defiende en prosa —marcar la pérdida en vez de
        // borrarla en silencio— no tenía respaldo ejecutable. Lo delató un mutante.
        assertThat(saneado)
                .isEqualTo("Necesito adicionar la asignatura ? para no atrasar el plan");
    }

    @Test
    @DisplayName("lo saneado siempre se puede escribir: la fuente ya no lanza")
    void sanitizedTextIsAlwaysWritable() {
        String original = "Mezcla 🎓 con acentos á é í y símbolos raros ☃ 🚀";

        String saneado = PdfTextEncoder.sanitize(original, HELVETICA);

        // La garantía que el saneador vende: después de pasar por él, escribir no falla.
        assertThatCode(() -> HELVETICA.encode(saneado)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un texto nulo o vacío no rompe el documento")
    void nullAndEmptyAreSafe() {
        assertThat(PdfTextEncoder.sanitize(null, HELVETICA)).isEmpty();
        assertThat(PdfTextEncoder.sanitize("", HELVETICA)).isEmpty();
    }
}
