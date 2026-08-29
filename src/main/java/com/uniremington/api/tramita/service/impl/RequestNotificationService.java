package com.uniremington.api.tramita.service.impl;

import com.uniremington.api.tramita.model.NotificationChannel;
import com.uniremington.api.tramita.model.NotificationStatus;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestNotification;
import com.uniremington.api.tramita.repo.IRequestNotificationRepo;
import com.uniremington.api.tramita.service.IRequestNotificationService;
import com.uniremington.api.tramita.shared.config.NotificationProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Intenta SMTP y, si no está disponible, registra fallback manual sin romper el cierre. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestNotificationService implements IRequestNotificationService {

    private final IRequestNotificationRepo notificationRepo;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void notifyFinalized(Request request) {
        String recipient = blankToNull(request.getStudentEmail());
        String subject = subjectFor(request);
        String body = bodyFor(request);
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        String fromEmail = blankToNull(properties.fromEmail());

        if (recipient == null) {
            recordEmailFailure(request, subject, body, null, "La solicitud no tiene correo del estudiante");
            recordManualFallback(request, subject, body, null, "La notificación debe enviarse manualmente");
            return;
        }

        if (mailSender == null || fromEmail == null) {
            String reason = mailSender == null
                    ? "SMTP no está configurado"
                    : "APP_NOTIFICATIONS_FROM_EMAIL no está configurado";
            recordEmailFailure(request, subject, body, recipient, reason);
            recordManualFallback(request, subject, body, recipient, "La notificación quedó pendiente para envío manual");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            record(request, NotificationChannel.EMAIL, NotificationStatus.SENT, recipient, subject, body, null, nowUtc());
        } catch (MailException exception) {
            log.warn("Fallo el envío SMTP para la solicitud {}: {}", request.getId(), exception.getMessage());
            recordEmailFailure(request, subject, body, recipient, exception.getMessage());
            recordManualFallback(request, subject, body, recipient, "La notificación quedó pendiente para envío manual");
        }
    }

    private void recordEmailFailure(Request request, String subject, String body, String recipient, String reason) {
        record(request, NotificationChannel.EMAIL, NotificationStatus.FAILED, recipient, subject, body, reason, null);
    }

    private void recordManualFallback(Request request, String subject, String body, String recipient, String reason) {
        // El fallback manual conserva el mensaje listo para que la Coordinación lo despache fuera del sistema.
        record(request, NotificationChannel.MANUAL, NotificationStatus.PENDING, recipient, subject, body, reason, null);
    }

    private void record(Request request, NotificationChannel channel, NotificationStatus status,
            String recipient, String subject, String body, String failureReason, LocalDateTime sentAt) {
        notificationRepo.save(RequestNotification.builder()
                .request(request)
                .channel(channel)
                .status(status)
                .recipientEmail(recipient)
                .subject(subject)
                .body(body)
                .failureReason(failureReason)
                .sentAt(sentAt)
                .build());
    }

    private String subjectFor(Request request) {
        return "Trámite %s finalizado: %s".formatted(request.getId(), request.getDefinition().getName());
    }

    private String bodyFor(Request request) {
        return "Hola %s,%n%nTu trámite %s quedó finalizado.%nRadicado: %s%nPrograma: %s%n%nLa Coordinación puede compartir contigo el PDF formal generado por Trámita."
                .formatted(
                        safe(request.getStudentName()),
                        request.getDefinition().getName(),
                        request.getId(),
                        safe(request.getProgram()));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(clock);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safe(String value) {
        return blankToNull(value) == null ? "No informado" : value.trim();
    }
}