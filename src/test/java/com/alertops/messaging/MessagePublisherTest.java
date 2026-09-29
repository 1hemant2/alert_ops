package com.alertops.messaging;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessagePublisherTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final MessagePublisher publisher = new MessagePublisher(rabbit);

    @Test
    void readyWorkPublishesPersistentlyWithoutDelay() {
        EscalationStepReadyMessage delivery = new EscalationStepReadyMessage(UUID.randomUUID(), 2, Instant.now().minusSeconds(30));
        doAnswer(invocation -> {
            EscalationStepReadyMessage payload = invocation.getArgument(2);
            MessagePostProcessor processor = invocation.getArgument(3);
            Message message = processor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertThat(payload.stepId()).isEqualTo(delivery.stepId());
            assertThat(payload.sendAttemptCount()).isEqualTo(2);
            assertThat(payload.dueAt()).isEqualTo(delivery.dueAt());
            assertThat(message.getMessageProperties().getExpiration()).isNull();
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq(RabbitMqConfig.ESCALATION_STEP_READY_EXCHANGE), eq(RabbitMqConfig.ESCALATION_STEP_READY_ROUTING_KEY),
                any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        publisher.publishEscalationStepReady(delivery);
    }

    @Test
    void brokerRejectionIsARecoverablePublicationFailure() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publishEscalationStepReady(new EscalationStepReadyMessage(UUID.randomUUID(), 0, Instant.now())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish");
    }

    @Test
    void unroutableMessagesAreFailuresEvenWhenTheBrokerAcknowledgesThem() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                    312, "NO_ROUTE", RabbitMqConfig.ESCALATION_STEP_READY_EXCHANGE, RabbitMqConfig.ESCALATION_STEP_READY_ROUTING_KEY));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publishEscalationStepReady(new EscalationStepReadyMessage(UUID.randomUUID(), 0, Instant.now())))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("RabbitMQ returned the ready message as unroutable");
    }
}
