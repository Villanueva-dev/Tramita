package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestTransitionLog;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowState;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import com.uniremington.api.tramita.shared.exception.IllegalTransitionException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PdfDocumentServiceTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private final IRequestRepo requestRepo = mock(IRequestRepo.class);
    private final IRequestTransitionLogRepo logRepo = mock(IRequestTransitionLogRepo.class);
    private final PdfDocumentService service = new PdfDocumentService(requestRepo, logRepo);

    @Test
    @DisplayName("no genera PDF mientras la solicitud no está finalizada")
    void rejectsNonFinalRequest() {
        Request request = request(false);
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.generate(REQUEST_ID))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    @DisplayName("genera un archivo PDF válido para una solicitud finalizada")
    void generatesValidPdfForFinalRequest() {
        Request request = request(true);
        RequestTransitionLog completion = RequestTransitionLog.builder().request(request).build();
        ReflectionTestUtils.setField(completion, "occurredAt", LocalDateTime.now());
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(logRepo.findByRequestIdOrderByOccurredAtAscIdAsc(REQUEST_ID))
                .thenReturn(List.of(completion));

        byte[] pdf = service.generate(REQUEST_ID);

        assertThat(pdf).startsWith(new byte[] {'%', 'P', 'D', 'F'});
    }

    private Request request(boolean finalState) {
        WorkflowState state = WorkflowState.builder()
                .code(finalState ? "FINALIZADA" : "EN_PROCESO")
                .name(finalState ? "Finalizada" : "En proceso")
                .finalState(finalState)
                .build();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .code("TRAMITE_PRUEBA").version(1).name("Trámite de prueba")
                .states(List.of(state)).build();
        Request request = Request.builder()
                .id(REQUEST_ID).definition(definition).currentState(state)
                .studentName("Estudiante PDF").studentDocument("123456").build();
        return request;
    }
}