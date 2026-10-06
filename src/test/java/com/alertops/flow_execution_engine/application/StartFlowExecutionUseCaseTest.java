package com.alertops.flow_execution_engine.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.alertops.flow.model.Node;
import com.alertops.flow.model.Flow;
import com.alertops.flow.repository.FlowRepository;
import com.alertops.flow.repository.NodeRepository;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;
import com.alertops.flow_execution_engine.service.FlowExecutionStartMode;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.model.Task;
import com.alertops.task.repository.TaskRepository;

class StartFlowExecutionUseCaseTest {
    private final NodeRepository nodes = mock(NodeRepository.class);
    private final EscalationRepository escalations = mock(EscalationRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final FlowRepository flows = mock(FlowRepository.class);
    private final FlowExecutionStateService executionStateService = mock(FlowExecutionStateService.class);
    private final StartFlowExecutionUseCase useCase = new StartFlowExecutionUseCase(nodes, escalations, tasks, flows);

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void scheduledRunCanBeStartedBeforeItsSavedStartTime() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));

        Escalation escalation = new Escalation();
        escalation.setId(escalationId);
        escalation.setTeamId(teamId);
        escalation.setTaskId(taskId);
        escalation.setFlowId(flowId);
        escalation.setStatus(EscalationStatus.SCHEDULED);
        escalation.setScheduledStartAt(Instant.parse("2099-01-01T00:00:00Z"));
        Task task = new Task();
        Node node = new Node();
        Flow flow = new Flow();

        when(escalations.findByIdAndTeamId(escalationId, teamId)).thenReturn(escalation);
        when(flows.findByIdAndTeamId(flowId, teamId)).thenReturn(flow);
        when(nodes.findAllByFlowIdOrderByPositionAsc(flowId)).thenReturn(List.of(node));
        when(tasks.findTaskByIdAndTeamId(taskId, teamId)).thenReturn(task);
        when(executionStateService.startFlowExecution(
                task, flow, List.of(node), escalationId, teamId, FlowExecutionStartMode.SCHEDULED_EARLY))
                .thenReturn("started");

        assertEquals("started", useCase.execute(executionStateService, escalationId));

        verify(executionStateService).startFlowExecution(
                task, flow, List.of(node), escalationId, teamId, FlowExecutionStartMode.SCHEDULED_EARLY);
    }

    @Test
    void terminalRunCannotBeStartedAgain() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(
                UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Escalation completed = new Escalation();
        completed.setStatus(EscalationStatus.COMPLETED);
        when(escalations.findByIdAndTeamId(escalationId, teamId)).thenReturn(completed);

        EscalationException error = assertThrows(
                EscalationException.class, () -> useCase.execute(executionStateService, escalationId));

        assertEquals(409, error.getStatus().value());
        verify(nodes, never()).findAllByFlowIdOrderByPositionAsc(any());
        verify(executionStateService, never()).startFlowExecution(
                any(Task.class), any(Flow.class), anyList(), eq(escalationId), eq(teamId), any(FlowExecutionStartMode.class));
    }

    @Test
    void scheduledStartPassesTheCurrentFlowSnapshotToExecutionCreation() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        Escalation escalation = new Escalation();
        escalation.setId(escalationId);
        escalation.setTeamId(teamId);
        escalation.setTaskId(taskId);
        escalation.setFlowId(flowId);
        escalation.setStatus(EscalationStatus.SCHEDULED);
        escalation.setScheduledStartAt(Instant.parse("2020-01-01T00:00:00Z"));
        Flow flow = new Flow();
        Task task = new Task();
        Node node = new Node();

        when(escalations.findById(escalationId)).thenReturn(java.util.Optional.of(escalation));
        when(flows.findByIdAndTeamId(flowId, teamId)).thenReturn(flow);
        when(nodes.findAllByFlowIdOrderByPositionAsc(flowId)).thenReturn(List.of(node));
        when(tasks.findTaskByIdAndTeamId(taskId, teamId)).thenReturn(task);
        when(executionStateService.startFlowExecution(
                task, flow, List.of(node), escalationId, teamId, FlowExecutionStartMode.SCHEDULED_DUE))
                .thenReturn("started");

        assertEquals("started", useCase.executeScheduled(
                executionStateService, escalationId, Instant.now()));

        verify(executionStateService).startFlowExecution(
                task, flow, List.of(node), escalationId, teamId, FlowExecutionStartMode.SCHEDULED_DUE);
    }
}
