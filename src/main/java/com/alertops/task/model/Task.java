package com.alertops.task.model;

import jakarta.persistence.*;

import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@SQLRestriction("deleted = false")
public class Task {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private  String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false, length = 120)
    private String source;
    @Column(length = 20)
    private String priority;
    @Column(length = 80)
    private String category;
    @Column(name = "reference_url", length = 2048)
    private String referenceUrl;

    private UUID teamId;
    
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;


    private  Boolean deleted = false;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getReferenceUrl() {
        return referenceUrl;
    }

    public void setReferenceUrl(String referenceUrl) {
        this.referenceUrl = referenceUrl;
    }

    

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

        public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

}
