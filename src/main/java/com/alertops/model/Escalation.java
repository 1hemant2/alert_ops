/*
package com.alertops.model;

import com.alertops.auth.model.User;
import jakarta.persistence.*;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "escalations")

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public boolean isStartFlow() {
        return startFlow;
    }

    public void setStartFlow(boolean startFlow) {
        this.startFlow = startFlow;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public List<EscalationStep> getSteps() {
        return steps;
    }

    public void setSteps(List<EscalationStep> steps) {
        this.steps = steps;
    }
}

*/
