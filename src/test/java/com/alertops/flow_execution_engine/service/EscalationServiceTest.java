package com.alertops.flow_execution_engine.service;

import com.alertops.flow.repository.FlowRepository;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class EscalationServiceTest {
    private final EscalationRepository escalationRepository = mock(EscalationRepository.class);
    private final FlowExecutionStateRepository executionStateRepository = mock(FlowExecutionStateRepository.class);
    private final FlowRepository flowRepository = mock(FlowRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final EscalationService service = new EscalationService(
            escalationRepository, executionStateRepository, flowRepository, taskRepository);

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void executionStatesAreReturnedOnlyForEscalationInSelectedTeam() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        when(escalationRepository.findByIdAndTeamId(escalationId, teamId)).thenReturn(null);

        assertNull(service.getExecutionStates(escalationId));

        verifyNoInteractions(executionStateRepository);
    }

    @Test
    void executionStateResponseContainsOnlyDisplayFieldsInPositionOrder() {
        UUID teamId = UUID.randomUUID();
        UUID escalationId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        when(escalationRepository.findByIdAndTeamId(escalationId, teamId)).thenReturn(new Escalation());
        FlowExecutionState state = new FlowExecutionState();
        state.setNodeId(nodeId);
        state.setPosition(BigInteger.valueOf(1000));
        state.setUserEmail("oncall@example.com");
        state.setExecutionState("ACTIVE");
        state.setNotificationState("NOT_SENT");
        state.setSendAttemptCount(1);
        when(executionStateRepository.findAllByProcessIdOrderByPositionAsc(escalationId)).thenReturn(List.of(state));

        var result = service.getExecutionStates(escalationId);

        assertEquals(1, result.size());
        assertEquals(nodeId, result.get(0).nodeId());
        assertEquals(BigInteger.valueOf(1000), result.get(0).position());
        assertEquals("oncall@example.com", result.get(0).userEmail());
        assertEquals("ACTIVE", result.get(0).executionState());
        assertEquals("NOT_SENT", result.get(0).notificationState());
        assertEquals(1, result.get(0).sendAttemptCount());
    }

    @Test
    void escalationCannotBindTaskOrFlowFromAnotherTeam() {
        UUID teamId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.createEscalation("Test", taskId, flowId));

        verify(escalationRepository, never()).save(any(Escalation.class));
    }
}
