package com.AlertOps.dto.task;

import com.AlertOps.model.Task;
import lombok.Data;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
public class UserTasksResponseDto {
    private Long id;
    private String name;
    private String description;

    public UserTasksResponseDto(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }
}
