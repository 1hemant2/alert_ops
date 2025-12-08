package com.alertops.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Where;

import java.util.Date;

@Entity
@Table(name = "tasks")
@Where(clause = "deleted = false")
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
