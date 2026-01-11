package com.alertops.flow.dto;

import java.math.BigInteger;
import java.util.UUID;

public class ReorderNodeDto {
    public String nodeId;
    public UUID afterNodeId;
    public Long version;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }

    public UUID getAfterNodeId() {
        return afterNodeId;
    }

    public void setAfterNodeId(UUID afterNodeId) {
        this.afterNodeId = afterNodeId;
    }

    
}
