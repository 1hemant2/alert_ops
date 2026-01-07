package com.alertops.task.model;

import jakarta.persistence.*;

import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.Where;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Where(clause = "deleted = false")
public class Task {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private  String name;
    private String description;

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

