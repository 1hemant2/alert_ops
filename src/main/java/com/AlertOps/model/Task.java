package com.AlertOps.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Where;

import java.util.Date;

@Entity
@Table(name = "tasks")
@Where(clause = "deleted = false")
@Data
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Long id;
    private  String name;
    private String description;

    //tells JPA that many roles can belong to one user. LAZY: avoids loading the user unless needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) //maps the foreign key column.
    private User user;

    @Column(name = "created_at", insertable = false)
    private Date createdAt;
    @Column(nullable = false)
    private  Boolean deleted = false;
}
