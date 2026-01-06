package com.alertops.security;

import java.util.UUID;

public class AuthContext {
    private UUID userId;
    private UUID teamId;
    private  String role;
    private String token;
    private String email;


    public AuthContext(UUID userId, UUID teamId, String role, String token, String email) {
        this.userId = userId;
        this.teamId = teamId;
        this.role = role;
        this.token = token;
        this.email = email;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
