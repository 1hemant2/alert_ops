package com.alertops.webhook.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.alertops.webhook.model.WebhookConfiguration;

import jakarta.persistence.LockModeType;

public interface WebhookConfigurationRepository extends JpaRepository<WebhookConfiguration, UUID> {
    List<WebhookConfiguration> findAllByTeamIdOrderByCreatedAtDesc(UUID teamId);

    Optional<WebhookConfiguration> findByIdAndTeamId(UUID id, UUID teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WebhookConfiguration w where w.id = :id")
    Optional<WebhookConfiguration> findByIdForUpdate(@Param("id") UUID id);
}
