package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestDocumentSeal;
import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.model.WorkflowState;
import com.uniremington.api.tramita.repo.IRequestDocumentSealRepo;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * FR-001: toda emisión deja un registro permanente.
 *
 * EL SELLO ES UNA FOTOGRAFÍA DEL MOMENTO DE LA EMISIÓN, y por eso estos casos miran QUÉ se
 * guarda y no solo que se guarde algo: si el estado, la revisión o la versión del formato no
 * quedaran congelados, verificar el documento meses después lo compararía contra un papel
 * distinto del que salió y lo declararía alterado siendo legítimo.
 */
class DocumentSealServiceImplTest {

    private final IRequestDocumentSealRepo sealRepo = mock(IRequestDocumentSealRepo.class);
    private final DocumentSealServiceImpl service = new DocumentSealServiceImpl(sealRepo);

    @Test
    @DisplayName("la emisión registra huella, código, versión del formato, revisión, estado y actor")
    void issuingRecordsTheWholePhotograph() {
        Request request = request();
        User actor = actor();
        LocalDateTime issuedAt = LocalDateTime.of(2026, 9, 18, 15, 30);
        when(sealRepo.save(any(RequestDocumentSeal.class))).thenAnswer(i -> i.getArgument(0));

        service.record(request, actor, "ABC123", "huella-sha256", "DO_FR_100/v1+logo.abc", issuedAt);

        ArgumentCaptor<RequestDocumentSeal> saved = ArgumentCaptor.forClass(RequestDocumentSeal.class);
        verify(sealRepo).save(saved.capture());
        RequestDocumentSeal seal = saved.getValue();

        assertThat(seal.getVerificationCode()).isEqualTo("ABC123");
        assertThat(seal.getDocumentSha256()).isEqualTo("huella-sha256");
        assertThat(seal.getFormatVersion()).isEqualTo("DO_FR_100/v1+logo.abc");
        assertThat(seal.getRequestVersion())
                .as("La revisión distingue un documento emitido antes de un cambio de datos")
                .isEqualTo(4L);
        assertThat(seal.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(seal.getActor()).isSameAs(actor);
        assertThat(seal.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("del estado se congelan el código Y el nombre legible, no solo el código")
    void bothStateCodeAndReadableNameAreFrozen() {
        when(sealRepo.save(any(RequestDocumentSeal.class))).thenAnswer(i -> i.getArgument(0));

        service.record(request(), actor(),
                "ABC123", "huella", "v1", LocalDateTime.of(2026, 9, 18, 15, 30));

        ArgumentCaptor<RequestDocumentSeal> saved = ArgumentCaptor.forClass(RequestDocumentSeal.class);
        verify(sealRepo).save(saved.capture());

        assertThat(saved.getValue().getStateCode()).isEqualTo("EN_FACULTAD");
        assertThat(saved.getValue().getStateName())
                .as("El pie imprime el NOMBRE. Si no se congelara, renombrar el estado —puro "
                        + "cambio de configuración— haría que el documento se declarara alterado")
                .isEqualTo("En facultad");
    }

    private static User actor() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("coordinacion@test");
        return user;
    }

    private static Request request() {
        WorkflowState state = WorkflowState.builder()
                .code("EN_FACULTAD").name("En facultad").build();
        return Request.builder()
                .id(UUID.randomUUID())
                .version(4L)
                .currentState(state)
                .studentName("Ana Prueba")
                .build();
    }
}
