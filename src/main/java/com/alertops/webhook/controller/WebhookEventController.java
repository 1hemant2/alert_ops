package com.alertops.webhook.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alertops.webhook.dto.WebhookTriggerResponse;
import com.alertops.webhook.service.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookEventController {
    private final WebhookService webhookService;

    public WebhookEventController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/{webhookId}/events")
    public ResponseEntity<WebhookTriggerResponse> receive(
            @PathVariable UUID webhookId,
            @RequestHeader(value = "X-AlertOps-Webhook-Secret", required = false) String secret,
            @RequestBody JsonNode payload) {
        WebhookTriggerResponse result = webhookService.receiveEvent(webhookId, secret, payload);
        return ResponseEntity.status(result.replayed() ? 200 : 202).body(result);
    }
}
