package com.AlertOps.dto.task;

import lombok.Data;

@Data
public class CreateTask {
    private Long userId;
    private String name;
    private String description;
}
