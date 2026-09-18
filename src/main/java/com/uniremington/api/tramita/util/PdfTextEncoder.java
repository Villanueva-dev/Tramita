package com.uniremington.api.tramita.util;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Deja un texto en condiciones de escribirse en el PDF formal, perdiendo lo mínimo.
 *
 * LE PREGUNTA A LA FUENTE, no a una lista blanca. Una lista de caracteres permitidos
 * sería una suposición sobre qué soporta la fuente, y quedaría desactualizada el día que
 * se cambie de Helvetica a otra cosa. Acá se intenta codificar cada carácter y se
 * reemplaza solo el que la fuente rechaza: el saneador se adapta solo.
 *
 * POR QUÉ IMPORTA QUE SEA ASÍ DE ANGOSTO. El prototipo del que se cosecha esta feature
 * aplicaba {@code replaceAll("[^\\x20-\\x7E]", "?")}, que convierte en interrogante toda
 * tilde y toda eñe. Se midió antes de escribir esto: Helvetica con WinAnsiEncoding
 * escribe sin problema á é í ó ú, Á É Í Ó Ú, ñ Ñ, ü, ¿ ¡, «», la raya —, las comillas
 * tipográficas y los puntos suspensivos. Aquel parche no arreglaba nada: mutilaba el
 * español, que es el idioma del documento.
 *
 * Lo que sí llega y la fuente no puede escribir —un emoji pegado desde un teléfono en
 * «Compromisos adquiridos», que admite 2000 caracteres libres desde un canal anónimo—
 * haría lanzar a PDFBox en pleno trazado, y esa excepción convierte la generación del
 * documento oficial en un 500.
 */
public final class PdfTextEncoder {

    /**
     * Se marca la pérdida en vez de borrarla en silencio: quien lea el documento debe
     * poder notar que ahí había algo que no se pudo representar.
     */
    private static final String REPLACEMENT = "?";

    private PdfTextEncoder() {
    }

    public static String sanitize(String text, PDFont font) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder sanitized = new StringBuilder(text.length());
        // Se recorre por CODE POINTS y no por chars: un emoji ocupa dos chars (par
        // suplente) y recorrerlo por char lo partiría en dos mitades inválidas, que
        // producirían dos interrogantes por un solo carácter perdido.
        text.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            sanitized.append(isWritable(character, font) ? character : REPLACEMENT);
        });
        return sanitized.toString();
    }

    private static boolean isWritable(String character, PDFont font) {
        try {
            font.encode(character);
            return true;
        } catch (IOException | IllegalArgumentException rejected) {
            return false;
        }
    }
}
