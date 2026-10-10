package com.alertops.flow_execution_engine.service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.flow.model.Node;
import com.alertops.flow.model.Flow;
import com.alertops.flow_execution_engine.exception.EscalationException;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.messaging.StepSchedulingService;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.task.model.Task;

@Service
public class FlowExecutionStateService {
    FlowExecutionStateRepository flowExecutionStateRepository;
    EscalationRepository escalationRepository;
    StepSchedulingService stepSchedulingService;
    AuditService auditService;
    private final Clock clock;

    // Creates the service that starts an escalation and schedules its first alert.
    public FlowExecutionStateService(
            FlowExecutionStateRepository flowExecutionStateRepository,
            EscalationRepository escalationRepository,
            StepSchedulingService stepSchedulingService,
            AuditService auditService,
            Clock clock) {
         this.flowExecutionStateRepository = flowExecutionStateRepository;
         this.escalationRepository = escalationRepository;
         this.stepSchedulingService = stepSchedulingService;
         this.auditService = Objects.requireNonNull(auditService, "auditService");
         this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    // Creates execution snapshots and schedules the first runtime step.
    public String startFlowExecution(
            Task task,
            Flow flow,
            List<Node> nodes,
            UUID escalationId,
            UUID teamId,
            FlowExecutionStartMode startMode) {
        try {
            if (startMode == null) {
                throw EscalationException.invalidRequest("A flow execution start mode is required.");
            }
            int claimedRows = claimStart(escalationId, teamId, startMode);
            if (claimedRows != 1) {
                throw EscalationException.startConflict();
            }
            EscalationStatus fromStatus = startMode == FlowExecutionStartMode.IDLE
                    ? EscalationStatus.IDLE
                    : EscalationStatus.SCHEDULED;
            AuthContext authContext = AuthContextHolder.get();
            UUID actorId = authContext == null ? null : authContext.getUserId();
            String actorEmail = authContext == null ? null : authContext.getEmail();
            auditService.record(new AuditEvent(
                    AuditEntityType.ESCALATION, escalationId, AuditAction.STARTED, fromStatus.name(),
                    EscalationStatus.OPEN.name(), actorId, actorEmail, clock.instant(), null, null));

            if (flow == null) {
                throw EscalationException.invalidRequest("The escalation flow is not available.");
            }
            if (nodes == null || nodes.isEmpty()) {
                throw EscalationException.invalidRequest("The escalation flow must contain at least one step.");
            }

            for (Node node : nodes) {
                if (node == null || (flow.isResolutionTimeoutEnabled() && node.getResolutionTimeout() == null)) {
                    throw EscalationException.invalidRequest(
                            "Every node requires a positive resolution timeout when the flow is enabled.");
                }
                if (flow.isResolutionTimeoutEnabled()
                        && (node.getResolutionTimeout().isZero() || node.getResolutionTimeout().isNegative())) {
                    throw EscalationException.invalidRequest(
                            "Every node requires a positive resolution timeout when the flow is enabled.");
                }
                FlowExecutionState flowExecutionState = new FlowExecutionState();
                flowExecutionState.setStatus(FlowExecutionStepStatus.PENDING);
                flowExecutionState.setTaskDetails(task.getDescription());
                flowExecutionState.setTaskName(task.getName());
                flowExecutionState.setTaskSource(task.getSource());
                flowExecutionState.setTaskPriority(task.getPriority());
                flowExecutionState.setTaskCategory(task.getCategory());
                flowExecutionState.setTaskReferenceUrl(task.getReferenceUrl());
                flowExecutionState.setNodeId(node.getId());
                flowExecutionState.setUserEmail(node.getEmail());
                flowExecutionState.setDuration(node.getDuration());
                flowExecutionState.setPosition(node.getPosition());
                flowExecutionState.setProcessId(escalationId);
                flowExecutionState.setTaskId(task.getId());
                flowExecutionState.setResolutionTimeoutEnabled(flow.isResolutionTimeoutEnabled());
                flowExecutionState.setResolutionTimeout(flow.isResolutionTimeoutEnabled()
                        ? node.getResolutionTimeout()
                        : null);
                flowExecutionStateRepository.save(flowExecutionState);
            }
            FlowExecutionState flowExecutionState = flowExecutionStateRepository.findTopByProcessIdOrderByPositionAsc(escalationId);
            stepSchedulingService.scheduleStepImmediately(flowExecutionState, clock.instant());
            return "Started Flow Execution for escalationId: " + escalationId;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    // Claims the escalation transition required by the selected start mode.
    private int claimStart(UUID escalationId, UUID teamId, FlowExecutionStartMode startMode) {
        return switch (startMode) {
            case IDLE -> escalationRepository.claimIdleForStart(escalationId, teamId);
            case SCHEDULED_DUE -> escalationRepository.claimScheduledForStart(
                    escalationId, teamId, clock.instant());
            case SCHEDULED_EARLY -> escalationRepository.claimScheduledForManualStart(escalationId, teamId);
        };
    }
}
