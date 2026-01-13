package com.alertops.flow_execution_engine.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alertops.flow.repository.NodeRepository;
import com.alertops.flow_execution_engine.model.Escalation;
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

    public void execute(FlowExecutionStateService flowExecutionStateService, UUID escalationId) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();

            Escalation escalation = escalationRepository.findByIdAndTeamId(escalationId, teamId);
 
            if (escalation == null) {
              throw new RuntimeException("Escalation not found for id: " + escalationId);
            }
 
            if(!"IDLE".equals(escalation.getStatus())) {
              throw new RuntimeException("Escalation is either IN_PROGRESS or COMPLETED for id: " + escalationId);
            }
            
            UUID flowId = escalation.getFlowId();
            List<Node> nodes = nodeRepository.findAllByFlowIdOrderByPositionAsc(flowId);

            UUID taskId = escalation.getTeamId();
            Task task = taskRepository.findByTaskId(taskId);

            flowExecutionStateService.startFlowExecution(task, nodes, escalationId);

            // saveNodeStates(escalation);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start flow execution");
        }
    }
}
