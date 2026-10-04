package com.alertops.flow_execution_engine.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.flow.model.Node;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
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
    AuditService auditService;

    public FlowExecutionStateService(
            FlowExecutionStateRepository flowExecutionStateRepository,
            EscalationRepository escalationRepository,
            StepSchedulingService stepSchedulingService,
            AuditService auditService) {
         this.flowExecutionStateRepository = flowExecutionStateRepository;
         this.escalationRepository = escalationRepository;
         this.stepSchedulingService = stepSchedulingService;
         this.auditService = Objects.requireNonNull(auditService, "auditService");
    }

    @Transactional
    public String startFlowExecution(Task task, List<Node> nodes, UUID escalationId, UUID teamId) {
        return startFlowExecution(task, nodes, escalationId, teamId, EscalationStatus.IDLE);
    }

    @Transactional
    public String startFlowExecution(
            Task task,
            List<Node> nodes,
            UUID escalationId,
            UUID teamId,
            EscalationStatus expectedStatus) {
        try {
            int claimedRows = expectedStatus == EscalationStatus.SCHEDULED
                    ? escalationRepository.claimScheduledForStart(escalationId, teamId, java.time.Instant.now())
                    : escalationRepository.claimIdleForStart(escalationId, teamId);
            if (claimedRows != 1) {
                throw EscalationException.startConflict();
            }
            EscalationStatus fromStatus = expectedStatus == null ? EscalationStatus.IDLE : expectedStatus;
            auditService.record(new AuditEvent(
                    AuditEntityType.ESCALATION, escalationId, AuditAction.STARTED, fromStatus.name(),
                    EscalationStatus.OPEN.name(), null, null, Instant.now(), null, null));

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
