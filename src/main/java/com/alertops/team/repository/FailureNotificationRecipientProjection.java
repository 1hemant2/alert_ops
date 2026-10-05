package com.alertops.team.repository;

import java.util.UUID;

public interface FailureNotificationRecipientProjection {
    UUID getUserId();

    String getEmail();
}
