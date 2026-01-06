/*
package com.alertops.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "scheduled_task")
public class ScheduledTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private UUID externalId = UUID.randomUUID();

    private String email;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "next_fire_at")
    private Long nextFireAt;

    private String status; // PENDING|QUEUED|PROCESSING|DONE|FAILED

    private boolean queued;

    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getExternalId() {
        return externalId;
    }

    public void setExternalId(UUID externalId) {
        this.externalId = externalId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getNextFireAt() {
        return nextFireAt;
    }

    public void setNextFireAt(Long nextFireAt) {
        this.nextFireAt = nextFireAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isQueued() {
        return queued;
    }

    public void setQueued(boolean queued) {
        this.queued = queued;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
*/
