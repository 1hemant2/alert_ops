package com.alertops.task.task;

import java.util.UUID;

public class TaskResponseDto {
    private UUID id;
    private String name;
    private String description; // fixed lowercase
    private String source;
    private String priority;
    private String category;
    private String referenceUrl;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskResponseDto(UUID id, String name, String description, String source, String priority,
                           String category, String referenceUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.source = source;
        this.priority = priority;
        this.category = category;
        this.referenceUrl = referenceUrl;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getReferenceUrl() { return referenceUrl; }
    public void setReferenceUrl(String referenceUrl) { this.referenceUrl = referenceUrl; }
}
