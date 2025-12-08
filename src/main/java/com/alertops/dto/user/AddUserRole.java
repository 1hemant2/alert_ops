package com.alertops.dto.user;


public class AddUserRole {
    Long userId;
    String roleName;
    public Long getUserId() {
        return userId;
    }
    public  String getRoleName() {
        return roleName;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
