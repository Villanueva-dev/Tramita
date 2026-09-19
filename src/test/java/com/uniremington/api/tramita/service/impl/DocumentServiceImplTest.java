package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.repo.IUserRepo;
import com.uniremington.api.tramita.service.DocumentSealMark;
import com.uniremington.api.tramita.service.IDocumentSealService;
import com.uniremington.api.tramita.util.VerificationCodeGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.model.WorkflowParameter;
import com.uniremington.api.tramita.model.WorkflowState;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IWorkflowParameterRepo;
import com.uniremington.api.tramita.service.IDocumentRenderer;
import com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La resolución del formato de un trámite (SP3, issue #10).
 *
 * QUÉ SE PRUEBA ACÁ Y NO EN EL RENDERER. El renderer sabe dibujar UN formato; este
 * servicio decide CUÁL formato le toca a una solicitud, leyendo el parámetro
 * {@code DOCUMENT_TEMPLATE} de su definición. Son dos responsabilidades y dos tests.
 *
 * LA VALIDACIÓN DE UNICIDAD NO ES CEREMONIA. El motor ya resuelve las guardas con
 * {@code guards.stream().filter(...).findFirst()} y sin comprobar que dos beans no
 * declaren la misma clave: con dos, gana el primero de la lista inyectada, sin error ni
 * log, y el resultado depende del orden de escaneo de Spring. Es deuda conocida del
 * proyecto (M5 del review de la 003). Acá no se repite: el registro falla al arrancar.
 */
class DocumentServiceImplTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID DEFINITION_ID = UUID.randomUUID();

    private static final String ACTOR_EMAIL = "coordinacion@test";

    private final IRequestRepo requestRepo = mock(IRequestRepo.class);
    private final IWorkflowParameterRepo parameterRepo = mock(IWorkflowParameterRepo.class);
    private final IDocumentSealService sealService = mock(IDocumentSealService.class);
    private final IUserRepo userRepo = mock(IUserRepo.class);

    @Test
    @DisplayName("dos formatos con la misma clave impiden arrancar, en vez de que gane uno en silencio")
    void duplicatedDocumentKeysFailAtStartup() {
        IDocumentRenderer one = renderer("DO_FR_100");
        IDocumentRenderer other = renderer("DO_FR_100");

        assertThatThrownBy(() -> new DocumentServiceImpl(requestRepo, parameterRepo, List.of(one, other), new VerificationCodeGenerator(), sealService, userRepo))
                .as("con dos implementaciones ganaría la primera de la lista inyectada, "
                        + "y cuál es la primera depende del orden de escaneo de Spring")
                .isInstanceOf(IncompleteConfigurationException.class)
                .hasMessageContaining("DO_FR_100");
    }

    @Test
    @DisplayName("claves distintas conviven: el registro existe para que haya más de un formato")
    void differentDocumentKeysCoexist() {
        IDocumentRenderer doFr100 = renderer("DO_FR_100");
        IDocumentRenderer another = renderer("OTRO_FORMATO");

        assertThat(new DocumentServiceImpl(requestRepo, parameterRepo, List.of(doFr100, another), new VerificationCodeGenerator(), sealService, userRepo))
                .isNotNull();
    }

    @Test
    @DisplayName("un trámite que no declara formato no tiene documento: 404, no 500")
    void tradeWithoutDeclaredTemplateHasNoDocument() {
        DocumentServiceImpl service = serviceWith(renderer("DO_FR_100"));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(request()));
        when(parameterRepo.findByDefinitionIdAndKey(DEFINITION_ID, "DOCUMENT_TEMPLATE"))
                .thenReturn(Optional.empty());

        // La ausencia del parámetro es el caso POR DEFECTO —este trámite no emite
        // documento formal—, no una configuración a medio cargar. Misma lectura que
        // PUBLIC_CAPTURE_ENABLED en el canal público: ausente significa «no», y «no»
        // no es un error del servidor.
        assertThatThrownBy(() -> service.generateFor(REQUEST_ID, ACTOR_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("un formato declarado que nadie implementa es configuración rota: falla ruidosamente")
    void declaredTemplateWithoutImplementationIsBrokenConfiguration() {
        DocumentServiceImpl service = serviceWith(renderer("DO_FR_100"));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(request()));
        when(parameterRepo.findByDefinitionIdAndKey(DEFINITION_ID, "DOCUMENT_TEMPLATE"))
                .thenReturn(Optional.of(parameter("FORMATO_QUE_NO_EXISTE")));

        // Acá sí es 500: alguien declaró un formato que el código no sabe dibujar. Leerlo
        // como «no tiene documento» escondería el error de configuración detrás de un 404.
        assertThatThrownBy(() -> service.generateFor(REQUEST_ID, ACTOR_EMAIL))
                .isInstanceOf(IncompleteConfigurationException.class)
                .hasMessageContaining("FORMATO_QUE_NO_EXISTE");
    }

    @Test
    @DisplayName("una solicitud inexistente no tiene documento")
    void missingRequestHasNoDocument() {
        DocumentServiceImpl service = serviceWith(renderer("DO_FR_100"));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateFor(REQUEST_ID, ACTOR_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("con el formato declarado, delega en el renderer que lo dibuja")
    void delegatesToTheDeclaredRenderer() {
        IDocumentRenderer doFr100 = renderer("DO_FR_100");
        IDocumentRenderer another = renderer("OTRO_FORMATO");
        DocumentServiceImpl service = new DocumentServiceImpl(
                requestRepo, parameterRepo, List.of(another, doFr100),
                new VerificationCodeGenerator(), sealService, userRepo);
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(request()));
        when(parameterRepo.findByDefinitionIdAndKey(DEFINITION_ID, "DOCUMENT_TEMPLATE"))
                .thenReturn(Optional.of(parameter("DO_FR_100")));
        when(userRepo.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(new User()));

        byte[] document = service.generateFor(REQUEST_ID, ACTOR_EMAIL);

        assertThat(new String(document))
                .as("tiene que dibujar el formato DECLARADO, no el primero de la lista")
                .isEqualTo("DO_FR_100");
    }

    @Test
    @DisplayName("FR-012 fail-closed: si el sello no se puede registrar, no se entrega documento")
    void aFailingSealMeansNoDocumentAtAll() {
        DocumentServiceImpl service = serviceWith(renderer("DO_FR_100"));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(request()));
        when(parameterRepo.findByDefinitionIdAndKey(DEFINITION_ID, "DOCUMENT_TEMPLATE"))
                .thenReturn(Optional.of(parameter("DO_FR_100")));
        when(userRepo.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(new User()));
        when(sealService.record(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("la base dijo que no"));

        // Entregar un documento sin registrarlo es peor que no entregarlo: quedaría
        // circulando un papel con apariencia oficial que el sistema no puede verificar
        // después. Por eso la excepción se propaga y Spring deshace la transacción, en vez
        // de que acá haya un try que devuelva el documento igual.
        assertThatThrownBy(() -> service.generateFor(REQUEST_ID, ACTOR_EMAIL))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la emisión registra el sello con la huella del documento entregado")
    void issuingRecordsTheSealWithTheDeliveredDigest() {
        DocumentServiceImpl service = serviceWith(renderer("DO_FR_100"));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(request()));
        when(parameterRepo.findByDefinitionIdAndKey(DEFINITION_ID, "DOCUMENT_TEMPLATE"))
                .thenReturn(Optional.of(parameter("DO_FR_100")));
        when(userRepo.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(new User()));

        service.generateFor(REQUEST_ID, ACTOR_EMAIL);

        // SHA-256 de los bytes "DO_FR_100", que es lo que devuelve el renderer de prueba:
        // la huella describe el ARCHIVO ENTREGADO, no los datos con que se dibujó.
        verify(sealService).record(any(), any(), any(),
                eq(sha256Of("DO_FR_100")), eq("DO_FR_100/v1"), any());
    }

    private static String sha256Of(String content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes()));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // --- helpers -------------------------------------------------------------------------

    private DocumentServiceImpl serviceWith(IDocumentRenderer... renderers) {
        return new DocumentServiceImpl(requestRepo, parameterRepo, List.of(renderers),
                new VerificationCodeGenerator(), sealService, userRepo);
    }

    /** Un renderer que devuelve su propia clave como contenido: así el test ve cuál corrió. */
    private static IDocumentRenderer renderer(String key) {
        return new IDocumentRenderer() {
            @Override
            public String documentKey() {
                return key;
            }

            @Override
            public String formatVersion() {
                return key + "/v1";
            }

            @Override
            public byte[] render(Request request, DocumentSealMark mark) {
                return key.getBytes();
            }
        };
    }

    private static WorkflowParameter parameter(String value) {
        return WorkflowParameter.builder().key("DOCUMENT_TEMPLATE").value(value).build();
    }

    private static Request request() {
        WorkflowState state = WorkflowState.builder().code("RADICADA").name("Radicada").initial(true).build();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .id(DEFINITION_ID)
                .code("ADICION_CREDITOS").version(1).name("Adición de créditos")
                .states(List.of(state))
                .build();
        return Request.builder().id(REQUEST_ID).definition(definition).currentState(state).build();
    }
}
