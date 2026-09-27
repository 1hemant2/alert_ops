package com.alertops.team.repository;

import java.util.UUID;

public interface TeamMemberProjection {
    UUID getMemberId();
    UUID getUserId();
    String getName();
    String getEmail();
    String getRole();
}
