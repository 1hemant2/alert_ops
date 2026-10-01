package com.alertops.webhook.dto;

import java.time.Instant;
import java.util.UUID;

import com.alertops.webhook.model.WebhookConfiguration;

public record WebhookConfigurationResponse(
        UUID id,
        UUID defaultFlowId,
        String name,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Instant lastTriggeredAt,
        String secret
) {
    public static WebhookConfigurationResponse from(WebhookConfiguration configuration, String secret) {
        return new WebhookConfigurationResponse(configuration.getId(), configuration.getDefaultFlowId(),
                configuration.getName(), configuration.isEnabled(), configuration.getCreatedAt(),
                configuration.getUpdatedAt(), configuration.getLastTriggeredAt(), secret);
    }
}
