package com.alertops.flow_execution_engine.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.flow.model.Node;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.messaging.MessagePublisher;
import com.alertops.task.model.Task;

@Service
public class FlowExecutionStateService {
    FlowExecutionStateRepository flowExecutionStateRepository;
    EscalationRepository escalationRepository;
    MessagePublisher messagePublisher;

    public FlowExecutionStateService(FlowExecutionStateRepository flowExecutionStateRepository, EscalationRepository escalationRepository, MessagePublisher messagePublisher) {
         this.flowExecutionStateRepository = flowExecutionStateRepository;
         this.escalationRepository = escalationRepository;
         this.messagePublisher = messagePublisher;
    }

    @Transactional
    public String startFlowExecution(Task task, List<Node> nodes, UUID escalationId) {
        try {
            escalationRepository.updateStatus(escalationId, "RUNNING");

            for(Node node : nodes) {
                FlowExecutionState flowExecutionState = new FlowExecutionState();
                flowExecutionState.setExecutionState("PENDING");
                flowExecutionState.setNotificationState("NOT_SENT");
                flowExecutionState.setTaskDetails(task.getDescription());
                flowExecutionState.setNodeId(node.getId());
                flowExecutionState.setUserEmail(node.getEmail());
                flowExecutionState.setDuration(node.getDuration());
                flowExecutionState.setPosition(node.getPosition());
                flowExecutionState.setProcessId(escalationId);
                flowExecutionState.setTaskId(node.getId());
                flowExecutionStateRepository.save(flowExecutionState);
            }
            FlowExecutionState flowExecutionState = flowExecutionStateRepository.findTopByProcessIdOrderByPositionAsc(escalationId);
            messagePublisher.publishWithDelay(flowExecutionState);
            return "Started Flow Execution for escalationId: " + escalationId;
        } catch (RuntimeException e) {
            throw e;
        }
    }
}
