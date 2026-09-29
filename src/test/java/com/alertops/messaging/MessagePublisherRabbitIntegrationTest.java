package com.alertops.messaging;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against a developer-provided RabbitMQ instance when explicitly enabled. */
@EnabledIfEnvironmentVariable(named = "RABBITMQ_INTEGRATION_TEST", matches = "true")
@SpringBootTest(classes = MessagePublisherRabbitIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.rabbitmq.publisher-confirm-type=correlated",
                "spring.rabbitmq.publisher-returns=true",
                "spring.rabbitmq.template.mandatory=true"
        })
class MessagePublisherRabbitIntegrationTest {
    private static final int WAIT_MILLIS = 5000;

    @Autowired private RabbitTemplate rabbit;
    @Autowired private AmqpAdmin admin;
    @Autowired private MessagePublisher publisher;

    @DynamicPropertySource
    static void rabbitConnection(DynamicPropertyRegistry properties) {
        properties.add("spring.rabbitmq.host", () -> requiredEnvironment("RABBITMQ_INTEGRATION_HOST"));
        properties.add("spring.rabbitmq.port", () -> requiredEnvironment("RABBITMQ_INTEGRATION_PORT"));
        properties.add("spring.rabbitmq.username", () -> requiredEnvironment("RABBITMQ_INTEGRATION_USERNAME"));
        properties.add("spring.rabbitmq.password", () -> requiredEnvironment("RABBITMQ_INTEGRATION_PASSWORD"));
    }

    @Test
    void publisherConfirmsPersistentReadyContractAndRoutesItToTheReadyQueue() throws Exception {
        String queueName = "alertops-it-ready-" + UUID.randomUUID();
        admin.declareQueue(QueueBuilder.durable(queueName).build());
        admin.declareBinding(new Binding(queueName, Binding.DestinationType.QUEUE,
                RabbitMqConfig.ESCALATION_STEP_READY_EXCHANGE, RabbitMqConfig.ESCALATION_STEP_READY_ROUTING_KEY, null));

        try {
            EscalationStepReadyMessage expected = new EscalationStepReadyMessage(UUID.randomUUID(), 4, Instant.now().minusSeconds(10));
            publisher.publishEscalationStepReady(expected);

            Message message = rabbit.receive(queueName, WAIT_MILLIS);
            assertThat(message).isNotNull();
            assertThat(message.getMessageProperties().getReceivedDeliveryMode())
                    .isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(message.getMessageProperties().getExpiration()).isNull();
            assertThat(rabbit.getMessageConverter().fromMessage(message)).isEqualTo(expected);
        } finally {
            admin.deleteQueue(queueName);
        }
    }

    @Test
    void brokerReturnsAnUnroutableMandatoryPublication() throws Exception {
        String exchangeName = "alertops-it-unroutable-" + UUID.randomUUID();
        admin.declareExchange(new DirectExchange(exchangeName, false, true));

        try {
            CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
            rabbit.convertAndSend(exchangeName, "no-bound-queue", "test", correlation);

            CorrelationData.Confirm confirm = correlation.getFuture().get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
            assertThat(confirm.isAck()).isTrue();
            assertThat(correlation.getReturned()).isNotNull();
            assertThat(correlation.getReturned().getReplyCode()).isEqualTo(312);
        } finally {
            admin.deleteExchange(exchangeName);
        }
    }

    @Test
    void persistentReadyDeliveryCanBeRedeliveredAfterConsumerNack() throws Exception {
        String exchangeName = "alertops-it-redelivery-" + UUID.randomUUID();
        String queueName = "alertops-it-redelivery-" + UUID.randomUUID();
        admin.declareExchange(new DirectExchange(exchangeName, true, false));
        admin.declareQueue(QueueBuilder.durable(queueName).build());
        admin.declareBinding(new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, "ready", null));

        try {
            EscalationStepReadyMessage expected = new EscalationStepReadyMessage(UUID.randomUUID(), 1, Instant.now());
            CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
            rabbit.convertAndSend(exchangeName, "ready", expected, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }, correlation);
            assertThat(correlation.getFuture().get(WAIT_MILLIS, TimeUnit.MILLISECONDS).isAck()).isTrue();

            rabbit.execute(channel -> {
                GetResponse firstDelivery = channel.basicGet(queueName, false);
                assertThat(firstDelivery).isNotNull();
                channel.basicNack(firstDelivery.getEnvelope().getDeliveryTag(), false, true);
                return null;
            });

            Message redelivery = rabbit.receive(queueName, WAIT_MILLIS);
            assertThat(redelivery).isNotNull();
            assertThat(redelivery.getMessageProperties().isRedelivered()).isTrue();
            assertThat(rabbit.getMessageConverter().fromMessage(redelivery)).isEqualTo(expected);
        } finally {
            admin.deleteQueue(queueName);
            admin.deleteExchange(exchangeName);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when RabbitMQ integration tests are enabled");
        }
        return value;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import({RabbitMqConfig.class, MessagePublisher.class})
    static class Config {
    }
}
