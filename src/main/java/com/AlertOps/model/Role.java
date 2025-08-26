package com.AlertOps.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue
    private  long id;
    private  String name;

    //tells JPA that many roles can belong to one user. LAZY: avoids loading the user unless needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) //maps the foreign key column.
    private User user;

    @Column(name = "created_at", insertable = false)
    private Date createdAt;
}
