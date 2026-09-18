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
 *
 * CAPTURES_CREDITS se stubea explícitamente en cada caso que lo necesita: su
 * ausencia ES el caso por defecto (un trámite que no captura créditos), así que
 * dejarlo sin stubear no es un descuido sino la mitad de los escenarios.
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

    // --- Qué trámites capturan créditos (FR-009) ------------------------------------------

    @Test
    @DisplayName("un trámite que captura créditos exige que TODAS sus asignaturas los declaren")
    void capturingCreditsRequiresEverySubjectToDeclareThem() {
        stub("CAPTURES_CREDITS", "true");
        stub("MAX_CREDITS", "21");

        // Sin esta regla la solicitud se registraba con 201 y el tope no se aplicaba
        // nunca: bastaba omitir el dato para que la validación no llegara a correr.
        assertThatThrownBy(() -> rules.validate(definition, bodyWithoutCredits()))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("créditos");
    }

    @Test
    @DisplayName("un trámite que NO captura créditos rechaza la solicitud que los declara")
    void notCapturingCreditsRejectsASubjectThatDeclaresThem() {
        // Sin CAPTURES_CREDITS stubeado: el trámite no los captura (novedad de notas).
        // Antes esto pedía MAX_CREDITS y moría con un 500 por culpa de un dato del cliente.
        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(3, null)))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("no captura créditos");
    }

    @Test
    @DisplayName("un trámite que no captura créditos no necesita MAX_CREDITS configurado")
    void aTradeThatDoesNotCaptureCreditsDoesNotNeedTheMaximum() {
        when(parameterRepo.findByDefinitionIdAndKey(eq(DEFINITION_ID), eq("MAX_CREDITS")))
                .thenReturn(Optional.empty());

        // Novedad de notas no captura créditos: exigirle MAX_CREDITS dejaría
        // inoperante un trámite que no lo necesita.
        assertThatCode(() -> rules.validate(definition, bodyWithGrades("3.0", "4.0")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una solicitud sin asignaturas sigue siendo válida (FR-006, SC-007)")
    void aRequestWithoutSubjectsRemainsValid() {
        stub("CAPTURES_CREDITS", "true");
        stub("MAX_CREDITS", "21");

        assertThatCode(() -> rules.validate(definition, body(List.of())))
                .doesNotThrowAnyException();
    }

    // --- Tope de créditos ----------------------------------------------------------------

    @Test
    @DisplayName("rechaza una solicitud cuyo total de créditos supera el máximo configurado")
    void rejectsCreditsAboveTheConfiguredMaximum() {
        stub("CAPTURES_CREDITS", "true");
        stub("MAX_CREDITS", "21");

        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(12, 10)))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("21");
    }

    @Test
    @DisplayName("acepta un total de créditos exactamente igual al máximo configurado")
    void acceptsCreditsExactlyAtTheConfiguredMaximum() {
        stub("CAPTURES_CREDITS", "true");
        stub("MAX_CREDITS", "21");

        assertThatCode(() -> rules.validate(definition, bodyWithCredits(12, 9)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el máximo se lee de la configuración: con otro valor cambia el veredicto")
    void theMaximumComesFromConfigurationAndNotFromCode() {
        stub("CAPTURES_CREDITS", "true");
        stub("MAX_CREDITS", "24");

        // Los mismos 22 créditos que serían rechazados con un tope de 21
        assertThatCode(() -> rules.validate(definition, bodyWithCredits(12, 10)))
                .doesNotThrowAnyException();
    }

    // --- Configuración incompleta o inválida (FR-010, FR-011) -----------------------------

    @Test
    @DisplayName("parámetro ausente: falla como configuración del servidor, no acepta en silencio")
    void missingParameterFailsAsServerConfigurationInsteadOfPassing() {
        stub("CAPTURES_CREDITS", "true");
        when(parameterRepo.findByDefinitionIdAndKey(eq(DEFINITION_ID), eq("MAX_CREDITS")))
                .thenReturn(Optional.empty());

        // El trámite DECLARA que captura créditos, así que su tope es obligatorio: que
        // falte es un error del servidor y NO puede confundirse con "no los captura",
        // que sería culpar al usuario de una configuración que nadie cargó.
        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(99, null)))
                .isInstanceOf(IncompleteConfigurationException.class);
    }

    @Test
    @DisplayName("parámetro no numérico: es configuración inválida, no un rechazo al usuario")
    void nonNumericParameterIsInvalidConfigurationNotAUserError() {
        stub("CAPTURES_CREDITS", "true");
        stub("MAX_CREDITS", "veintiuno");

        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(3, null)))
                .isInstanceOf(IncompleteConfigurationException.class);
    }

    @Test
    @DisplayName("parámetro en cero: es configuración inválida, no un límite que rechaza todo")
    void zeroParameterIsInvalidConfigurationNotALimitOfZero() {
        stub("CAPTURES_CREDITS", "true");
        stub("MAX_CREDITS", "0");

        // Tratarlo como límite cero rechazaría toda solicitud con un 422, culpando
        // al usuario de una configuración que nadie cargó bien.
        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(3, null)))
                .isInstanceOf(IncompleteConfigurationException.class);
    }

    @Test
    @DisplayName("CAPTURES_CREDITS no interpretable: es configuración inválida, no un 'false'")
    void nonBooleanCapturesCreditsIsInvalidConfiguration() {
        stub("CAPTURES_CREDITS", "puede ser");
        stub("MAX_CREDITS", "21");

        // Leerlo como false haría que un trámite que sí captura créditos rechazara
        // toda solicitud con un 422, culpando al usuario del error de configuración.
        assertThatThrownBy(() -> rules.validate(definition, bodyWithCredits(3, null)))
                .isInstanceOf(IncompleteConfigurationException.class);
    }

    // --- Rango de notas (FR-012) ----------------------------------------------------------

    @Test
    @DisplayName("rechaza una nota por encima del rango configurado, indicando el rango")
    void rejectsGradeAboveTheConfiguredRange() {
        assertThatThrownBy(() -> rules.validate(definition, bodyWithGrades("3.0", "5.1")))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("5.0");
    }

    @Test
    @DisplayName("rechaza una nota por debajo del rango configurado, indicando el rango")
    void rejectsGradeBelowTheConfiguredRange() {
        stub("MIN_GRADE", "1.0");

        // La cota inferior necesita su propio caso: con MIN_GRADE en 0.0 ninguna nota
        // válida puede quedar por debajo, así que la rama nunca se ejercitaba.
        assertThatThrownBy(() -> rules.validate(definition, bodyWithGrades("3.0", "0.5")))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("1.0");
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

    @Test
    @DisplayName("rango invertido: es configuración inválida, no un rango que rechaza todo")
    void invertedGradeRangeIsIncompleteConfiguration() {
        stub("MIN_GRADE", "5.0");
        stub("MAX_GRADE", "0.0");

        // Sin esta guarda, ninguna nota podría estar dentro del rango y toda solicitud
        // recibiría un 422 por un error que el usuario no cometió.
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

    private CreateRequestBody bodyWithoutCredits() {
        return body(List.of(subject(null, null, null)));
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
