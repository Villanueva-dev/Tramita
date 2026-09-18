package com.uniremington.api.tramita.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D3: el código que se imprime en el documento y con el que cualquiera lo consulta sin sesión.
 *
 * ES LA ÚNICA LLAVE DEL CANAL PÚBLICO, así que su fuerza es la protección: 64 bits son unas
 * 1,8 × 10¹⁹ combinaciones, y recorrer ese espacio no es viable. Por eso ese canal no lleva
 * límite de tasa propio — la protección es el tamaño del espacio, no un contador.
 *
 * Y TIENE QUE CABER EN UN PAPEL. El SC-004 exige contrastar un documento en menos de un
 * minuto sin transcribir códigos largos: un UUID, que es el tipo natural del proyecto, son 36
 * caracteres y lo incumple. Base 36 sobre 64 bits da 13 como máximo.
 */
class VerificationCodeGeneratorTest {

    private final VerificationCodeGenerator generator = new VerificationCodeGenerator();

    @Test
    @DisplayName("el código cabe en la columna y en el papel: 13 caracteres alfanuméricos a lo sumo")
    void codeFitsInThirteenAlphanumericCharacters() {
        IntStream.range(0, 500).forEach(i -> {
            String code = generator.generate();
            assertThat(code)
                    .as("Más de 13 caracteres no entra en verification_code VARCHAR(13), y con "
                            + "fail-closed eso no trunca el sello: apaga la emisión")
                    .hasSizeLessThanOrEqualTo(13)
                    .isNotEmpty()
                    .matches("[0-9A-Z]+");
        });
    }

    @Test
    @DisplayName("no se repite: 10 000 códigos seguidos son todos distintos")
    void codesDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        IntStream.range(0, 10_000).forEach(i -> seen.add(generator.generate()));

        assertThat(seen)
                .as("Dos documentos con el mismo código harían que el canal público mostrara "
                        + "el sello equivocado, y el índice único abortaría la segunda emisión")
                .hasSize(10_000);
    }
}
