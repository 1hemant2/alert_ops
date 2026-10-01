package com.alertops.webhook.dto;

import java.util.UUID;

public record WebhookTriggerResponse(
        UUID webhookId,
        String eventId,
        UUID flowId,
        UUID taskId,
        UUID escalationId,
        boolean replayed
) {}
