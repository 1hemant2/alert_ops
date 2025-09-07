package com.AlertOps.dto.task;

import lombok.Data;

@Data
public class UpdateTaskRequestDTO {
    private  Long id;
    private  String name;
    private String Description;
}
