package com.AlertOps.model;

import jakarta.persistence.*;

import java.util.Date;

public class Escalations {
    @Id
    @GeneratedValue
    private  long id;

    @Column(name = "task_name")
    private  String taskName;

    @Column(name = "user_name")
    private  String userName;

    @Column(name = "user_email")
    private String userEmail;

    //tells JPA that many roles can belong to one user. LAZY: avoids loading the user unless needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) //maps the foreign key column.
    private User user;

    @Column(name = "created_at", insertable = false)
    private Date createdAt;
}
