package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestSubject;
import com.uniremington.api.tramita.model.RequestTransitionLog;
import com.uniremington.api.tramita.repo.IRequestRepo;
import com.uniremington.api.tramita.repo.IRequestTransitionLogRepo;
import com.uniremington.api.tramita.shared.exception.IllegalTransitionException;
import com.uniremington.api.tramita.shared.exception.ResourceNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Genera la constancia formal usando información persistida de la solicitud. */
@Service
@RequiredArgsConstructor
public class PdfDocumentService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final IRequestRepo requestRepo;
    private final IRequestTransitionLogRepo logRepo;

    @Transactional(readOnly = true)
    public byte[] generate(UUID requestId) {
        Request request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("La solicitud %s no existe".formatted(requestId)));
        if (!request.getCurrentState().isFinalState()) {
            throw new IllegalTransitionException("El documento solo se genera cuando el trámite está finalizado");
        }

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = 735;
                y = write(content, "UNIVERSIDAD REMINGTON", 18, true, 72, y);
                y = write(content, "Sede Cali - Coordinacion Academica", 11, false, 72, y - 8);
                y = write(content, "DOCUMENTO OFICIAL DE CIERRE", 14, true, 72, y - 28);
                y = write(content, "Solicitud: " + request.getId(), 10, false, 72, y - 20);
                y = write(content, "Tipo: " + request.getDefinition().getName(), 10, false, 72, y - 14);
                y = write(content, "Fecha de generacion: " + DATE_FORMAT.format(findCompletion(request)), 10, false, 72, y - 14);
                y -= 28;
                y = write(content, "DATOS DEL ESTUDIANTE", 12, true, 72, y);
                y = write(content, "Nombre: " + safe(request.getStudentName()), 10, false, 72, y - 16);
                y = write(content, "Documento: " + safe(request.getStudentDocument()), 10, false, 72, y - 14);
                y = write(content, "Codigo: " + safe(request.getStudentCode()), 10, false, 72, y - 14);
                y = write(content, "Correo: " + safe(request.getStudentEmail()), 10, false, 72, y - 14);
                y = write(content, "Programa: " + safe(request.getProgram()), 10, false, 72, y - 14);
                y = write(content, "Semestre: " + safe(request.getSemester()), 10, false, 72, y - 14);
                y -= 22;
                y = write(content, "DETALLE", 12, true, 72, y);
                for (RequestSubject subject : request.getSubjects()) {
                    y = write(content, subjectLine(subject), 10, false, 72, y - 16);
                }
                y = write(content, "Justificacion: " + safe(request.getReason()), 10, false, 72, y - 20);
                y -= 24;
                write(content, "Documento generado por Tramita. Verificable mediante el identificador de la solicitud.", 8, false, 72, y);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible generar el documento PDF", exception);
        }
    }

    private java.time.LocalDateTime findCompletion(Request request) {
        List<RequestTransitionLog> entries = logRepo.findByRequestIdOrderByOccurredAtAscIdAsc(request.getId());
        return entries.getLast().getOccurredAt();
    }

    private String subjectLine(RequestSubject subject) {
        return "- " + subject.getCode() + " | " + subject.getName() + " | Creditos: " + safe(subject.getCredits());
    }

    private String safe(Object value) {
        return value == null ? "No registrado" : value.toString();
    }

    private float write(PDPageContentStream content, String text, float size, boolean bold, float x, float y)
            throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA), size);
        content.newLineAtOffset(x, y);
        content.showText(text.replaceAll("[^\\x20-\\x7E]", "?"));
        content.endText();
        return y;
    }
}