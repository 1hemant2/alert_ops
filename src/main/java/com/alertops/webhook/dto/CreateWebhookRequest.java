package com.alertops.webhook.dto;

import java.util.UUID;

public class CreateWebhookRequest {
    private String name;
    private UUID flowId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getFlowId() { return flowId; }
    public void setFlowId(UUID flowId) { this.flowId = flowId; }
}
