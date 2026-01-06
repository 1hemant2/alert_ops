package com.alertops.dto.task;


public class TaskResponseDto {
    private Long id;
    private String name;
    private String description; // fixed lowercase
    private Long userId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public TaskResponseDto(Long id, String name, String description, Long userId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.userId = userId;
    }
}
