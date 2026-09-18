package com.uniremington.api.tramita;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Arranque común de los IT.
 *
 * Existe porque estas properties DEBEN ser idénticas en todas las clases: el caché
 * de contexto de Spring las compara por valor, así que un literal distinto en un
 * solo archivo le cuesta a esa clase un contexto entero. Medido sobre este repo:
 * al divergir un único valor, la caché pasó de 2 contextos a 3.
 *
 * Hasta el issue #20 ese bloque vivía copiado en cada IT, y la copia fue el vector
 * del defecto: APP_CORS_ALLOWED_ORIGINS apuntó dos meses a un puerto que nadie
 * usaba, y cada archivo nuevo lo heredó del hermano del que se copió.
 *
 * @AutoConfigureMockMvc queda FUERA a propósito: también entra en la clave del
 * caché, y TimelineImmutabilityIT no lo necesita.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(properties = {
        // Placeholders fail-fast de application.yml: deben resolver; los de datasource
        // los pisa @ServiceConnection con los del contenedor.
        "DB_URL=jdbc:postgresql://placeholder:5432/placeholder",
        "DB_USER=placeholder",
        "DB_PASSWORD=placeholder",
        "APP_CORS_ALLOWED_ORIGINS=" + TramitaIntegrationTest.ALLOWED_ORIGIN,
        "SEED_COORD_EMAIL=" + TramitaIntegrationTest.SEED_EMAIL,
        "SEED_COORD_PASSWORD=" + TramitaIntegrationTest.SEED_PASSWORD
})
@Import(TestcontainersConfiguration.class)
public @interface TramitaIntegrationTest {

    /**
     * Origen del SPA en los IT. Sintético a propósito: un valor de test no debe
     * parecer una afirmación sobre el entorno real — ese malentendido originó el
     * issue #20. El origen de desarrollo de verdad vive en .env.example.
     */
    String ALLOWED_ORIGIN = "https://spa.tramita.test";

    String SEED_EMAIL = "coordinacion.cali@uniremington.edu.co";

    String SEED_PASSWORD = "frase de paso de integracion";
}
