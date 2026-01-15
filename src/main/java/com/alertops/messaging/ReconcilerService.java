package com.alertops.messaging;


import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.alertops.flow_execution_engine.model.Escalation;
import com.alertops.flow_execution_engine.model.FlowExecutionState;
import com.alertops.flow_execution_engine.repository.EscalationRepository;
import com.alertops.flow_execution_engine.repository.FlowExecutionStateRepository;

import java.util.List;


@Component
public class ReconcilerService {
    private final MessagePublisher messagePublisher;
    private final EscalationRepository escalationRepository;
    private final FlowExecutionStateRepository flowExecutionStateRepository;

    public ReconcilerService(MessagePublisher messagePublisher, EscalationRepository escalationRepository, FlowExecutionStateRepository flowExecutionStateRepository) {
        this.messagePublisher = messagePublisher;
        this.escalationRepository = escalationRepository;
        this.flowExecutionStateRepository = flowExecutionStateRepository;
    }


    @PostConstruct
    public void onStartup() {
        reconcileOnce();
    }

    @Transactional
    public void reconcileOnce() {
        List<Escalation> escalations = escalationRepository.findAllByStatus("RUNNING");        

        for(Escalation escalation : escalations) {
            FlowExecutionState flowExecutionState = flowExecutionStateRepository
                                                   .findFirstByProcessIdAndExecutionStateOrderByPositionAsc(escalation.getId(), "ACTIVE");
            messagePublisher.publishWithDelay(flowExecutionState);
        }
    }
}

