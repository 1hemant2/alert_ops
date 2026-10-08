package com.alertops.flow.dto;

import java.util.List;

public class UpdateFlowTimingDto {
    private Boolean resolutionTimeoutEnabled;
    private List<NodeTimingDto> nodeTimings;
    private Long version;

    public Boolean getResolutionTimeoutEnabled() {
        return resolutionTimeoutEnabled;
    }

    public void setResolutionTimeoutEnabled(Boolean resolutionTimeoutEnabled) {
        this.resolutionTimeoutEnabled = resolutionTimeoutEnabled;
    }

    public List<NodeTimingDto> getNodeTimings() {
        return nodeTimings;
    }

    public void setNodeTimings(List<NodeTimingDto> nodeTimings) {
        this.nodeTimings = nodeTimings;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
