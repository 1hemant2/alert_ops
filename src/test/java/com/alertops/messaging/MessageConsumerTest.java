package com.alertops.messaging;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
    private final MessagePublisher messagePublisher = mock(MessagePublisher.class);
    private final MessageConsumer consumer = new MessageConsumer(
            stateRepository, escalationRepository, notification, messagePublisher);

    @Test
    void ignoresMessageFromOlderSendAttempt() {
        FlowExecutionState queuedState = queuedState(0);
        FlowExecutionState currentState = currentState("ACTIVE", "NOT_SENT", 1);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));

        consumer.onMessage(queuedState);

        verify(stateRepository, never()).claimForDelivery(STEP_ID, 0);
        verifyNoInteractions(notification, escalationRepository, messagePublisher);
    }

    @Test
    void ignoresDeliveryWhenAnotherConsumerHasAlreadyClaimedTheStep() {
        FlowExecutionState queuedState = queuedState(0);
        FlowExecutionState currentState = currentState("ACTIVE", "NOT_SENT", 0);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findById(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(STEP_ID, 0)).thenReturn(0);

        consumer.onMessage(queuedState);

        verifyNoInteractions(notification, messagePublisher);
        verify(stateRepository, never()).save(currentState);
    }

    @Test
    void sendsOnlyOnceWhenTheSameMessageArrivesAfterTheStepFinished() {
        FlowExecutionState queuedState = queuedState(0);
        FlowExecutionState currentState = currentState("ACTIVE", "NOT_SENT", 0);
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findById(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(STEP_ID, 0)).thenReturn(1);
        when(notification.sendEmail(currentState)).thenReturn(true);

        consumer.onMessage(queuedState);
        consumer.onMessage(queuedState);

        verify(notification, times(1)).sendEmail(currentState);
        verify(stateRepository, times(1)).claimForDelivery(STEP_ID, 0);
        assertEquals("TERMINAL", currentState.getExecutionState());
        assertEquals("SENT", currentState.getNotificationState());
        assertEquals(1, currentState.getSendAttemptCount());
    }

    @Test
    void schedulesTheNextPendingStepWhenProcessingThrows() {
        FlowExecutionState queuedState = queuedState(0);
        FlowExecutionState currentState = currentState("ACTIVE", "NOT_SENT", 0);
        FlowExecutionState nextState = currentState("PENDING", "NOT_SENT", 0);
        nextState.setId(UUID.fromString("51000000-0000-0000-0000-000000000003"));
        when(stateRepository.findById(STEP_ID)).thenReturn(Optional.of(currentState));
        when(escalationRepository.findById(ESCALATION_ID)).thenReturn(Optional.of(runningEscalation()));
        when(stateRepository.claimForDelivery(STEP_ID, 0)).thenReturn(1);
        when(stateRepository.findFirstByProcessIdAndExecutionStateOrderByPositionAsc(ESCALATION_ID, "PENDING"))
                .thenReturn(nextState, nextState);
        when(notification.sendEmail(currentState)).thenThrow(new RuntimeException("unexpected processing error"));

        consumer.onMessage(queuedState);

        assertEquals("FAILED", currentState.getExecutionState());
        assertEquals("FAILED", currentState.getNotificationState());
        verify(messagePublisher).publishWithDelay(nextState);
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    private FlowExecutionState queuedState(int sendAttemptCount) {
        FlowExecutionState state = new FlowExecutionState();
        state.setId(STEP_ID);
        state.setSendAttemptCount(sendAttemptCount);
        return state;
    }

    private FlowExecutionState currentState(String executionState, String notificationState, int sendAttemptCount) {
        FlowExecutionState state = new FlowExecutionState();
        state.setId(STEP_ID);
        state.setProcessId(ESCALATION_ID);
        state.setExecutionState(executionState);
        state.setNotificationState(notificationState);
        state.setSendAttemptCount(sendAttemptCount);
        state.setUserEmail("oncall@example.com");
        return state;
    }

    private Escalation runningEscalation() {
        Escalation escalation = new Escalation();
        escalation.setId(ESCALATION_ID);
        escalation.setStatus("RUNNING");
        return escalation;
    }
}
