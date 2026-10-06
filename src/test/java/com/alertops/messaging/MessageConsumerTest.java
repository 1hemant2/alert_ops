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
    private final MessageConsumer consumer = new MessageConsumer(
            stateRepository, escalationRepository, notification, stepSchedulingService, acknowledgementService);

    @Test
    void ignoresMessageFromOlderSendAttempt() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 1);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));

        consumer.onMessage(queuedState);

        verify(stateRepository, never()).claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class));
        verifyNoInteractions(notification, escalationRepository, stepSchedulingService);
    }

    @Test
    void ignoresDeliveryWhenAnotherConsumerHasAlreadyClaimedTheStep() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class))).thenReturn(0);

        consumer.onMessage(queuedState);

        verifyNoInteractions(notification, stepSchedulingService);
        verify(stateRepository, never()).save(currentState);
    }

    @Test
    void sendsOnlyOnceWhenTheSameMessageArrivesAfterTheStepFinished() {
        EscalationStepReadyMessage queuedState = queuedState(0);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class))).thenReturn(1);
        when(acknowledgementService.createAcknowledgementUrl(any(Escalation.class), eq("oncall@example.com")))
                .thenReturn("https://alerts.example.com/acknowledge?token=test-token");
        when(notification.sendEmail(currentState, "https://alerts.example.com/acknowledge?token=test-token")).thenReturn(true);

        consumer.onMessage(queuedState);
        consumer.onMessage(queuedState);

        verify(notification, times(1)).sendEmail(currentState, "https://alerts.example.com/acknowledge?token=test-token");
        verify(stateRepository, times(1)).claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class));
        assertEquals(FlowExecutionStepStatus.SENT, currentState.getStatus());
        assertEquals(1, currentState.getSendAttemptCount());
    }

    @Test
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
        when(acknowledgementService.createAcknowledgementUrl(any(Escalation.class), eq("oncall@example.com")))
                .thenReturn("https://alerts.example.com/acknowledge?token=test-token");
        when(notification.sendEmail(currentState, "https://alerts.example.com/acknowledge?token=test-token"))
                .thenThrow(new RuntimeException("unexpected processing error"));

        assertThrows(RuntimeException.class, () -> consumer.onMessage(queuedState));

        assertEquals(FlowExecutionStepStatus.SENDING, currentState.getStatus());
        verify(stepSchedulingService, never()).schedule(nextState);
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    void earlyReadyMessageRearmsSavedDueTimeWithoutClaimingOrSending() {
        Instant dueAt = Instant.now().plusSeconds(60);
        EscalationStepReadyMessage earlyMessage = new EscalationStepReadyMessage(STEP_ID, 0, dueAt);
        FlowExecutionState currentState = currentState(FlowExecutionStepStatus.SCHEDULED, 0);
        currentState.setDueAt(dueAt);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findByIdForUpdate(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));

        consumer.onMessage(earlyMessage);

        verify(stepSchedulingService).rescheduleStepAtDueTime(currentState);
        verify(stateRepository, never()).claimForDelivery(eq(STEP_ID), eq(0), any(Instant.class));
        verifyNoInteractions(notification);
    }

    private EscalationStepReadyMessage queuedState(int sendAttemptCount) {
        return new EscalationStepReadyMessage(STEP_ID, sendAttemptCount, Instant.EPOCH);
    }

    private FlowExecutionState currentState(FlowExecutionStepStatus status, int sendAttemptCount) {
        FlowExecutionState state = new FlowExecutionState();
        state.setId(STEP_ID);
        state.setProcessId(ESCALATION_ID);
        state.setStatus(status);
        state.setSendAttemptCount(sendAttemptCount);
        state.setDueAt(Instant.EPOCH);
        state.setUserEmail("oncall@example.com");
        return state;
    }

    private Escalation runningEscalation() {
        Escalation escalation = new Escalation();
        escalation.setId(ESCALATION_ID);
        escalation.setStatus(EscalationStatus.OPEN);
        return escalation;
    }
}
