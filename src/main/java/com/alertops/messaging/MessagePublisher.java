package com.alertops.messaging;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import org.springframework.amqp.rabbit.connection.CorrelationData;


@Component
public class MessagePublisher {
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;

    public MessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // Publishes a step after its wait is over; the timer retries if this fails.
    public void publishEscalationStepReady(EscalationStepReadyMessage readyMessage) {
        try {
            // Tracks RabbitMQ's response for this message.
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

            rabbitTemplate.convertAndSend(
                RabbitMqConfig.ESCALATION_STEP_READY_EXCHANGE,
                RabbitMqConfig.ESCALATION_STEP_READY_ROUTING_KEY,
                readyMessage,
                amqpMessage -> {
                    // Keep the message after a RabbitMQ restart; its wait is already over.
                    amqpMessage.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                    return amqpMessage;
                },
                correlationData
            );

            // Wait up to five seconds for RabbitMQ to accept the message.
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // A message can be accepted but still have no queue to reach.
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned the ready message as unroutable");
            }
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ did not confirm the ready message: " + confirm.getReason());
            }
        } catch (InterruptedException e) {
            // Keep the interruption signal for shutdown.
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for RabbitMQ to confirm the ready message", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // A retry can duplicate an email if the app crashes after SMTP accepts it.
            throw new RuntimeException("Failed to publish ready message", e);
        }
    }
}
