package com.AlertOps.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "escalations")
@Getter
@Setter
public class Escalation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_name", unique = true, nullable = false)
    private String flowName;

    // Flow owner
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    private Instant startTime;

    private boolean startFlow;

    @Column(name = "created_at", updatable = false)
    @org.hibernate.annotations.CreationTimestamp
    private Date createdAt;

    // Ordered steps of escalation
    @OneToMany(mappedBy = "escalation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<EscalationStep> steps = new ArrayList<>();

    public boolean getStartFlow() {
        return  this.startFlow;
    }
}

