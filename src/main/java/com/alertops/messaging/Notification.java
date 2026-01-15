package com.alertops.messaging;


import org.springframework.stereotype.Component;
import com.alertops.flow_execution_engine.model.FlowExecutionState;


@Component
public class Notification {
    public boolean sendEmail(FlowExecutionState flowExecutionState) {
        try {
            System.out.println("[SEND] to=" + flowExecutionState.getUserEmail() + " msg=" + flowExecutionState.getTaskDetails());
            return true; // true => succes
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }
}

