package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniremington.api.tramita.model.NotificationChannel;
import com.uniremington.api.tramita.model.NotificationStatus;
import com.uniremington.api.tramita.model.Request;
import com.uniremington.api.tramita.model.RequestNotification;
import com.uniremington.api.tramita.model.WorkflowDefinition;
import com.uniremington.api.tramita.repo.IRequestNotificationRepo;
import com.uniremington.api.tramita.shared.config.NotificationProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class RequestNotificationServiceTest {

    private final IRequestNotificationRepo notificationRepo = mock(IRequestNotificationRepo.class);
    private final ObjectProvider<JavaMailSender> senderProvider = mock(ObjectProvider.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-26T20:30:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("sin SMTP: registra fallo email y fallback manual pendiente")
    void notifyFinalizedWithoutSmtpRegistersFallback() {
        when(senderProvider.getIfAvailable()).thenReturn(null);
        when(notificationRepo.save(any(RequestNotification.class))).thenAnswer(inv -> inv.getArgument(0));
        RequestNotificationService service = new RequestNotificationService(
                notificationRepo, senderProvider, new NotificationProperties(""), clock);

        service.notifyFinalized(sampleRequest());

        ArgumentCaptor<RequestNotification> captor = ArgumentCaptor.forClass(RequestNotification.class);
        verify(notificationRepo, times(2)).save(captor.capture());
        List<RequestNotification> saved = captor.getAllValues();
        assertThat(saved).extracting(RequestNotification::getChannel)
                .containsExactly(NotificationChannel.EMAIL, NotificationChannel.MANUAL);
        assertThat(saved).extracting(RequestNotification::getStatus)
                .containsExactly(NotificationStatus.FAILED, NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("con SMTP operativo: registra envío exitoso")
    void notifyFinalizedWithSmtpSendsMail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        when(notificationRepo.save(any(RequestNotification.class))).thenAnswer(inv -> inv.getArgument(0));
        RequestNotificationService service = new RequestNotificationService(
                notificationRepo, senderProvider, new NotificationProperties("coord@uniremington.edu.co"), clock);

        service.notifyFinalized(sampleRequest());

        verify(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
        ArgumentCaptor<RequestNotification> captor = ArgumentCaptor.forClass(RequestNotification.class);
        verify(notificationRepo).save(captor.capture());
        RequestNotification saved = captor.getValue();
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getSentAt()).isEqualTo(java.time.LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("si SMTP falla: registra el fallo y deja fallback manual")
    void notifyFinalizedWithMailFailureRegistersFallback() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        when(notificationRepo.save(any(RequestNotification.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new MailSendException("SMTP caído")).when(sender)
                .send(any(org.springframework.mail.SimpleMailMessage.class));
        RequestNotificationService service = new RequestNotificationService(
                notificationRepo, senderProvider, new NotificationProperties("coord@uniremington.edu.co"), clock);

        service.notifyFinalized(sampleRequest());

        ArgumentCaptor<RequestNotification> captor = ArgumentCaptor.forClass(RequestNotification.class);
        verify(notificationRepo, times(2)).save(captor.capture());
        List<RequestNotification> saved = captor.getAllValues();
        assertThat(saved.getFirst().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getLast().getChannel()).isEqualTo(NotificationChannel.MANUAL);
        assertThat(saved.getLast().getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    private Request sampleRequest() {
        return Request.builder()
                .id(java.util.UUID.fromString("00000000-0000-0000-0000-000000000123"))
                .definition(WorkflowDefinition.builder().code("ADICION_CREDITOS").name("Adición de créditos").version(1).build())
                .studentName("Laura Gómez")
                .studentEmail("laura.gomez@estudiante.remington.edu.co")
                .program("Ingeniería de Sistemas")
                .studentDocument("1144099888")
                .build();
    }
}