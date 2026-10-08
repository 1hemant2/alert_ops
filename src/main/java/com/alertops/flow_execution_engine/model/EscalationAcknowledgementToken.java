package com.alertops.flow_execution_engine.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "escalation_acknowledgement_token")
public class EscalationAcknowledgementToken {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "escalation_id", nullable = false)
    private UUID escalationId;

    @Column(name = "execution_step_id")
    private UUID executionStepId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 32)
    private EscalationActionCapability capability = EscalationActionCapability.ACKNOWLEDGE;

    @Column(name = "expected_target_step_id")
    private UUID expectedTargetStepId;

    public UUID getId() {
        return id;
    }

    public UUID getEscalationId() {
        return escalationId;
    }

    public void setEscalationId(UUID escalationId) {
        this.escalationId = escalationId;
    }

    public UUID getExecutionStepId() {
        return executionStepId;
    }

    public void setExecutionStepId(UUID executionStepId) {
        this.executionStepId = executionStepId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    // Returns the permission carried by this recipient token.
    public EscalationActionCapability getCapability() {
        return capability;
    }

    // Sets the permission carried by this recipient token.
    public void setCapability(EscalationActionCapability capability) {
        this.capability = capability;
    }

    // Returns the exact next step this token was created to advance.
    public UUID getExpectedTargetStepId() {
        return expectedTargetStepId;
    }

    // Sets the exact next step this token was created to advance.
    public void setExpectedTargetStepId(UUID expectedTargetStepId) {
        this.expectedTargetStepId = expectedTargetStepId;
    }
}
