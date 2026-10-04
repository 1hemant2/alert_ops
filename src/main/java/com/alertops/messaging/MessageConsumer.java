package com.alertops.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
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


    public MessageConsumer(FlowExecutionStateRepository flowExecutionStateRepository, EscalationRepository escalationRepository,
        Notification notification, StepSchedulingService stepSchedulingService,
        EscalationAcknowledgementService acknowledgementService
    ) {
        this.flowExecutionStateRepository = flowExecutionStateRepository;
        this.escalationRepository = escalationRepository;
        this.notification = notification;
        this.stepSchedulingService = stepSchedulingService;
        this.acknowledgementService = acknowledgementService;
    }


    @RabbitListener(queues = RabbitMqConfig.ESCALATION_STEP_READY_QUEUE)
    @Transactional
    public void onMessage(EscalationStepReadyMessage readyMessage) {
        if (readyMessage == null || readyMessage.stepId() == null || readyMessage.dueAt() == null) {
            return;
        }

        FlowExecutionState currentState = flowExecutionStateRepository.findById(readyMessage.stepId()).orElse(null);
        if (currentState == null
                || currentState.getSendAttemptCount() != readyMessage.sendAttemptCount()
                || !readyMessage.dueAt().equals(currentState.getDueAt())
                || !"ACTIVE".equals(currentState.getExecutionState())
                || !"NOT_SENT".equals(currentState.getNotificationState())
                || currentState.getProcessId() == null) {
            return;
        }

        // Lock the run while checking and sending so acknowledgement cannot race a new step.
        Escalation escalation = escalationRepository.findByIdForUpdate(currentState.getProcessId()).orElse(null);
        if (escalation == null || !"OPEN".equals(escalation.getStatus())) {
            return;
        }

        if (currentState.getDueAt().isAfter(Instant.now())) {
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
        currentState.setExecutionState("PROCESSING");
        currentState.setPublicationPending(false);
        consume(currentState, escalation);
    }


    private void consume(FlowExecutionState flowExecutionState, Escalation escalation) {
        boolean retryOnFailureEnabled = flowExecutionState.isRetryOnFailureEnabled();
        int maxRetryAttempts = flowExecutionState.getMaxRetryAttempts();
        int sendAttemptCount = flowExecutionState.getSendAttemptCount();
        FlowExecutionState nextNode = flowExecutionStateRepository
                .findFirstByProcessIdAndExecutionStateOrderByPositionAsc(
                        flowExecutionState.getProcessId(), "PENDING");

        String acknowledgementUrl = acknowledgementService.createAcknowledgementUrl(
                escalation, flowExecutionState.getUserEmail());
        boolean mailSent = notification.sendEmail(flowExecutionState, acknowledgementUrl);
        // Persist total attempts, including successful SMTP submissions.
        flowExecutionState.setSendAttemptCount(sendAttemptCount + 1);
        if (mailSent) {
            flowExecutionState.setExecutionState("TERMINAL");
            flowExecutionState.setNotificationState("SENT");
            flowExecutionStateRepository.save(flowExecutionState);
            if (nextNode == null) {
                escalation.setStatus("COMPLETED");
                escalation.setResolutionType("EXHAUSTED");
                escalationRepository.save(escalation);
            } else {
                stepSchedulingService.schedule(nextNode);
            }
        } else if (retryOnFailureEnabled && sendAttemptCount < maxRetryAttempts) {
            flowExecutionStateRepository.save(flowExecutionState);
            stepSchedulingService.schedule(flowExecutionState);
        } else {
            flowExecutionState.setExecutionState("TERMINAL");
            flowExecutionState.setNotificationState("FAILED");
            flowExecutionStateRepository.save(flowExecutionState);
            if (nextNode == null) {
                escalation.setStatus("COMPLETED");
                escalation.setResolutionType("EXHAUSTED");
                escalationRepository.save(escalation);
            } else {
                stepSchedulingService.schedule(nextNode);
            }
        }
    }
}
