package com.alertops.audit.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable append-only audit row with no dependency on a domain entity. */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEventEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "previous_state", length = 64)
    private String previousState;

    @Column(name = "new_state", length = 64)
    private String newState;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 500)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(AuditEvent event) {
        AuditEvent source = Objects.requireNonNull(event, "event");
        AuditEntityType entityType = Objects.requireNonNull(source.entityType(), "event.entityType");
        AuditAction action = Objects.requireNonNull(source.action(), "event.action");
        this.entityType = entityType.name();
        this.entityId = source.entityId();
        this.action = action.name();
        this.previousState = source.previousState();
        this.newState = source.newState();
        this.userId = source.userId();
        this.userEmail = source.userEmail();
        this.occurredAt = source.occurredAt();
        this.reason = source.reason();
        this.metadata = source.metadata();
    }

    public UUID getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getPreviousState() {
        return previousState;
    }

    public String getNewState() {
        return newState;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getReason() {
        return reason;
    }

    public String getMetadata() {
        return metadata;
    }
}
