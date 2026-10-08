package com.alertops.flow.dto;

import java.math.BigInteger;
import java.util.UUID;

public class CreateNodeDto {
    private UUID id;
    private UUID flowId;
    private String nodeName;
    private int durationInMinutes;
    private Integer resolutionTimeoutInMinutes;
    private String email;
    private BigInteger position;

    public CreateNodeDto() {
    }
    

    public CreateNodeDto(UUID id, UUID flowId, String nodeName, int durationInMinutes, String email, BigInteger position) {
        this(id, flowId, nodeName, durationInMinutes, null, email, position);
    }

    public CreateNodeDto(UUID id, UUID flowId, String nodeName, int durationInMinutes,
                         Integer resolutionTimeoutInMinutes, String email, BigInteger position) {
        this.id = id;
        this.flowId = flowId;
        this.nodeName = nodeName;
        this.durationInMinutes = durationInMinutes;
        this.resolutionTimeoutInMinutes = resolutionTimeoutInMinutes;
        this.email = email;
        this.position = position;
    }
    
    public UUID getFlowId() {
        return flowId;
    }
    public void setFlowId(UUID flowId) {
        this.flowId = flowId;
    }
    public String getNodeName() {
        return nodeName;
    }
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }
    public int getDurationInMinutes() {
        return durationInMinutes;
    }

    public void setDurationInMinutes(int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
    }

    public Integer getResolutionTimeoutInMinutes() {
        return resolutionTimeoutInMinutes;
    }

    public void setResolutionTimeoutInMinutes(Integer resolutionTimeoutInMinutes) {
        this.resolutionTimeoutInMinutes = resolutionTimeoutInMinutes;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }

    public BigInteger getPosition() {
        return position;
    }

    public void setPosition(BigInteger position) {
        this.position = position;
    }

    
}
