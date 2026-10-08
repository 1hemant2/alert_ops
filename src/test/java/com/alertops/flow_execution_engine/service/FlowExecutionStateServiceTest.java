package com.alertops.flow_execution_engine.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow.model.Node;
import com.alertops.flow.model.Flow;
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
    // Verifies that early scheduled starts use the explicit step scheduler.
    void manualScheduledStartClaimsWithoutCheckingSavedDueTimeAndAuditsActor() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                actorId, teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Task task = new Task();
        Flow flow = new Flow();
        Node node = new Node();
        FlowExecutionState firstState = new FlowExecutionState();

        when(escalations.claimScheduledForManualStart(escalationId, teamId)).thenReturn(1);
        when(states.findTopByProcessIdOrderByPositionAsc(escalationId)).thenReturn(firstState);

        service.startFlowExecution(
                task, flow, List.of(node), escalationId, teamId, FlowExecutionStartMode.SCHEDULED_EARLY);

        verify(escalations).claimScheduledForManualStart(escalationId, teamId);
        verify(escalations, never()).claimScheduledForStart(eq(escalationId), eq(teamId), any());
        verify(scheduling).scheduleStep(firstState);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        org.junit.jupiter.api.Assertions.assertEquals(actorId, event.getValue().userId());
        org.junit.jupiter.api.Assertions.assertEquals("owner@example.com", event.getValue().userEmail());
    }

    @Test
    // Verifies every execution step preserves task metadata and timing snapshots.
    void startSnapshotsEnabledFlowTimingForEveryExecutionStep() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Task task = new Task();
        task.setPriority("P1");
        task.setCategory("Platform");
        task.setReferenceUrl("https://example.com/requests/123");
        Flow flow = new Flow();
        flow.setResolutionTimeoutEnabled(true);
        Node first = new Node();
        first.setDuration(Duration.ofMinutes(5));
        first.setResolutionTimeout(Duration.ofMinutes(30));
        first.setEmail("same@example.com");
        Node second = new Node();
        second.setDuration(Duration.ofMinutes(10));
        second.setResolutionTimeout(Duration.ofMinutes(45));
        second.setEmail("same@example.com");
        FlowExecutionState firstState = new FlowExecutionState();

        when(escalations.claimIdleForStart(escalationId, teamId)).thenReturn(1);
        when(states.save(any(FlowExecutionState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(states.findTopByProcessIdOrderByPositionAsc(escalationId)).thenReturn(firstState);

        service.startFlowExecution(
                task, flow, List.of(first, second), escalationId, teamId, FlowExecutionStartMode.IDLE);

        ArgumentCaptor<FlowExecutionState> savedStates = ArgumentCaptor.forClass(FlowExecutionState.class);
        verify(states, org.mockito.Mockito.times(2)).save(savedStates.capture());
        List<FlowExecutionState> snapshots = savedStates.getAllValues();
        org.junit.jupiter.api.Assertions.assertTrue(snapshots.stream()
                .allMatch(FlowExecutionState::isResolutionTimeoutEnabled));
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(Duration.ofMinutes(30), Duration.ofMinutes(45)),
                snapshots.stream().map(FlowExecutionState::getResolutionTimeout).toList());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("same@example.com", "same@example.com"),
                snapshots.stream().map(FlowExecutionState::getUserEmail).toList());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("P1", "P1"), snapshots.stream().map(FlowExecutionState::getTaskPriority).toList());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("Platform", "Platform"), snapshots.stream().map(FlowExecutionState::getTaskCategory).toList());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("https://example.com/requests/123", "https://example.com/requests/123"),
                snapshots.stream().map(FlowExecutionState::getTaskReferenceUrl).toList());
    }

    @Test
    void startSnapshotsDisabledFlowWithoutResolutionTimeouts() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Task task = new Task();
        Flow flow = new Flow();
        flow.setResolutionTimeoutEnabled(false);
        Node node = new Node();
        node.setResolutionTimeout(Duration.ofMinutes(30));
        FlowExecutionState firstState = new FlowExecutionState();

        when(escalations.claimIdleForStart(escalationId, teamId)).thenReturn(1);
        when(states.save(any(FlowExecutionState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(states.findTopByProcessIdOrderByPositionAsc(escalationId)).thenReturn(firstState);

        service.startFlowExecution(
                task, flow, List.of(node), escalationId, teamId, FlowExecutionStartMode.IDLE);

        ArgumentCaptor<FlowExecutionState> savedState = ArgumentCaptor.forClass(FlowExecutionState.class);
        verify(states).save(savedState.capture());
        org.junit.jupiter.api.Assertions.assertFalse(savedState.getValue().isResolutionTimeoutEnabled());
        org.junit.jupiter.api.Assertions.assertNull(savedState.getValue().getResolutionTimeout());
    }

    @Test
    // Verifies that failed snapshot persistence never schedules the first step.
    void failedSnapshotSaveDoesNotScheduleTheFirstStep() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        when(escalations.claimIdleForStart(escalationId, teamId)).thenReturn(1);
        doThrow(new IllegalStateException("database unavailable"))
                .when(states).save(any(FlowExecutionState.class));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.startFlowExecution(
                new Task(), new Flow(), List.of(new Node()), escalationId, teamId, FlowExecutionStartMode.IDLE));

        verify(scheduling, never()).scheduleStep(any(FlowExecutionState.class));
    }
}
