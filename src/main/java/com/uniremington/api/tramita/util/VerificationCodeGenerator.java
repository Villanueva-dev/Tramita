package com.uniremington.api.tramita.util;

import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * ⚠️ «CRIPTOGRÁFICAMENTE SEGURO» NO SE PUEDE ASEVERAR POR COMPORTAMIENTO (#34 A1). Ningún test
 * puede distinguir, mirando solo los códigos que produce, una fuente {@link SecureRandom} de
 * una {@link java.util.Random} con semilla fija: las dos pasan igual de bien «no se repite
 * entre 10 000» y «mide ≤13 caracteres». Medido: sustituir el campo por
 * {@code new Random(42L)} sobrevivía la suite completa. La única barrera posible es de TIPO,
 * no de comportamiento: la fuente es una dependencia de constructor tipada como
 * {@code SecureRandom}, así que degradarla exige tocar esta firma —visible en cualquier
 * revisión de código— y no un campo privado que se puede debilitar en silencio.
 */
@Component
public class VerificationCodeGenerator {

    private final SecureRandom random;

    /** La forma que usa Spring en producción: una fuente criptográficamente segura nueva. */
    @Autowired
    public VerificationCodeGenerator() {
        this(new SecureRandom());
    }

    /**
     * La forma que usan los tests que necesitan una fuente controlada. Que exista este
     * constructor —y no un método {@code setRandom} o reflexión— es lo que hace visible en la
     * firma pública cualquier intento de debilitar la fuente por defecto.
     */
    public VerificationCodeGenerator(SecureRandom random) {
        this.random = random;
    }

    /** Un código nuevo, no adivinable, de 13 caracteres a lo sumo. */
    public String generate() {
        return Long.toUnsignedString(random.nextLong(), Character.MAX_RADIX).toUpperCase(Locale.ROOT);
    }
}
