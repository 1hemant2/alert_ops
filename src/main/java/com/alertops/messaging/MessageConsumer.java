package com.alertops.messaging;

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
   private final MessagePublisher messagePublisher;


    public MessageConsumer(FlowExecutionStateRepository flowExecutionStateRepository, EscalationRepository escalationRepository,
        Notification notification, MessagePublisher messagePublisher
    ) {
        this.flowExecutionStateRepository = flowExecutionStateRepository;
        this.escalationRepository = escalationRepository;
        this.notification = notification;
        this.messagePublisher = messagePublisher;
    }


    @RabbitListener(queues = RabbitMqConfig.FINAL_QUEUE)
    @Transactional
    public void onMessage(FlowExecutionState queuedState) {
        if (queuedState == null || queuedState.getId() == null) {
            return;
        }

        FlowExecutionState currentState = flowExecutionStateRepository.findById(queuedState.getId()).orElse(null);
        if (currentState == null
                || currentState.getSendAttemptCount() != queuedState.getSendAttemptCount()
                || !"ACTIVE".equals(currentState.getExecutionState())
                || !"NOT_SENT".equals(currentState.getNotificationState())
                || currentState.getProcessId() == null) {
            return;
        }

        Escalation escalation = escalationRepository.findById(currentState.getProcessId()).orElse(null);
        if (escalation == null || !"RUNNING".equals(escalation.getStatus())) {
            return;
        }

        int claimedRows = flowExecutionStateRepository.claimForDelivery(
                currentState.getId(), queuedState.getSendAttemptCount());
        if (claimedRows != 1) {
            return;
        }

        // The bulk update bypasses the persistence context, so keep the managed copy aligned.
        currentState.setExecutionState("PROCESSING");
        consume(currentState, escalation);
    }


    private void consume(FlowExecutionState flowExecutionState, Escalation escalation) {
        try {
            String esclationStatus  = escalation.getStatus();
            String flowExecutionNodeStatus = flowExecutionState.getExecutionState();
            String notificationStatus = flowExecutionState.getNotificationState();
            boolean retryOnFailureEnabled = flowExecutionState.isRetryOnFailureEnabled();
            int maxRetryAttempts = flowExecutionState.getMaxRetryAttempts();
            int sendAttemptCount = flowExecutionState.getSendAttemptCount();
            FlowExecutionState nextNode = flowExecutionStateRepository.
                                            findFirstByProcessIdAndExecutionStateOrderByPositionAsc(flowExecutionState.getProcessId(), "PENDING");

            if(esclationStatus.equals("RUNNING") && flowExecutionNodeStatus.equals("PROCESSING") ) {
                if(notificationStatus.equals("NOT_SENT")) {
                    boolean mailSent = notification.sendEmail(flowExecutionState);
                    // Persist total attempts, including successful SMTP submissions.
                    flowExecutionState.setSendAttemptCount(sendAttemptCount + 1);
                    if(mailSent) {
                       // change node status terminal, notification status sent
                       flowExecutionState.setExecutionState("TERMINAL");
                       flowExecutionState.setNotificationState("SENT");
                       flowExecutionStateRepository.save(flowExecutionState);
                       if(nextNode == null) {
                          escalation.setStatus("COMPLETED"); 
                          escalation.setResolutionType("EXHAUSTED"); 
                          escalationRepository.save(escalation);
                       } else {
                           messagePublisher.publishWithDelay(nextNode);
                       }
                    } else {
                        if(retryOnFailureEnabled && sendAttemptCount < maxRetryAttempts) {
                           // don't change node status, notification_status, increase retry count push the same node
                            // publish current node again to delay Q.
                            flowExecutionStateRepository.save(flowExecutionState);
                            messagePublisher.publishWithDelay(flowExecutionState);
                        } else {
                            // change the node status as failed, notification status failed, push the next node
                            flowExecutionState.setExecutionState("TERMINAL");
                            flowExecutionState.setNotificationState("FAILED");
                            if(nextNode == null) {
                               escalation.setStatus("COMPLETED");
                               escalation.setResolutionType("EXHAUSTED"); 
                               escalationRepository.save(escalation);
                            } else {
                                messagePublisher.publishWithDelay(nextNode);
                            }
                        }
                    }
                    flowExecutionStateRepository.save(flowExecutionState);
                }
            } 
        } catch (Exception e) {
            //send the email node has been failed to execute 
            flowExecutionState.setExecutionState("FAILED");
            flowExecutionState.setNotificationState("FAILED");
            flowExecutionStateRepository.save(flowExecutionState);
            FlowExecutionState nextNode = flowExecutionStateRepository.
                                            findFirstByProcessIdAndExecutionStateOrderByPositionAsc(flowExecutionState.getProcessId(), "PENDING");
            if(nextNode == null) {
               escalation.setStatus("COMPLETED");
               escalation.setResolutionType("EXHAUSTED"); 
               escalationRepository.save(escalation);
            } else {
                messagePublisher.publishWithDelay(nextNode);
            }
        }
    }
}
