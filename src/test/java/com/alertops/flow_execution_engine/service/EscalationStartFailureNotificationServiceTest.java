package com.alertops.flow_execution_engine.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStartFailureNotification;
import com.alertops.flow_execution_engine.model.EscalationStartFailureNotificationStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.EscalationStartFailureNotificationRepository;
import com.alertops.messaging.Notification;
import com.alertops.team.repository.FailureNotificationRecipientProjection;
import com.alertops.team.repository.TeamMemberRepository;

class EscalationStartFailureNotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final EscalationStartFailureNotificationRepository notifications =
            mock(EscalationStartFailureNotificationRepository.class);
    private final EscalationRepository escalations = mock(EscalationRepository.class);
    private final TeamMemberRepository members = mock(TeamMemberRepository.class);
    private final Notification mailer = mock(Notification.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final EscalationStartFailureNotificationService service =
            new EscalationStartFailureNotificationService(
                    notifications,
                    escalations,
                    members,
                    mailer,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    taskScheduler,
                    Duration.ofMinutes(5),
                    Duration.ofMinutes(1));

    @Test
    void createsOnePendingObligationPerUniqueResponsibleRecipient() {
        UUID escalationId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID schedulerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Escalation escalation = failedEscalation(escalationId, teamId);
        escalation.setScheduledByUserId(schedulerId);
        escalation.setScheduledByUserEmail("scheduler@example.com");

        FailureNotificationRecipientProjection owner = mock(FailureNotificationRecipientProjection.class);
        when(owner.getUserId()).thenReturn(ownerId);
        when(owner.getEmail()).thenReturn("owner@example.com");
        FailureNotificationRecipientProjection duplicate = mock(FailureNotificationRecipientProjection.class);
        when(duplicate.getUserId()).thenReturn(UUID.randomUUID());
        when(duplicate.getEmail()).thenReturn("SCHEDULER@example.com");
        when(members.findFailureNotificationRecipients(teamId)).thenReturn(List.of(owner, duplicate));
        when(notifications.existsByEscalationIdAndRecipientEmailIgnoreCase(eq(escalationId), any(String.class)))
                .thenReturn(false);

        service.createPendingNotifications(escalation, "START_ATTEMPT_FAILED");

        ArgumentCaptor<EscalationStartFailureNotification> captured =
                ArgumentCaptor.forClass(EscalationStartFailureNotification.class);
        verify(notifications, org.mockito.Mockito.times(2)).save(captured.capture());
        List<EscalationStartFailureNotification> saved = captured.getAllValues();
        org.junit.jupiter.api.Assertions.assertEquals(2, saved.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                EscalationStartFailureNotificationStatus.PENDING, saved.get(0).getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(NOW, saved.get(0).getNextAttemptAt());
        org.junit.jupiter.api.Assertions.assertEquals("START_ATTEMPT_FAILED", saved.get(0).getReason());
        org.junit.jupiter.api.Assertions.assertEquals("owner@example.com", saved.get(1).getRecipientEmail());
    }

    @Test
    void successfulDeliveryMarksTheObligationSent() {
        UUID notificationId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        EscalationStartFailureNotification obligation = obligation(notificationId, escalationId);
        Escalation escalation = failedEscalation(escalationId, UUID.randomUUID());
        when(notifications.findRecoverableIdsForEscalation(
                eq(escalationId), any(), eq(NOW))).thenReturn(List.of(notificationId));
        when(notifications.claimForDelivery(eq(notificationId), eq(NOW), eq(NOW.plusSeconds(60))))
                .thenReturn(1);
        when(notifications.findById(notificationId)).thenReturn(Optional.of(obligation));
        when(escalations.findById(escalationId)).thenReturn(Optional.of(escalation));
        when(mailer.sendStartFailureEmail(
                escalationId, escalation.getName(), obligation.getRecipientEmail(), obligation.getReason()))
                .thenReturn(true);

        service.deliverPendingForEscalation(escalationId);

        verify(notifications).markSent(notificationId, NOW);
        verify(notifications, never()).markPending(any(), any(), any());
    }

    @Test
    void failedDeliveryLeavesTheObligationPendingForRecovery() {
        UUID notificationId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        EscalationStartFailureNotification obligation = obligation(notificationId, escalationId);
        Escalation escalation = failedEscalation(escalationId, UUID.randomUUID());
        when(notifications.findRecoverableIdsForEscalation(
                eq(escalationId), any(), eq(NOW))).thenReturn(List.of(notificationId));
        when(notifications.claimForDelivery(eq(notificationId), eq(NOW), eq(NOW.plusSeconds(60))))
                .thenReturn(1);
        when(notifications.findById(notificationId)).thenReturn(Optional.of(obligation));
        when(escalations.findById(escalationId)).thenReturn(Optional.of(escalation));
        when(mailer.sendStartFailureEmail(
                escalationId, escalation.getName(), obligation.getRecipientEmail(), obligation.getReason()))
                .thenReturn(false);

        service.deliverPendingForEscalation(escalationId);

        verify(notifications).markPending(
                notificationId, NOW.plus(Duration.ofMinutes(5)), "The email provider did not accept the notification");
        verify(notifications, never()).markSent(any(), any());
    }

    @Test
    void startupReleasesInFlightObligationsBeforeDeliveringDueRows() {
        when(notifications.findRecoverableIds(any(), eq(NOW))).thenReturn(List.of());

        service.recoverPendingNotifications();

        verify(notifications).requeueInFlightForRecovery(
                NOW, "Recovered after application restart before delivery completed");
        verify(notifications).findRecoverableIds(any(), eq(NOW));
    }

    private Escalation failedEscalation(UUID id, UUID teamId) {
        Escalation escalation = new Escalation();
        escalation.setId(id);
        escalation.setTeamId(teamId);
        escalation.setName("Database outage");
        return escalation;
    }

    private EscalationStartFailureNotification obligation(UUID id, UUID escalationId) {
        EscalationStartFailureNotification obligation = new EscalationStartFailureNotification();
        obligation.setId(id);
        obligation.setEscalationId(escalationId);
        obligation.setRecipientEmail("owner@example.com");
        obligation.setReason("START_ATTEMPT_FAILED");
        obligation.setStatus(EscalationStartFailureNotificationStatus.PENDING);
        obligation.setNextAttemptAt(NOW);
        return obligation;
    }
}
