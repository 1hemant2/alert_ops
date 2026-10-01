package com.alertops.webhook.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alertops.webhook.dto.CreateWebhookRequest;
import com.alertops.webhook.dto.UpdateWebhookRequest;
import com.alertops.webhook.dto.WebhookConfigurationResponse;
import com.alertops.webhook.dto.WebhookEventResponse;
import com.alertops.webhook.service.WebhookService;

@RestController
@RequestMapping("/api/v1/team/webhooks")
public class WebhookConfigurationController {
    private final WebhookService webhookService;

    public WebhookConfigurationController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<WebhookConfigurationResponse> create(@RequestBody CreateWebhookRequest request) {
        return ResponseEntity.status(201).body(webhookService.createWebhook(request));
    }

    @GetMapping
    public List<WebhookConfigurationResponse> list() {
        return webhookService.listWebhooks();
    }

    @PostMapping("/{webhookId}/rotate")
    public WebhookConfigurationResponse rotate(@PathVariable UUID webhookId) {
        return webhookService.rotateSecret(webhookId);
    }

    @PatchMapping("/{webhookId}")
    public WebhookConfigurationResponse update(@PathVariable UUID webhookId,
                                               @RequestBody UpdateWebhookRequest request) {
        return webhookService.updateWebhook(webhookId, request);
    }

    @GetMapping("/{webhookId}/events")
    public List<WebhookEventResponse> events(@PathVariable UUID webhookId) {
        return webhookService.listEvents(webhookId);
    }

    @GetMapping("/{webhookId}/events/{eventId}")
    public WebhookEventResponse event(@PathVariable UUID webhookId, @PathVariable UUID eventId) {
        return webhookService.getEvent(webhookId, eventId);
    }
}
