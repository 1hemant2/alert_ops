package com.AlertOps.service;

// import com.example.scheduler.config.RabbitMQConfig;
// import com.example.scheduler.dto.ScheduledEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.AlertOps.Configuration.RabbitMqConfig;
// import com.AlertOps.dto.Schedular.ScheduledEvent;
import com.AlertOps.dto.Schedular.ScheduledEvent;


@Service
public class MessagePublisher {
    @Autowired
    RabbitTemplate rabbitTemplate;

    public void publishWithDelay(ScheduledEvent event, long delayMs) {

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.NORMAL_EXCHANGE,
                RabbitMqConfig.DELAY_ROUTING_KEY,
                event,
                message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(delayMs));
                    message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                    return message;
                }
        );

        System.out.println("Sent: " + " with delay " + delayMs + "ms");
    }
}