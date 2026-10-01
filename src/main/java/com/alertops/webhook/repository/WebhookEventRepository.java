package com.alertops.webhook.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alertops.webhook.model.WebhookEvent;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    Optional<WebhookEvent> findByWebhookIdAndEventId(UUID webhookId, String eventId);
    List<WebhookEvent> findAllByWebhookIdOrderByReceivedAtDesc(UUID webhookId);
    Optional<WebhookEvent> findByIdAndWebhookId(UUID id, UUID webhookId);
}
