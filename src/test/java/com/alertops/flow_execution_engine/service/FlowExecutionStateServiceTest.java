package com.alertops.flow_execution_engine.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow.model.Node;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.messaging.StepSchedulingService;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.model.Task;

class FlowExecutionStateServiceTest {
    private final FlowExecutionStateRepository states = mock(FlowExecutionStateRepository.class);
    private final EscalationRepository escalations = mock(EscalationRepository.class);
    private final StepSchedulingService scheduling = mock(StepSchedulingService.class);
    private final AuditService audit = mock(AuditService.class);
    private final FlowExecutionStateService service = new FlowExecutionStateService(
            states, escalations, scheduling, audit);

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void manualScheduledStartClaimsWithoutCheckingSavedDueTimeAndAuditsActor() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                actorId, teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Task task = new Task();
        Node node = new Node();
        FlowExecutionState firstState = new FlowExecutionState();

        when(escalations.claimScheduledForManualStart(escalationId, teamId)).thenReturn(1);
        when(states.findTopByProcessIdOrderByPositionAsc(escalationId)).thenReturn(firstState);

        service.startFlowExecution(
                task, List.of(node), escalationId, teamId, FlowExecutionStartMode.SCHEDULED_EARLY);

        verify(escalations).claimScheduledForManualStart(escalationId, teamId);
        verify(escalations, never()).claimScheduledForStart(eq(escalationId), eq(teamId), any());
        verify(scheduling).schedule(firstState);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        org.junit.jupiter.api.Assertions.assertEquals(actorId, event.getValue().userId());
        org.junit.jupiter.api.Assertions.assertEquals("owner@example.com", event.getValue().userEmail());
    }
}
