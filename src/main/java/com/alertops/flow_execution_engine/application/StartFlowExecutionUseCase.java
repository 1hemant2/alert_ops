package com.alertops.flow_execution_engine.application;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alertops.flow.repository.NodeRepository;
import com.alertops.flow.repository.FlowRepository;
import com.alertops.flow.model.Flow;
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
import com.alertops.flow.model.Node;

@Service
public class StartFlowExecutionUseCase {

    NodeRepository  nodeRepository;
    FlowRepository flowRepository;
    EscalationRepository escalationRepository;
    TaskRepository taskRepository;

    StartFlowExecutionUseCase(NodeRepository nodeRepository, EscalationRepository escalationRepository,
                              TaskRepository taskRepository, FlowRepository flowRepository){
        this.nodeRepository = nodeRepository;
        this.escalationRepository = escalationRepository;
        this.taskRepository = taskRepository;
        this.flowRepository = flowRepository;
    }

    public String  execute(FlowExecutionStateService flowExecutionStateService, UUID escalationId) {
        AuthContext authContext = AuthContextHolder.get();
        if (authContext == null) {
            throw EscalationException.unauthorized();
        }
        UUID teamId = authContext.getTeamId();

        return executeForTeam(flowExecutionStateService, escalationId, teamId);
    }

    public String executeForTeam(FlowExecutionStateService flowExecutionStateService, UUID escalationId, UUID teamId) {
        if (escalationId == null) {
            throw EscalationException.invalidRequest("An escalationId is required.");
        }
        if (teamId == null) {
            throw EscalationException.forbidden("Select a team before starting an escalation.");
        }

        Escalation escalation = escalationRepository.findByIdAndTeamId(escalationId, teamId);
 
        if (escalation == null) {
          throw EscalationException.notFound();
        }
 
        EscalationStatus expectedStatus = escalation.getStatus();
        if (expectedStatus != EscalationStatus.IDLE
                && expectedStatus != EscalationStatus.SCHEDULED) {
            throw EscalationException.startConflict();
        }
        if (expectedStatus == EscalationStatus.SCHEDULED
                && escalation.getScheduledStartAt() == null) {
            throw EscalationException.startConflict();
        }
        
        UUID flowId = escalation.getFlowId();
        Flow flow = flowRepository.findByIdAndTeamId(flowId, teamId);
        if (flow == null) {
            throw EscalationException.invalidRequest("The escalation flow is not available in this team.");
        }
        List<Node> nodes = nodeRepository.findAllByFlowIdOrderByPositionAsc(flowId);

        if (nodes == null || nodes.isEmpty()) {
            throw EscalationException.invalidRequest("The escalation flow must contain at least one step.");
        }

        UUID taskId = escalation.getTaskId();
        Task task = taskRepository.findTaskByIdAndTeamId(taskId, teamId);
        if(task == null) {
            throw EscalationException.invalidRequest("The escalation task is not available in this team.");
        }

        FlowExecutionStartMode startMode = expectedStatus == EscalationStatus.IDLE
                ? FlowExecutionStartMode.IDLE
                : FlowExecutionStartMode.SCHEDULED_EARLY;
        return flowExecutionStateService.startFlowExecution(
                task, flow, nodes, escalationId, teamId, startMode);
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
                flowExecutionStateService, escalation, escalation.getTeamId(), FlowExecutionStartMode.SCHEDULED_DUE);
    }

    private String startForTeam(
            FlowExecutionStateService flowExecutionStateService,
            Escalation escalation,
            UUID teamId,
            FlowExecutionStartMode startMode) {
        if (teamId == null) {
            throw EscalationException.forbidden("Select a team before starting an escalation.");
        }
        Flow flow = flowRepository.findByIdAndTeamId(escalation.getFlowId(), teamId);
        if (flow == null) {
            throw EscalationException.invalidRequest("The escalation flow is not available in this team.");
        }
        List<Node> nodes = nodeRepository.findAllByFlowIdOrderByPositionAsc(escalation.getFlowId());
        if (nodes == null || nodes.isEmpty()) {
            throw EscalationException.invalidRequest("The escalation flow must contain at least one step.");
        }
        Task task = taskRepository.findTaskByIdAndTeamId(escalation.getTaskId(), teamId);
        if (task == null) {
            throw EscalationException.invalidRequest("The escalation task is not available in this team.");
        }
        return flowExecutionStateService.startFlowExecution(
                task, flow, nodes, escalation.getId(), teamId, startMode);
    }
}
