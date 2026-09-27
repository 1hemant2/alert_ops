package com.alertops.team.repository;

import java.util.UUID;

public interface TeamListItemProjection {
    UUID getId();
    String getName();
    String getRole();
}
