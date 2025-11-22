package com.AlertOps.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "teams")
@Data
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private Date createdAt;

    @ManyToMany(mappedBy = "teams", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<User> users = new HashSet<>();
}
