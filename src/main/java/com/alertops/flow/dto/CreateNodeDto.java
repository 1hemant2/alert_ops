package com.alertops.flow.dto;

import java.math.BigInteger;
import java.util.UUID;

public class CreateNodeDto {
    private UUID id;
    private UUID flowId;
    private String nodeName;
    private int durationInMinutes;
    private String email;
    private BigInteger position;
    

    public CreateNodeDto(UUID id, UUID flowId, String nodeName, int durationInMinutes, String email, BigInteger position) {
        this.id = id;
        this.flowId = flowId;
        this.nodeName = nodeName;
        this.durationInMinutes = durationInMinutes;
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
