package com.alertops.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.time.temporal.ChronoUnit;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.audit.model.AuditAction;
import com.alertops.audit.model.AuditEntityType;
import com.alertops.audit.model.AuditEvent;
import com.alertops.audit.service.AuditService;
import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.EscalationResolutionType;
import com.alertops.flow_execution_engine.model.EscalationStatus;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.model.FlowExecutionStepStatus;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;
import com.alertops.flow_execution_engine.service.EscalationAcknowledgementService;

@Component
public class MessageConsumer {
   private final Notification notification;
   private final FlowExecutionStateRepository flowExecutionStateRepository;
   private final EscalationRepository escalationRepository;
   private final StepSchedulingService stepSchedulingService;
   private final EscalationAcknowledgementService acknowledgementService;
   private final EscalationTimeoutService timeoutService;
   private final AuditService auditService;
   private final Clock clock;


    // Creates the RabbitMQ consumer that delivers scheduled escalation steps.
    public MessageConsumer(FlowExecutionStateRepository flowExecutionStateRepository, EscalationRepository escalationRepository,
        Notification notification, StepSchedulingService stepSchedulingService,
        EscalationAcknowledgementService acknowledgementService,
        EscalationTimeoutService timeoutService,
        AuditService auditService,
        Clock clock
    ) {
        this.flowExecutionStateRepository = flowExecutionStateRepository;
        this.escalationRepository = escalationRepository;
        this.notification = notification;
        this.stepSchedulingService = stepSchedulingService;
        this.acknowledgementService = acknowledgementService;
        this.timeoutService = timeoutService;
        this.auditService = auditService;
        this.clock = clock;
    }


    @RabbitListener(queues = RabbitMqConfig.ESCALATION_STEP_READY_QUEUE)
    @Transactional
    // Claims and delivers one ready escalation step.
    public void deliverReadyStep(EscalationStepReadyMessage readyMessage) {
        if (readyMessage == null || readyMessage.stepId() == null || readyMessage.dueAt() == null) {
            return;
        }

        FlowExecutionState currentState = flowExecutionStateRepository.findById(readyMessage.stepId()).orElse(null);
        if (currentState == null
                || currentState.getSendAttemptCount() != readyMessage.sendAttemptCount()
                || !readyMessage.dueAt().equals(currentState.getDueAt())
                || currentState.getStatus() != FlowExecutionStepStatus.SCHEDULED
                || currentState.getProcessId() == null) {
            return;
        }

        // Lock the run while checking and sending so acknowledgement cannot race a new step.
        Escalation escalation = escalationRepository.findByIdForUpdate(currentState.getProcessId()).orElse(null);
        if (escalation == null || escalation.getStatus() != EscalationStatus.OPEN) {
            return;
        }

        if (currentState.getDueAt().isAfter(clock.instant())) {
            // Do not send an early or legacy message; schedule it for the saved due time.
            stepSchedulingService.rescheduleStepAtDueTime(currentState);
            return;
        }

        int claimedRows = flowExecutionStateRepository.claimForDelivery(
                currentState.getId(), readyMessage.sendAttemptCount(), readyMessage.dueAt());
        if (claimedRows != 1) {
            return;
        }

        // The bulk update bypasses the persistence context, so keep the managed copy aligned.
        currentState.setStatus(FlowExecutionStepStatus.SENDING);
        currentState.setPublicationPending(false);
        deliverStepNotification(currentState, escalation);
    }


