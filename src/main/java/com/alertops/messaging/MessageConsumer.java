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
   private final EscalationRepository esclEscalationRepository;
   private final MessagePublisher messagePublisher;


    public MessageConsumer(FlowExecutionStateRepository flowExecutionStateRepository, EscalationRepository escalationRepository,
        Notification notification, MessagePublisher messagePublisher
    ) {
        this.flowExecutionStateRepository = flowExecutionStateRepository;
        this.esclEscalationRepository = escalationRepository;
        this.notification = notification;
        this.messagePublisher = messagePublisher;
    }


    @RabbitListener(queues = RabbitMqConfig.FINAL_QUEUE)
    @Transactional
    public void onMessage(FlowExecutionState flowExecutionState) {
        try {
            UUID processId = flowExecutionState.getProcessId();

            if(processId == null) {
                //send the esclation failed notification who have created the escalation
                throw new RuntimeException("Escalation failed");
            }

            Escalation escalation = esclEscalationRepository.findById(processId).orElse(null);

            if(escalation != null) {
                consume(flowExecutionState, escalation);
            } else {
                //send the mail escalation failed 
                throw new RuntimeException("Something went wrong while getting the escaltion");
            }

        } catch (Exception e) {
            System.out.println("Something went wrong" + e.getMessage());
        };
    }


    public void consume(FlowExecutionState flowExecutionState, Escalation escalation) {
        try {
            String esclationStatus  = escalation.getStatus();
            String flowExecutionNodeStatus = flowExecutionState.getExecutionState();
            String notificationStatus = flowExecutionState.getNotificationState();
            boolean retryOnFailureEnabled = flowExecutionState.isRetryOnFailureEnabled();
            int maxRetryAttempts = flowExecutionState.getMaxRetryAttempts();
            int sendAttemptCount = flowExecutionState.getSendAttemptCount();
            FlowExecutionState nextNode = flowExecutionStateRepository.
                                            findFirstByProcessIdAndExecutionStateOrderByPositionAsc(flowExecutionState.getProcessId(), "PENDING");

            if(esclationStatus.equals("RUNNING") && flowExecutionNodeStatus.equals("ACTIVE") ) {
                if(notificationStatus.equals("NOT_SENT")) {
                    boolean mailSent = notification.sendEmail(flowExecutionState);
                    if(mailSent) {
                       // change node status terminal, notification status sent
                       flowExecutionState.setExecutionState("TERMINAL");
                       flowExecutionState.setNotificationState("SENT");
                       flowExecutionStateRepository.save(flowExecutionState);
                       if(nextNode == null) {
                          escalation.setStatus("COMPLETED"); 
                          escalation.setResolutionType("EXHAUSTED"); 
                          esclEscalationRepository.save(escalation);
                       } else {
                           messagePublisher.publishWithDelay(nextNode);
                       }
                    } else {
                        if(retryOnFailureEnabled && sendAttemptCount < maxRetryAttempts) {
                           // don't change node status, notification_status, increase retry count push the same node
                            // publish current node again to delay Q.
                            flowExecutionState.setSendAttemptCount(sendAttemptCount + 1);
                            flowExecutionStateRepository.save(flowExecutionState);
                            messagePublisher.publishWithDelay(flowExecutionState);
                        } else {
                            // change the node status as failed, notification status failed, push the next node
                            flowExecutionState.setExecutionState("TERMINAL");
                            flowExecutionState.setNotificationState("FAILED");
                            flowExecutionState.setSendAttemptCount(sendAttemptCount + 1);
                            if(nextNode == null) {
                               escalation.setStatus("COMPLETED");
                               escalation.setResolutionType("EXHAUSTED"); 
                               esclEscalationRepository.save(escalation);
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
                                            findFirstByProcessIdAndExecutionStateOrderByPositionAsc(flowExecutionState.getProcessId(), "IDLE");
            if(nextNode == null) {
               escalation.setStatus("COMPLETED");
               escalation.setResolutionType("EXHAUSTED"); 
               esclEscalationRepository.save(escalation);
            } else {
                messagePublisher.publishWithDelay(nextNode);
            }
        }
    }
}
