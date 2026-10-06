package com.alertops.flow.dto;

import java.util.UUID;

public class NodeTimingDto {
    private UUID nodeId;
    private Integer resolutionTimeoutInMinutes;

    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    public Integer getResolutionTimeoutInMinutes() {
        return resolutionTimeoutInMinutes;
    }

    public void setResolutionTimeoutInMinutes(Integer resolutionTimeoutInMinutes) {
        this.resolutionTimeoutInMinutes = resolutionTimeoutInMinutes;
    }
}
