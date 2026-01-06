package com.alertops.team.dto;

public class DeleteUserRole {
    Long userId;
    String userRole;
    public Long getUserId() {
        return userId;
    }
    public String getUserRole() {
        return userRole;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
}
