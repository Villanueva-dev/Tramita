package com.uniremington.api.tramita.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.uniremington.api.tramita.controller.RequestController;
import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.SubjectRequestBody;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Unit test del mapeo de concurrencia (T023): la carrera real de dos avances no
 * se puede orquestar honestamente en MockMvc — aquí se verifica que, cuando el
 * provider la detecta (@Version, research.md D6), el cliente recibe un 409 con
 * instrucción de reintento y sin internals filtrados.
 */
class GlobalExceptionHandlerTest {

    private static final LocalValidatorFactoryBean VALIDATOR = validator();

    private static LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.afterPropertiesSet();
        return bean;
    }

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("conflicto optimista: 409 con detail accionable y sin internals de JPA")
    void optimisticLockMapsTo409WithoutLeakingInternals() {
        var exception = new ObjectOptimisticLockingFailureException(
                "com.uniremington.api.tramita.model.Request", "un-uuid-interno");

        ProblemDetail problem = handler.handleOptimisticLock(exception);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Conflicto de concurrencia");
        // Nada del mensaje del provider (clase, id) llega al cliente
        assertThat(problem.getDetail()).doesNotContain("Request", "uuid-interno");
    }

    @Test
    @DisplayName("configuración incompleta: 500 con título fijo y sin el diagnóstico interno")
    void incompleteConfigurationMapsTo500WithoutLeakingDiagnostic() {
        var exception = new IncompleteConfigurationException(
                "MAX_CREDITS ausente para definition_id=un-uuid-interno");

        ProblemDetail problem = handler.handleIncompleteConfiguration(exception);

        // 500 y no 422: la falla es de configuración del sistema, no del formulario
        // que envió la Coordinación (research.md D2, FR-010).
        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Configuración del trámite incompleta");
        // El diagnóstico nombra el parámetro y el id de la definición: nada de eso
        // puede llegar al cliente.
        assertThat(problem.getDetail()).isNull();
    }

    // === 400 del canal interno: qué campo y por qué (issue del «Invalid request content.») ===

    /**
     * Construye el BindingResult tal como lo arma MVC: se valida el record real y se envuelve
     * con el MethodParameter del método del controller que lo recibe.
     */
    private static MethodArgumentNotValidException bodyRejection(CreateRequestBody body)
            throws NoSuchMethodException {
        var errors = new BeanPropertyBindingResult(body, "createRequestBody");
        VALIDATOR.validate(body, errors);
        var parameter = new MethodParameter(
                RequestController.class.getDeclaredMethod(
                        "register", CreateRequestBody.class, Authentication.class),
                0);
        return new MethodArgumentNotValidException(parameter, errors);
    }

    @SuppressWarnings("unchecked")
    private static List<String> missingFields(ProblemDetail problem) {
        return (List<String>) problem.getProperties().get("missingFields");
    }

    @SuppressWarnings("unchecked")
    private static List<String> invalidFields(ProblemDetail problem) {
        return (List<String>) problem.getProperties().get("invalidFields");
    }

    private static CreateRequestBody withSubject(SubjectRequestBody subject) {
        return new CreateRequestBody(
                "NOVEDAD_NOTAS", "Ana Probadora", "1017234567",
                null, null, null, null, List.of(subject));
    }

    @Test
    @DisplayName("campo ausente: sigue siendo 400, y ahora lo nombra en missingFields")
    void missingFieldIsNamed() throws Exception {
        ProblemDetail problem = handler.invalidBody(
                bodyRejection(new CreateRequestBody("", "Ana", "1017234567")));

        // El código NO cambia: el 422 es del canal público, donde quien envía es el estudiante.
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Petición incompleta");
        assertThat(problem.getDetail())
                .isEqualTo("El cuerpo de la petición está incompleto. "
                        + "Campos ausentes: definitionCode");
        assertThat(missingFields(problem)).containsExactly("definitionCode");
        assertThat(invalidFields(problem)).isEmpty();
    }

    @Test
    @DisplayName("valor inválido: NO dice que falte, y lo nombra en invalidFields")
    void invalidValueIsNotReportedAsMissing() throws Exception {
        // Es el defecto que originó este cambio: el formulario de novedad de notas mandaba
        // credits = 0 y la respuesta era «Invalid request content.», sin decir qué campo.
        ProblemDetail problem = handler.invalidBody(bodyRejection(
                withSubject(new SubjectRequestBody("IS-704", "Arq", 0, null, null, null))));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Petición inválida");
        assertThat(problem.getDetail())
                .doesNotContain("incompleto")
                .isEqualTo("El cuerpo de la petición tiene campos con un valor inválido. "
                        + "Campos: subjects[0].credits");
        assertThat(invalidFields(problem)).containsExactly("subjects[0].credits");
        assertThat(missingFields(problem)).isEmpty();
    }

    @Test
    @DisplayName("ausentes e inválidos a la vez: el detail nombra los dos grupos por separado")
    void missingAndInvalidAreNamedSeparately() throws Exception {
        ProblemDetail problem = handler.invalidBody(bodyRejection(new CreateRequestBody(
                "NOVEDAD_NOTAS", "", "1017234567", null, null, null, null,
                List.of(new SubjectRequestBody("IS-704", "Arq", 0, null, null, null)))));

        assertThat(problem.getTitle()).isEqualTo("Petición incompleta");
        assertThat(problem.getDetail()).isEqualTo(
                "El cuerpo de la petición está incompleto y además tiene campos con un valor "
                        + "inválido. Ausentes: studentName. Inválidos: subjects[0].credits");
        assertThat(missingFields(problem)).containsExactly("studentName");
        assertThat(invalidFields(problem)).containsExactly("subjects[0].credits");
    }

    @Test
    @DisplayName("un campo que viola las dos reglas cuenta como ausente, no como inválido")
    void absenceDominatesWhenOneFieldBreaksBothRules() throws Exception {
        // studentName en blanco dispara @NotBlank y, si excediera el tope, también @Size.
        // Listarlo en los dos arreglos obligaría al cliente a decidir cuál mostrar.
        ProblemDetail problem = handler.invalidBody(
                bodyRejection(new CreateRequestBody("NOVEDAD_NOTAS", "   ", "1017234567")));

        assertThat(missingFields(problem)).containsExactly("studentName");
        assertThat(invalidFields(problem)).isEmpty();
    }

    @Test
    @DisplayName("nombra campos, nunca valores: nada de lo enviado se refleja de vuelta")
    void namesFieldsButNeverValues() throws Exception {
        // §III de la constitución. El cuerpo rechazado puede traer datos personales, y el
        // problem+json viaja a registros de acceso y a la pantalla.
        String cedulaRechazada = "1017234567890123456789";
        ProblemDetail problem = handler.invalidBody(bodyRejection(
                new CreateRequestBody("NOVEDAD_NOTAS", "Ana Probadora", cedulaRechazada)));

        assertThat(invalidFields(problem)).containsExactly("studentDocument");
        assertThat(problem.getDetail()).doesNotContain(cedulaRechazada);
        assertThat(problem.getProperties().values().toString()).doesNotContain(cedulaRechazada);
    }
}
