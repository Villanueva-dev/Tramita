package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.dto.PublicSealResponse;
import com.uniremington.api.tramita.dto.VerdictResponse;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestDocumentSeal;
import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.model.WorkflowState;
import com.uniremington.api.tramita.repo.IRequestDocumentSealRepo;
import com.uniremington.api.tramita.service.DocumentSealMark;
import com.uniremington.api.tramita.service.IDocumentRenderer;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
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
    private final IDocumentRenderer renderer = mock(IDocumentRenderer.class);
    private final DocumentSealServiceImpl service =
            new DocumentSealServiceImpl(sealRepo, List.of(renderer));

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

    // ---------------------------------------------------------------------------------------
    // FR-006 y FR-007: los tres veredictos, y los DOS motivos por los que el sistema no puede
    // pronunciarse.
    //
    // 🔑 SE COMPARA CONTRA LA HUELLA GUARDADA, NO CONTRA UN DOCUMENTO REGENERADO. Si coincide,
    // ese archivo ES el emitido, con certeza criptográfica y sin importar cuánto haya avanzado
    // el trámite. Recién cuando NO coincide se buscan las explicaciones legítimas, y solo si
    // ninguna aplica se dice ALTERADO: eso es lo que el FR-007 exige.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("el documento tal como se emitió da ÍNTEGRO")
    void anUntouchedDocumentIsIntact() {
        byte[] document = "el documento emitido".getBytes(StandardCharsets.UTF_8);
        RequestDocumentSeal seal = seal("v1", 4L, request(4L), sha256(document));
        when(sealRepo.findByVerificationCode("ABC123")).thenReturn(Optional.of(seal));
        when(renderer.formatVersion()).thenReturn("v1");

        VerdictResponse verdict = service.verify("ABC123", sha256(document));

        assertThat(verdict.status()).isEqualTo(VerdictResponse.Status.INTACT);
        assertThat(verdict.reason())
                .as("Con INTACT la comparación SÍ ocurrió: no hay nada que explicar")
                .isNull();
    }

    @Test
    @DisplayName("un documento modificado da ALTERADO, porque acá el sistema SÍ podía comparar")
    void aModifiedDocumentIsTampered() {
        byte[] emitido = "el documento emitido".getBytes(StandardCharsets.UTF_8);
        RequestDocumentSeal seal = seal("v1", 4L, request(4L), sha256(emitido));
        when(sealRepo.findByVerificationCode("ABC123")).thenReturn(Optional.of(seal));
        when(renderer.formatVersion()).thenReturn("v1");

        VerdictResponse verdict = service.verify(
                "ABC123", sha256("el documento ALTERADO".getBytes(StandardCharsets.UTF_8)));

        assertThat(verdict.status()).isEqualTo(VerdictResponse.Status.TAMPERED);
    }

    @Test
    @DisplayName("emitido con un formato que ya no rige: NO VERIFICABLE por formato, nunca ALTERADO")
    void aDocumentFromAnObsoleteFormatIsNotVerifiable() {
        RequestDocumentSeal seal = seal("v1", 4L, request(4L), "la-huella-de-entonces");
        when(sealRepo.findByVerificationCode("ABC123")).thenReturn(Optional.of(seal));
        // El papel cambió: logo, maquetación o tipografías
        when(renderer.formatVersion()).thenReturn("v2");

        VerdictResponse verdict = service.verify("ABC123", "cualquier-huella");

        assertThat(verdict.status())
                .as("Un cambio de formato tumba TODOS los sellos anteriores a la vez y en "
                        + "silencio. Llamarlos alterados sería acusar al sistema entero de "
                        + "falsificar sus propios documentos (SC-003)")
                .isEqualTo(VerdictResponse.Status.NOT_VERIFIABLE);
        assertThat(verdict.reason()).isEqualTo(VerdictResponse.Reason.FORMAT_CHANGED);
    }

    @Test
    @DisplayName("emitido sobre una revisión anterior: NO VERIFICABLE por datos, nunca ALTERADO")
    void aDocumentFromAnEarlierRevisionIsNotVerifiable() {
        RequestDocumentSeal seal = seal("v1", 4L, request(5L), "la-huella-de-entonces");
        when(sealRepo.findByVerificationCode("ABC123")).thenReturn(Optional.of(seal));
        when(renderer.formatVersion()).thenReturn("v1");

        VerdictResponse verdict = service.verify("ABC123", "cualquier-huella");

        assertThat(verdict.status())
                .as("La huella no coincide Y el trámite avanzó: el papel pudo cambiar por eso, "
                        + "así que el sistema no puede sostener una acusación (FR-007)")
                .isEqualTo(VerdictResponse.Status.NOT_VERIFIABLE);
        assertThat(verdict.reason()).isEqualTo(VerdictResponse.Reason.DATA_CHANGED);
    }

    @Test
    @DisplayName("el trámite avanzó y el documento SIGUE dando ÍNTEGRO: el sello no caduca")
    void anIssuedDocumentStaysIntactAfterTheRequestMovesOn() {
        byte[] document = "el documento emitido".getBytes(StandardCharsets.UTF_8);
        // Sellado sobre la revisión 4; la solicitud ya va por la 9 — el trámite siguió su curso.
        RequestDocumentSeal seal = seal("v1", 4L, request(9L), sha256(document));
        when(sealRepo.findByVerificationCode("ABC123")).thenReturn(Optional.of(seal));
        when(renderer.formatVersion()).thenReturn("v1");

        VerdictResponse verdict = service.verify("ABC123", sha256(document));

        assertThat(verdict.status())
                .as("ESTE ES EL CASO QUE JUSTIFICA COMPARAR CONTRA LA HUELLA GUARDADA. Que el "
                        + "trámite avance es lo normal, no la excepción: si eso bastara para "
                        + "dejar de poder verificar, el sello caducaría el mismo día que se "
                        + "emite y no serviría para nada. La huella coincide, así que ese "
                        + "archivo ES el que salió del sistema, con certeza criptográfica")
                .isEqualTo(VerdictResponse.Status.INTACT);
        assertThat(verdict.reason()).isNull();
    }

    @Test
    @DisplayName("un código que el sistema nunca emitió es 404, no un cuarto veredicto")
    void anUnknownCodeIsNotAVerdictAtAll() {
        when(sealRepo.findByVerificationCode("NOEXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("NOEXISTE", "cualquier-huella"))
                .as("Acusar de alteración a un papel que el sistema nunca produjo es la misma "
                        + "acusación insostenible que FR-007 prohíbe: sin sello no hay nada "
                        + "contra qué comparar, y el contrato lo resuelve con 404 (edge case 5)")
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------------------------------
    // FR-014b: el canal público, por posesión del código impreso. No recibe huella (D10), así
    // que no compara nada y no hay veredicto de integridad: solo afirma que el sello existe.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("lookup: un código existente da ISSUED con fecha, estado y revisión")
    void lookupOfAnExistingSealIsIssued() {
        RequestDocumentSeal seal = seal("v1", 4L, request(4L), "cualquier-huella");
        when(sealRepo.findByVerificationCode("ABC123")).thenReturn(Optional.of(seal));

        PublicSealResponse response = service.lookup("ABC123");

        assertThat(response.status()).isEqualTo(PublicSealResponse.Status.ISSUED);
        assertThat(response.issuedAt()).isEqualTo(seal.getIssuedAt());
        assertThat(response.stateName()).isEqualTo(seal.getStateName());
        assertThat(response.revision()).isEqualTo(seal.getRequestVersion());
    }

    @Test
    @DisplayName("lookup: un código que el sistema nunca emitió es 404")
    void lookupOfAnUnknownCodeIsNotFound() {
        when(sealRepo.findByVerificationCode("NOEXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup("NOEXISTE"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static RequestDocumentSeal seal(
            String formatVersion, long sealedRevision, Request request, String documentSha256) {
        return RequestDocumentSeal.builder()
                .request(request)
                .actor(actor())
                .verificationCode("ABC123")
                .documentSha256(documentSha256)
                .formatVersion(formatVersion)
                .requestVersion(sealedRevision)
                .stateCode("EN_FACULTAD")
                .stateName("En facultad")
                .issuedAt(LocalDateTime.of(2026, 9, 18, 15, 30))
                .build();
    }

    /** La misma huella que calcula {@code DocumentServiceImpl} al emitir (research.md D2). */
    private static String sha256(byte[] document) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(document));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 es parte de la plataforma", impossible);
        }
    }

    private static User actor() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("coordinacion@test");
        return user;
    }

    private static Request request() {
        return request(4L);
    }

    /** @param currentRevision la revisión VIGENTE de la solicitud, que el sello puede no compartir */
    private static Request request(long currentRevision) {
        WorkflowState state = WorkflowState.builder()
                .code("EN_FACULTAD").name("En facultad").build();
        return Request.builder()
                .id(UUID.randomUUID())
                .version(currentRevision)
                .currentState(state)
                .studentName("Ana Prueba")
                .build();
    }
}
