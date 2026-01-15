package com.alertops.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;


@Component
public class MessagePublisher {
    @Autowired
    RabbitTemplate rabbitTemplate;
    FlowExecutionStateRepository flowExecutionStateRepository;

    MessagePublisher(FlowExecutionStateRepository flowExecutionStateRepository) {
        this.flowExecutionStateRepository = flowExecutionStateRepository;
    }

    public void publishWithDelay(FlowExecutionState flowExecutionState) {
        try {
            flowExecutionState.setExecutionState("ACTIVE");
            rabbitTemplate.convertAndSend(
                RabbitMqConfig.NORMAL_EXCHANGE,
                RabbitMqConfig.DELAY_ROUTING_KEY,
                flowExecutionState,
                message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(flowExecutionState.getDuration().toMillis()));
                    message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                    return message;
                }
            );
            flowExecutionStateRepository.save(flowExecutionState);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to publish delayed message", e);
        }
    }
}
