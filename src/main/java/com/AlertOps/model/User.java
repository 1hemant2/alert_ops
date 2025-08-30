package com.AlertOps.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.AlertOps.model.Role;

import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    //this row column will not be updated.
    @Column(name = "created_at", updatable = false)
    @org.hibernate.annotations.CreationTimestamp // auto create a new date
    private Date createdAt;
}
