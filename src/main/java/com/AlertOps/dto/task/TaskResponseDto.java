package com.AlertOps.dto.task;

import lombok.Data;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
@Data
public class TaskResponseDto {
    private Long id;
    private String name;
    private String description; // fixed lowercase
    private Long userId;
}
