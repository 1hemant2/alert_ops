package com.alertops.permissions;

import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;

@Component
public class GrantPermission {
    public void grant(String role, String permission) {
        try {
            if (role == null || permission == null
                    || !RolePermissionRegistry.hasPermission(Role.valueOf(role), Permission.valueOf(permission))) {
                throw new AccessDeniedException("You are not authorized to perform this operation.");
            }
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("You are not authorized to perform this operation.");
        }
    }
}
