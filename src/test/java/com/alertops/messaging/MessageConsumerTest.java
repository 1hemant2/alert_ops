package com.alertops.messaging;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.flow_execution_engine.service.EscalationAcknowledgementService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageConsumerTest {
    private static final UUID STEP_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID ESCALATION_ID = UUID.fromString("51000000-0000-0000-0000-000000000002");

    private final FlowExecutionStateRepository stateRepository = mock(FlowExecutionStateRepository.class);
    private final EscalationRepository escalationRepository = mock(EscalationRepository.class);
    private final Notification notification = mock(Notification.class);
    private final StepSchedulingService stepSchedulingService = mock(StepSchedulingService.class);
    private final EscalationAcknowledgementService acknowledgementService = mock(EscalationAcknowledgementService.class);
    private final EscalationTimeoutService timeoutService = mock(EscalationTimeoutService.class);
    private final MessageConsumer consumer = new MessageConsumer(
            stateRepository, escalationRepository, notification, stepSchedulingService,
            acknowledgementService, timeoutService, Clock.systemUTC());

    @Test
    // Verifies that an older send attempt is ignored.
    void ignoresMessageFromOlderSendAttempt() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 1);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));

        consumer.deliverReadyStep(queuedState);

        verify(stateRepository, never()).claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class));
        verifyNoInteractions(notification, escalationRepository, stepSchedulingService);
    }

    @Test
    // Verifies that a previously claimed step is not delivered twice.
    void ignoresDeliveryWhenAnotherConsumerHasAlreadyClaimedTheStep() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class))).thenReturn(0);

        consumer.deliverReadyStep(queuedState);

        verifyNoInteractions(notification, stepSchedulingService);
        verify(stateRepository, never()).save(currentState);
    }

    @Test
    // Verifies that duplicate ready messages produce one notification.
    void sendsOnlyOnceWhenTheSameMessageArrivesAfterTheStepFinished() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class))).thenReturn(1);
        when(acknowledgementService.createAcknowledgementUrl(any(Escalation.class), any(FlowExecutionState.class)))
                .thenReturn("https://alerts.example.com/acknowledge?token=test-token");
        when(notification.sendEmail(currentState, "https://alerts.example.com/acknowledge?token=test-token", null)).thenReturn(true);

        consumer.deliverReadyStep(queuedState);
        consumer.deliverReadyStep(queuedState);

        verify(notification, times(1)).sendEmail(currentState, "https://alerts.example.com/acknowledge?token=test-token", null);
        verify(stateRepository, times(1)).claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class));
        assertEquals(FlowExecutionStepStatus.SENT, currentState.getStatus());
        assertEquals(1, currentState.getSendAttemptCount());
    }

    @Test
    // Verifies that a successful final send waits for acknowledgement.
    void successfulFinalSendWaitsForAcknowledgement() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class))).thenReturn(1);
        when(acknowledgementService.createAcknowledgementUrl(any(Escalation.class), any(FlowExecutionState.class)))
                .thenReturn("https://alerts.example.com/acknowledge?token=test-token");
        when(notification.sendEmail(currentState, "https://alerts.example.com/acknowledge?token=test-token", null))
                .thenReturn(true);

        consumer.deliverReadyStep(queuedState);

        assertEquals(FlowExecutionStepStatus.SENT, currentState.getStatus());
        verify(timeoutService).scheduleAcknowledgementTimeout(
                eq(ESCALATION_ID), eq(STEP_ID), eq(currentState.getDueAt()));
        verify(escalationRepository, never()).save(any(Escalation.class));
        verify(stepSchedulingService, never()).scheduleStep(any(FlowExecutionState.class));
    }

    @Test
    // Verifies that a non-final send uses the next step due time as its boundary.
    void nonFinalSendUsesNextStepDueAtForAcknowledgement() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        FlowExecutionState nextState = currentState(
                FlowExecutionStepStatus.PENDING, 0);
        nextState.setId(UUID.fromString("51000000-0000-0000-0000-000000000003"));
        Instant nextDueAt = Instant.parse("2026-10-07T12:05:00Z");
        nextState.setDueAt(nextDueAt);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class))).thenReturn(1);
        when(stateRepository.findFirstByProcessIdAndStatusOrderByPositionAsc(
                ESCALATION_ID, FlowExecutionStepStatus.PENDING)).thenReturn(nextState);
        when(stepSchedulingService.scheduleStep(nextState)).thenReturn(nextState);
        when(acknowledgementService.createAcknowledgementUrl(any(Escalation.class), any(FlowExecutionState.class)))
                .thenReturn("https://alerts.example.com/acknowledge?token=test-token");
        when(acknowledgementService.createEscalateNowUrl(any(Escalation.class), eq(currentState), eq(nextState)))
                .thenReturn("https://alerts.example.com/escalate?token=escalate-token");
        when(notification.sendEmail(
                currentState,
                "https://alerts.example.com/acknowledge?token=test-token",
                "https://alerts.example.com/escalate?token=escalate-token"))
                .thenReturn(true);

        consumer.deliverReadyStep(queuedState);

        assertEquals(nextDueAt, currentState.getDueAt());
        verify(stepSchedulingService).scheduleStep(nextState);
        verify(acknowledgementService).createEscalateNowUrl(any(Escalation.class), eq(currentState), eq(nextState));
        verify(notification).sendEmail(
                currentState,
                "https://alerts.example.com/acknowledge?token=test-token",
                "https://alerts.example.com/escalate?token=escalate-token");
        verify(timeoutService, never()).scheduleAcknowledgementTimeout(any(), any(), any());
    }

    @Test
    // Verifies that paused acknowledgement steps ignore ready callbacks.
    void ignoresAReadyCallbackAfterAcknowledgementPause() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        Escalation acknowledgedEscalation = runningEscalation();
        acknowledgedEscalation.setStatus(EscalationStatus.ACKNOWLEDGED);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(acknowledgedEscalation));

        consumer.deliverReadyStep(queuedState);

        verify(stateRepository, never()).claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class));
        verifyNoInteractions(notification, stepSchedulingService);
    }

    @Test
    // Verifies that unexpected delivery errors escape for broker retry.
    void letsUnexpectedProcessingErrorsEscapeSoRabbitCanRetry() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        FlowExecutionState nextState = currentState(FlowExecutionStepStatus.PENDING, 0);
        nextState.setId(UUID.fromString("51000000-0000-0000-0000-000000000003"));
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class))).thenReturn(1);
        when(stateRepository.findFirstByProcessIdAndStatusOrderByPositionAsc(
                ESCALATION_ID, FlowExecutionStepStatus.PENDING))
                .thenReturn(nextState, nextState);
        when(acknowledgementService.createAcknowledgementUrl(any(Escalation.class), any(FlowExecutionState.class)))
                .thenReturn("https://alerts.example.com/acknowledge?token=test-token");
        when(notification.sendEmail(currentState, "https://alerts.example.com/acknowledge?token=test-token", null))
                .thenThrow(new RuntimeException("unexpected processing error"));

        assertThrows(RuntimeException.class, () -> consumer.deliverReadyStep(queuedState));

        assertEquals(FlowExecutionStepStatus.SENDING, currentState.getStatus());
        verify(stepSchedulingService, never()).scheduleStep(nextState);
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    // Verifies that an early ready message reschedules the saved due time.
    void earlyReadyMessageReschedulesSavedDueTime() {
        Instant dueAt = Instant.now().plusSeconds(60);
        EscalationStepReadyMessage earlyMessage = new EscalationStepReadyMessage(STEP_ID, 0, dueAt);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        currentState.setDueAt(dueAt);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));

        consumer.deliverReadyStep(earlyMessage);

        verify(stepSchedulingService).rescheduleStepAtDueTime(currentState);
        verify(stateRepository, never()).claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class));
        verifyNoInteractions(notification);
    }

    // Builds a ready-message payload for one send attempt.
    private EscalationStepReadyMessage queuedState(int sendAttemptCount) {
        return new EscalationStepReadyMessage(STEP_ID, sendAttemptCount, Instant.EPOCH);
    }

    // Builds an execution step with the requested delivery status.
    private FlowExecutionState currentState(FlowExecutionStepStatus status, int sendAttemptCount) {
        FlowExecutionState state = new FlowExecutionState();
        state.setId(STEP_ID);
        state.setProcessId(ESCALATION_ID);
        state.setStatus(status);
        state.setSendAttemptCount(sendAttemptCount);
        state.setDueAt(Instant.EPOCH);
        state.setDuration(java.time.Duration.ZERO);
        state.setUserEmail("oncall@example.com");
        return state;
    }

    // Builds an open escalation for delivery tests.
    private Escalation runningEscalation() {
        Escalation escalation = new Escalation();
        escalation.setId(ESCALATION_ID);
        escalation.setStatus(EscalationStatus.OPEN);
        return escalation;
    }
}
