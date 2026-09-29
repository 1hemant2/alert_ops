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

@Component
public class MessageConsumer {
   private final Notification notification;
   private final FlowExecutionStateRepository flowExecutionStateRepository;
   private final EscalationRepository escalationRepository;
   private final StepSchedulingService stepSchedulingService;


    public MessageConsumer(FlowExecutionStateRepository flowExecutionStateRepository, EscalationRepository escalationRepository,
        Notification notification, StepSchedulingService stepSchedulingService
    ) {
        this.flowExecutionStateRepository = flowExecutionStateRepository;
        this.escalationRepository = escalationRepository;
        this.notification = notification;
        this.stepSchedulingService = stepSchedulingService;
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

        Escalation escalation = escalationRepository.findById(currentState.getProcessId()).orElse(null);
        if (escalation == null || !"RUNNING".equals(escalation.getStatus())) {
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

        boolean mailSent = notification.sendEmail(flowExecutionState);
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
