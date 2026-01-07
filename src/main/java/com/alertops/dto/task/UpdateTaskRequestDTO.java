package com.alertops.dto.task;

import java.util.UUID;

public class UpdateTaskRequestDTO {
    private  UUID id;
    private  String name;
    private String Description;

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
        return Description;
    }

    public void setDescription(String description) {
        Description = description;
    }
}
