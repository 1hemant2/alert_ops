
package com.alertops.flow_execution_engine.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "escalation")
public class Escalation {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private String name;

    private UUID taskId;

    private UUID flowId;
    
    private String status;

    private UUID teamId;

    private String resolutionType;
   
    private Instant createdAt;

    private Instant updatedAt;

    private String issueSolvedBy;

    private Instant acknowledgedAt;

    private Instant scheduledStartAt;

    private String scheduleTimezone;

    @Column(name = "scheduled_start_retry_count", nullable = false)
    private int scheduledStartRetryCount;

    @Column(name = "scheduled_start_next_retry_at")
    private Instant scheduledStartNextRetryAt;

    private Instant cancelledAt;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getFlowId() {
        return flowId;
    }

    public void setFlowId(UUID flowId) {
        this.flowId = flowId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public String getResolutionType() {
        return resolutionType;
    }

    public void setResolutionType(String resolutionType) {
        this.resolutionType = resolutionType;
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

    public String getIssueSolvedBy() {
        return issueSolvedBy;
    }

    public void setIssueSolvedBy(String issueSolvedBy) {
        this.issueSolvedBy = issueSolvedBy;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public Instant getScheduledStartAt() {
        return scheduledStartAt;
    }

    public void setScheduledStartAt(Instant scheduledStartAt) {
        this.scheduledStartAt = scheduledStartAt;
    }

    public String getScheduleTimezone() {
        return scheduleTimezone;
    }

    public void setScheduleTimezone(String scheduleTimezone) {
        this.scheduleTimezone = scheduleTimezone;
    }

    public int getScheduledStartRetryCount() {
        return scheduledStartRetryCount;
    }

    public void setScheduledStartRetryCount(int scheduledStartRetryCount) {
        this.scheduledStartRetryCount = scheduledStartRetryCount;
    }

    public Instant getScheduledStartNextRetryAt() {
        return scheduledStartNextRetryAt;
    }

    public void setScheduledStartNextRetryAt(Instant scheduledStartNextRetryAt) {
        this.scheduledStartNextRetryAt = scheduledStartNextRetryAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    
}
