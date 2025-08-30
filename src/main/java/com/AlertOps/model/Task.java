package com.AlertOps.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "users")
public class Task {
    @Id
    @GeneratedValue
    private  long id;
    private  String name;
    private String description;

    //tells JPA that many roles can belong to one user. LAZY: avoids loading the user unless needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) //maps the foreign key column.
    private User user;

    @Column(name = "created_at", insertable = false)
    private Date createdAt;

    private  Boolean deleted;
}
