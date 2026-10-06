package com.alertops.flow_execution_engine.model;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
public class FlowExecutionState {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlowExecutionStepStatus status;
    private UUID taskId;
    @Column(name = "task_name", length = 120)
    private String taskName;
    @Column(name = "task_source", length = 120)
    private String taskSource;
    @Column(name = "task_details", columnDefinition = "TEXT")
    private String taskDetails;
    private UUID nodeId;
    private String userEmail;
    private Duration duration;
    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "resolution_timeout_enabled", nullable = false)
    private boolean resolutionTimeoutEnabled;

    @Column(name = "resolution_timeout")
    private Duration resolutionTimeout;

    @Column(name = "publication_pending", nullable = false)
    private boolean publicationPending;
    private BigInteger position;
    private UUID processId;
    private Instant createdAt;
    private Instant updatedAt;

    // rule: is retry behavior enabled for this node?
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean retryOnFailureEnabled;

    // rule: maximum retry attempts allowed (excluding first send)
    @Column(nullable = false, columnDefinition = "int default 0")
    private int maxRetryAttempts;

    // state: total number of send attempts already made
    @Column(nullable = false, columnDefinition = "int default 0")
    private int sendAttemptCount;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public FlowExecutionStepStatus getStatus() {
        return status;
    }

    public void setStatus(FlowExecutionStepStatus status) {
        this.status = status;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskSource() { return taskSource; }
    public void setTaskSource(String taskSource) { this.taskSource = taskSource; }

    public String getTaskDetails() {
        return taskDetails;
    }

    public void setTaskDetails(String taskDetails) {
        this.taskDetails = taskDetails;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public boolean isResolutionTimeoutEnabled() {
        return resolutionTimeoutEnabled;
    }

    public void setResolutionTimeoutEnabled(boolean resolutionTimeoutEnabled) {
        this.resolutionTimeoutEnabled = resolutionTimeoutEnabled;
    }

    public Duration getResolutionTimeout() {
        return resolutionTimeout;
    }

    public void setResolutionTimeout(Duration resolutionTimeout) {
        this.resolutionTimeout = resolutionTimeout;
    }

    public boolean isPublicationPending() {
        return publicationPending;
    }

    public void setPublicationPending(boolean publicationPending) {
        this.publicationPending = publicationPending;
    }

    public BigInteger getPosition() {
        return position;
    }

    public void setPosition(BigInteger position) {
        this.position = position;
    }

    public UUID getProcessId() {
        return processId;
    }

    public void setProcessId(UUID processId) {
        this.processId = processId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isRetryOnFailureEnabled() {
        return retryOnFailureEnabled;
    }

    public void setRetryOnFailureEnabled(boolean retryOnFailureEnabled) {
        this.retryOnFailureEnabled = retryOnFailureEnabled;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public int getSendAttemptCount() {
        return sendAttemptCount;
    }

    public void setSendAttemptCount(int sendAttemptCount) {
        this.sendAttemptCount = sendAttemptCount;
    }

}
