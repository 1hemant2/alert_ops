package com.alertops.team.dto;


public class InviteDtoReq {
    private String email;
    private String role;
    private Long ttl;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getTtl() {
        return ttl;
    }

}
