package com.alertops.dto.escalation;


import java.util.List;


public class EscalationDto {
    private Long id;
    private String flowName;
    private String startFlow;
    private String startTime;
    // If you want owner info, flatten it:
    private Long ownerId;
    private String ownerName;
    private boolean isFlowStarted;
    List<Long> steps;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public String getStartFlow() {
        return startFlow;
    }

    public void setStartFlow(String startFlow) {
        this.startFlow = startFlow;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean isFlowStarted() {
        return isFlowStarted;
    }

    public void setFlowStarted(boolean flowStarted) {
        isFlowStarted = flowStarted;
    }

    public List<Long> getSteps() {
        return steps;
    }

    public void setSteps(List<Long> steps) {
        this.steps = steps;
    }
}
