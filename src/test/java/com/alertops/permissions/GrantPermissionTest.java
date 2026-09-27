package com.alertops.permissions;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrantPermissionTest {
    private final GrantPermission grantPermission = new GrantPermission();

    @Test
    void allowsTeamOwnersAndAdminsToInvite() {
        assertDoesNotThrow(() -> grantPermission.grant("TEAM_OWNER", "INVITE_USER"));
        assertDoesNotThrow(() -> grantPermission.grant("ADMIN", "INVITE_USER"));
    }

    @Test
    void deniesMembersAndInvalidRoleNames() {
        assertThrows(AccessDeniedException.class, () -> grantPermission.grant("USER", "INVITE_USER"));
        assertThrows(AccessDeniedException.class, () -> grantPermission.grant("UNKNOWN", "INVITE_USER"));
        assertThrows(AccessDeniedException.class, () -> grantPermission.grant(null, "INVITE_USER"));
    }
}
