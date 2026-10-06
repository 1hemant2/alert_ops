package com.alertops.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;

class StepSchedulingServiceTest {
    private final FlowExecutionStateRepository states = mock(FlowExecutionStateRepository.class);
    private final EscalationRepository escalations = mock(EscalationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final StepSchedulingService service = new StepSchedulingService(states, escalations, events, 10);

    @Test
    void scheduleRejectsNullStateClearly() {
        assertThrows(IllegalArgumentException.class, () -> service.schedule(null));
    }

    @Test
    void scheduleUsesOnePersistedScheduledStatus() {
        FlowExecutionState state = new FlowExecutionState();
        state.setStatus(FlowExecutionStepStatus.PENDING);
        state.setDuration(java.time.Duration.ZERO);
        when(states.save(any(FlowExecutionState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.schedule(state);

        assertEquals(FlowExecutionStepStatus.SCHEDULED, state.getStatus());
        assertTrue(state.isPublicationPending());
        verify(events).publishEvent(any(EscalationStepSchedule.class));
    }

    @Test
    void publicationCheckIgnoresStateWithoutProcessId() {
        UUID stepId = UUID.randomUUID();
        Instant dueAt = Instant.parse("2026-01-01T00:00:00Z");
        FlowExecutionState current = new FlowExecutionState();
        current.setId(stepId);
        current.setStatus(FlowExecutionStepStatus.SCHEDULED);
        current.setPublicationPending(true);
        current.setSendAttemptCount(0);
        current.setDueAt(dueAt);
        when(states.findById(stepId)).thenReturn(Optional.of(current));

        boolean pending = service.isStepStillPendingForPublication(
                new EscalationStepSchedule(stepId, 0, dueAt));

        assertFalse(pending);
        verifyNoInteractions(escalations);
    }

    @Test
    void rescheduleIgnoresNullState() {
        service.rescheduleStepAtDueTime(null);

        verifyNoInteractions(states, escalations, events);
    }

    @Test
    void rescheduleIgnoresCurrentStateWithoutDueTime() {
        UUID stepId = UUID.randomUUID();
        FlowExecutionState requested = new FlowExecutionState();
        requested.setId(stepId);
        requested.setDueAt(Instant.parse("2026-01-01T00:00:00Z"));

        FlowExecutionState current = new FlowExecutionState();
        current.setId(stepId);
        current.setStatus(FlowExecutionStepStatus.SCHEDULED);
        when(states.findById(stepId)).thenReturn(Optional.of(current));

        service.rescheduleStepAtDueTime(requested);

        verify(escalations, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any());
        verify(states, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
