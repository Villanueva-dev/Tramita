package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.WorkflowParameter;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IWorkflowParameterRepo;
import com.uniremington.api.tramita.service.IDocumentRenderer;
import com.uniremington.api.tramita.model.User;
import com.uniremington.api.tramita.repo.IUserRepo;
import com.uniremington.api.tramita.service.DocumentSealMark;
import com.uniremington.api.tramita.service.IDocumentSealService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import com.uniremington.api.tramita.service.IDocumentService;
import com.uniremington.api.tramita.util.VerificationCodeGenerator;
import com.uniremington.api.tramita.shared.exception.IncompleteConfigurationException;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve qué formato le toca a una solicitud y delega el trazado.
 *
 * EL FORMATO SE ELIGE POR DATO (§VI). La definición del trámite declara
 * {@code DOCUMENT_TEMPLATE} y el valor selecciona la implementación. Sumar un trámite que
 * use el DO-FR-100 es declarar el parámetro; sumar un formato nuevo es agregar un bean.
 * Ninguna de las dos cosas toca este servicio.
 *
 * LA UNICIDAD SE VALIDA AL ARRANCAR, Y ES DELIBERADO. El motor resuelve las guardas con
 * {@code guards.stream().filter(...).findFirst()} sin comprobar que dos beans no declaren
 * la misma clave: con dos, gana el primero de la lista inyectada, sin error ni log, y cuál
 * es el primero depende del orden de escaneo de Spring. Es deuda conocida —M5 del review
 * de la 003— y la pieza nueva no la hereda: un formato duplicado impide arrancar. Un modo
 * de fallo al arranque es preferible a un documento oficial dibujado por el renderer
 * equivocado.
 */
@Service
public class DocumentServiceImpl implements IDocumentService {

    private static final String DOCUMENT_TEMPLATE = "DOCUMENT_TEMPLATE";

    private final IRequestRepo requestRepo;
    private final IWorkflowParameterRepo parameterRepo;
    private final Map<String, IDocumentRenderer> renderersByKey;
    private final VerificationCodeGenerator codeGenerator;
    private final IDocumentSealService sealService;
    private final IUserRepo userRepo;

    public DocumentServiceImpl(
            IRequestRepo requestRepo,
            IWorkflowParameterRepo parameterRepo,
            List<IDocumentRenderer> renderers,
            VerificationCodeGenerator codeGenerator,
            IDocumentSealService sealService,
            IUserRepo userRepo) {
        this.codeGenerator = codeGenerator;
        this.sealService = sealService;
        this.userRepo = userRepo;

        this.requestRepo = requestRepo;
        this.parameterRepo = parameterRepo;
        this.renderersByKey = indexByKey(renderers);
    }

    private static Map<String, IDocumentRenderer> indexByKey(List<IDocumentRenderer> renderers) {
        Map<String, IDocumentRenderer> index = new LinkedHashMap<>();
        for (IDocumentRenderer renderer : renderers) {
            IDocumentRenderer previous = index.put(renderer.documentKey(), renderer);
            if (previous != null) {
                throw new IncompleteConfigurationException(
                        "Hay dos formatos registrados con la clave '%s': %s y %s".formatted(
                                renderer.documentKey(),
                                previous.getClass().getSimpleName(),
                                renderer.getClass().getSimpleName()));
            }
        }
        return index;
    }

    /**
     * ⚠️ YA NO ES {@code readOnly}, Y ESO TIENE UN EFECTO QUE CONVIENE CONOCER: al quitarlo se
     * reactiva el dirty checking, así que cualquier modificación accidental de {@code Request}
     * durante el renderizado se persistiría al cerrar la transacción. Hoy el renderer solo lee.
     *
     * FAIL-CLOSED (FR-012): sellar y entregar son atómicos. Si el guardado del sello falla,
     * Spring deshace la transacción y propaga la excepción; el método nunca retorna y el
     * controlador nunca construye la respuesta. El comportamiento seguro es el que se obtiene
     * al NO escribir manejo de error, así que acá no hay try, ni reintento, ni contador: pedir
     * el documento de nuevo es el reintento, y el manejador global ya responde RFC 9457.
     */
    @Override
    @Transactional
    public byte[] generateFor(UUID requestId, String actorEmail) {
        Request request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La solicitud %s no existe".formatted(requestId)));

        // La ausencia del parámetro es el caso POR DEFECTO —este trámite no emite
        // documento formal—, no una configuración a medio cargar. Misma lectura que
        // PUBLIC_CAPTURE_ENABLED en el canal público (research.md D1 de la 004): ausente
        // significa «no», y «no» no es un error del servidor.
        String declared = parameterRepo
                .findByDefinitionIdAndKey(request.getDefinition().getId(), DOCUMENT_TEMPLATE)
                .map(WorkflowParameter::getValue)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El trámite de esta solicitud no emite documento formal"));

        IDocumentRenderer renderer = renderersByKey.get(declared.trim());
        if (renderer == null) {
            // Distinto del caso anterior: alguien declaró un formato que el código no sabe
            // dibujar. Devolver 404 escondería un error de configuración detrás de un
            // «no tiene documento», que es justo la confusión que el §III de la 003 evita.
            throw new IncompleteConfigurationException(
                    "El trámite %s declara el formato '%s', que no tiene implementación registrada"
                            .formatted(request.getDefinition().getCode(), declared));
        }

        // EL ORDEN IMPORTA Y PARECE CIRCULAR, PERO NO LO ES (research.md D2): el código se
        // genera primero porque no depende del contenido; se dibuja el documento CON el código
        // impreso; y recién entonces se calcula la huella. Hashear antes de imprimir el código
        // produciría una huella que no describe el archivo que se entrega.
        String verificationCode = codeGenerator.generate();

        // El instante se fija ACÁ y se usa para imprimir y para guardar. Si lo pusiera la base
        // al persistir, el pie podría decir una cosa y el sello otra, y la reconstrucción no
        // coincidiría.
        LocalDateTime issuedAt = LocalDateTime.now(ZoneOffset.UTC);

        byte[] document = renderer.render(request, new DocumentSealMark(
                verificationCode, issuedAt,
                request.getCurrentState().getName(), request.getVersion()));

        sealService.record(request, resolveActor(actorEmail), verificationCode,
                sha256(document), renderer.formatVersion(), issuedAt);

        return document;
    }

    private User resolveActor(String actorEmail) {
        // Con sesión válida el usuario existe; si no, es un estado imposible (500 honesto)
        return userRepo.findByEmail(actorEmail).orElseThrow(
                () -> new IllegalStateException("La sesión referencia un usuario inexistente"));
    }

    /** Huella del documento ENTREGADO, con el código ya impreso dentro (research.md D2). */
    private static String sha256(byte[] document) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(document));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 es parte de la plataforma", impossible);
        }
    }
}
