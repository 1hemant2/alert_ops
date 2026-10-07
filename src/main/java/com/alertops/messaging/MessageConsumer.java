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
   private final Clock clock;


    // Creates the RabbitMQ consumer that delivers scheduled escalation steps.
    public MessageConsumer(FlowExecutionStateRepository flowExecutionStateRepository, EscalationRepository escalationRepository,
        Notification notification, StepSchedulingService stepSchedulingService,
        EscalationAcknowledgementService acknowledgementService,
        EscalationTimeoutService timeoutService,
        Clock clock
    ) {
        this.flowExecutionStateRepository = flowExecutionStateRepository;
        this.escalationRepository = escalationRepository;
        this.notification = notification;
        this.stepSchedulingService = stepSchedulingService;
        this.acknowledgementService = acknowledgementService;
        this.timeoutService = timeoutService;
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
                timeoutService.scheduleAcknowledgementTimeout(
                        escalation.getId(), flowExecutionState.getId(), acknowledgementTimeoutAt);
            } else {
                FlowExecutionState scheduledNextNode = Objects.requireNonNull(
                        stepSchedulingService.scheduleStep(nextNode), "Scheduled next response step is required");
                // The next step's durable dueAt is also this sent step's acknowledgement boundary.
                flowExecutionState.setDueAt(Objects.requireNonNull(
                        scheduledNextNode.getDueAt(), "Scheduled next response step requires a due time"));
                flowExecutionStateRepository.save(flowExecutionState);
            }
        } else if (retryOnFailureEnabled && sendAttemptCount < maxRetryAttempts) {
            flowExecutionStateRepository.save(flowExecutionState);
            stepSchedulingService.scheduleStep(flowExecutionState);
        } else {
            flowExecutionState.setStatus(FlowExecutionStepStatus.FAILED);
            flowExecutionStateRepository.save(flowExecutionState);
            if (nextNode == null) {
                escalation.setStatus(EscalationStatus.COMPLETED);
                escalation.setResolutionType(EscalationResolutionType.EXHAUSTED);
                escalationRepository.save(escalation);
            } else {
                stepSchedulingService.scheduleStep(nextNode);
            }
        }
    }

    // Calculates when the final step acknowledgement wait ends.
    private Instant calculateAcknowledgementTimeoutAt(Duration waitDuration) {
        if (waitDuration == null || waitDuration.isNegative()) {
            throw new IllegalStateException("A sent response step requires a nonnegative acknowledgement wait");
        }
        return clock.instant().plus(waitDuration).truncatedTo(ChronoUnit.MICROS);
    }
}
