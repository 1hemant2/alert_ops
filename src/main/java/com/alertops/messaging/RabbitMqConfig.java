package com.alertops.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    public static final String ESCALATION_STEP_READY_EXCHANGE = "alertops.escalation-step-ready.exchange";
    public static final String ERROR_EXCHANGE = "error.exchange";
    public static final String ESCALATION_STEP_READY_QUEUE = "alertops.escalation-step-ready.queue";
    public static final String ERROR_QUEUE = "error.queue";
    public static final String ESCALATION_STEP_READY_ROUTING_KEY = "alertops.escalation-step-ready";
    public static final String ERROR_ROUTING_KEY = "error.key";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter("com.alertops.messaging");
    }

    @Bean
    public MessageRecoverer listenerFailureRecoverer() {
        // If handling still fails after retries, send the message to the error queue.
        return new RejectAndDontRequeueRecoverer();
    }

    @Bean
    public DirectExchange escalationStepReadyExchange() {
        // Routes due escalation steps to the escalation-step-ready queue.
        return new DirectExchange(ESCALATION_STEP_READY_EXCHANGE);
    }

    @Bean
    public DirectExchange errorExchange() {
        return new DirectExchange(ERROR_EXCHANGE);
    }

    @Bean
    public Queue escalationStepReadyQueue() {
        return QueueBuilder.durable(ESCALATION_STEP_READY_QUEUE)
                .withArgument("x-dead-letter-exchange", ERROR_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ERROR_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue errorQueue() {
        return QueueBuilder.durable(ERROR_QUEUE).build();
    }

    @Bean
    public Binding escalationStepReadyBinding() {
        return BindingBuilder.bind(escalationStepReadyQueue())
                .to(escalationStepReadyExchange())
                .with(ESCALATION_STEP_READY_ROUTING_KEY);
    }

    @Bean
    public Binding errorBinding() {
        return BindingBuilder.bind(errorQueue())
                .to(errorExchange())
                .with(ERROR_ROUTING_KEY);
    }
}
