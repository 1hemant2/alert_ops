package com.alertops.auth.dto;

import com.alertops.team.model.Role;


import java.util.Set;

public class UserDto {
    private  String name;
    private  String email;
    private Long id;

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


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
