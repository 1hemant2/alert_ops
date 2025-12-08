package com.alertops.configuration;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMqConfig {

    public static final String NORMAL_EXCHANGE = "normal.exchange";
    public static final String FINAL_EXCHANGE = "final.exchange";

    public static final String DELAY_QUEUE = "delay.queue";
    public static final String FINAL_QUEUE = "final.queue";

    public static final String DELAY_ROUTING_KEY = "delay.key";
    public static final String FINAL_ROUTING_KEY = "final.key";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange normalExchange() {
        return new DirectExchange(NORMAL_EXCHANGE);
    }

    @Bean
    public DirectExchange finalExchange() {
        return new DirectExchange(FINAL_EXCHANGE);
    }

    // Queue where messages wait (TTL queue)
    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", FINAL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FINAL_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue finalQueue() {
        return QueueBuilder.durable(FINAL_QUEUE).build();
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue())
                .to(normalExchange())
                .with(DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding finalBinding() {
        return BindingBuilder.bind(finalQueue())
                .to(finalExchange())
                .with(FINAL_ROUTING_KEY);
    }
}
