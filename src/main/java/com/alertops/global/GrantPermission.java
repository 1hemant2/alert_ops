package com.alertops.global;

import org.springframework.stereotype.Component;

@Component
public class GrantPermission {
    public void grant(String role, String permission) {
        if(!RolePermissionRegistry.hasPermission(Role.valueOf(role), Permission.valueOf(permission))) {
            throw new RuntimeException("you are not authrized to perform this operation");
        }
    }
}
