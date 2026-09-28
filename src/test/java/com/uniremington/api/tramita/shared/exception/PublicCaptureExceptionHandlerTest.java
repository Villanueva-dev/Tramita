package com.uniremington.api.tramita.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniremington.api.tramita.controller.PublicRequestController;
import com.uniremington.api.tramita.dto.PublicRequestBody;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Unit test del 422 del canal público (issue #27).
 *
 * POR QUÉ ES UNITARIO Y NO UN IT. Lo que se verifica acá es la PARTICIÓN —qué campo
 * cuenta como ausente y cuál como inválido— y sus cuatro combinaciones, incluido un
 * borde que en MockMvc costaría cuatro contextos y un origen distinto por caso para
 * no chocar con el límite de envíos. El IT verifica que esto llega servido como JSON;
 * acá se verifica que la decisión es la correcta.
 *
 * La excepción se construye con el MethodParameter REAL del controller, de modo que
 * el BindingResult que recibe el handler es el mismo que produce MVC, no una imitación.
 */
class PublicCaptureExceptionHandlerTest {

    private final PublicCaptureExceptionHandler handler = new PublicCaptureExceptionHandler();

    private static final LocalValidatorFactoryBean VALIDATOR = validator();

    private static LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }

    @Test
    @DisplayName("solo campos ausentes: sigue diciendo «incompleto» y los nombra en missingFields")
    void missingFieldsOnlyKeepTheIncompleteWording() throws Exception {
        ProblemDetail problem = handler.handleIncompleteForm(
                exceptionFor(form().withCampus("").withFaculty("   ").build()));

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("Formato incompleto");
        assertThat(problem.getDetail()).isEqualTo(
                "El formato está incompleto. Revise estos campos: campus, faculty");
        assertThat(missingFields(problem)).containsExactly("campus", "faculty");
        assertThat(invalidFields(problem)).isEmpty();
    }

    @Test
    @DisplayName("solo valores inválidos: NO dice «incompleto» y los nombra en invalidFields")
    void invalidValuesAreNotReportedAsMissing() throws Exception {
        ProblemDetail problem = handler.handleIncompleteForm(
                exceptionFor(form().withEmail("esto-no-es-un-correo").build()));

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("Formato inválido");
        // El defecto que este issue corrige: pedirle rellenar una casilla que ya rellenó.
        assertThat(problem.getDetail())
                .doesNotContain("incompleto")
                .isEqualTo("El formato tiene campos con un valor que no se puede procesar. "
                        + "Revise estos campos: studentEmail");
        assertThat(invalidFields(problem)).containsExactly("studentEmail");
        assertThat(missingFields(problem)).isEmpty();
    }

    @Test
    @DisplayName("ausentes e inválidos a la vez: el detail nombra los dos grupos por separado")
    void missingAndInvalidAreNamedSeparately() throws Exception {
        ProblemDetail problem = handler.handleIncompleteForm(
                exceptionFor(form().withCampus("").withEmail("esto-no-es-un-correo").build()));

        assertThat(problem.getTitle()).isEqualTo("Formato incompleto");
        assertThat(problem.getDetail()).isEqualTo(
                "El formato está incompleto y además tiene campos con un valor que no se "
                        + "puede procesar. Faltan: campus. No se pueden procesar: studentEmail");
        assertThat(missingFields(problem)).containsExactly("campus");
        assertThat(invalidFields(problem)).containsExactly("studentEmail");
    }

    @Test
    @DisplayName("un campo que viola las dos reglas cuenta como ausente, no como inválido")
    void absenceDominatesWhenOneFieldBreaksBothRules() throws Exception {
        // Medido en la sonda de la Fase 0: studentEmail = "   " dispara DOS violaciones
        // sobre el mismo campo, NotBlank y Email. Listarlo en las dos listas obligaría
        // al cliente a decidir cuál mostrar, que es la decisión que este cambio le quita.
        ProblemDetail problem = handler.handleIncompleteForm(
                exceptionFor(form().withEmail("   ").build()));

        assertThat(missingFields(problem)).containsExactly("studentEmail");
        assertThat(invalidFields(problem)).isEmpty();
        assertThat(problem.getTitle()).isEqualTo("Formato incompleto");
    }

    // --- InvalidFieldValueException (009, FR-002): mismo 422 que Bean Validation ----------

    @Test
    @DisplayName("catálogo de programas: mismo 422 «Formato inválido» que un valor inválido de Bean Validation")
    void invalidFieldValueMapsToTheSame422AsBeanValidation() {
        ProblemDetail problem = handler.handleInvalidFieldValue(
                new InvalidFieldValueException(List.of("program")));

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("Formato inválido");
        assertThat(problem.getDetail()).isEqualTo(
                "El formato tiene campos con un valor que no se puede procesar. "
                        + "Revise estos campos: program");
        assertThat(missingFields(problem)).isEmpty();
        assertThat(invalidFields(problem)).containsExactly("program");
    }

    // --- helpers -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static java.util.List<String> missingFields(ProblemDetail problem) {
        return (java.util.List<String>) problem.getProperties().get("missingFields");
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<String> invalidFields(ProblemDetail problem) {
        return (java.util.List<String>) problem.getProperties().get("invalidFields");
    }

    /**
     * El BindingResult tal como lo arma MVC: se valida el record real y se envuelve con
     * el MethodParameter del método que lo recibe.
     */
    private static MethodArgumentNotValidException exceptionFor(PublicRequestBody body)
            throws NoSuchMethodException {
        var errors = new BeanPropertyBindingResult(body, "publicRequestBody");
        VALIDATOR.validate(body, errors);

        var parameter = new MethodParameter(
                PublicRequestController.class.getDeclaredMethod(
                        "submit", String.class, PublicRequestBody.class),
                1);
        return new MethodArgumentNotValidException(parameter, errors);
    }

    private static FormBuilder form() {
        return new FormBuilder();
    }

    /** Formato completo y válido, del que cada caso rompe solo lo que necesita romper. */
    private static final class FormBuilder {
        private String email = "estudiante.de.prueba@ejemplo.test";
        private String campus = "Cali";
        private String faculty = "Facultad de Ingeniería";

        FormBuilder withEmail(String value) {
            this.email = value;
            return this;
        }

        FormBuilder withCampus(String value) {
            this.campus = value;
            return this;
        }

        FormBuilder withFaculty(String value) {
            this.faculty = value;
            return this;
        }

        PublicRequestBody build() {
            return new PublicRequestBody(
                    "Estudiante De Prueba",
                    "SIN-DATO-REAL-U01",
                    email,
                    "3000000001",
                    "COD-PRUEBA",
                    "Ingeniería de Sistemas",
                    campus,
                    faculty,
                    "Distancia",
                    "5",
                    "Necesito adicionar una asignatura del siguiente nivel.",
                    "data:image/png;base64,iVBORw0KGgo=");
        }
    }
}
