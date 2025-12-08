package com.alertops.dto.user;

import com.alertops.model.Role;


import java.util.Set;

public class UserDto {
    private  String name;
    private  String email;
    private  String userName;
    private Long id;
   // private List<EscalationResDto> escalations;
    private Set<Role> roles;
   // private

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }
}
