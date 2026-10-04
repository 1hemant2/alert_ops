package com.alertops.flow_execution_engine.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.flow.model.Node;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.messaging.StepSchedulingService;
import com.alertops.task.model.Task;

@Service
public class FlowExecutionStateService {
    FlowExecutionStateRepository flowExecutionStateRepository;
    EscalationRepository escalationRepository;
    StepSchedulingService stepSchedulingService;

    public FlowExecutionStateService(FlowExecutionStateRepository flowExecutionStateRepository, EscalationRepository escalationRepository, StepSchedulingService stepSchedulingService) {
         this.flowExecutionStateRepository = flowExecutionStateRepository;
         this.escalationRepository = escalationRepository;
         this.stepSchedulingService = stepSchedulingService;
    }

    @Transactional
    public String startFlowExecution(Task task, List<Node> nodes, UUID escalationId, UUID teamId) {
        return startFlowExecution(task, nodes, escalationId, teamId, "IDLE");
    }

    @Transactional
    public String startFlowExecution(
            Task task,
            List<Node> nodes,
            UUID escalationId,
            UUID teamId,
            String expectedStatus) {
        try {
            int claimedRows = "SCHEDULED".equals(expectedStatus)
                    ? escalationRepository.claimScheduledForStart(escalationId, teamId, java.time.Instant.now())
                    : escalationRepository.claimIdleForStart(escalationId, teamId);
            if (claimedRows != 1) {
                throw EscalationException.startConflict();
            }

            for(Node node : nodes) {
                FlowExecutionState flowExecutionState = new FlowExecutionState();
                flowExecutionState.setExecutionState("PENDING");
                flowExecutionState.setNotificationState("NOT_SENT");
                flowExecutionState.setTaskDetails(task.getDescription());
                flowExecutionState.setTaskName(task.getName());
                flowExecutionState.setTaskSource(task.getSource());
                flowExecutionState.setNodeId(node.getId());
                flowExecutionState.setUserEmail(node.getEmail());
                flowExecutionState.setDuration(node.getDuration());
                flowExecutionState.setPosition(node.getPosition());
                flowExecutionState.setProcessId(escalationId);
                flowExecutionState.setTaskId(task.getId());
                flowExecutionStateRepository.save(flowExecutionState);
            }
            FlowExecutionState flowExecutionState = flowExecutionStateRepository.findTopByProcessIdOrderByPositionAsc(escalationId);
            stepSchedulingService.schedule(flowExecutionState);
            return "Started Flow Execution for escalationId: " + escalationId;
        } catch (RuntimeException e) {
            throw e;
        }
    }
}
