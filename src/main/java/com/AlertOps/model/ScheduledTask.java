package com.AlertOps.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
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
    private Instant nextFireAt;
    
    
    private String status; // PENDING|QUEUED|PROCESSING|DONE|FAILED
    
    
    private boolean queued;
    
    
    private int attempts;
    
    
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    
}
