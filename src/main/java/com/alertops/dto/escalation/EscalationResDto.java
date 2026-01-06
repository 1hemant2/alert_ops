package com.alertops.dto.escalation;


import java.time.ZonedDateTime;

public class EscalationResDto {
    private Long flowId;
    private  String flowName;
    private Boolean isFlowStarted;
    private ZonedDateTime startTime; // preserves full ZoneId
    private ZonedDateTime createdAt;

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public Boolean getFlowStarted() {
        return isFlowStarted;
    }

    public void setFlowStarted(Boolean flowStarted) {
        isFlowStarted = flowStarted;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(ZonedDateTime startTime) {
        this.startTime = startTime;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public EscalationResDto(Long flowId, String flowName, Boolean isFlowStarted, ZonedDateTime startTime, ZonedDateTime createdAt) {
        this.flowId = flowId;
        this.flowName = flowName;
        this.isFlowStarted = isFlowStarted;
        this.startTime = startTime;
        this.createdAt = createdAt;
    }
}
