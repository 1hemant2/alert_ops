package com.alertops.flow_execution_engine.application;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alertops.flow.repository.NodeRepository;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.service.FlowExecutionStateService;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.model.Task;
import com.alertops.task.repository.TaskRepository;
import com.alertops.flow.model.Node;

@Service
public class StartFlowExecutionUseCase {

    NodeRepository  nodeRepository;
    EscalationRepository escalationRepository;
    TaskRepository taskRepository;

    StartFlowExecutionUseCase(NodeRepository nodeRepository, EscalationRepository escalationRepository,  TaskRepository taskRepository){
        this.nodeRepository = nodeRepository;
        this.escalationRepository = escalationRepository;
        this.taskRepository = taskRepository;
    }

    public String  execute(FlowExecutionStateService flowExecutionStateService, UUID escalationId) {
        AuthContext authContext = AuthContextHolder.get();
        UUID teamId = authContext.getTeamId();

        return executeForTeam(flowExecutionStateService, escalationId, teamId);
    }

    public String executeForTeam(FlowExecutionStateService flowExecutionStateService, UUID escalationId, UUID teamId) {
        if (teamId == null) {
            throw new RuntimeException("Team is required to start an escalation");
        }

        Escalation escalation = escalationRepository.findByIdAndTeamId(escalationId, teamId);
 
        if (escalation == null) {
          throw new RuntimeException("Escalation not found for id: " + escalationId);
        }
 
        if (escalation.getStatus() != EscalationStatus.IDLE) {
          throw EscalationException.startConflict();
        }
        
        UUID flowId = escalation.getFlowId();
        List<Node> nodes = nodeRepository.findAllByFlowIdOrderByPositionAsc(flowId);

        if(nodes.size() == 0) {
            throw new RuntimeException("Must be at least single node to start the escalation");
        }

        UUID taskId = escalation.getTaskId();
        Task task = taskRepository.findTaskByIdAndTeamId(taskId, teamId);
        if(task == null) {
            throw new RuntimeException("TaskId can't be empty, please create a new escaltion");
        }

        return flowExecutionStateService.startFlowExecution(task, nodes, escalationId, teamId);
    }

    public String executeScheduled(
            FlowExecutionStateService flowExecutionStateService,
            UUID escalationId,
            Instant now) {
        if (escalationId == null || now == null) {
            throw EscalationException.startConflict();
        }
        Escalation escalation = escalationRepository.findById(escalationId).orElse(null);
        if (escalation == null || escalation.getStatus() != EscalationStatus.SCHEDULED
                || escalation.getScheduledStartAt() == null
                || escalation.getScheduledStartAt().isAfter(now)) {
            throw EscalationException.startConflict();
        }
        return startForTeam(
                flowExecutionStateService, escalation, escalation.getTeamId(), EscalationStatus.SCHEDULED);
    }

    private String startForTeam(
            FlowExecutionStateService flowExecutionStateService,
            Escalation escalation,
            UUID teamId,
            EscalationStatus expectedStatus) {
        if (teamId == null) {
            throw new RuntimeException("Team is required to start an escalation");
        }
        List<Node> nodes = nodeRepository.findAllByFlowIdOrderByPositionAsc(escalation.getFlowId());
        if (nodes.isEmpty()) {
            throw new RuntimeException("Must be at least single node to start the escalation");
        }
        Task task = taskRepository.findTaskByIdAndTeamId(escalation.getTaskId(), teamId);
        if (task == null) {
            throw new RuntimeException("TaskId can't be empty, please create a new escaltion");
        }
        return flowExecutionStateService.startFlowExecution(
                task, nodes, escalation.getId(), teamId, expectedStatus);
    }
}