    // Sends one step notification and applies its delivery outcome.
    private void deliverStepNotification(FlowExecutionState flowExecutionState, Escalation escalation) {
        boolean retryOnFailureEnabled = flowExecutionState.isRetryOnFailureEnabled();
        int maxRetryAttempts = flowExecutionState.getMaxRetryAttempts();
        int sendAttemptCount = flowExecutionState.getSendAttemptCount();
        FlowExecutionState nextNode = flowExecutionStateRepository
                .findFirstByProcessIdAndStatusOrderByPositionAsc(
                        flowExecutionState.getProcessId(), FlowExecutionStepStatus.PENDING);

        String acknowledgementUrl = acknowledgementService.createAcknowledgementUrl(
                escalation, flowExecutionState);
        String escalateNowUrl = nextNode == null
                ? null
                : acknowledgementService.createEscalateNowUrl(escalation, flowExecutionState, nextNode);
        boolean mailSent = notification.sendEmail(flowExecutionState, acknowledgementUrl, escalateNowUrl);
        // Persist total attempts, including successful SMTP submissions.
        flowExecutionState.setSendAttemptCount(sendAttemptCount + 1);
        if (mailSent) {
            flowExecutionState.setStatus(FlowExecutionStepStatus.SENT);
            if (nextNode == null) {
                Instant acknowledgementTimeoutAt = calculateAcknowledgementTimeoutAt(
                        flowExecutionState.getDuration());
                flowExecutionState.setDueAt(acknowledgementTimeoutAt);
                flowExecutionStateRepository.save(flowExecutionState);
                recordStepAudit(
                        escalation,
                        AuditAction.NOTIFICATION_SENT,
                        FlowExecutionStepStatus.SENDING,
                        FlowExecutionStepStatus.SENT,
                        flowExecutionState,
                        "acknowledgementTimeoutAt=" + flowExecutionState.getDueAt());
                timeoutService.scheduleAcknowledgementTimeout(
                        escalation.getId(), flowExecutionState.getId(), acknowledgementTimeoutAt);
            } else {
                FlowExecutionState scheduledNextNode = Objects.requireNonNull(
                        stepSchedulingService.scheduleStep(nextNode), "Scheduled next response step is required");
                // The next step's durable dueAt is also this sent step's acknowledgement boundary.
                flowExecutionState.setDueAt(Objects.requireNonNull(
                        scheduledNextNode.getDueAt(), "Scheduled next response step requires a due time"));
                flowExecutionStateRepository.save(flowExecutionState);
                recordStepAudit(
                        escalation,
                        AuditAction.NOTIFICATION_SENT,
                        FlowExecutionStepStatus.SENDING,
                        FlowExecutionStepStatus.SENT,
                        flowExecutionState,
                        "acknowledgementTimeoutAt=" + flowExecutionState.getDueAt());
            }
        } else if (retryOnFailureEnabled && sendAttemptCount < maxRetryAttempts) {
            flowExecutionStateRepository.save(flowExecutionState);
            FlowExecutionState retryState = Objects.requireNonNull(
                    stepSchedulingService.scheduleStep(flowExecutionState),
                    "Scheduled retry step is required");
            recordStepAudit(
                    escalation,
                    AuditAction.NOTIFICATION_FAILED,
                    FlowExecutionStepStatus.SENDING,
                    FlowExecutionStepStatus.SCHEDULED,
                    flowExecutionState,
                    "failure=DELIVERY_FAILED;retryAt=" + retryState.getDueAt());
            recordStepAudit(
                    escalation,
                    AuditAction.NOTIFICATION_RETRY_SCHEDULED,
                    FlowExecutionStepStatus.SENDING,
                    FlowExecutionStepStatus.SCHEDULED,
                    flowExecutionState,
                    "nextAttempt=" + flowExecutionState.getSendAttemptCount()
                            + ";retryAt=" + retryState.getDueAt());
        } else {
            flowExecutionState.setStatus(FlowExecutionStepStatus.FAILED);
            flowExecutionStateRepository.save(flowExecutionState);
            recordStepAudit(
                    escalation,
                    AuditAction.NOTIFICATION_FAILED,
                    FlowExecutionStepStatus.SENDING,
                    FlowExecutionStepStatus.FAILED,
                    flowExecutionState,
                    "failure=DELIVERY_FAILED");
            if (nextNode == null) {
                escalation.setStatus(EscalationStatus.COMPLETED);
                escalation.setResolutionType(EscalationResolutionType.EXHAUSTED);
                escalationRepository.save(escalation);
                auditService.record(new AuditEvent(
                        AuditEntityType.ESCALATION,
                        escalation.getId(),
                        AuditAction.COMPLETED,
                        EscalationStatus.OPEN.name(),
                        EscalationStatus.COMPLETED.name(),
                        null,
                        null,
                        clock.instant(),
                        "EXHAUSTED",
                        "failedStepId=" + flowExecutionState.getId()
                                + ";resolutionType=" + EscalationResolutionType.EXHAUSTED.name()));
            } else {
                stepSchedulingService.scheduleStep(nextNode);
            }
        }
    }

    // Records one automatic step-delivery event without exposing tokens or diagnostics.
    private void recordStepAudit(
            Escalation escalation,
            AuditAction action,
            FlowExecutionStepStatus previousStatus,
            FlowExecutionStepStatus newStatus,
            FlowExecutionState step,
            String details) {
        auditService.record(new AuditEvent(
                AuditEntityType.ESCALATION,
                escalation.getId(),
                action,
                previousStatus.name(),
                newStatus.name(),
                null,
                null,
                clock.instant(),
                null,
                "executionStepId=" + step.getId()
                        + ";recipientEmail=" + step.getUserEmail()
                        + ";attempt=" + step.getSendAttemptCount()
                        + ";" + details));
    }

    // Calculates when the final step acknowledgement wait ends.
    private Instant calculateAcknowledgementTimeoutAt(Duration waitDuration) {
        if (waitDuration == null || waitDuration.isNegative()) {
            throw new IllegalStateException("A sent response step requires a nonnegative acknowledgement wait");
        }
        return clock.instant().plus(waitDuration).truncatedTo(ChronoUnit.MICROS);
    }
}
