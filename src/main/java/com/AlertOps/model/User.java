package com.AlertOps.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.AlertOps.model.Role;

@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue
  private  long id;
  private  String name;
  private  String email;
  private  String password;

  @Column(name = "created_at", insertable = false)
  private Date createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Role> roles = new ArrayList<>();
}
