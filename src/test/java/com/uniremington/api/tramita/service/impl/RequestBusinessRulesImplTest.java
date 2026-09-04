package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.dto.CreateRequestBody;
import com.uniremington.api.tramita.dto.SubjectRequestBody;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowParameter;
import com.uniremington.api.tramita.repo.IWorkflowParameterRepo;
import com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException;
import com.uniremington.api.tramita.shared.exception.UnprocessableRequestException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reglas de negocio leídas de configuración (US2).
 *
 * Estos tests stubean SIEMPRE los parámetros que el caso necesita. No es
 * ceremonia: el prototipo de referencia tenía un test cuyo nombre hablaba de
 * notas y que no stubeaba el repositorio — pasaba por accidente, porque su
 * fixture no llevaba créditos y la validación que no se stubeó nunca se
 * ejecutaba. Un stub ausente convierte un test en decoración.
 */
class RequestBusinessRulesImplTest {

    private static final UUID DEFINITION_ID = UUID.randomUUID();

    private final IWorkflowParameterRepo parameterRepo = mock(IWorkflowParameterRepo.class);
    private final RequestBusinessRulesImpl rules = new RequestBusinessRulesImpl(parameterRepo);

    private final WorkflowDefinition definition = WorkflowDefinition.builder()
            .id(DEFINITION_ID)
            .code("ADICION_CREDITOS")
            .version(1)
            .name("Adición de créditos")
            .build();

    @BeforeEach
    void configureGradeRange() {
        stub("MIN_GRADE", "0.0");
        stub("MAX_GRADE", "5.0");
    }

    // --- Tope de créditos ----------------------------------------------------------------

    @Test
    @DisplayName("rechaza una solicitud cuyo total de créditos supera el máximo configurado")
    void rejectsCreditsAboveTheConfiguredMaximum() {
        stub("MAX_CREDITS", "21");

        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(12, 10)))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("21");
    }

    @Test
    @DisplayName("acepta un total de créditos exactamente igual al máximo configurado")
    void acceptsCreditsExactlyAtTheConfiguredMaximum() {
        stub("MAX_CREDITS", "21");

        assertThatCode(() -> rules.validate(definition, bodyWithCredits(12, 9)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el máximo se lee de la configuración: con otro valor cambia el veredicto")
    void theMaximumComesFromConfigurationAndNotFromCode() {
        stub("MAX_CREDITS", "24");

        // Los mismos 22 créditos que serían rechazados con un tope de 21
        assertThatCode(() -> rules.validate(definition, bodyWithCredits(12, 10)))
                .doesNotThrowAnyException();
    }

    // --- Configuración incompleta o inválida (FR-010, FR-011) -----------------------------

    @Test
    @DisplayName("parámetro ausente: falla como configuración del servidor, no acepta en silencio")
    void missingParameterFailsAsServerConfigurationInsteadOfPassing() {
        when(parameterRepo.findByDefinitionIdAndKey(eq(DEFINITION_ID), eq("MAX_CREDITS")))
                .thenReturn(Optional.empty());

        // Sin este comportamiento, una solicitud de 99 créditos se registraría con
        // un 201 y sin haber aplicado ningún límite: la validación aparecería en el
        // código pero no ocurriría.
        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(99, null)))
                .isInstanceOf(IncompleteConfigurationException.class);
    }

    @Test
    @DisplayName("parámetro no numérico: es configuración inválida, no un rechazo al usuario")
    void nonNumericParameterIsInvalidConfigurationNotAUserError() {
        stub("MAX_CREDITS", "veintiuno");

        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(3, null)))
                .isInstanceOf(IncompleteConfigurationException.class);
    }

    @Test
    @DisplayName("parámetro en cero: es configuración inválida, no un límite que rechaza todo")
    void zeroParameterIsInvalidConfigurationNotALimitOfZero() {
        stub("MAX_CREDITS", "0");

        // Tratarlo como límite cero rechazaría toda solicitud con un 422, culpando
        // al usuario de una configuración que nadie cargó bien.
        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(3, null)))
                .isInstanceOf(IncompleteConfigurationException.class);
    }

    @Test
    @DisplayName("sin créditos declarados no se exige el parámetro de créditos")
    void withoutDeclaredCreditsTheCreditParameterIsNotRequired() {
        when(parameterRepo.findByDefinitionIdAndKey(eq(DEFINITION_ID), eq("MAX_CREDITS")))
                .thenReturn(Optional.empty());

        // Novedad de notas no captura créditos: exigirle MAX_CREDITS dejaría
        // inoperante un trámite que no lo necesita.
        assertThatCode(() -> rules.validate(definition, bodyWithGrades("3.0", "4.0")))
                .doesNotThrowAnyException();
    }

    // --- Rango de notas (FR-012) ----------------------------------------------------------

    @Test
    @DisplayName("rechaza una nota fuera del rango configurado, indicando el rango")
    void rejectsGradeOutsideTheConfiguredRange() {
        assertThatThrownBy(() -> rules.validate(definition, bodyWithGrades("3.0", "5.1")))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("5.0");
    }

    @Test
    @DisplayName("el rango de notas se lee de la configuración: con otro máximo el veredicto cambia")
    void theGradeRangeComesFromConfiguration() {
        stub("MAX_GRADE", "10.0");

        assertThatCode(() -> rules.validate(definition, bodyWithGrades("3.0", "5.1")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rango de notas sin configurar: configuración incompleta, no nota aceptada")
    void missingGradeRangeIsIncompleteConfiguration() {
        when(parameterRepo.findByDefinitionIdAndKey(eq(DEFINITION_ID), eq("MAX_GRADE")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> rules.validate(definition, bodyWithGrades("3.0", "4.0")))
                .isInstanceOf(IncompleteConfigurationException.class);
    }

    // --- Helpers -------------------------------------------------------------------------

    private void stub(String key, String value) {
        when(parameterRepo.findByDefinitionIdAndKey(eq(DEFINITION_ID), eq(key)))
                .thenReturn(Optional.of(WorkflowParameter.builder()
                        .definition(definition).key(key).value(value).build()));
    }

    private CreateRequestBody bodyWithCredits(Integer first, Integer second) {
        var subjects = second == null
                ? List.of(subject(first, null, null))
                : List.of(subject(first, null, null), subject(second, null, null));
        return body(subjects);
    }

    private CreateRequestBody bodyWithGrades(String current, String proposed) {
        return body(List.of(subject(null, current, proposed)));
    }

    private SubjectRequestBody subject(Integer credits, String current, String proposed) {
        return new SubjectRequestBody("MAT-101", "Cálculo Diferencial", credits, "G1",
                current == null ? null : new BigDecimal(current),
                proposed == null ? null : new BigDecimal(proposed));
    }

    private CreateRequestBody body(List<SubjectRequestBody> subjects) {
        return new CreateRequestBody("ADICION_CREDITOS", "Estudiante De Prueba", "DOC-TEST-0001",
                null, null, null, null, subjects);
    }
}
