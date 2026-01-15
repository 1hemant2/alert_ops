package com.alertops.permissions;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

public final class RolePermissionRegistry {

    private static final EnumMap<Role, Set<Permission>> ROLE_PERMISSIONS = new EnumMap<>(Role.class);

    static {
        ROLE_PERMISSIONS.put(
                Role.TEAM_OWNER,
                EnumSet.of(
                        Permission.DELETE_TEAM,
                        Permission.EDIT_TEAM,
                        Permission.DELETE_MEMBER,
                        Permission.INVITE_USER,
                        Permission.CREATE_TASK,
                        Permission.EDIT_TASK,
                        Permission.DELETE_TASK,
                        Permission.READ_TASK,
                        Permission.SELECT_TEAM
                )
        );

        ROLE_PERMISSIONS.put(
                Role.ADMIN,
                EnumSet.of(
                        Permission.INVITE_USER,
                        Permission.CREATE_TASK,
                        Permission.EDIT_TASK,
                        Permission.DELETE_TASK,
                        Permission.READ_TASK,
                        Permission.DELETE_MEMBER,
                        Permission.SELECT_TEAM
                )
        );

        ROLE_PERMISSIONS.put(
                Role.USER,
                EnumSet.of(
                        Permission.CREATE_TASK,
                        Permission.EDIT_TASK,
                        Permission.DELETE_TASK,
                        Permission.READ_TASK,
                        Permission.SELECT_TEAM
                )
        );

        ROLE_PERMISSIONS.put(
                Role.NONE,
                EnumSet.of(Permission.NONE)
        );
    }


    public static boolean hasPermission(Role role, Permission permission) {
        return ROLE_PERMISSIONS
                .getOrDefault(role, Set.of())
                .contains(permission);
    }
}