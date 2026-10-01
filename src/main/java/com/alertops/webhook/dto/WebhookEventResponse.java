package com.alertops.webhook.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record WebhookEventResponse(
        UUID id,
        UUID webhookId,
        String eventId,
        Instant receivedAt,
        JsonNode payload,
        UUID taskId,
        UUID escalationId
) {}
