package com.alertops.flow_execution_engine.dto;

import java.util.UUID;

public class CreateEscalationReqDto {
    private String escalationName;
    private UUID flowId;
    private UUID taskId;
    
    public String getEscalationName() {
        return escalationName;
    }
    public void setEscalationName(String escalationName) {
        this.escalationName = escalationName;
    }
    public UUID getFlowId() {
        return flowId;
    }
    public void setFlowId(UUID flowId) {
        this.flowId = flowId;
    }
    public UUID getTaskId() {
        return taskId;
    }
    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    
}
