package com.alertops.team.dto;

public class UpdateUserRole {
    private  Long userId;
    private String existingRole;
    private String newRole;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getExistingRole() {
        return existingRole;
    }

    public void setExistingRole(String existingRole) {
        this.existingRole = existingRole;
    }

    public String getNewRole() {
        return newRole;
    }

    public void setNewRole(String newRole) {
        this.newRole = newRole;
    }
}
