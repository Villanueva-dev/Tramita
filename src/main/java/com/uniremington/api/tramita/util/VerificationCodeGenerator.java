package com.uniremington.api.tramita.util;

import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Genera el código con el que un documento emitido se consulta sin sesión (D3).
 *
 * 64 BITS DE {@link SecureRandom} EN BASE 36. La elección la fija el SC-004: contrastar un
 * documento tiene que tomar menos de un minuto y sin transcribir códigos largos. Un UUID —el
 * tipo que el proyecto usa en todas partes, y por lo tanto lo natural— son 36 caracteres y lo
 * incumple; base 36 sobre 64 bits da 13 como máximo.
 *
 * NO SE USÓ BASE 32 CON ALFABETO SIN AMBIGÜEDADES, que daría 16 caracteres y evitaría
 * confundir 0/O y 1/l al leer a mano. Java no trae Base32 en su biblioteca estándar y habría
 * que escribir y mantener el codificador; {@code Long.toUnsignedString(v, 36)} es una llamada
 * de biblioteca estándar. El trade-off está declarado: se mitiga imprimiendo el código JUNTO A
 * la dirección de verificación, de modo que el camino normal sea copiar del archivo y no
 * transcribir del papel.
 *
 * 🔑 LA FUERZA DE ESTE CÓDIGO ES LA PROTECCIÓN DEL CANAL PÚBLICO. Son unas 1,8 × 10¹⁹
 * combinaciones: recorrer el espacio no es viable, y por eso ese canal NO lleva límite de tasa
 * propio. Sumar un tercer contador sería resolver con infraestructura lo que la aritmética ya
 * resuelve. Debilitar esta generación —un {@code Random} en vez de {@code SecureRandom}, menos
 * bits— convierte esa decisión en un agujero.
 *
 * Devuelve MAYÚSCULAS, y eso es parte del dato y no de la presentación: el código se busca por
 * igualdad exacta contra la columna, así que imprimir en un caso y guardar en otro rompería la
 * consulta pública justo para quien copia del papel.
 */
@Component
public class VerificationCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    /** Un código nuevo, no adivinable, de 13 caracteres a lo sumo. */
    public String generate() {
        return Long.toUnsignedString(random.nextLong(), Character.MAX_RADIX).toUpperCase(Locale.ROOT);
    }
}
