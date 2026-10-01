package com.alertops.task.interfaces;

import java.time.Instant;
import java.util.UUID;

public interface TaskView {
    UUID getId();
    String getName();
    String getDescription();
    String getSource();
    String getPriority();
    String getCategory();
    String getReferenceUrl();
    Instant getCreatedAt();
}
