package com.AlertOps.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Entity
@Table(name = "escalation_steps")
@Getter
@Setter
public class EscalationStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int stepOrder; // 1st, 2nd, 3rd in chain

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalation_id", nullable = false)
    private Escalation escalation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
