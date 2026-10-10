
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
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscalationStatus status;

    private UUID teamId;

    @Enumerated(EnumType.STRING)
    private EscalationResolutionType resolutionType;
   
    private Instant createdAt;

    private Instant updatedAt;

    private String issueSolvedBy;

    private Instant acknowledgedAt;

    @Column(name = "acknowledged_step_id")
    private UUID acknowledgedStepId;

    @Column(name = "resolution_deadline")
    private Instant resolutionDeadline;

    @Column(name = "resolved_by", length = 320)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    private Instant scheduledStartAt;

    private String scheduleTimezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type", nullable = false)
    private RepeatType repeatType = RepeatType.NONE;

    @Column(name = "next_repeat_at")
    private Instant nextRepeatAt;

    @Column(name = "repeat_source_id")
    private UUID repeatSourceId;

    @Column(name = "scheduled_start_retry_count", nullable = false)
    private int scheduledStartRetryCount;

    @Column(name = "scheduled_start_next_retry_at")
    private Instant scheduledStartNextRetryAt;

    private Instant cancelledAt;

    @Column(name = "scheduled_by_user_id")
    private UUID scheduledByUserId;

    @Column(name = "scheduled_by_user_email", length = 320)
    private String scheduledByUserEmail;

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

    public EscalationStatus getStatus() {
        return status;
    }

    public void setStatus(EscalationStatus status) {
        this.status = status;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public EscalationResolutionType getResolutionType() {
        return resolutionType;
    }

    public void setResolutionType(EscalationResolutionType resolutionType) {
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

    public UUID getAcknowledgedStepId() {
        return acknowledgedStepId;
    }

    public void setAcknowledgedStepId(UUID acknowledgedStepId) {
        this.acknowledgedStepId = acknowledgedStepId;
    }

    public Instant getResolutionDeadline() {
        return resolutionDeadline;
    }

    public void setResolutionDeadline(Instant resolutionDeadline) {
        this.resolutionDeadline = resolutionDeadline;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
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

    // Returns the calendar rule owned by the original escalation.
    public RepeatType getRepeatType() {
        return repeatType;
    }

    // Stores the calendar rule for this escalation row.
    public void setRepeatType(RepeatType repeatType) {
        this.repeatType = repeatType;
    }

    // Returns the next repeat boundary that has not been materialized.
    public Instant getNextRepeatAt() {
        return nextRepeatAt;
    }

    // Stores the next durable repeat boundary.
    public void setNextRepeatAt(Instant nextRepeatAt) {
        this.nextRepeatAt = nextRepeatAt;
    }

    // Returns the original escalation ID for a generated occurrence.
    public UUID getRepeatSourceId() {
        return repeatSourceId;
    }

    // Links a generated occurrence to its original escalation.
    public void setRepeatSourceId(UUID repeatSourceId) {
        this.repeatSourceId = repeatSourceId;
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

    public UUID getScheduledByUserId() {
        return scheduledByUserId;
    }

    public void setScheduledByUserId(UUID scheduledByUserId) {
        this.scheduledByUserId = scheduledByUserId;
    }

    public String getScheduledByUserEmail() {
        return scheduledByUserEmail;
    }

    public void setScheduledByUserEmail(String scheduledByUserEmail) {
        this.scheduledByUserEmail = scheduledByUserEmail;
    }

    
}
